// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.verifier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.microsoft.z3.ArrayExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FPExpr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Quantifier;
import com.microsoft.z3.SeqExpr;
import com.microsoft.z3.Sort;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.Operator;
import dev.cel.common.ast.CelBlock;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelList;
import dev.cel.common.ast.CelExpr.ExprKind;
import dev.cel.common.types.CelKind;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.NullableType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Internal translator from CEL AST to Z3 SMT expressions using a 4-valued logic tagged union.
 *
 * <p><b>Lifetime & Mutability:</b> This translator is stateful and highly mutable. It accumulates
 * type constraints, loop variables (via its symbol table), and uninterpreted functions (via field
 * caches) during AST traversal.
 *
 * <ul>
 *   <li>It is <b>not thread-safe</b>.
 *   <li>It is strictly bound to the lifecycle of a single Z3 {@link Context}.
 *   <li>It should be instantiated once per verification task.
 *   <li>For equivalence checks involving multiple ASTs, the <b>same</b> translator instance must be
 *       used to translate all ASTs. This ensures that uninterpreted functions (like field accesses)
 *       and type constraints are correctly shared and unified across the expressions.
 * </ul>
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // Z3 Java API uses raw types.
final class CelAstToZ3Translator {
  private static final String MAP_REF_PREFIX = "!mapRef_";
  private static final String LIST_REF_PREFIX = "!listRef_";
  private static final String MSG_REF_PREFIX = "!msg_ref";
  private static final String EMPTY_MSG_REF_PREFIX = "!empty_msg_ref_";
  private static final String EMPTY_LIST_PREFIX = "!empty_list";
  private static final String EMPTY_MAP_PREFIX = "!empty_map";
  private static final String FUNCTION_PREFIX = "!fn_";
  private static final String NULL_VALUE_FIELD = "null_value";
  private static final String SECONDS_FIELD = "seconds";
  private static final String NANOS_FIELD = "nanos";
  private static final long MAX_UINT32 = 0xFFFFFFFFL;
  private final Context ctx;
  private final CelZ3TypeSystem typeSystem;
  private final CelZ3TranslationContext context;
  private final CelZ3TypeConstraintGenerator typeConstraintGenerator;
  private final CelZ3OperatorTranslator operatorTranslator;
  private final CelZ3ComprehensionTranslator comprehensionTranslator;
  private final ImmutableSet<String> unknownIdentifiers;
  private final CelTypeProvider typeProvider;
  private final Map<String, Expr<?>> emptyMessageCache;
  private final Map<Object, Expr<?>> listLiteralCache;
  private Expr<?> emptyListCache;
  private Expr<?> emptyMapCache;

  /**
   * Returns a set of Z3 boolean expressions representing type constraints for the translated AST.
   */
  Set<BoolExpr> getTypeConstraints() {
    return context.constraints();
  }

  BoolExpr hasTruncation() {
    return context.hasTruncation();
  }

  CelZ3TypeSystem getTypeSystem() {
    return typeSystem;
  }

  /**
   * Returns a Z3 boolean expression asserting that the provided CEL value is a boolean and is true.
   */
  BoolExpr isTrue(Expr<?> celValue) {
    return ctx.mkAnd(typeSystem.isBool(celValue), (BoolExpr) typeSystem.unwrapBool(celValue));
  }

  /**
   * Binds an identifier name in the symbol table to an already-translated value. Used to bind
   * reserved policy symbols such as 'rule.result' to the composed policy graph.
   */
  void bindSymbol(String varName, TranslatedValue value) {
    context.bindSymbol(varName, value);
  }

  TranslatedValue translate(CelAbstractSyntaxTree ast) {
    TranslatedValue result;
    CelBlock celBlock = CelBlock.extract(ast).orElse(null);
    if (celBlock != null) {
      result = translateBlock(celBlock.indices(), 0, celBlock.result(), ast);
    } else {
      result = translateExpr(ast.getExpr(), ast);
    }

    Set<Expr<?>> visited = new LinkedHashSet<>();
    Set<Expr<?>> listRefs = new LinkedHashSet<>();
    Set<Expr<?>> mapRefs = new LinkedHashSet<>();
    Set<Expr<?>> msgRefs = new LinkedHashSet<>();

    for (Expr<?> constraint : context.constraints()) {
      collectReferences(constraint, visited, listRefs, mapRefs, msgRefs);
    }
    collectReferences(result.z3Expr(), visited, listRefs, mapRefs, msgRefs);

    CelZ3ExtensionalityAxioms.generateAxioms(ctx, typeSystem, listRefs, mapRefs, msgRefs)
        .forEach(context::addConstraint);

    return result;
  }

  private void collectReferences(
      Expr<?> expr,
      Set<Expr<?>> visited,
      Set<Expr<?>> listRefs,
      Set<Expr<?>> mapRefs,
      Set<Expr<?>> msgRefs) {
    if (!visited.add(expr)) {
      return;
    }
    Sort sort = expr.getSort();
    if (sort.equals(typeSystem.listRefSort())) {
      listRefs.add(expr);
    } else if (sort.equals(typeSystem.mapRefSort())) {
      mapRefs.add(expr);
    } else if (sort.equals(typeSystem.messageRefSort())) {
      msgRefs.add(expr);
    }

    if (expr.isApp()) {
      for (Expr<?> arg : expr.getArgs()) {
        collectReferences(arg, visited, listRefs, mapRefs, msgRefs);
      }
    } else if (expr.isQuantifier()) {
      collectReferences(((Quantifier) expr).getBody(), visited, listRefs, mapRefs, msgRefs);
    }
  }

