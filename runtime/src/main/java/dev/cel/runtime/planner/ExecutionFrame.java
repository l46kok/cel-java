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

import dev.cel.common.CelOptions;
import dev.cel.common.exceptions.CelIterationLimitExceededException;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.GlobalResolver;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Tracks execution context within a planned program. */
final class ExecutionFrame implements GlobalResolver {

  private final GlobalResolver delegate;
  private final CelFunctionResolver functionResolver;
  private final int comprehensionIterationLimit;
  private int iterationCount;

  @Override
  public @Nullable Object resolve(String name) {
    return delegate.resolve(name);
  }

  Optional<CelResolvedOverload> findOverload(
      String functionName, List<String> overloadIds, Object[] args) throws CelEvaluationException {
    if (overloadIds.isEmpty()) {
      return functionResolver.findOverloadMatchingArgs(functionName, args);
    }
    return functionResolver.findOverloadMatchingArgs(functionName, overloadIds, args);
  }

  void incrementIterations() {
    if (comprehensionIterationLimit < 0) {
      return;
    }
    if (++iterationCount > comprehensionIterationLimit) {
      throw new CelIterationLimitExceededException(comprehensionIterationLimit);
    }
  }

  static ExecutionFrame create(
      GlobalResolver delegate, CelFunctionResolver functionResolver, CelOptions celOptions) {
    return new ExecutionFrame(
        delegate, functionResolver, celOptions.comprehensionMaxIterations());
  }

  private ExecutionFrame(
      GlobalResolver delegate, CelFunctionResolver functionResolver, int limit) {
    this.delegate = delegate;
    this.functionResolver = functionResolver;
    this.comprehensionIterationLimit = limit;
  }
}
