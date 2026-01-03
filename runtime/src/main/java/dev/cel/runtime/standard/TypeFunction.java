// Copyright 2026 Google LLC
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

package dev.cel.runtime.standard;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelOptions;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.TypeResolver;

/**
 * Standard function for {@code type} conversion function.
 *
 * <p>For proper protobuf message type resolution, create an instance using {@link
 * #create(TypeResolver)} with an appropriate type resolver:
 *
 * <ul>
 *   <li>For full protobuf: use {@code DescriptorTypeResolver.create()}
 *   <li>For protobuf-lite (Android): use {@code LiteDescriptorTypeResolver.create(pool)}
 * </ul>
 */
public final class TypeFunction extends CelStandardFunction {

  /**
   * Creates a new instance of the {@code type} function with the given type resolver.
   *
   * @param typeResolver The type resolver to use for resolving object types.
   */
  public static TypeFunction create(TypeResolver typeResolver) {
    return new TypeFunction(typeResolver);
  }

  /**
   * Overloads for the standard function.
   *
   * <p>Note: The TYPE overload uses the base TypeResolver which does not support protobuf message
   * types. For protobuf support, use {@link #create(TypeResolver)} with an appropriate resolver.
   */
  public enum TypeOverload implements CelStandardOverload {
    TYPE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "type",
                Object.class,
                (Object arg) -> TypeResolver.create().resolveObjectType(arg, SimpleType.DYN)));

    private final CelStandardOverload standardOverload;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      return standardOverload.newFunctionBinding(celOptions, runtimeEquality);
    }

    TypeOverload(CelStandardOverload standardOverload) {
      this.standardOverload = standardOverload;
    }
  }

  private final TypeResolver typeResolver;

  private TypeFunction(TypeResolver typeResolver) {
    super("type", ImmutableSet.copyOf(TypeOverload.values()));
    this.typeResolver = typeResolver;
  }

  @Override
  public ImmutableSet<CelFunctionBinding> newFunctionBindings(
      CelOptions celOptions, RuntimeEquality runtimeEquality) {
    return ImmutableSet.of(
        CelFunctionBinding.from(
            "type",
            Object.class,
            (Object arg) -> typeResolver.resolveObjectType(arg, SimpleType.DYN)));
  }

  /**
   * Creates a new instance of the {@code type} function with the default type resolver.
   *
   * <p>Note: The default TypeResolver does not support protobuf message types. For protobuf
   * support, use {@link #create(TypeResolver)} with either DescriptorTypeResolver (full protobuf)
   * or LiteDescriptorTypeResolver (protobuf-lite/Android).
   */
  public static TypeFunction create() {
    return create(TypeResolver.create());
  }
}