  private TranslatedValue translateExpr(CelExpr celExpr, CelAbstractSyntaxTree ast) {
    ExprKind.Kind kind = celExpr.exprKind().getKind();

    switch (kind) {
      case CONSTANT:
        return translateConstant(celExpr);
      case IDENT:
        return translateIdent(celExpr, ast);
      case SELECT:
        return translateSelect(celExpr, ast);
      case CALL:
        return translateCall(celExpr, ast);
      case LIST:
        return translateList(celExpr, ast);
      case MAP:
        return translateMap(celExpr, ast);
      case STRUCT:
        return translateStruct(celExpr, ast);
      case COMPREHENSION:
        return comprehensionTranslator.translateComprehension(celExpr, ast);
      default:
        throw new IllegalArgumentException("Unsupported expression kind: " + kind);
    }
  }

  private TranslatedValue translateConstant(CelExpr celExpr) {
    CelConstant constant = celExpr.constant();
    Expr<?> val;
    switch (constant.getKind()) {
      case BOOLEAN_VALUE:
        val = typeSystem.mkBool(constant.booleanValue());
        break;
      case INT64_VALUE:
        val = typeSystem.mkInt(constant.int64Value());
        break;
      case UINT64_VALUE:
        val = typeSystem.mkUint(constant.uint64Value().longValue());
        break;
      case DOUBLE_VALUE:
        val = typeSystem.mkDouble(constant.doubleValue());
        break;
      case STRING_VALUE:
        val = typeSystem.mkString(constant.stringValue());
        break;
      case BYTES_VALUE:
        val = typeSystem.mkBytes(constant.bytesValue().toStringUtf8());
        break;
      case NULL_VALUE:
        val = typeSystem.mkNull();
        break;
      default:
        throw new UnsupportedOperationException("Unsupported constant: " + constant.getKind());
    }
    return TranslatedValue.create(val, celExpr, typeSystem, ctx.mkFalse());
  }

  private TranslatedValue translateIdent(CelExpr celExpr, CelAbstractSyntaxTree ast) {
    CelExpr.CelIdent ident = celExpr.ident();
    long exprId = celExpr.id();
    String name = ident.name();
    CelType type = ast.getTypeOrThrow(exprId);
    if (type instanceof TypeType) {
      TypeType typeType = (TypeType) type;
      if (typeType.type().kind() != CelKind.DYN) {
        return TranslatedValue.create(
            typeSystem.mkString(typeType.containingTypeName()), celExpr, typeSystem, ctx.mkFalse());
      }
    }
    TranslatedValue tv =
        context.computeSymbolIfAbsent(
            name,
            n -> {
              Expr<?> v = ctx.mkConst(n, typeSystem.celValueSort());
              context.addInputVariable(v, type);

              // Variables at rest can never be pre-cooked Errors
              // Basically prevents error being a counterexample of var == var
              context.addConstraint(ctx.mkNot(typeSystem.isError(v)));

              BoolExpr constraint = typeConstraintGenerator.createTypeConstraint(v, exprId, ast);
              if (unknownIdentifiers.contains(name)) {
                context.addConstraint(ctx.mkOr(typeSystem.isUnknown(v), constraint));
              } else {
                context.addConstraint(ctx.mkNot(typeSystem.isUnknown(v)));
                context.addConstraint(constraint);
              }

              return TranslatedValue.create(
                  v,
                  CelExpr.newBuilder().setIdent(ident).setId(exprId).build(),
                  typeSystem,
                  /* isApproximate= */ ctx.mkFalse());
            });

    return TranslatedValue.create(tv.z3Expr(), celExpr, typeSystem, tv.isApproximate());
  }

  private TranslatedValue translateList(CelExpr celExpr, CelAbstractSyntaxTree ast) {
    CelList createList = celExpr.list();
    Optional<Object> cacheKey = toCacheKey(celExpr);
    List<TranslatedValue> elementsTv = new ArrayList<>();
    Expr<?> listRef = cacheKey.map(listLiteralCache::get).orElse(null);

    // Cache list literals containing only constants. If the same list literal appears multiple
    // times (e.g., in equality checks like `[1, 2] == [1, 2]`), caching allows us to reuse the
    // same Z3 constant. This avoids expensive sequence equality reasoning in Z3 by reducing the
    // check to a trivial identity check (e.g., `list_ref_0 == list_ref_0`).
    if (listRef == null) {
      SeqExpr seq = ctx.mkEmptySeq(ctx.mkSeqSort(typeSystem.celValueSort()));
      List<Expr<?>> values = new ArrayList<>();
      ImmutableList<Integer> optionalIndices = createList.optionalIndices();
      ImmutableList<CelExpr> elements = createList.elements();
      for (int i = 0; i < elements.size(); i++) {
        CelExpr element = elements.get(i);
        TranslatedValue elem = translateExpr(element, ast);

        if (optionalIndices.contains(i)) {
          Expr<?> checkedValue =
              typeSystem.withRuntimeError(
                  elem.z3Expr(), ctx.mkNot(typeSystem.isOptional(elem.z3Expr())));
          elem = TranslatedValue.create(checkedValue, element, typeSystem, elem.isApproximate());

          Expr<?> optRef = typeSystem.getOptionalRef(elem.z3Expr());
          Expr<?> value = typeSystem.getOptionalValue(optRef);
          seq =
              (SeqExpr)
                  ctx.mkITE(
                      typeSystem.optHasValue(optRef),
                      typeSystem.mkConcatSafe(seq, ctx.mkUnit(value)),
                      seq);
          values.add(value);
        } else {
          seq = typeSystem.mkConcatSafe(seq, ctx.mkUnit(elem.z3Expr()));
          values.add(elem.z3Expr());
        }
        elementsTv.add(elem);
      }
      listRef = typeSystem.mkListRefConst(LIST_REF_PREFIX);
      context.addConstraint(ctx.mkEq(typeSystem.getSeq(listRef), seq));
      propagateTruncatedTypeConstraint(typeSystem.wrapList(listRef), values);
      Expr<?> finalListRef = listRef;
      cacheKey.ifPresent(key -> listLiteralCache.put(key, finalListRef));
    }

    Expr<?> result = typeSystem.wrapList(listRef);
    return TranslatedValue.propagateStrict(ctx, typeSystem, result, celExpr, elementsTv);
  }

