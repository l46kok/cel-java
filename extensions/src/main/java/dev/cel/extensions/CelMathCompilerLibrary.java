// Copyright 2023 Google LLC
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

package dev.cel.extensions;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelIssue;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelMacroExprFactory;
import dev.cel.parser.CelParserBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Internal implementation of CEL Math compile-time extensions. */
@Immutable
public final class CelMathCompilerLibrary
    implements CelCompilerLibrary, CelExtensionLibrary.FeatureSet {

  private static final String MATH_NAMESPACE = "math";

  static final String MATH_MAX_FUNCTION = "math.@max";
  private static final String MATH_MAX_OVERLOAD_DOC =
      "Returns the greatest valued number present in the arguments.";
  static final String MATH_MIN_FUNCTION = "math.@min";
  private static final String MATH_MIN_OVERLOAD_DOC =
      "Returns the least valued number present in the arguments.";

  // Rounding Functions
  static final String MATH_CEIL_FUNCTION = "math.ceil";
  static final String MATH_FLOOR_FUNCTION = "math.floor";
  static final String MATH_ROUND_FUNCTION = "math.round";
  static final String MATH_TRUNC_FUNCTION = "math.trunc";

  // Floating Point Functions
  static final String MATH_ISFINITE_FUNCTION = "math.isFinite";
  static final String MATH_ISNAN_FUNCTION = "math.isNaN";
  static final String MATH_ISINF_FUNCTION = "math.isInf";

  // Signedness Functions
  static final String MATH_ABS_FUNCTION = "math.abs";
  static final String MATH_SIGN_FUNCTION = "math.sign";

  // Bitwise Functions
  static final String MATH_BIT_AND_FUNCTION = "math.bitAnd";
  static final String MATH_BIT_OR_FUNCTION = "math.bitOr";
  static final String MATH_BIT_XOR_FUNCTION = "math.bitXor";
  static final String MATH_BIT_NOT_FUNCTION = "math.bitNot";
  static final String MATH_BIT_LEFT_SHIFT_FUNCTION = "math.bitShiftLeft";
  static final String MATH_BIT_RIGHT_SHIFT_FUNCTION = "math.bitShiftRight";

  static final String MATH_SQRT_FUNCTION = "math.sqrt";

  /** Enumeration of functions for Math compile-time extension. */
  public enum Function {
    MAX(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_MAX_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_@max_double", MATH_MAX_OVERLOAD_DOC, SimpleType.DOUBLE, SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_int", MATH_MAX_OVERLOAD_DOC, SimpleType.INT, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_uint", MATH_MAX_OVERLOAD_DOC, SimpleType.UINT, SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_double_double",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DOUBLE,
                SimpleType.DOUBLE,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_int_int",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_uint_uint",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_int_uint",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.INT,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_int_double",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.INT,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_double_int",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.DOUBLE,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_double_uint",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.DOUBLE,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_uint_int",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.UINT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_uint_double",
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.UINT,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@max_list_dyn", // Implementation supports double, int and uint as list
                // literals. Anything else will error during macro expansion.
                MATH_MAX_OVERLOAD_DOC,
                SimpleType.DYN,
                ListType.create(SimpleType.DYN)))),
    MIN(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_MIN_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_@min_double", MATH_MIN_OVERLOAD_DOC, SimpleType.DOUBLE, SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_int", MATH_MIN_OVERLOAD_DOC, SimpleType.INT, SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_uint", MATH_MIN_OVERLOAD_DOC, SimpleType.UINT, SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_double_double",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DOUBLE,
                SimpleType.DOUBLE,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_int_int",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_uint_uint",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_int_uint",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.INT,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_int_double",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.INT,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_double_int",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.DOUBLE,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_double_uint",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.DOUBLE,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_uint_int",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.UINT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_uint_double",
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                SimpleType.UINT,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_@min_list_dyn", // Implementation supports double, int and uint as list
                // literals. Anything else will error during macro expansion.
                MATH_MIN_OVERLOAD_DOC,
                SimpleType.DYN,
                ListType.create(SimpleType.DYN)))),
    CEIL(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_CEIL_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_ceil_double",
                "Compute the ceiling of a double value.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE))),
    FLOOR(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_FLOOR_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_floor_double",
                "Compute the floor of a double value.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE))),
    ROUND(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_ROUND_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_round_double",
                "Rounds the double value to the nearest whole number with ties rounding away from"
                    + " zero.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE))),
    TRUNC(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_TRUNC_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_trunc_double",
                "Truncates the fractional portion of the double value.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE))),
    ISFINITE(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_ISFINITE_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_isFinite_double",
                "Returns true if the value is a finite number.",
                SimpleType.BOOL,
                SimpleType.DOUBLE))),
    ISNAN(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_ISNAN_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_isNaN_double",
                "Returns true if the input double value is NaN, false otherwise.",
                SimpleType.BOOL,
                SimpleType.DOUBLE))),
    ISINF(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_ISINF_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_isInf_double",
                "Returns true if the input double value is -Inf or +Inf.",
                SimpleType.BOOL,
                SimpleType.DOUBLE))),
    ABS(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_ABS_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_abs_double",
                "Compute the absolute value of a double value.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_abs_int",
                "Compute the absolute value of an int value.",
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_abs_uint",
                "Compute the absolute value of a uint value.",
                SimpleType.UINT,
                SimpleType.UINT))),
    SIGN(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_SIGN_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_sign_double",
                "Returns the sign of the input numeric type, either -1, 0, 1 cast as double.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_sign_uint",
                "Returns the sign of the input numeric type, either -1, 0, 1 case as uint.",
                SimpleType.UINT,
                SimpleType.UINT),
            CelOverloadDecl.newGlobalOverload(
                "math_sign_int",
                "Returns the sign of the input numeric type, either -1, 0, 1.",
                SimpleType.INT,
                SimpleType.INT))),
    BITAND(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_AND_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitAnd_int_int",
                "Performs a bitwise-AND operation over two int values.",
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitAnd_uint_uint",
                "Performs a bitwise-AND operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.UINT))),
    BITOR(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_OR_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitOr_int_int",
                "Performs a bitwise-OR operation over two int values.",
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitOr_uint_uint",
                "Performs a bitwise-OR operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.UINT))),
    BITXOR(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_XOR_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitXor_int_int",
                "Performs a bitwise-XOR operation over two int values.",
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitXor_uint_uint",
                "Performs a bitwise-XOR operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.UINT))),
    BITNOT(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_NOT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitNot_int_int",
                "Performs a bitwise-NOT operation over two int values.",
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitNot_uint_uint",
                "Performs a bitwise-NOT operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT))),
    BITSHIFTLEFT(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_LEFT_SHIFT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitShiftLeft_int_int",
                "Performs a bitwise-SHIFTLEFT operation over two int values.",
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitShiftLeft_uint_int",
                "Performs a bitwise-SHIFTLEFT operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.INT))),
    BITSHIFTRIGHT(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_BIT_RIGHT_SHIFT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_bitShiftRight_int_int",
                "Performs a bitwise-SHIFTRIGHT operation over two int values.",
                SimpleType.INT,
                SimpleType.INT,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_bitShiftRight_uint_int",
                "Performs a bitwise-SHIFTRIGHT operation over two uint values.",
                SimpleType.UINT,
                SimpleType.UINT,
                SimpleType.INT))),
    SQRT(
        CelFunctionDecl.newFunctionDeclaration(
            MATH_SQRT_FUNCTION,
            CelOverloadDecl.newGlobalOverload(
                "math_sqrt_double",
                "Computes square root of the double value.",
                SimpleType.DOUBLE,
                SimpleType.DOUBLE),
            CelOverloadDecl.newGlobalOverload(
                "math_sqrt_int",
                "Computes square root of the int value.",
                SimpleType.DOUBLE,
                SimpleType.INT),
            CelOverloadDecl.newGlobalOverload(
                "math_sqrt_uint",
                "Computes square root of the unsigned value.",
                SimpleType.DOUBLE,
                SimpleType.UINT)));

    private final CelFunctionDecl functionDecl;

    public String getFunction() {
      return functionDecl.name();
    }

    public CelFunctionDecl getFunctionDecl() {
      return functionDecl;
    }

    Function(CelFunctionDecl functionDecl) {
      this.functionDecl = functionDecl;
    }
  }

  private static final class Library implements CelExtensionLibrary<CelMathCompilerLibrary> {
    private final CelMathCompilerLibrary version0;
    private final CelMathCompilerLibrary version1;
    private final CelMathCompilerLibrary version2;

    Library() {
      version0 = new CelMathCompilerLibrary(0, ImmutableSet.of(Function.MIN, Function.MAX));

      version1 =
          new CelMathCompilerLibrary(
              1,
              ImmutableSet.<Function>builder()
                  .addAll(version0.functions)
                  .add(
                      Function.CEIL,
                      Function.FLOOR,
                      Function.ROUND,
                      Function.TRUNC,
                      Function.ISINF,
                      Function.ISNAN,
                      Function.ISFINITE,
                      Function.ABS,
                      Function.SIGN,
                      Function.BITAND,
                      Function.BITOR,
                      Function.BITXOR,
                      Function.BITNOT,
                      Function.BITSHIFTLEFT,
                      Function.BITSHIFTRIGHT)
                  .build());

      version2 =
          new CelMathCompilerLibrary(
              2,
              ImmutableSet.<Function>builder()
                  .addAll(version1.functions)
                  .add(Function.SQRT)
                  .build());
    }

    @Override
    public String name() {
      return "math";
    }

    @Override
    public ImmutableSet<CelMathCompilerLibrary> versions() {
      return ImmutableSet.of(version0, version1, version2);
    }
  }

  private static final Library LIBRARY = new Library();

  public static CelExtensionLibrary<CelMathCompilerLibrary> library() {
    return LIBRARY;
  }

  /** Returns the latest version of the 'math' compiler extension. */
  public static CelMathCompilerLibrary math() {
    return library().latest();
  }

  /** Returns the specified version of the 'math' compiler extension. */
  public static CelMathCompilerLibrary math(int version) {
    return library().version(version);
  }

  /** Returns the 'math' compiler extension with only the specified functions. */
  public static CelMathCompilerLibrary math(Function... functions) {
    return math(ImmutableSet.copyOf(functions));
  }

  /** Returns the 'math' compiler extension with only the specified functions. */
  public static CelMathCompilerLibrary math(Set<Function> functions) {
    return new CelMathCompilerLibrary(functions);
  }

  private final ImmutableSet<Function> functions;
  private final int version;

  CelMathCompilerLibrary(Set<Function> functions) {
    this(-1, functions);
  }

  private CelMathCompilerLibrary(int version, Set<Function> functions) {
    this.version = version;
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public int version() {
    return version;
  }

  @Override
  public ImmutableSet<CelFunctionDecl> functions() {
    return functions.stream().map(Function::getFunctionDecl).collect(toImmutableSet());
  }

  @Override
  public ImmutableSet<CelMacro> macros() {
    return ImmutableSet.of(
        CelMacro.newReceiverVarArgMacro("greatest", CelMathCompilerLibrary::expandGreatestMacro),
        CelMacro.newReceiverVarArgMacro("least", CelMathCompilerLibrary::expandLeastMacro));
  }

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    parserBuilder.addMacros(macros());
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    functions.forEach(function -> checkerBuilder.addFunctionDeclarations(function.functionDecl));
  }

  private static Optional<CelExpr> expandGreatestMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    if (!isTargetInNamespace(target)) {
      // Return empty to indicate that we're not interested in expanding this macro, and
      // that the parser should default to a function call on the receiver.
      return Optional.empty();
    }

    switch (arguments.size()) {
      case 0:
        return newError(exprFactory, "math.greatest() requires at least one argument", target);
      case 1:
        Optional<CelExpr> invalidArg =
            checkInvalidArgumentSingleArg(exprFactory, "math.greatest()", arguments.get(0));
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(exprFactory.newGlobalCall(MATH_MAX_FUNCTION, arguments.get(0)));
      case 2:
        invalidArg = checkInvalidArgument(exprFactory, "math.greatest()", arguments);
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(exprFactory.newGlobalCall(MATH_MAX_FUNCTION, arguments));
      default:
        invalidArg = checkInvalidArgument(exprFactory, "math.greatest()", arguments);
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(
            exprFactory.newGlobalCall(MATH_MAX_FUNCTION, exprFactory.newList(arguments)));
    }
  }

  private static Optional<CelExpr> expandLeastMacro(
      CelMacroExprFactory exprFactory, CelExpr target, ImmutableList<CelExpr> arguments) {
    if (!isTargetInNamespace(target)) {
      // Return empty to indicate that we're not interested in expanding this macro, and
      // that the parser should default to a function call on the receiver.
      return Optional.empty();
    }

    switch (arguments.size()) {
      case 0:
        return newError(exprFactory, "math.least() requires at least one argument", target);
      case 1:
        Optional<CelExpr> invalidArg =
            checkInvalidArgumentSingleArg(exprFactory, "math.least()", arguments.get(0));
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(exprFactory.newGlobalCall(MATH_MIN_FUNCTION, arguments.get(0)));
      case 2:
        invalidArg = checkInvalidArgument(exprFactory, "math.least()", arguments);
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(exprFactory.newGlobalCall(MATH_MIN_FUNCTION, arguments));
      default:
        invalidArg = checkInvalidArgument(exprFactory, "math.least()", arguments);
        if (invalidArg.isPresent()) {
          return invalidArg;
        }

        return Optional.of(
            exprFactory.newGlobalCall(MATH_MIN_FUNCTION, exprFactory.newList(arguments)));
    }
  }

  private static boolean isTargetInNamespace(CelExpr target) {
    return target.exprKind().getKind().equals(Kind.IDENT)
        && target.ident().name().equals(MATH_NAMESPACE);
  }

  private static Optional<CelExpr> checkInvalidArgument(
      CelMacroExprFactory exprFactory, String functionName, List<CelExpr> arguments) {

    for (CelExpr arg : arguments) {
      if (!isArgumentValidType(arg)) {
        return newError(
            exprFactory,
            String.format("%s simple literal arguments must be numeric", functionName),
            arg);
      }
    }
    return Optional.empty();
  }

  private static Optional<CelExpr> checkInvalidArgumentSingleArg(
      CelMacroExprFactory exprFactory, String functionName, CelExpr argument) {
    if (argument.exprKind().getKind() == Kind.LIST) {
      if (argument.list().elements().isEmpty()) {
        return newError(
            exprFactory, String.format("%s invalid single argument value", functionName), argument);
      }

      return checkInvalidArgument(exprFactory, functionName, argument.list().elements());
    }
    if (isArgumentValidType(argument)) {
      return Optional.empty();
    }

    return newError(
        exprFactory, String.format("%s invalid single argument value", functionName), argument);
  }

  private static boolean isArgumentValidType(CelExpr argument) {
    if (argument.exprKind().getKind() == Kind.CONSTANT) {
      CelConstant constant = argument.constant();
      return constant.getKind() == CelConstant.Kind.INT64_VALUE
          || constant.getKind() == CelConstant.Kind.UINT64_VALUE
          || constant.getKind() == CelConstant.Kind.DOUBLE_VALUE;
    } else if (argument.exprKind().getKind().equals(Kind.LIST)
        || argument.exprKind().getKind().equals(Kind.STRUCT)
        || argument.exprKind().getKind().equals(Kind.MAP)) {
      return false;
    }

    return true;
  }

  private static Optional<CelExpr> newError(
      CelMacroExprFactory exprFactory, String errorMessage, CelExpr argument) {
    return Optional.of(
        exprFactory.reportError(
            CelIssue.formatError(exprFactory.getSourceLocation(argument), errorMessage)));
  }
}
