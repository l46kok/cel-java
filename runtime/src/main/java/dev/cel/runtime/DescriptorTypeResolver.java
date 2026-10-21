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

package dev.cel.runtime;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageOrBuilder;
import dev.cel.common.annotations.Internal;
import dev.cel.common.types.CelType;
import dev.cel.common.types.DefaultTypeProvider;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelValueConverter;
import java.util.Optional;

/**
 * {@code DescriptorTypeResolver} extends {@link TypeResolver} and additionally resolves incoming
 * protobuf message types using descriptors.
 *
 * <p>CEL Library Internals. Do Not Use.
 *
 * @deprecated This only exists to maintain support for the legacy runtime. Use {@link TypeResolver}
 *     instead.
 */
@Deprecated
@Immutable
@Internal
final class DescriptorTypeResolver extends TypeResolver {

  /**
   * Creates a {@code DescriptorTypeResolver}. All protobuf messages are resolved as a type of
   * {@link StructTypeReference}.
   */
  static DescriptorTypeResolver create(CelValueConverter celValueConverter) {
    return new DescriptorTypeResolver(celValueConverter);
  }

  @Override
  public TypeType resolveObjectType(Object obj, CelType typeCheckedType) {
    checkNotNull(obj);

    Optional<TypeType> wellKnownTypeType = resolveWellKnownObjectType(obj);
    if (wellKnownTypeType.isPresent()) {
      return wellKnownTypeType.get();
    }

    if (obj instanceof MessageOrBuilder) {
      return TypeType.create(
          StructTypeReference.create(
              ((MessageOrBuilder) obj).getDescriptorForType().getFullName()));
    }

    return super.resolveObjectType(obj, typeCheckedType);
  }

  private DescriptorTypeResolver(CelValueConverter celValueConverter) {
    super(DefaultTypeProvider.getInstance(), celValueConverter);
  }
}
