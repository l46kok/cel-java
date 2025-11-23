package dev.cel.runtime.planner;

import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.Interpretable;

@Immutable
interface InterpretableAttribute extends Interpretable {

  InterpretableAttribute addQualifier(Qualifier qualifier);
}