  private TranslatedValue translateMap(CelExpr celExpr, CelAbstractSyntaxTree ast) {
    CelExpr.CelMap createMap = celExpr.map();
    Expr<?> mapRef = typeSystem.mkMapRefConst(MAP_REF_PREFIX);

    ArrayExpr mapValues = ctx.mkConstArray(typeSystem.celValueSort(), typeSystem.mkUnknown());
    ArrayExpr mapPresence = ctx.mkConstArray(typeSystem.celValueSort(), ctx.mkFalse());
    Expr<?> keysSeq = ctx.mkEmptySeq(ctx.mkSeqSort(typeSystem.celValueSort()));

    List<TranslatedValue> elementsTv = new ArrayList<>();
    List<Expr<?>> values = new ArrayList<>();

    for (CelExpr.CelMap.Entry entryAst : createMap.entries()) {
      TranslatedValue keyTv = translateExpr(entryAst.key(), ast);
      Expr<?> key = keyTv.z3Expr();
      elementsTv.add(keyTv);
      TranslatedValue valueTv = translateExpr(entryAst.value(), ast);
      Expr<?> value = valueTv.z3Expr();

      Expr<?> finalValue = value;
      BoolExpr finalPresence = ctx.mkTrue();
      if (entryAst.optionalEntry()) {
        Expr<?> checkedValue =
            typeSystem.withRuntimeError(value, ctx.mkNot(typeSystem.isOptional(value)));
        valueTv =
            TranslatedValue.create(
                checkedValue, entryAst.value(), typeSystem, valueTv.isApproximate());
        Expr<?> optRef = typeSystem.getOptionalRef(checkedValue);
        finalPresence = typeSystem.optHasValue(optRef);
        finalValue = typeSystem.getOptionalValue(optRef);
      }
      elementsTv.add(valueTv);
      values.add(finalValue);

      BoolExpr keyAlreadyPresent = (BoolExpr) ctx.mkSelect(mapPresence, key);
      BoolExpr shouldInsertKey = ctx.mkAnd(ctx.mkNot(keyAlreadyPresent), finalPresence);
      keysSeq =
          ctx.mkITE(shouldInsertKey, typeSystem.mkConcatSafe(keysSeq, ctx.mkUnit(key)), keysSeq);

      mapValues =
          (ArrayExpr) ctx.mkITE(finalPresence, ctx.mkStore(mapValues, key, finalValue), mapValues);
      mapPresence =
          (ArrayExpr)
              ctx.mkITE(finalPresence, ctx.mkStore(mapPresence, key, ctx.mkTrue()), mapPresence);
    }

    context.addConstraint(ctx.mkEq(typeSystem.getMapValues(mapRef), mapValues));
    context.addConstraint(ctx.mkEq(typeSystem.getMapPresence(mapRef), mapPresence));
    context.addConstraint(ctx.mkEq(typeSystem.getMapKeys(mapRef), keysSeq));

    Expr<?> result = typeSystem.wrapMap(mapRef);
    propagateTruncatedTypeConstraint(result, values);
    return TranslatedValue.propagateStrict(ctx, typeSystem, result, celExpr, elementsTv);
  }

  /**
   * Marks {@code container} as having a truncated type constraint if any of {@code elements} has
   * one, unless it is an error or unknown: those are shared terms that hold no elements.
   */
  private void propagateTruncatedTypeConstraint(Expr<?> container, Iterable<Expr<?>> elements) {
    BoolExpr hasTruncated =
        ctx.mkOr(
            typeSystem.isErrorOrUnknown(container),
            typeSystem.hasTruncatedTypeConstraint(container));
    for (Expr<?> element : elements) {
      context.addConstraint(
          ctx.mkImplies(typeSystem.hasTruncatedTypeConstraint(element), hasTruncated));
    }
  }

