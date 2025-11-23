package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.planner.Qualifier.PresenceTestQualifier;

@Immutable
final class EvalPresenceTest implements InterpretableAttribute {

  private final InterpretableAttribute attr;

  @Override
  public Object eval(GlobalResolver resolver) throws CelEvaluationException {
    return attr.eval(resolver);
  }

  @Override
  public EvalPresenceTest addQualifier(Qualifier qualifier) {
    PresenceTestQualifier presenceTestQualifier = new PresenceTestQualifier(qualifier.value());
    return new EvalPresenceTest(attr.addQualifier(presenceTestQualifier));
  }

  @Override
  public Object eval(GlobalResolver resolver, CelEvaluationListener listener) throws CelEvaluationException {
    throw new UnsupportedOperationException("Not yet supported");
  }

  @Override
  public Object eval(GlobalResolver resolver, CelFunctionResolver lateBoundFunctionResolver) throws CelEvaluationException {
    throw new UnsupportedOperationException("Not yet supported");
  }

  @Override
  public Object eval(GlobalResolver resolver, CelFunctionResolver lateBoundFunctionResolver, CelEvaluationListener listener) throws CelEvaluationException {
    throw new UnsupportedOperationException("Not yet supported");
  }

  static EvalPresenceTest create(InterpretableAttribute attr) {
    return new EvalPresenceTest(attr);
  }

  private EvalPresenceTest(InterpretableAttribute attr) {
    this.attr = attr;
  }
}
