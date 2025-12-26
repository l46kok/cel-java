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

package dev.cel.runtime.standard;

import com.google.common.collect.ImmutableSet;
import dev.cel.common.CelOptions;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.TypeResolver;
import java.util.Arrays;

/** Standard function for {@code type} conversion function. */
public final class TypeFunction extends CelStandardFunction {

  private static final TypeFunction ALL_OVERLOADS = create();

  public static TypeFunction create(TypeResolver typeResolver) {
    return new TypeFunction(typeResolver);
  }

  /** Overloads for the standard function. */
  public enum TypeOverload implements CelStandardOverload {
    TYPE(
        (celOptions, runtimeEquality) ->
            CelFunctionBinding.from(
                "type",
                Object.class,
                (Object arg) ->
                    // This placeholder lambda isn't actually used when the binding is created via
                    // the TypeFunction instance, as the instance's bind method is called.
                    // However, we need to satisfy the interface.
                    // A cleaner approach requires redesigning CelStandardOverload to support instance-based logic better.
                    // For now, let's keep the static definition but note it relies on the instance context.
                    // Wait, the enum defines the binding logic. The binding logic is functional.
                    // To inject state into the function binding, we need to move away from purely static enum-based definition
                    // or pass the resolver into the binding creation.
                    // The standard pattern is that the `create` method returns the `CelStandardFunction` instance.
                    // The `CelStandardFunction` instance has `newFunctionBindings`.
                    // We can override `newFunctionBindings` in `TypeFunction` to use the injected `typeResolver`.
                    TypeResolver.create().resolveObjectType(arg, SimpleType.DYN))); // Legacy path

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
   */
  public static TypeFunction create() {
    return create(TypeResolver.create());
  }
}
