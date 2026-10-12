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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import dev.cel.common.CelOptions;
import dev.cel.common.exceptions.CelIterationLimitExceededException;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationListener;
import dev.cel.runtime.CelFunctionResolver;
import dev.cel.runtime.CelResolvedOverload;
import dev.cel.runtime.PartialVars;
import java.util.Collection;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Tracks execution context within a planned program. */
final class ExecutionFrame {

  private final int comprehensionIterationLimit;
  private final boolean enableShortCircuiting;
  private final CelFunctionResolver functionResolver;
  private final @Nullable PartialVars partialVars;
  private final @Nullable CelEvaluationListener listener;
  private final @Nullable AsyncCallStateTracker asyncTracker;
  private int iterationCount;
  private @Nullable BlockMemoizer blockMemoizer;
  // The last select chain root that adapted to a new object in this evaluation, and that object.
  private @Nullable Object lastRoot;
  private @Nullable Object lastTraversalTarget;

  static ExecutionFrame create(
      CelFunctionResolver functionResolver,
      CelOptions celOptions,
      @Nullable PartialVars partialVars,
      @Nullable CelEvaluationListener listener) {
    return new ExecutionFrame(
        functionResolver, celOptions, partialVars, listener, /* asyncTracker= */ null);
  }

  static ExecutionFrame createForAsync(
      CelFunctionResolver functionResolver,
      CelOptions celOptions,
      @Nullable PartialVars partialVars,
      @Nullable CelEvaluationListener listener,
      AsyncCallStateTracker asyncTracker) {
    checkNotNull(asyncTracker, "asyncTracker");
    return new ExecutionFrame(functionResolver, celOptions, partialVars, listener, asyncTracker);
  }

  private static int getComprehensionMaxIterations(CelOptions celOptions) {
    return celOptions.enableComprehension() ? celOptions.comprehensionMaxIterations() : 0;
  }

  Optional<CelResolvedOverload> findOverload(
      String functionName, Collection<String> overloadIds, Object[] args)
      throws CelEvaluationException {
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

  void setBlockMemoizer(BlockMemoizer blockMemoizer) {
    if (this.blockMemoizer != null) {
      throw new IllegalStateException("BlockMemoizer is already initialized");
    }
    this.blockMemoizer = blockMemoizer;
  }

  BlockMemoizer getBlockMemoizer() {
    return blockMemoizer;
  }

  /** Adapts a select chain root, reusing the last adaptation if the root is the same object. */
  @SuppressWarnings("ReferenceEquality") // Identity is the point: value equality would be O(size).
  Object toTraversalTarget(Object root, CelValueConverter celValueConverter) {
    // The converter isn't part of the key: a program plans all its chains with one converter.
    if (root == lastRoot) {
      return lastTraversalTarget;
    }
    Object adapted = celValueConverter.toTraversalTarget(root);
    // Roots that adapt to themselves (maps, CelValues) mustn't evict a reusable wrapper.
    if (adapted != root) {
      lastRoot = root;
      lastTraversalTarget = adapted;
    }
    return adapted;
  }

  boolean isAsync() {
    return asyncTracker != null;
  }

  AsyncCallStateTracker asyncTracker() {
    checkState(asyncTracker != null, "Not in async execution mode");
    return asyncTracker;
  }

  boolean enableShortCircuiting() {
    return enableShortCircuiting;
  }

  Optional<PartialVars> partialVars() {
    return Optional.ofNullable(partialVars);
  }

  @Nullable CelEvaluationListener getListener() {
    return listener;
  }

  private ExecutionFrame(
      CelFunctionResolver functionResolver,
      CelOptions celOptions,
      @Nullable PartialVars partialVars,
      @Nullable CelEvaluationListener listener,
      @Nullable AsyncCallStateTracker asyncTracker) {
    this.comprehensionIterationLimit = getComprehensionMaxIterations(celOptions);
    this.enableShortCircuiting = celOptions.enableShortCircuiting();
    this.functionResolver = functionResolver;
    this.partialVars = partialVars;
    this.listener = listener;
    this.asyncTracker = asyncTracker;
  }
}
