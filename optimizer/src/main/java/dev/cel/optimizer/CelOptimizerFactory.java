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

package dev.cel.optimizer;

import dev.cel.bundle.CelBuilder;
import dev.cel.bundle.CelFactory;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.compiler.CelCompilerBuilder;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.parser.CelParserBuilder;
import dev.cel.runtime.CelRuntimeBuilder;
import java.util.function.Supplier;

/** Factory class for constructing an {@link CelOptimizer} instance. */
public final class CelOptimizerFactory {

  public static CelOptimizerBuilder standardCelOptimizerBuilder(
      Supplier<CelBuilder> celBuilderFunction) {
    return CelOptimizerImpl.newBuilder(celBuilderFunction);
  }

  /** Create a new builder for constructing a {@link CelOptimizer} instance. */
  public static CelOptimizerBuilder standardCelOptimizerBuilder(
      Supplier<CelCompilerBuilder> celCompiler, Supplier<CelRuntimeBuilder> celRuntime) {
    return standardCelOptimizerBuilder(CelFactory.combineBuilders(celCompiler, celRuntime));
  }

  /** Create a new builder for constructing a {@link CelOptimizer} instance. */
  public static CelOptimizerBuilder standardCelOptimizerBuilder(
      Supplier<CelParserBuilder> celParser, Supplier<CelCheckerBuilder> celChecker, Supplier<CelRuntimeBuilder> celRuntime) {
    return standardCelOptimizerBuilder(
        CelCompilerFactory.combineBuilders(celParser, celChecker), celRuntime);
  }


  private CelOptimizerFactory() {}


}