  private TranslatedValue translateStruct(CelExpr celExpr, CelAbstractSyntaxTree ast) {
    CelExpr.CelStruct createStruct = celExpr.struct();
    if (isJsonWkt(createStruct.messageName())) {
      return translateJsonWktStruct(celExpr, createStruct, ast);
    }
    if (CelTypes.isWrapperType(createStruct.messageName())) {
      return translateWrapperWktStruct(celExpr, createStruct, ast);
    }
    if (createStruct.messageName().equals(CelTypes.TIMESTAMP_MESSAGE)) {
      return translateTimestampWktStruct(celExpr, createStruct, ast);
    }
    if (createStruct.messageName().equals(CelTypes.DURATION_MESSAGE)) {
      return translateDurationWktStruct(celExpr, createStruct, ast);
    }

    // Bypass SMT when the struct is empty (return the cached SMT default pointer)
    if (createStruct.entries().isEmpty()) {
      return TranslatedValue.create(
          getDefaultValueForType(StructTypeReference.create(createStruct.messageName())),
          celExpr,
          typeSystem,
          ctx.mkFalse());
    }

    Expr<?> msgRef = typeSystem.mkMessageRefConst(MSG_REF_PREFIX);

    // Initialize the values array with Unknown. Missing fields and explicit primitive defaults
    // will both bypass the store and fall back to this Unknown base. However, their msgPresence
    // will evaluate to false, allowing translateSelect to properly return the default value.
    ArrayExpr msgValues = ctx.mkConstArray(ctx.getStringSort(), typeSystem.mkUnknown());
    ArrayExpr msgPresence = ctx.mkConstArray(ctx.getStringSort(), ctx.mkFalse());

    List<TranslatedValue> elementsTv = new ArrayList<>();

    for (CelExpr.CelStruct.Entry entryAst : createStruct.entries()) {
      Expr<?> key = ctx.mkString(entryAst.fieldKey());
      TranslatedValue valueTv = translateExpr(entryAst.value(), ast);
      Expr<?> value = valueTv.z3Expr();

      CelType fieldType =
          typeProvider
              .findType(createStruct.messageName())
              .filter(t -> t instanceof StructType)
              .map(t -> (StructType) t)
              .flatMap(t -> t.findField(entryAst.fieldKey()))
              .map(StructType.Field::type)
              .orElseGet(() -> extractAstTypeOrDefault(ast, entryAst.value().id()));
      Expr<?> defaultVal = getDefaultValueForType(fieldType);

      Expr<?> finalValue = value;
      BoolExpr optionalHasValue = ctx.mkTrue();
      if (entryAst.optionalEntry()) {
        Expr<?> checkedValue =
            typeSystem.withRuntimeError(value, ctx.mkNot(typeSystem.isOptional(value)));
        valueTv =
            TranslatedValue.create(
                checkedValue, entryAst.value(), typeSystem, valueTv.isApproximate());
        Expr<?> optRef = typeSystem.getOptionalRef(checkedValue);
        optionalHasValue = typeSystem.optHasValue(optRef);
        finalValue = typeSystem.getOptionalValue(optRef);
      }
      elementsTv.add(valueTv);

      // Canonicalization Trick:
      //
      // We avoid storing explicit default values (e.g. `single_int32: 0`)
      // in `msgValues`, leaving them as `Unknown` (identical to missing fields).
      // This structurally aligns defaults with missing fields, allowing Z3's native array equality
      // (`msg1 == msg2`) to work without using quantifiers (which avoids MBQI loops).
      // Because proto3 singular primitives do not have field presence, we also skip setting
      // `msgPresence`.
      BoolExpr isDefaultPrimitive =
          fieldType.kind().isPrimitive() ? ctx.mkEq(finalValue, defaultVal) : ctx.mkFalse();

      BoolExpr shouldBypass = ctx.mkOr(ctx.mkNot(optionalHasValue), isDefaultPrimitive);

      msgValues =
          (ArrayExpr) ctx.mkITE(shouldBypass, msgValues, ctx.mkStore(msgValues, key, finalValue));

      msgPresence =
          (ArrayExpr)
              ctx.mkITE(shouldBypass, msgPresence, ctx.mkStore(msgPresence, key, ctx.mkTrue()));
    }

    context.addConstraint(
        ctx.mkEq(typeSystem.getMsgTypeName(msgRef), ctx.mkString(createStruct.messageName())));
    context.addConstraint(ctx.mkEq(typeSystem.getMsgValues(msgRef), msgValues));
    context.addConstraint(ctx.mkEq(typeSystem.getMsgPresence(msgRef), msgPresence));

    Expr<?> result = typeSystem.wrapMessage(msgRef);
    return TranslatedValue.propagateStrict(ctx, typeSystem, result, celExpr, elementsTv);
  }

  private static boolean isJsonWkt(String messageName) {
    return messageName.equals(CelTypes.VALUE_MESSAGE)
        || messageName.equals(CelTypes.LIST_VALUE_MESSAGE)
        || messageName.equals(CelTypes.STRUCT_MESSAGE);
  }

  // Concretize JSON WKT unwrapping directly into native Z3 primitives to avoid
  // sort incompatibilities (Message == String) and solver performance penalties (quantifiers).
  private TranslatedValue translateJsonWktStruct(
      CelExpr celExpr, CelExpr.CelStruct createStruct, CelAbstractSyntaxTree ast) {
    Expr<?> fallback;
    if (createStruct.messageName().equals(CelTypes.VALUE_MESSAGE)) {
      fallback = typeSystem.mkNull();
    } else if (createStruct.messageName().equals(CelTypes.LIST_VALUE_MESSAGE)) {
      fallback = getDefaultValueForType(ListType.create(SimpleType.DYN));
    } else {
      fallback = getDefaultValueForType(MapType.create(SimpleType.STRING, SimpleType.DYN));
    }

    if (createStruct.entries().isEmpty()) {
      return TranslatedValue.create(fallback, celExpr, typeSystem, ctx.mkFalse());
    }

    // JSON WKT messages (gp.Struct, gp.Value, gp.ListValue) can only have a single top-level
    // field entry in non-empty creation literals (e.g., 'fields' for Struct, 'values' for
    // ListValue, or a single 'oneof' field for Value).
    CelExpr.CelStruct.Entry entry = Iterables.getOnlyElement(createStruct.entries());

    // Translate the value to properly capture approximations and Optionals
    TranslatedValue entryTv = translateExpr(entry.value(), ast);
    Expr<?> finalVal = entryTv.z3Expr();

    boolean isNullValueField =
        createStruct.messageName().equals(CelTypes.VALUE_MESSAGE)
            && entry.fieldKey().equals(NULL_VALUE_FIELD);

    if (entry.optionalEntry()) {
      Expr<?> checkedVal =
          typeSystem.withRuntimeError(finalVal, ctx.mkNot(typeSystem.isOptional(finalVal)));
      entryTv =
          TranslatedValue.create(
              checkedVal, entry.value(), typeSystem, entryTv.isApproximate());
      Expr<?> optRef = typeSystem.getOptionalRef(checkedVal);
      BoolExpr hasValue = typeSystem.optHasValue(optRef);

      Expr<?> unpackedVal = typeSystem.getOptionalValue(optRef);
      if (isNullValueField) {
        unpackedVal = typeSystem.mkNull();
      }
      finalVal = ctx.mkITE(hasValue, unpackedVal, fallback);
    } else if (isNullValueField) {
      finalVal = typeSystem.mkNull();
    }

    return TranslatedValue.propagateStrict(
        ctx, typeSystem, finalVal, celExpr, ImmutableList.of(entryTv));
  }

