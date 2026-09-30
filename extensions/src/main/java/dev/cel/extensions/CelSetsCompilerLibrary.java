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
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.compiler.CelCompilerLibrary;
import java.util.Set;

/** Internal implementation of CEL Set compile-time extensions. */
@Immutable
public final class CelSetsCompilerLibrary
    implements CelCompilerLibrary, CelExtensionLibrary.FeatureSet {

  private static final String SET_CONTAINS_OVERLOAD_DOC =
      "Returns whether the first list argument contains all elements in the second list"
          + " argument. The list may contain elements of any type and standard CEL"
          + " equality is used to determine whether a value exists in both lists. If the"
          + " second list is empty, the result will always return true.";
  private static final String SET_EQUIVALENT_OVERLOAD_DOC =
      "Returns whether the first and second list are set equivalent. Lists are set equivalent if"
          + " for every item in the first list, there is an element in the second which is equal."
          + " The lists may not be of the same size as they do not guarantee the elements within"
          + " them are unique, so size does not factor into the computation.";
  private static final String SET_INTERSECTS_OVERLOAD_DOC =
      "Returns whether the first and second list intersect. Lists intersect if there is at least"
          + " one element in the first list which is equal to an element in the second list. The"
          + " lists may not be of the same size as they do not guarantee the elements within them"
          + " are unique, so size does not factor into the computation. If either list is empty,"
          + " the result will be false.";

  /** Enumeration of functions for Set compile-time extension. */
  public enum Function {
    CONTAINS(
        CelFunctionDecl.newFunctionDeclaration(
            "sets.contains",
            CelOverloadDecl.newGlobalOverload(
                "list_sets_contains_list",
                SET_CONTAINS_OVERLOAD_DOC,
                SimpleType.BOOL,
                ListType.create(TypeParamType.create("T")),
                ListType.create(TypeParamType.create("T"))))),
    EQUIVALENT(
        CelFunctionDecl.newFunctionDeclaration(
            "sets.equivalent",
            CelOverloadDecl.newGlobalOverload(
                "list_sets_equivalent_list",
                SET_EQUIVALENT_OVERLOAD_DOC,
                SimpleType.BOOL,
                ListType.create(TypeParamType.create("T")),
                ListType.create(TypeParamType.create("T"))))),
    INTERSECTS(
        CelFunctionDecl.newFunctionDeclaration(
            "sets.intersects",
            CelOverloadDecl.newGlobalOverload(
                "list_sets_intersects_list",
                SET_INTERSECTS_OVERLOAD_DOC,
                SimpleType.BOOL,
                ListType.create(TypeParamType.create("T")),
                ListType.create(TypeParamType.create("T")))));

    private final CelFunctionDecl functionDecl;

    public String getFunction() {
      return functionDecl.name();
    }

    public CelFunctionDecl getFunctionDecl() {
      return functionDecl;
    }

    Function(CelFunctionDecl functionDecl) {
      this.functionDecl = functionDecl;
    }
  }

  private static final class Library implements CelExtensionLibrary<CelSetsCompilerLibrary> {
    private final CelSetsCompilerLibrary version0;

    Library() {
      version0 = new CelSetsCompilerLibrary(0, ImmutableSet.copyOf(Function.values()));
    }

    @Override
    public String name() {
      return "sets";
    }

    @Override
    public ImmutableSet<CelSetsCompilerLibrary> versions() {
      return ImmutableSet.of(version0);
    }
  }

  private static final Library LIBRARY = new Library();

  public static CelExtensionLibrary<CelSetsCompilerLibrary> library() {
    return LIBRARY;
  }

  /** Returns the latest version of the 'sets' compiler extension. */
  public static CelSetsCompilerLibrary sets() {
    return library().latest();
  }

  /** Returns the specified version of the 'sets' compiler extension. */
  public static CelSetsCompilerLibrary sets(int version) {
    return library().version(version);
  }

  /** Returns the 'sets' compiler extension with only the specified functions. */
  public static CelSetsCompilerLibrary sets(Function... functions) {
    return sets(ImmutableSet.copyOf(functions));
  }

  /** Returns the 'sets' compiler extension with only the specified functions. */
  public static CelSetsCompilerLibrary sets(Set<Function> functions) {
    return new CelSetsCompilerLibrary(functions);
  }

  private final ImmutableSet<Function> functions;
  private final int version;

  CelSetsCompilerLibrary(Set<Function> functions) {
    this(-1, functions);
  }

  private CelSetsCompilerLibrary(int version, Set<Function> functions) {
    this.version = version;
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public int version() {
    return version;
  }

  @Override
  public ImmutableSet<CelFunctionDecl> functions() {
    return functions.stream().map(Function::getFunctionDecl).collect(toImmutableSet());
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    functions.forEach(function -> checkerBuilder.addFunctionDeclarations(function.functionDecl));
  }
}
