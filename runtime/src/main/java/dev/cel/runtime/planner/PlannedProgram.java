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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelOptions;
import dev.cel.common.CelRuntimeException;
import dev.cel.common.values.ErrorValue;
import dev.cel.runtime.Activation;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationExceptionBuilder;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.CelVariableResolver;
import dev.cel.runtime.GlobalResolver;
import dev.cel.runtime.Program;
import dev.cel.runtime.CelResolvedOverload;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Immutable
@AutoValue
abstract class PlannedProgram implements Program {
  private static final CelFunctionResolver NO_LATE_BOUND_FUNCTIONS =
      new CelFunctionResolver() {
        @Override
        public Optional<CelResolvedOverload> findOverloadMatchingArgs(
            String functionName, List<String> overloadIds, Object[] args) {
          return Optional.empty();
        }

        @Override
        public Optional<CelResolvedOverload> findOverloadMatchingArgs(
            String functionName, Object[] args) {
          return Optional.empty();
        }
      };

  abstract PlannedInterpretable interpretable();

  abstract ErrorMetadata metadata();

  abstract CelOptions options();

  abstract CelFunctionResolver lateBoundResolver();

  @Override
  public Object eval() throws CelEvaluationException {
    return evalOrThrow(interpretable(), GlobalResolver.EMPTY, lateBoundResolver());
  }

  @Override
  public Object eval(Map<String, ?> mapValue) throws CelEvaluationException {
    return evalOrThrow(
        interpretable(), Activation.copyOf(mapValue), lateBoundResolver());
  }

  @Override
  public Object eval(Map<String, ?> mapValue, CelFunctionResolver lateBoundFunctionResolver)
      throws CelEvaluationException {
    return evalOrThrow(
        interpretable(), Activation.copyOf(mapValue), lateBoundFunctionResolver);
  }

  @Override
  public Object eval(CelVariableResolver resolver) throws CelEvaluationException {
    return evalOrThrow(
        interpretable(),
        ((name) -> resolver.find(name).orElse(null)),
        lateBoundResolver());
  }

  private Object evalOrThrow(
      PlannedInterpretable interpretable,
      GlobalResolver resolver,
      CelFunctionResolver functionResolver)
      throws CelEvaluationException {
    try {
      ExecutionFrame frame = ExecutionFrame.create(resolver, functionResolver, options());
      Object evalResult = interpretable.eval(resolver, frame);
      if (evalResult instanceof ErrorValue) {
        ErrorValue errorValue = (ErrorValue) evalResult;
        throw newCelEvaluationException(errorValue.exprId(), errorValue.value());
      }

      return evalResult;
    } catch (RuntimeException e) {
      throw newCelEvaluationException(interpretable.exprId(), e);
    }
  }

  private CelEvaluationException newCelEvaluationException(long exprId, Exception e) {
    CelEvaluationExceptionBuilder builder;
    if (e instanceof StrictErrorException) {
      // Preserve detailed error, including error codes if one exists.
      builder = CelEvaluationExceptionBuilder.newBuilder((CelRuntimeException) e.getCause());
    } else {
      builder = CelEvaluationExceptionBuilder.newBuilder(e.getMessage());
    }

    return builder.setMetadata(metadata(), exprId).build();
  }

  static Program create(
      PlannedInterpretable interpretable,
      ErrorMetadata metadata,
      CelOptions options,
      CelFunctionResolver lateBoundResolver) {
    return new AutoValue_PlannedProgram(interpretable, metadata, options, lateBoundResolver);
  }
}