  private TranslatedValue translateWrapperWktStruct(
      CelExpr celExpr, CelExpr.CelStruct createStruct, CelAbstractSyntaxTree ast) {
    String typeName = createStruct.messageName();
    CelType wrapperType =
        CelTypes.getWellKnownCelType(typeName)
            .orElseThrow(
                () -> new IllegalArgumentException("Unexpected wrapper type: " + typeName));
    CelType targetType = ((NullableType) wrapperType).targetType();
    Expr<?> currentVal = getDefaultValueForType(targetType);

    List<TranslatedValue> entryTvs = new ArrayList<>();
    for (CelExpr.CelStruct.Entry entry : createStruct.entries()) {
      TranslatedValue entryTv = translateExpr(entry.value(), ast);
      Expr<?> val = entryTv.z3Expr();
      if (entry.optionalEntry()) {
        Expr<?> checkedVal =
            typeSystem.withRuntimeError(val, ctx.mkNot(typeSystem.isOptional(val)));
        entryTv =
            TranslatedValue.create(
                checkedVal, entry.value(), typeSystem, entryTv.isApproximate());
        Expr<?> optRef = typeSystem.getOptionalRef(checkedVal);
        BoolExpr hasValue = typeSystem.optHasValue(optRef);
        currentVal = ctx.mkITE(hasValue, typeSystem.getOptionalValue(optRef), currentVal);
      } else {
        currentVal = val;
      }
      entryTvs.add(entryTv);
    }
    BoolExpr validValue =
        typeConstraintGenerator.createTypeConstraintForType(currentVal, targetType);
    if (typeName.equals(CelTypes.INT32_WRAPPER_MESSAGE)) {
      IntExpr intVal = typeSystem.getInt(currentVal);
      validValue =
          ctx.mkAnd(
              validValue,
              ctx.mkGe(intVal, ctx.mkInt(Integer.MIN_VALUE)),
              ctx.mkLe(intVal, ctx.mkInt(Integer.MAX_VALUE)));
    } else if (typeName.equals(CelTypes.UINT32_WRAPPER_MESSAGE)) {
      IntExpr uintVal = typeSystem.getUint(currentVal);
      validValue =
          ctx.mkAnd(
              validValue,
              ctx.mkGe(uintVal, ctx.mkInt(0)),
              ctx.mkLe(uintVal, ctx.mkInt(MAX_UINT32)));
    } else if (typeName.equals(CelTypes.FLOAT_WRAPPER_MESSAGE)) {
      FPExpr doubleVal = typeSystem.getDouble(currentVal);
      FPExpr narrowedFloat =
          ctx.mkFPToFP(ctx.mkFPRoundNearestTiesToEven(), doubleVal, ctx.mkFPSortSingle());
      FPExpr widenedDouble =
          ctx.mkFPToFP(ctx.mkFPRoundNearestTiesToEven(), narrowedFloat, ctx.mkFPSortDouble());
      currentVal = typeSystem.wrapDouble(widenedDouble);
    }
    currentVal = typeSystem.withRuntimeError(currentVal, ctx.mkNot(validValue));
    return TranslatedValue.propagateStrict(ctx, typeSystem, currentVal, celExpr, entryTvs);
  }

  @FunctionalInterface
  private interface TimeWktValueFactory {
    Expr<?> create(IntExpr seconds, IntExpr nanos, IntExpr totalNanos);
  }

  private TranslatedValue translateTimestampWktStruct(
      CelExpr celExpr, CelExpr.CelStruct createStruct, CelAbstractSyntaxTree ast) {
    return translateTimeWktStruct(
        celExpr,
        createStruct,
        ast,
        /* minNanos= */ 0,
        (seconds, nanos, totalNanos) ->
            typeSystem.withRuntimeError(
                typeSystem.wrapTimestamp(totalNanos),
                typeSystem.checkTimestampOverflow(totalNanos)));
  }

  private TranslatedValue translateDurationWktStruct(
      CelExpr celExpr, CelExpr.CelStruct createStruct, CelAbstractSyntaxTree ast) {
    return translateTimeWktStruct(
        celExpr,
        createStruct,
        ast,
        CelZ3TypeSystem.MIN_DURATION_NANOS,
        (seconds, nanos, totalNanos) -> {
          BoolExpr signMismatch =
              ctx.mkOr(
                  ctx.mkAnd(ctx.mkGt(seconds, ctx.mkInt(0)), ctx.mkLt(nanos, ctx.mkInt(0))),
                  ctx.mkAnd(ctx.mkLt(seconds, ctx.mkInt(0)), ctx.mkGt(nanos, ctx.mkInt(0))));
          return typeSystem.withRuntimeError(
              typeSystem.wrapDuration(totalNanos),
              signMismatch,
              typeSystem.checkDurationOverflow(totalNanos));
        });
  }

