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
package dev.cel.testing.utils;

import dev.cel.expr.ExprValue;
import dev.cel.expr.ListValue;
import dev.cel.expr.MapValue;
import dev.cel.expr.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.TypeRegistry;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.internal.DefaultInstanceMessageFactory;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeType;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Utility class for ExprValue and Value type conversions during test execution. */
@SuppressWarnings({"UnnecessarilyFullyQualified"})
public final class ExprValueUtils {

  private ExprValueUtils() {}

  public static final TypeRegistry DEFAULT_TYPE_REGISTRY = newDefaultTypeRegistry();
  public static final ExtensionRegistry DEFAULT_EXTENSION_REGISTRY = newDefaultExtensionRegistry();

  private static ExtensionRegistry newDefaultExtensionRegistry() {
    ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
    dev.cel.expr.conformance.proto2.TestAllTypesExtensions.registerAllExtensions(extensionRegistry);

    return extensionRegistry;
  }

  private static TypeRegistry newDefaultTypeRegistry() {
    CelDescriptors allDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
            ImmutableList.of(
                dev.cel.expr.conformance.proto2.TestAllTypes.getDescriptor().getFile(),
                dev.cel.expr.conformance.proto3.TestAllTypes.getDescriptor().getFile()));

    return TypeRegistry.newBuilder().add(allDescriptors.messageTypeDescriptors()).build();
  }

  /**
   * Converts a {@link Value} to a Java native object.
   *
   * @param value The {@link Value} to convert.
   * @return The converted Java object.
   * @throws Exception If the value type is unsupported or if there's an error during conversion.
   */
  public static Object fromValue(Value value) throws Exception {
    switch (value.getKindCase()) {
      case NULL_VALUE:
        return value.getNullValue();
      case BOOL_VALUE:
        return value.getBoolValue();
      case INT64_VALUE:
        return value.getInt64Value();
      case UINT64_VALUE:
        return UnsignedLong.fromLongBits(value.getUint64Value());
      case DOUBLE_VALUE:
        return value.getDoubleValue();
      case STRING_VALUE:
        return value.getStringValue();
      case BYTES_VALUE:
        return value.getBytesValue();
      case ENUM_VALUE:
        return value.getEnumValue();
      case OBJECT_VALUE:
        {
          Any object = value.getObjectValue();
          Descriptor descriptor =
              DEFAULT_TYPE_REGISTRY.getDescriptorForTypeUrl(object.getTypeUrl());
          Message prototype =
              DefaultInstanceMessageFactory.getInstance()
                  .getPrototype(descriptor)
                  .orElseThrow(
                      () ->
                          new NoSuchElementException(
                              "Could not find a default message for: " + descriptor.getFullName()));
          return prototype
              .getParserForType()
              .parseFrom(object.getValue(), DEFAULT_EXTENSION_REGISTRY);
        }
      case MAP_VALUE:
        {
          MapValue map = value.getMapValue();
          ImmutableMap.Builder<Object, Object> builder =
              ImmutableMap.builderWithExpectedSize(map.getEntriesCount());
          for (MapValue.Entry entry : map.getEntriesList()) {
            builder.put(fromValue(entry.getKey()), fromValue(entry.getValue()));
          }
          return builder.buildOrThrow();
        }
      case LIST_VALUE:
        {
          ListValue list = value.getListValue();
          ImmutableList.Builder<Object> builder =
              ImmutableList.builderWithExpectedSize(list.getValuesCount());
          for (Value element : list.getValuesList()) {
            builder.add(fromValue(element));
          }
          return builder.build();
        }
      case TYPE_VALUE:
        return value.getTypeValue();
      default:
        throw new IllegalArgumentException(
            String.format("Unexpected binding value kind: %s", value.getKindCase()));
    }
  }

  /**
   * Converts a Java object to an {@link ExprValue}.
   *
   * @param object The Java object to convert.
   * @param type The {@link CelType} of the object.
   * @return The converted {@link ExprValue}.
   * @throws Exception If there's an error during conversion.
   */
  public static ExprValue toExprValue(Object object, CelType type) throws Exception {
    if (object instanceof ExprValue) {
      return (ExprValue) object;
    }
    return ExprValue.newBuilder().setValue(toValue(object, type)).build();
  }

  /**
   * Converts a Java object to an {@link Value}.
   *
   * @param object The Java object to convert.
   * @param type The {@link CelType} of the object.
   * @return The converted {@link Value}.
   * @throws Exception If there's an error during conversion.
   */
  @SuppressWarnings("unchecked")
  public static Value toValue(Object object, CelType type) throws Exception {
    if (object == null) {
      object = NullValue.NULL_VALUE;
    }
    if (object instanceof dev.cel.expr.Value) {
      object =
          Value.parseFrom(
              ((dev.cel.expr.Value) object).toByteArray(), DEFAULT_EXTENSION_REGISTRY);
    }
    if (object instanceof Value) {
      return (Value) object;
    }
    if (object instanceof NullValue) {
      return Value.newBuilder().setNullValue((NullValue) object).build();
    }
    if (object instanceof Boolean) {
      return Value.newBuilder().setBoolValue((Boolean) object).build();
    }
    if (object instanceof UnsignedLong) {
      switch (type.kind()) {
        case UINT:
        case DYN:
        case ANY:
          return Value.newBuilder().setUint64Value(((UnsignedLong) object).longValue()).build();
        default:
          throw new IllegalArgumentException(String.format("Unexpected result type: %s", type));
      }
    }
    if (object instanceof Long) {
      switch (type.kind()) {
        case INT:
        case DYN:
        case ANY:
          return Value.newBuilder().setInt64Value((Long) object).build();
        case UINT:
          return Value.newBuilder().setUint64Value((Long) object).build();
        default:
          throw new IllegalArgumentException(String.format("Unexpected result type: %s", type));
      }
    }
    if (object instanceof Double) {
      return Value.newBuilder().setDoubleValue((Double) object).build();
    }
    if (object instanceof String) {
      switch (type.kind()) {
        case TYPE:
          return Value.newBuilder().setTypeValue((String) object).build();
        case STRING:
        case DYN:
        case ANY:
          return Value.newBuilder().setStringValue((String) object).build();
        default:
          throw new IllegalArgumentException(String.format("Unexpected result type: %s", type));
      }
    }
    if (object instanceof ByteString) {
      return Value.newBuilder().setBytesValue((ByteString) object).build();
    }
    if (object instanceof List) {
      CelType elemType = type instanceof ListType ? ((ListType) type).elemType() : SimpleType.DYN;
      ListValue.Builder builder = ListValue.newBuilder();
      for (Object element : ((List<Object>) object)) {
        builder.addValues(toValue(element, elemType));
      }
      return Value.newBuilder().setListValue(builder.build()).build();
    }
    if (object instanceof Map) {
      CelType keyType = type instanceof MapType ? ((MapType) type).keyType() : SimpleType.DYN;
      CelType valueType = type instanceof MapType ? ((MapType) type).valueType() : SimpleType.DYN;
      MapValue.Builder builder = MapValue.newBuilder();
      for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) object).entrySet()) {
        builder.addEntries(
            MapValue.Entry.newBuilder()
                .setKey(toValue(entry.getKey(), keyType))
                .setValue(toValue(entry.getValue(), valueType))
                .build());
      }
      return Value.newBuilder().setMapValue(builder.build()).build();
    }
    if (object instanceof Message) {
      return Value.newBuilder().setObjectValue(Any.pack((Message) object)).build();
    }
    if (object instanceof TypeType) {
      return Value.newBuilder().setTypeValue(((TypeType) object).containingTypeName()).build();
    }

    if (object instanceof Optional) {
      return toValue(((Optional<?>) object).get(), type);
    }

    throw new IllegalArgumentException(
        String.format("Unexpected result type: %s", object.getClass()));
  }
}
