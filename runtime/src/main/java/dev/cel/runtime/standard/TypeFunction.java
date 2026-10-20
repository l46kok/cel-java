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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelOptions;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.RuntimeEquality;
import dev.cel.runtime.TypeResolver;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/** Standard function for the {@code type} function. */
@Immutable
public final class TypeFunction extends CelStandardFunction {
  private static final TypeFunction ALL_OVERLOADS = create(TypeOverload.values());

  private final @Nullable TypeResolver typeResolver;

  public static TypeFunction create() {
    return ALL_OVERLOADS;
  }

  public static TypeFunction create(TypeFunction.TypeOverload... overloads) {
    return create(Arrays.asList(overloads));
  }

  public static TypeFunction create(Iterable<TypeFunction.TypeOverload> overloads) {
    return new TypeFunction(ImmutableSet.copyOf(overloads), /* typeResolver= */ null);
  }

  public static TypeFunction create(TypeResolver typeResolver) {
    return new TypeFunction(ImmutableSet.copyOf(TypeOverload.values()), checkNotNull(typeResolver));
  }

  /** Overloads for the standard {@code type} function. */
  public enum TypeOverload implements CelStandardOverload {
    TYPE;

    @Override
    public CelFunctionBinding newFunctionBinding(
        CelOptions celOptions, RuntimeEquality runtimeEquality) {
      // This overload is not used directly. The binding is created in TypeFunction via the
      // TypeResolver instance.
      // TODO: Instantiate from CelStandardFunctions.
      throw new UnsupportedOperationException(
          "TypeOverload bindings must be created through TypeFunction");
    }
  }

  @Override
  public ImmutableSet<CelFunctionBinding> newFunctionBindings(
      CelOptions celOptions, RuntimeEquality runtimeEquality) {
    return newFunctionBindings(checkNotNull(typeResolver));
  }

  @Override
  public ImmutableSet<CelFunctionBinding> newFunctionBindings(
      CelOptions celOptions,
      RuntimeEquality runtimeEquality,
      CelTypeProvider celTypeProvider,
      CelValueConverter celValueConverter) {
    return newFunctionBindings(TypeResolver.create(celTypeProvider, celValueConverter));
  }

  private static ImmutableSet<CelFunctionBinding> newFunctionBindings(TypeResolver typeResolver) {
    CelFunctionBinding binding =
        CelFunctionBinding.from(
            "type",
            Object.class,
            arg -> typeResolver.resolveObjectType(arg, TypeType.create(SimpleType.DYN)));

    return CelFunctionBinding.fromOverloads("type", binding);
  }

  private TypeFunction(
      ImmutableSet<CelStandardOverload> overloads, @Nullable TypeResolver typeResolver) {
    super("type", overloads);
    this.typeResolver = typeResolver;
  }
}