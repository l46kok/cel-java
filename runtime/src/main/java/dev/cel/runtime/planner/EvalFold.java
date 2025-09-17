package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.common.values.CelValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.GlobalResolver;

@Immutable
final class EvalFold implements CelValueInterpretable {

  private final String accuVar;
  private final CelValueInterpretable accuInit;
  private final String iterVar;
  private final String iterVar2;
  private final CelValueInterpretable iterRange;
  private final CelValueInterpretable condition;
  private final CelValueInterpretable loopStep;
  private final CelValueInterpretable result;

  @Override
  public CelValue eval(GlobalResolver resolver) throws CelEvaluationException {
    CelValue foldRange = iterRange.eval(resolver);

    return foldRange;
  }

  static EvalFold create(
      String accuVar,
      CelValueInterpretable accuInit,
      String iterVar,
      String iterVar2,
      CelValueInterpretable iterRange,
      CelValueInterpretable condition,
      CelValueInterpretable loopStep,
      CelValueInterpretable result) {
    return new EvalFold(
        accuVar,
        accuInit,
        iterVar,
        iterVar2,
        iterRange,
        condition,
        loopStep,
        result
    );
  }

  private EvalFold(
      String accuVar,
      CelValueInterpretable accuInit,
      String iterVar,
      String iterVar2,
      CelValueInterpretable iterRange,
      CelValueInterpretable condition,
      CelValueInterpretable loopStep,
      CelValueInterpretable result) {
    this.accuVar = accuVar;
    this.accuInit = accuInit;
    this.iterVar = iterVar;
    this.iterVar2 = iterVar2;
    this.iterRange = iterRange;
    this.condition = condition;
    this.loopStep = loopStep;
    this.result = result;
  }
}