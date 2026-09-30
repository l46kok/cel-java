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

package dev.cel.extensions;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.parser.CelMacro;
import dev.cel.parser.CelParserBuilder;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.Set;

/**
 * Internal implementation of Math Extensions
 *
 * <p>Note: For equal numbers with different types, the result is always the first argument e.g.:
 * math.greatest(1u, 1.0) -> 1u
 */
@Immutable
public final class CelMathExtensions
    implements CelCompilerLibrary, CelRuntimeLibrary, CelExtensionLibrary.FeatureSet {

  /** Enumeration of functions for Math extension. */
  public enum Function {
    MAX(CelMathCompilerLibrary.Function.MAX, CelMathRuntimeLibrary.Function.MAX),
    MIN(CelMathCompilerLibrary.Function.MIN, CelMathRuntimeLibrary.Function.MIN),
    CEIL(CelMathCompilerLibrary.Function.CEIL, CelMathRuntimeLibrary.Function.CEIL),
    FLOOR(CelMathCompilerLibrary.Function.FLOOR, CelMathRuntimeLibrary.Function.FLOOR),
    ROUND(CelMathCompilerLibrary.Function.ROUND, CelMathRuntimeLibrary.Function.ROUND),
    TRUNC(CelMathCompilerLibrary.Function.TRUNC, CelMathRuntimeLibrary.Function.TRUNC),
    ISFINITE(CelMathCompilerLibrary.Function.ISFINITE, CelMathRuntimeLibrary.Function.ISFINITE),
    ISNAN(CelMathCompilerLibrary.Function.ISNAN, CelMathRuntimeLibrary.Function.ISNAN),
    ISINF(CelMathCompilerLibrary.Function.ISINF, CelMathRuntimeLibrary.Function.ISINF),
    ABS(CelMathCompilerLibrary.Function.ABS, CelMathRuntimeLibrary.Function.ABS),
    SIGN(CelMathCompilerLibrary.Function.SIGN, CelMathRuntimeLibrary.Function.SIGN),
    BITAND(CelMathCompilerLibrary.Function.BITAND, CelMathRuntimeLibrary.Function.BITAND),
    BITOR(CelMathCompilerLibrary.Function.BITOR, CelMathRuntimeLibrary.Function.BITOR),
    BITXOR(CelMathCompilerLibrary.Function.BITXOR, CelMathRuntimeLibrary.Function.BITXOR),
    BITNOT(CelMathCompilerLibrary.Function.BITNOT, CelMathRuntimeLibrary.Function.BITNOT),
    BITSHIFTLEFT(
        CelMathCompilerLibrary.Function.BITSHIFTLEFT, CelMathRuntimeLibrary.Function.BITSHIFTLEFT),
    BITSHIFTRIGHT(
        CelMathCompilerLibrary.Function.BITSHIFTRIGHT,
        CelMathRuntimeLibrary.Function.BITSHIFTRIGHT),
    SQRT(CelMathCompilerLibrary.Function.SQRT, CelMathRuntimeLibrary.Function.SQRT);

    private final CelMathCompilerLibrary.Function compilerFunction;
    private final CelMathRuntimeLibrary.Function runtimeFunction;

    String getFunction() {
      return compilerFunction.getFunction();
    }

    Function(
        CelMathCompilerLibrary.Function compilerFunction,
        CelMathRuntimeLibrary.Function runtimeFunction) {
      this.compilerFunction = compilerFunction;
      this.runtimeFunction = runtimeFunction;
    }
  }

  private static final class Library implements CelExtensionLibrary<CelMathExtensions> {
    private final ImmutableSet<CelMathExtensions> versions;

    Library() {
      versions =
          CelMathCompilerLibrary.library().versions().stream()
              .map(CelMathExtensions::new)
              .collect(toImmutableSet());
    }

    @Override
    public String name() {
      return CelMathCompilerLibrary.library().name();
    }

    @Override
    public ImmutableSet<CelMathExtensions> versions() {
      return versions;
    }
  }

  private static final Library LIBRARY = new Library();

  static CelExtensionLibrary<CelMathExtensions> library() {
    return LIBRARY;
  }

  private final CelMathCompilerLibrary compilerLibrary;
  private final CelMathRuntimeLibrary mathRuntime;

  CelMathExtensions(Set<Function> functions) {
    this.compilerLibrary =
        new CelMathCompilerLibrary(
            functions.stream().map(f -> f.compilerFunction).collect(toImmutableSet()));
    this.mathRuntime =
        new CelMathRuntimeLibrary(
            functions.stream().map(f -> f.runtimeFunction).collect(toImmutableSet()));
  }

  private CelMathExtensions(CelMathCompilerLibrary compilerLibrary) {
    this.compilerLibrary = compilerLibrary;
    this.mathRuntime = CelMathRuntimeLibrary.math(compilerLibrary.version());
  }

  @Override
  public int version() {
    return compilerLibrary.version();
  }

  @Override
  public ImmutableSet<CelFunctionDecl> functions() {
    return compilerLibrary.functions();
  }

  @Override
  public ImmutableSet<CelMacro> macros() {
    return compilerLibrary.macros();
  }

  @Override
  public void setParserOptions(CelParserBuilder parserBuilder) {
    compilerLibrary.setParserOptions(parserBuilder);
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    compilerLibrary.setCheckerOptions(checkerBuilder);
  }

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    runtimeBuilder.addFunctionBindings(mathRuntime.newFunctionBindings());
  }
}