  private TranslatedValue translateTimeWktStruct(
      CelExpr celExpr,
      CelExpr.CelStruct createStruct,
      CelAbstractSyntaxTree ast,
      int minNanos,
      TimeWktValueFactory valueFactory) {
    List<TranslatedValue> entryTvs = new ArrayList<>();
    Expr<?> secondsVal =
        resolveStructFieldValue(createStruct, SECONDS_FIELD, typeSystem.mkInt(0), ast, entryTvs);
    Expr<?> nanosVal =
        resolveStructFieldValue(createStruct, NANOS_FIELD, typeSystem.mkInt(0), ast, entryTvs);

    IntExpr seconds = typeSystem.getInt(secondsVal);
    IntExpr nanos = typeSystem.getInt(nanosVal);
    IntExpr totalNanos =
        (IntExpr)
            ctx.mkAdd(ctx.mkMul(seconds, ctx.mkInt(CelZ3TypeSystem.NANOS_PER_SECOND)), nanos);

    BoolExpr typeError =
        ctx.mkOr(ctx.mkNot(typeSystem.isInt(secondsVal)), ctx.mkNot(typeSystem.isInt(nanosVal)));
    BoolExpr nanosOutOfRange =
        ctx.mkOr(
            ctx.mkLt(nanos, ctx.mkInt(minNanos)),
            ctx.mkGe(nanos, ctx.mkInt(CelZ3TypeSystem.NANOS_PER_SECOND)));

    Expr<?> result =
        typeSystem.withRuntimeError(
            valueFactory.create(seconds, nanos, totalNanos), typeError, nanosOutOfRange);
    return TranslatedValue.propagateStrict(ctx, typeSystem, result, celExpr, entryTvs);
  }

  private Expr<?> resolveStructFieldValue(
      CelExpr.CelStruct createStruct,
      String fieldName,
      Expr<?> defaultValue,
      CelAbstractSyntaxTree ast,
      List<TranslatedValue> entryTvs) {
    return Streams.findLast(
            createStruct.entries().stream().filter(entry -> entry.fieldKey().equals(fieldName)))
        .<Expr<?>>map(
            entry -> {
              TranslatedValue entryTv = translateExpr(entry.value(), ast);
              Expr<?> val = entryTv.z3Expr();
              if (entry.optionalEntry()) {
                Expr<?> checkedVal =
                    typeSystem.withRuntimeError(val, ctx.mkNot(typeSystem.isOptional(val)));
                entryTv =
                    TranslatedValue.create(
                        checkedVal, entry.value(), typeSystem, entryTv.isApproximate());
                Expr<?> optRef = typeSystem.getOptionalRef(checkedVal);
                val =
                    ctx.mkITE(
                        typeSystem.optHasValue(optRef),
                        typeSystem.getOptionalValue(optRef),
                        defaultValue);
              }
              entryTvs.add(entryTv);
              return val;
            })
        .orElse(defaultValue);
  }

  private Expr<?> getDefaultValueForType(CelType type) {
    if (type instanceof NullableType) {
      return typeSystem.mkNull();
    }
    if (type instanceof OptionalType) {
      return typeSystem.mkOptionalNone();
    }
    if (type.equals(SimpleType.INT)) {
      return typeSystem.mkInt(0);
    }
    if (type.equals(SimpleType.BOOL)) {
      return typeSystem.wrapBool(ctx.mkFalse());
    }
    if (type.equals(SimpleType.STRING)) {
      return typeSystem.mkString("");
    }
    if (type.equals(SimpleType.BYTES)) {
      return typeSystem.mkBytes("");
    }
    if (type.equals(SimpleType.DOUBLE)) {
      return typeSystem.mkDouble(0.0);
    }
    if (type.equals(SimpleType.UINT)) {
      return typeSystem.mkUint(0);
    }
    if (type.equals(SimpleType.TIMESTAMP)) {
      return typeSystem.wrapTimestamp(ctx.mkInt(0));
    }
    if (type.equals(SimpleType.DURATION)) {
      return typeSystem.wrapDuration(ctx.mkInt(0));
    }
    if (type instanceof ListType) {
      if (emptyListCache == null) {
        emptyListCache = typeSystem.mkListRefConst(EMPTY_LIST_PREFIX);
        context.addConstraint(
            ctx.mkEq(
                typeSystem.getSeq(emptyListCache),
                ctx.mkEmptySeq(ctx.mkSeqSort(typeSystem.celValueSort()))));
      }
      return typeSystem.wrapList(emptyListCache);
    }
    if (type instanceof MapType) {
      if (emptyMapCache == null) {
        emptyMapCache = typeSystem.mkMapRefConst(EMPTY_MAP_PREFIX);
        context.addConstraint(
            ctx.mkEq(
                typeSystem.getMapPresence(emptyMapCache),
                ctx.mkConstArray(typeSystem.celValueSort(), ctx.mkFalse())));
        context.addConstraint(
            ctx.mkEq(
                typeSystem.getMapValues(emptyMapCache),
                ctx.mkConstArray(typeSystem.celValueSort(), typeSystem.mkUnknown())));
        context.addConstraint(
            ctx.mkEq(
                typeSystem.getMapKeys(emptyMapCache),
                ctx.mkEmptySeq(ctx.mkSeqSort(typeSystem.celValueSort()))));
      }
      return typeSystem.wrapMap(emptyMapCache);
    }
    if (type.kind() == CelKind.STRUCT) {
      String messageName = type.name();
      Expr<?> msgRef =
          emptyMessageCache.computeIfAbsent(
              messageName,
              name -> {
                Expr<?> ref = typeSystem.mkMessageRefConst(EMPTY_MSG_REF_PREFIX + name);
                context.addConstraint(
                    ctx.mkEq(
                        typeSystem.getMsgPresence(ref),
                        ctx.mkConstArray(ctx.getStringSort(), ctx.mkFalse())));
                context.addConstraint(
                    ctx.mkEq(
                        typeSystem.getMsgValues(ref),
                        ctx.mkConstArray(ctx.getStringSort(), typeSystem.mkUnknown())));
                context.addConstraint(ctx.mkEq(typeSystem.getMsgTypeName(ref), ctx.mkString(name)));
                return ref;
              });
      return typeSystem.wrapMessage(msgRef);
    }
    return typeSystem.mkUnknown();
  }

