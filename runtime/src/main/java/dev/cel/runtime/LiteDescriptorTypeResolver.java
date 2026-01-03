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

package dev.cel.runtime;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.MessageLite;
import dev.cel.common.internal.CelLiteDescriptorPool;
import dev.cel.common.types.CelType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeType;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.util.Optional;

/**
 * Type resolver for MessageLite objects using CelLiteDescriptor.
 *
 * <p>This is the Android-compatible alternative to {@link DescriptorTypeResolver}. It uses {@link
 * CelLiteDescriptorPool} to resolve the type names of protobuf MessageLite objects without
 * requiring full protobuf descriptor support.
 */
@Immutable
public final class LiteDescriptorTypeResolver extends TypeResolver {
  private final CelLiteDescriptorPool liteDescriptorPool;

  /** Creates a new LiteDescriptorTypeResolver with the given descriptor pool. */
  public static LiteDescriptorTypeResolver create(CelLiteDescriptorPool liteDescriptorPool) {
    return new LiteDescriptorTypeResolver(liteDescriptorPool);
  }

  @Override
  public TypeType resolveObjectType(Object obj, CelType typeCheckedType) {
    checkNotNull(obj);

    Optional<TypeType> wellKnownTypeType = resolveWellKnownObjectType(obj);
    if (wellKnownTypeType.isPresent()) {
      return wellKnownTypeType.get();
    }

    if (obj instanceof MessageLite) {
      MessageLite msg = (MessageLite) obj;
      Optional<MessageLiteDescriptor> descriptor = liteDescriptorPool.findDescriptor(msg);
      if (descriptor.isPresent()) {
        return TypeType.create(StructTypeReference.create(descriptor.get().getProtoTypeName()));
      }
    }

    return super.resolveObjectType(obj, typeCheckedType);
  }

  private LiteDescriptorTypeResolver(CelLiteDescriptorPool liteDescriptorPool) {
    this.liteDescriptorPool = checkNotNull(liteDescriptorPool);
  }
}
