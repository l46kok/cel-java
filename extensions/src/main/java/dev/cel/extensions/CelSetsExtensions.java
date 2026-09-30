// Copyright 2024 Google LLC
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
import dev.cel.common.CelOptions;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
import dev.cel.compiler.CelCompilerLibrary;
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import dev.cel.runtime.ProtoMessageRuntimeEquality;
import java.util.Set;

/**
 * Internal implementation of CEL Set extensions.
 *
 * <p>TODO: https://github.com/google/cel-go/blob/master/ext/sets.go#L127
 *
 * <p>Invoking in operator will result in O(n) complexity. We need to wire in the CEL optimizers to
 * rewrite the AST into a map to achieve a O(1) lookup.
 */
@Immutable
public final class CelSetsExtensions
    implements CelCompilerLibrary, CelRuntimeLibrary, CelExtensionLibrary.FeatureSet {

  /** Denotes the set extension function. */
  public enum Function {
    CONTAINS(CelSetsCompilerLibrary.Function.CONTAINS, CelSetsRuntimeLibrary.Function.CONTAINS),
    EQUIVALENT(
        CelSetsCompilerLibrary.Function.EQUIVALENT, CelSetsRuntimeLibrary.Function.EQUIVALENT),
    INTERSECTS(
        CelSetsCompilerLibrary.Function.INTERSECTS, CelSetsRuntimeLibrary.Function.INTERSECTS);

    private final CelSetsCompilerLibrary.Function compilerFunction;
    private final CelSetsRuntimeLibrary.Function runtimeFunction;

    String getFunction() {
      return compilerFunction.getFunction();
    }

    Function(
        CelSetsCompilerLibrary.Function compilerFunction,
        CelSetsRuntimeLibrary.Function runtimeFunction) {
      this.compilerFunction = compilerFunction;
      this.runtimeFunction = runtimeFunction;
    }
  }

  private static final class Library implements CelExtensionLibrary<CelSetsExtensions> {
    private final ImmutableSet<CelSetsExtensions> versions;

    Library(CelOptions celOptions) {
      versions =
          CelSetsCompilerLibrary.library().versions().stream()
              .map(compilerLibrary -> new CelSetsExtensions(celOptions, compilerLibrary))
              .collect(toImmutableSet());
    }

    @Override
    public String name() {
      return CelSetsCompilerLibrary.library().name();
    }

    @Override
    public ImmutableSet<CelSetsExtensions> versions() {
      return versions;
    }
  }

  static CelExtensionLibrary<CelSetsExtensions> library(CelOptions options) {
    return new Library(options);
  }

  private final CelSetsCompilerLibrary compilerLibrary;
  private final CelSetsRuntimeLibrary setsRuntime;

  CelSetsExtensions(CelOptions celOptions) {
    this(celOptions, CelSetsCompilerLibrary.sets());
  }

  CelSetsExtensions(CelOptions celOptions, Set<Function> functions) {
    this.compilerLibrary =
        new CelSetsCompilerLibrary(
            functions.stream().map(f -> f.compilerFunction).collect(toImmutableSet()));
    ProtoMessageRuntimeEquality runtimeEquality =
        ProtoMessageRuntimeEquality.create(
            DynamicProto.create(DefaultMessageFactory.INSTANCE), celOptions);
    this.setsRuntime =
        new CelSetsRuntimeLibrary(
            runtimeEquality,
            functions.stream().map(f -> f.runtimeFunction).collect(toImmutableSet()));
  }

  private CelSetsExtensions(CelOptions celOptions, CelSetsCompilerLibrary compilerLibrary) {
    this.compilerLibrary = compilerLibrary;
    ProtoMessageRuntimeEquality runtimeEquality =
        ProtoMessageRuntimeEquality.create(
            DynamicProto.create(DefaultMessageFactory.INSTANCE), celOptions);
    this.setsRuntime = new CelSetsRuntimeLibrary(runtimeEquality, compilerLibrary.version());
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
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    compilerLibrary.setCheckerOptions(checkerBuilder);
  }

  @Override
  public void setRuntimeOptions(CelRuntimeBuilder runtimeBuilder) {
    runtimeBuilder.addFunctionBindings(setsRuntime.newFunctionBindings());
  }
}
