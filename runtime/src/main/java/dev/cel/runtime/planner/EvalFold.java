// Copyright 2025 Google LLC
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.values.MutableMapValue;
import dev.cel.runtime.AccumulatedUnknowns;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.ConcatenatedListView;
import dev.cel.runtime.GlobalResolver;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import org.jspecify.annotations.Nullable;

@Immutable
final class EvalFold extends PlannedInterpretable {

  private final int accuSlot;
  private final PlannedInterpretable accuInit;
  private final int iterSlot;
  private final int iter2Slot;
  private final PlannedInterpretable iterRange;
  private final PlannedInterpretable condition;
  private final PlannedInterpretable loopStep;
  private final PlannedInterpretable result;

  static EvalFold create(
      CelExpr expr,
      int accuSlot,
      PlannedInterpretable accuInit,
      int iterSlot,
      int iter2Slot,
      PlannedInterpretable iterRange,
      PlannedInterpretable loopCondition,
      PlannedInterpretable loopStep,
      PlannedInterpretable result) {
    return new EvalFold(
        expr, accuSlot, accuInit, iterSlot, iter2Slot, iterRange, loopCondition, loopStep, result);
  }

  @Override
  Object evalInternal(GlobalResolver resolver, ExecutionFrame frame) throws CelEvaluationException {
    Object iterRangeRaw = iterRange.eval(resolver, frame);
    if (iterRangeRaw instanceof AccumulatedUnknowns) {
      return iterRangeRaw;
    }
    frame.setComprehensionSlot(accuSlot, accuInit);
    if (iterRangeRaw instanceof Map) {
      return evalMap((Map<?, ?>) iterRangeRaw, resolver, frame);
    } else if (iterRangeRaw instanceof List) {
      List<?> list = (List<?>) iterRangeRaw;
      // ConcatenatedListView (a mutable accumulator) does not support get(int).
      return evalList(
          list instanceof RandomAccess ? list : ImmutableList.copyOf(list), resolver, frame);
    } else {
      throw new IllegalArgumentException("Unexpected iter_range type: " + iterRangeRaw.getClass());
    }
  }

  private Object evalMap(Map<?, ?> iterRange, GlobalResolver resolver, ExecutionFrame frame)
      throws CelEvaluationException {
    for (Map.Entry<?, ?> entry : iterRange.entrySet()) {
      frame.incrementIterations();

      frame.setComprehensionSlot(iterSlot, entry.getKey());
      if (iter2Slot >= 0) {
        frame.setComprehensionSlot(iter2Slot, entry.getValue());
      }

      Object condResult = condition.eval(resolver, frame);
      if (condResult instanceof AccumulatedUnknowns) {
        return mergeAccumulator(frame.getComprehensionSlot(accuSlot), condResult);
      }
      if (!(condResult instanceof Boolean)) {
        throw new IllegalArgumentException(
            String.format("Expected boolean value, found :%s", condResult));
      }
      if (!(boolean) condResult) {
        break;
      }

      Object stepResult = loopStep.eval(resolver, frame);
      frame.setComprehensionSlot(
          accuSlot, mergeAccumulator(frame.getComprehensionSlot(accuSlot), stepResult));
    }
    return evalResult(resolver, frame);
  }

  private Object evalList(List<?> iterRange, GlobalResolver resolver, ExecutionFrame frame)
      throws CelEvaluationException {
    for (int index = 0; index < iterRange.size(); index++) {
      frame.incrementIterations();

      Object item = iterRange.get(index);
      if (iter2Slot < 0) {
        frame.setComprehensionSlot(iterSlot, item);
      } else {
        frame.setComprehensionSlot(iterSlot, (long) index);
        frame.setComprehensionSlot(iter2Slot, item);
      }

      Object condResult = condition.eval(resolver, frame);
      if (condResult instanceof AccumulatedUnknowns) {
        return mergeAccumulator(frame.getComprehensionSlot(accuSlot), condResult);
      }
      if (!(condResult instanceof Boolean)) {
        throw new IllegalArgumentException(
            String.format("Expected boolean value, found :%s", condResult));
      }
      if (!(boolean) condResult) {
        break;
      }

      Object stepResult = loopStep.eval(resolver, frame);
      frame.setComprehensionSlot(
          accuSlot, mergeAccumulator(frame.getComprehensionSlot(accuSlot), stepResult));
    }
    return evalResult(resolver, frame);
  }

  private Object evalResult(GlobalResolver resolver, ExecutionFrame frame)
      throws CelEvaluationException {
    Object accuVal = frame.getComprehensionSlot(accuSlot);
    // Materialize the mutable accumulator so the result expr never observes in-place mutation.
    if (accuVal instanceof ConcatenatedListView) {
      frame.setComprehensionSlot(accuSlot, ImmutableList.copyOf((ConcatenatedListView<?>) accuVal));
    } else if (accuVal instanceof MutableMapValue) {
      frame.setComprehensionSlot(accuSlot, ImmutableMap.copyOf((MutableMapValue) accuVal));
    }
    return result.eval(resolver, frame);
  }

  private static Object mergeAccumulator(@Nullable Object currentAccu, Object newVal) {
    if (newVal instanceof AccumulatedUnknowns && currentAccu instanceof AccumulatedUnknowns) {
      return ((AccumulatedUnknowns) currentAccu).merge((AccumulatedUnknowns) newVal);
    }
    return newVal;
  }

  private static Object maybeWrapAccumulator(Object val) {
    if (val instanceof Collection) {
      return new ConcatenatedListView<>((Collection<?>) val);
    }
    if (val instanceof Map) {
      return MutableMapValue.create((Map<?, ?>) val);
    }
    return val;
  }

  /** Evaluates a comprehension variable stored in an {@link ExecutionFrame} slot. */
  @Immutable
  static final class EvalComprehensionSlot extends PlannedInterpretable {
    private final int slotIndex;
    private final boolean wrapMutableAccu;

    @Override
    Object evalInternal(GlobalResolver resolver, ExecutionFrame frame)
        throws CelEvaluationException {
      Object val = frame.getComprehensionSlot(slotIndex);
      if (val instanceof PlannedInterpretable) {
        val = ((PlannedInterpretable) val).eval(resolver, frame);
        if (wrapMutableAccu && frame.enableShortCircuiting()) {
          val = maybeWrapAccumulator(val);
        }
        frame.setComprehensionSlot(slotIndex, val);
      }
      return val;
    }

    static EvalComprehensionSlot create(CelExpr expr, int slotIndex, boolean wrapMutableAccu) {
      return new EvalComprehensionSlot(expr, slotIndex, wrapMutableAccu);
    }

    private EvalComprehensionSlot(CelExpr expr, int slotIndex, boolean wrapMutableAccu) {
      super(expr);
      this.slotIndex = slotIndex;
      this.wrapMutableAccu = wrapMutableAccu;
    }
  }

  private EvalFold(
      CelExpr expr,
      int accuSlot,
      PlannedInterpretable accuInit,
      int iterSlot,
      int iter2Slot,
      PlannedInterpretable iterRange,
      PlannedInterpretable condition,
      PlannedInterpretable loopStep,
      PlannedInterpretable result) {
    super(expr);
    this.accuSlot = accuSlot;
    this.accuInit = accuInit;
    this.iterSlot = iterSlot;
    this.iter2Slot = iter2Slot;
    this.iterRange = iterRange;
    this.condition = condition;
    this.loopStep = loopStep;
    this.result = result;
  }
}
