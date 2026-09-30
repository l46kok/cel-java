// Copyright 2022 Google LLC
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
import dev.cel.runtime.CelRuntimeBuilder;
import dev.cel.runtime.CelRuntimeLibrary;
import java.util.Set;

/** Internal implementation of CEL string extensions. */
@Immutable
public final class CelStringExtensions
    implements CelCompilerLibrary, CelRuntimeLibrary, CelExtensionLibrary.FeatureSet {

  /** Denotes the string extension function */
  public enum Function {
    CHAR_AT(CelStringCompilerLibrary.Function.CHAR_AT, CelStringRuntimeLibrary.Function.CHAR_AT),
    FORMAT(CelStringCompilerLibrary.Function.FORMAT, CelStringRuntimeLibrary.Function.FORMAT),
    INDEX_OF(CelStringCompilerLibrary.Function.INDEX_OF, CelStringRuntimeLibrary.Function.INDEX_OF),
    JOIN(CelStringCompilerLibrary.Function.JOIN, CelStringRuntimeLibrary.Function.JOIN),
    LAST_INDEX_OF(
        CelStringCompilerLibrary.Function.LAST_INDEX_OF,
        CelStringRuntimeLibrary.Function.LAST_INDEX_OF),
    LOWER_ASCII(
        CelStringCompilerLibrary.Function.LOWER_ASCII,
        CelStringRuntimeLibrary.Function.LOWER_ASCII),
    QUOTE(CelStringCompilerLibrary.Function.QUOTE, CelStringRuntimeLibrary.Function.QUOTE),
    REPLACE(CelStringCompilerLibrary.Function.REPLACE, CelStringRuntimeLibrary.Function.REPLACE),
    REVERSE(CelStringCompilerLibrary.Function.REVERSE, CelStringRuntimeLibrary.Function.REVERSE),
    SPLIT(CelStringCompilerLibrary.Function.SPLIT, CelStringRuntimeLibrary.Function.SPLIT),
    SUBSTRING(
        CelStringCompilerLibrary.Function.SUBSTRING, CelStringRuntimeLibrary.Function.SUBSTRING),
    TRIM(CelStringCompilerLibrary.Function.TRIM, CelStringRuntimeLibrary.Function.TRIM),
    UPPER_ASCII(
        CelStringCompilerLibrary.Function.UPPER_ASCII,
        CelStringRuntimeLibrary.Function.UPPER_ASCII);

    private final CelStringCompilerLibrary.Function compilerFunction;
    private final CelStringRuntimeLibrary.Function runtimeFunction;

    String getFunction() {
      return compilerFunction.getFunction();
    }

    Function(
        CelStringCompilerLibrary.Function compilerFunction,
        CelStringRuntimeLibrary.Function runtimeFunction) {
      this.compilerFunction = compilerFunction;
      this.runtimeFunction = runtimeFunction;
    }
  }

  private static final class Library implements CelExtensionLibrary<CelStringExtensions> {
    private final ImmutableSet<CelStringExtensions> versions;

    Library() {
      versions =
          CelStringCompilerLibrary.library().versions().stream()
              .map(CelStringExtensions::new)
              .collect(toImmutableSet());
    }

    @Override
    public String name() {
      return CelStringCompilerLibrary.library().name();
    }

    @Override
    public ImmutableSet<CelStringExtensions> versions() {
      return versions;
    }
  }

  private static final Library LIBRARY = new Library();

  static CelExtensionLibrary<CelStringExtensions> library() {
    return LIBRARY;
  }

  private final CelStringCompilerLibrary compilerLibrary;
  private final CelStringRuntimeLibrary stringRuntime;

  CelStringExtensions() {
    this(CelStringCompilerLibrary.strings());
  }

  CelStringExtensions(Set<Function> functions) {
    this.compilerLibrary =
        new CelStringCompilerLibrary(
            functions.stream().map(f -> f.compilerFunction).collect(toImmutableSet()));
    this.stringRuntime =
        new CelStringRuntimeLibrary(
            functions.stream().map(f -> f.runtimeFunction).collect(toImmutableSet()));
  }

  private CelStringExtensions(CelStringCompilerLibrary compilerLibrary) {
    this.compilerLibrary = compilerLibrary;
    this.stringRuntime = CelStringRuntimeLibrary.strings(compilerLibrary.version());
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
    runtimeBuilder.addFunctionBindings(stringRuntime.newFunctionBindings());
  }
}
