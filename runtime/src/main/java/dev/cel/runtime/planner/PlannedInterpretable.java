package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.Interpretable;

@Immutable
abstract class PlannedInterpretable implements Interpretable {
  private final long exprId;

  long exprId() {
    return exprId;
  }

  PlannedInterpretable(long exprId) {
    this.exprId = exprId;
  }
}