  private static final class FieldAccess {
    private final BoolExpr presence;
    private final Expr<?> value;

    private FieldAccess(BoolExpr presence, Expr<?> value) {
      this.presence = presence;
      this.value = value;
    }
  }

  private FieldAccess getMapAccess(Expr<?> operand, String field, BoolExpr typeGuard) {
    Expr<?> mapRef = typeSystem.getMapRef(operand);
    Expr<?> mapFieldZ3Str = typeSystem.mkString(field);
    BoolExpr presence =
        (BoolExpr) ctx.mkSelect((ArrayExpr) typeSystem.getMapPresence(mapRef), mapFieldZ3Str);
    Expr<?> value = ctx.mkSelect((ArrayExpr) typeSystem.getMapValues(mapRef), mapFieldZ3Str);
    context.constrainValidValue(CelZ3Exprs.mkAndFlattened(ctx, typeGuard, presence), value);
    return new FieldAccess(presence, value);
  }

  private FieldAccess getMsgAccess(Expr<?> operand, String field, BoolExpr typeGuard) {
    Expr<?> msgRef = typeSystem.getMessageRef(operand);
    Expr<?> msgFieldZ3Str = ctx.mkString(field);
    BoolExpr presence =
        (BoolExpr) ctx.mkSelect((ArrayExpr) typeSystem.getMsgPresence(msgRef), msgFieldZ3Str);
    Expr<?> value = ctx.mkSelect((ArrayExpr) typeSystem.getMsgValues(msgRef), msgFieldZ3Str);
    context.constrainValidValue(CelZ3Exprs.mkAndFlattened(ctx, typeGuard, presence), value);
    return new FieldAccess(presence, value);
  }

  private TranslatedValue translateSelect(CelExpr celExpr, CelAbstractSyntaxTree ast) {
    CelExpr.CelSelect select = celExpr.select();
    long exprId = celExpr.id();
    TranslatedValue operandTv = translateExpr(select.operand(), ast);
    Expr<?> operand = operandTv.z3Expr();
    String field = select.field();
    CelType operandType = extractAstTypeOrDefault(ast, select.operand().id());

    Expr<?> presenceResult;
    Expr<?> valueResult;

    if (operandType instanceof MapType) {
      FieldAccess mapAcc = getMapAccess(operand, field, ctx.mkTrue());
      presenceResult = mapAcc.presence;
      valueResult = ctx.mkITE(mapAcc.presence, mapAcc.value, typeSystem.mkError());
    } else if (operandType.kind() == CelKind.STRUCT) {
      FieldAccess msgAcc = getMsgAccess(operand, field, ctx.mkTrue());
      presenceResult = msgAcc.presence;
      Expr<?> defaultVal = getDefaultValueForType(extractAstTypeOrDefault(ast, exprId));
      valueResult = ctx.mkITE(msgAcc.presence, msgAcc.value, defaultVal);
    } else {
      // Dynamic type: generate the full SMT decision tree
      BoolExpr isMap = typeSystem.isMap(operand);
      BoolExpr isMessage = typeSystem.isMessage(operand);

      FieldAccess mapAcc = getMapAccess(operand, field, isMap);
      FieldAccess msgAcc = getMsgAccess(operand, field, isMessage);

      presenceResult =
          CelZ3Exprs.SwitchBuilder.newBuilder(ctx)
              .addCase(isMessage, msgAcc.presence)
              .addCase(isMap, mapAcc.presence)
              .build(ctx.mkFalse());

      Expr<?> defaultVal = getDefaultValueForType(extractAstTypeOrDefault(ast, exprId));
      Expr<?> msgRead = ctx.mkITE(msgAcc.presence, msgAcc.value, defaultVal);
      Expr<?> mapRead = ctx.mkITE(mapAcc.presence, mapAcc.value, typeSystem.mkError());

      valueResult =
          CelZ3Exprs.SwitchBuilder.newBuilder(ctx)
              .addCase(isMessage, msgRead)
              .addCase(isMap, mapRead)
              .build(typeSystem.mkError());
    }

    Expr<?> fieldAccess = select.testOnly() ? typeSystem.wrapBool(presenceResult) : valueResult;

    context.addConstraint(typeConstraintGenerator.createTypeConstraint(fieldAccess, exprId, ast));

    TranslatedValue result =
        TranslatedValue.propagateStrict(
            ctx, typeSystem, fieldAccess, celExpr, ImmutableList.of(operandTv));
    // Map presence is only linked to the keys up to the unroll limit.
    return operandType.kind() == CelKind.STRUCT
        ? result
        : result.withApproximation(operatorTranslator.isTruncatedMap(operand));
  }

  private TranslatedValue translateBlock(
      List<CelExpr> boundExprs, int currentIndex, CelExpr resultExpr, CelAbstractSyntaxTree ast) {
    if (currentIndex >= boundExprs.size()) {
      return translateExpr(resultExpr, ast);
    }

    CelExpr subExprAst = boundExprs.get(currentIndex);
    TranslatedValue subExprVal = translateExpr(subExprAst, ast);
    String varName = CelBlock.INDEX_PREFIX + currentIndex;

    return context.withScope(
        varName, subExprVal, () -> translateBlock(boundExprs, currentIndex + 1, resultExpr, ast));
  }

