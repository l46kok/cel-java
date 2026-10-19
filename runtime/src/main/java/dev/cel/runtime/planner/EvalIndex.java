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

package dev.cel.runtime.planner;

import static dev.cel.runtime.planner.EvalHelpers.evalNonstrictly;
import static dev.cel.runtime.planner.EvalHelpers.evalStrictly;

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.AccumulatedUnknowns;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalIndex extends PlannedInterpretable {

  private final String functionName;
  private final CelResolvedOverload resolvedOverload;
  private final PlannedInterpretable target;
  private final PlannedInterpretable index;
  private final CelValueConverter celValueConverter;

  @Override
  Object evalInternal(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    boolean isStrict = resolvedOverload.isStrict();
    Object targetVal =
        isStrict ? evalStrictly(target, resolver, frame) : evalNonstrictly(target, resolver, frame);
    Object indexVal =
        isStrict ? evalStrictly(index, resolver, frame) : evalNonstrictly(index, resolver, frame);

    if (isStrict) {
      AccumulatedUnknowns unknowns = AccumulatedUnknowns.maybeMerge(targetVal, indexVal);
      if (unknowns != null) {
        return unknowns;
      }
    }

    return EvalHelpers.dispatch(
        functionName, resolvedOverload, celValueConverter, targetVal, indexVal);
  }

  /** Plans {@code target[index]}. */
  static PlannedInterpretable create(
      CelExpr expr,
      String functionName,
      CelResolvedOverload resolvedOverload,
      PlannedInterpretable target,
      PlannedInterpretable index,
      CelValueConverter celValueConverter) {
    // Qualifies the attribute so unknown patterns match through constant indices, as in CEL-C++.
    if (target instanceof EvalAttribute
        && resolvedOverload.isStrict()
        && index instanceof EvalConstant
        && isQualifierKind(((EvalConstant) index).constantValue())) {
      return ((EvalAttribute) target)
          .addQualifier(
              expr,
              new ConstantIndexQualifier(
                  expr.id(),
                  resolvedOverload,
                  ((EvalConstant) index).constantValue(),
                  celValueConverter));
    }
    return new EvalIndex(expr, functionName, resolvedOverload, target, index, celValueConverter);
  }

  // Kinds CelAttribute.Qualifier.fromGeneric accepts when unknown patterns are matched.
  private static boolean isQualifierKind(Object value) {
    return value instanceof Long
        || value instanceof UnsignedLong
        || value instanceof String
        || value instanceof Boolean;
  }

  /** Indexes an attribute's value by a constant through the {@code _[_]} overload. */
  @Immutable
  private static final class ConstantIndexQualifier implements Qualifier {
    private final long exprId;
    private final CelResolvedOverload resolvedOverload;

    @SuppressWarnings("Immutable") // Immutable CEL constant.
    private final Object index;

    private final CelValueConverter celValueConverter;

    @Override
    public Object value() {
      return index;
    }

    @Override
    public Object qualify(Object operand) {
      // Mirrors EvalIndex: the overload receives the same adapted target, and failures are
      // localized to the index expression.
      try {
        return celValueConverter.toTraversalTarget(
            EvalHelpers.dispatch(
                resolvedOverload.getFunctionName(),
                resolvedOverload,
                celValueConverter,
                EvalHelpers.convertAndAdaptResult(celValueConverter, operand),
                index));
      } catch (Exception e) {
        throw EvalHelpers.localize(e, exprId);
      }
    }

    private ConstantIndexQualifier(
        long exprId,
        CelResolvedOverload resolvedOverload,
        Object index,
        CelValueConverter celValueConverter) {
      this.exprId = exprId;
      this.resolvedOverload = resolvedOverload;
      this.index = index;
      this.celValueConverter = celValueConverter;
    }
  }

  private EvalIndex(
      CelExpr expr,
      String functionName,
      CelResolvedOverload resolvedOverload,
      PlannedInterpretable target,
      PlannedInterpretable index,
      CelValueConverter celValueConverter) {
    super(expr);
    this.functionName = functionName;
    this.resolvedOverload = resolvedOverload;
    this.target = target;
    this.index = index;
    this.celValueConverter = celValueConverter;
  }
}
