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
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * {@code DescriptorTypeResolver} extends {@link TypeResolver} and additionally resolves incoming
 * protobuf message types using descriptors.
 */
@Immutable
public final class DescriptorTypeResolver extends TypeResolver {

  private final @Nullable CelTypeProvider typeProvider;

  public static DescriptorTypeResolver create() {
    return new DescriptorTypeResolver();
  }

  public static DescriptorTypeResolver create(CelTypeProvider typeProvider) {
    return new DescriptorTypeResolver(typeProvider);
  }

  @Override
  public TypeType resolveObjectType(Object obj, CelType typeCheckedType) {
    checkNotNull(obj);

    Optional<TypeType> wellKnownTypeType = resolveWellKnownObjectType(obj);
    if (wellKnownTypeType.isPresent()) {
      return wellKnownTypeType.get();
    }

    if (obj instanceof MessageOrBuilder) {
      MessageOrBuilder msg = (MessageOrBuilder) obj;
      String typeName = msg.getDescriptorForType().getFullName();
      if (typeProvider != null) {
        Optional<CelType> type = typeProvider.findType(typeName);
        if (type.isPresent()) {
          return TypeType.create(type.get());
        }
      }

      return TypeType.create(StructTypeReference.create(typeName));
    }

    return super.resolveObjectType(obj, typeCheckedType);
  }

  private DescriptorTypeResolver() {
    this(null);
  }

  private DescriptorTypeResolver(CelTypeProvider typeProvider) {
    this.typeProvider = typeProvider;
  }
}