  private TranslatedValue translateCall(CelExpr expr, CelAbstractSyntaxTree ast) {
    CelExpr.CelCall call = expr.call();
    long exprId = expr.id();
    String functionName = call.function();
    ImmutableList<CelExpr> argsAst = call.args();

    List<TranslatedValue> args = new ArrayList<>();

    if (call.target().isPresent()) {
      CelExpr targetAst = call.target().get();
      args.add(translateExpr(targetAst, ast));
    }

    for (CelExpr argAst : argsAst) {
      args.add(translateExpr(argAst, ast));
    }

    return operatorTranslator
        .translateFunctionCall(functionName, args, exprId, ast)
        .map(
            tv -> {
              if (mayEmbedArgs(functionName, ast.getTypeOrThrow(exprId))) {
                propagateTruncatedTypeConstraint(
                    tv.z3Expr(), Iterables.transform(args, TranslatedValue::z3Expr));
              }
              return TranslatedValue.create(tv.z3Expr(), expr, typeSystem, tv.isApproximate());
            })
        .orElseGet(
            () -> {
              // Uninterpreted function
              Sort[] argSorts = new Sort[args.size()];
              Arrays.fill(argSorts, 0, args.size(), typeSystem.celValueSort());
              FuncDecl<?> funcDecl =
                  typeSystem.internFuncDecl(
                      uninterpretedFunctionSymbol(exprId, ast),
                      argSorts,
                      typeSystem.celValueSort());

              Expr<?>[] exprArgs = args.stream().map(TranslatedValue::z3Expr).toArray(Expr[]::new);
              Expr<?> callRes = ctx.mkApp(funcDecl, exprArgs);
              context.addConstraint(
                  typeConstraintGenerator.createTypeConstraint(callRes, exprId, ast));
              context.addConstraint(ctx.mkNot(typeSystem.isUnknown(callRes)));
              context.addConstraint(ctx.mkNot(typeSystem.isError(callRes)));

              // The solver picks the result freely, so a model depending on it is never exact.
              return TranslatedValue.propagateStrict(
                  ctx,
                  typeSystem,
                  functionName,
                  callRes,
                  Optional.of(expr),
                  /* baseTaint= */ ctx.mkTrue(),
                  args);
            });
  }

  /**
   * Returns the Z3 symbol for an uninterpreted call. Each set of candidate overloads gets its own
   * symbol, since distinct overloads may have different implementations even on equal values (e.g.
   * empty lists of different element types). The prefix keeps it apart from variables and internal
   * symbols.
   */
  private static String uninterpretedFunctionSymbol(long exprId, CelAbstractSyntaxTree ast) {
    ImmutableList<String> overloadIds = ast.getReferenceOrThrow(exprId).overloadIds();
    return FUNCTION_PREFIX + String.join("|", ImmutableSortedSet.copyOf(overloadIds));
  }

  /**
   * Whether a call may construct a container that embeds its args (e.g. list concatenation, map
   * insertion or optional.of). Indexing and conditionals return existing values instead, so
   * propagating to them would needlessly taint e.g. an element whenever its container has a
   * truncated type constraint.
   */
  private static boolean mayEmbedArgs(String functionName, CelType resultType) {
    return (!CelZ3OperatorTranslator.isStaticallyKnown(resultType)
            || !resultType.parameters().isEmpty())
        && !functionName.equals(Operator.INDEX.getFunction())
        && !functionName.equals(Operator.CONDITIONAL.getFunction());
  }

  private static CelType extractAstTypeOrDefault(CelAbstractSyntaxTree ast, long id) {
    return ast.getType(id).orElse(SimpleType.DYN);
  }

  private Optional<Object> toCacheKey(CelExpr expr) {
    switch (expr.exprKind().getKind()) {
      case CONSTANT:
        return Optional.of(expr.constant());
      case LIST:
        if (!expr.list().optionalIndices().isEmpty()) {
          // Do not cache lists with optional elements. Optional elements conditionally alter
          // sequence length and presence via ITE branches at runtime; caching would collide
          // [1, 2] with [?1, 2] and freeze conditional evaluations to a static reference.
          return Optional.empty();
        }
        ImmutableList.Builder<Object> builder = ImmutableList.builder();
        for (CelExpr elem : expr.list().elements()) {
          Optional<Object> elemKey = toCacheKey(elem);
          if (!elemKey.isPresent()) {
            return Optional.empty(); // Contains non-constants
          }
          builder.add(elemKey.get());
        }
        return Optional.of(builder.build());
      default:
        return Optional.empty();
    }
  }

  CelAstToZ3Translator(
      Context ctx,
      int comprehensionUnrollLimit,
      ImmutableSet<String> unknownIdentifiers,
      CelZ3FunctionRegistry functionRegistry,
      CelTypeProvider typeProvider,
      boolean parameterizeUnknowns) {
    this.ctx = ctx;
    this.typeSystem = new CelZ3TypeSystem(ctx, parameterizeUnknowns);
    this.context = new CelZ3TranslationContext(ctx, typeSystem, comprehensionUnrollLimit);
    this.typeConstraintGenerator = context.typeConstraintGenerator();
    this.operatorTranslator = new CelZ3OperatorTranslator(context, functionRegistry);
    this.comprehensionTranslator = new CelZ3ComprehensionTranslator(context, this::translateExpr);
    this.unknownIdentifiers = unknownIdentifiers;
    this.emptyMessageCache = new HashMap<>();
    this.listLiteralCache = new HashMap<>();
    this.typeProvider = typeProvider;
  }
}
