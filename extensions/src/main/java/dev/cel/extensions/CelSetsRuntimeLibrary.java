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

package dev.cel.extensions;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelOptions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelLiteRuntimeBuilder;
import dev.cel.runtime.CelLiteRuntimeLibrary;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.RuntimeHelpers;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/** Runtime implementation of CEL Set extension functions. */
@Immutable
public final class CelSetsRuntimeLibrary implements CelLiteRuntimeLibrary {

  /** Enumeration of functions for Set runtime extension. */
  public enum Function {
    CONTAINS("sets.contains"),
    EQUIVALENT("sets.equivalent"),
    INTERSECTS("sets.intersects");

    private final String functionName;

    public String getFunction() {
      return functionName;
    }

    Function(String functionName) {
      this.functionName = functionName;
    }
  }

  private static ImmutableSet<Function> getFunctionsForVersion(int version) {
    switch (version) {
      case 0:
      case Integer.MAX_VALUE:
        return ImmutableSet.copyOf(Function.values());
      default:
        throw new IllegalArgumentException("Unsupported 'sets' extension version " + version);
    }
  }

  /**
   * Returns the latest version of the 'sets' runtime functions using {@link CelOptions#DEFAULT}.
   */
  public static CelSetsRuntimeLibrary sets() {
    return sets(CelOptions.DEFAULT);
  }

  /**
   * Returns the specified version of the 'sets' runtime functions using {@link CelOptions#DEFAULT}.
   */
  public static CelSetsRuntimeLibrary sets(int version) {
    return sets(CelOptions.DEFAULT, version);
  }

  /**
   * Returns the 'sets' runtime functions with only the specified functions using {@link
   * CelOptions#DEFAULT}.
   */
  public static CelSetsRuntimeLibrary sets(Function... functions) {
    return sets(CelOptions.DEFAULT, functions);
  }

  /**
   * Returns the 'sets' runtime functions with only the specified functions using {@link
   * CelOptions#DEFAULT}.
   */
  public static CelSetsRuntimeLibrary sets(Set<Function> functions) {
    return sets(CelOptions.DEFAULT, functions);
  }

  /** Returns the latest version of the 'sets' runtime functions. */
  public static CelSetsRuntimeLibrary sets(CelOptions celOptions) {
    return sets(celOptions, ImmutableSet.copyOf(Function.values()));
  }

  /** Returns the specified version of the 'sets' runtime functions. */
  public static CelSetsRuntimeLibrary sets(CelOptions celOptions, int version) {
    return sets(celOptions, getFunctionsForVersion(version));
  }

  /** Returns the 'sets' runtime functions with only the specified functions. */
  public static CelSetsRuntimeLibrary sets(CelOptions celOptions, Function... functions) {
    return sets(celOptions, ImmutableSet.copyOf(functions));
  }

  /** Returns the 'sets' runtime functions with only the specified functions. */
  public static CelSetsRuntimeLibrary sets(CelOptions celOptions, Set<Function> functions) {
    RuntimeEquality runtimeEquality = RuntimeEquality.create(RuntimeHelpers.create(), celOptions);
    return new CelSetsRuntimeLibrary(runtimeEquality, functions);
  }

  private final RuntimeEquality runtimeEquality;
  private final ImmutableSet<Function> functions;

  CelSetsRuntimeLibrary(RuntimeEquality runtimeEquality, int version) {
    this(runtimeEquality, getFunctionsForVersion(version));
  }

  CelSetsRuntimeLibrary(RuntimeEquality runtimeEquality, Set<Function> functions) {
    this.runtimeEquality = runtimeEquality;
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public void setRuntimeOptions(CelLiteRuntimeBuilder runtimeBuilder) {
    runtimeBuilder.addFunctionBindings(newFunctionBindings());
  }

  /** Creates the {@link CelFunctionBinding}s for the configured set functions. */
  public ImmutableSet<CelFunctionBinding> newFunctionBindings() {
    ImmutableSet.Builder<CelFunctionBinding> bindingBuilder = ImmutableSet.builder();
    for (Function function : functions) {
      switch (function) {
        case CONTAINS:
          bindingBuilder.addAll(
              CelFunctionBinding.fromOverloads(
                  function.getFunction(),
                  CelFunctionBinding.from(
                      "list_sets_contains_list",
                      Collection.class,
                      Collection.class,
                      this::containsAll)));
          break;
        case EQUIVALENT:
          bindingBuilder.addAll(
              CelFunctionBinding.fromOverloads(
                  function.getFunction(),
                  CelFunctionBinding.from(
                      "list_sets_equivalent_list",
                      Collection.class,
                      Collection.class,
                      (listA, listB) -> containsAll(listA, listB) && containsAll(listB, listA))));
          break;
        case INTERSECTS:
          bindingBuilder.addAll(
              CelFunctionBinding.fromOverloads(
                  function.getFunction(),
                  CelFunctionBinding.from(
                      "list_sets_intersects_list",
                      Collection.class,
                      Collection.class,
                      this::setIntersects)));
          break;
      }
    }

    return bindingBuilder.build();
  }

  /**
   * This implementation iterates over the specified collection, checking each element returned by
   * the iterator in turn to see if it's contained in this collection. If all elements are so
   * contained <tt>true</tt> is returned, otherwise <tt>false</tt>.
   *
   * <p>This is picked verbatim as implemented in the Java standard library
   * Collections.containsAll() method.
   *
   * @see #contains(Object, Collection)
   */
  private boolean containsAll(Collection<?> list, Collection<?> subList) {
    for (Object e : subList) {
      if (!contains(e, list)) {
        return false;
      }
    }
    return true;
  }

  /**
   * This implementation iterates over the elements in the collection, checking each element in turn
   * for equality with the specified element.
   *
   * <p>This is picked verbatim as implemented in the Java standard library Collections.contains()
   * method.
   *
   * <p>Source: <a
   * href="https://hg.openjdk.org/jdk8u/jdk8u-dev/jdk/file/c5d02f908fb2/src/share/classes/java/util/AbstractCollection.java#l98">OpenJDK
   * AbstractCollection<a>
   */
  private boolean contains(Object o, Collection<?> list) {
    Iterator<?> it = list.iterator();
    if (o == null) {
      while (it.hasNext()) {
        if (it.next() == null) {
          return true;
        }
      }
    } else {
      while (it.hasNext()) {
        Object item = it.next();
        if (objectsEquals(item, o)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean objectsEquals(Object o1, Object o2) {
    return runtimeEquality.objectEquals(o1, o2);
  }

  private boolean setIntersects(Collection<?> listA, Collection<?> listB) {
    if (listA.isEmpty() || listB.isEmpty()) {
      return false;
    }
    for (Object element : listB) {
      if (contains(element, listA)) {
        return true;
      }
    }
    return false;
  }
}
