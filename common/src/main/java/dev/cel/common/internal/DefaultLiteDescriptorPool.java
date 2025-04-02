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

package dev.cel.common.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.MessageLite;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import dev.cel.common.annotations.Internal;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor.JavaType;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.util.Optional;
import java.util.function.Supplier;

/** Descriptor pool for {@link CelLiteDescriptor}s. */
@Immutable
@Internal
public final class DefaultLiteDescriptorPool implements CelLiteDescriptorPool {
  private final ImmutableMap<String, MessageLiteDescriptor> protoFqnToMessageInfo;

  public static DefaultLiteDescriptorPool newInstance(ImmutableSet<CelLiteDescriptor> descriptors) {
    return new DefaultLiteDescriptorPool(descriptors);
  }

  @Override
  public Optional<MessageLiteDescriptor> findDescriptor(String protoTypeName) {
    return Optional.ofNullable(protoFqnToMessageInfo.get(protoTypeName));
  }

  private static MessageLiteDescriptor newMessageInfo(WellKnownProto wellKnownProto) {
    ImmutableList.Builder<FieldLiteDescriptor> fieldDescriptors = ImmutableList.builder();
    Supplier<MessageLite.Builder> messageBuilder = null;
    switch (wellKnownProto) {
      case JSON_STRUCT_VALUE:
        messageBuilder = Struct::newBuilder;
        fieldDescriptors.add(
            new FieldLiteDescriptor(
                1,
                "fields",
                "google.protobuf.Struct.fields",
                JavaType.MESSAGE.toString(),
                FieldLiteDescriptor.CelFieldValueType.MAP.toString(),
                FieldLiteDescriptor.Type.MESSAGE.toString(),
                false,
                "google.protobuf.Struct.FieldsEntry"));
        break;
      case BOOL_VALUE:
        messageBuilder = BoolValue::newBuilder;
        fieldDescriptors.add(
            newPrimitiveFieldInfo(
                JavaType.BOOLEAN,
                FieldLiteDescriptor.Type.BOOL));
        break;
      case BYTES_VALUE:
        messageBuilder = BytesValue::newBuilder;
        fieldDescriptors.add(
            newPrimitiveFieldInfo(
                JavaType.BYTE_STRING,
                FieldLiteDescriptor.Type.BYTES));
        break;
      case DOUBLE_VALUE:
        messageBuilder = DoubleValue::newBuilder;
        fieldDescriptors.add(
            newPrimitiveFieldInfo(
                JavaType.DOUBLE,
                FieldLiteDescriptor.Type.DOUBLE));
        break;
      case FLOAT_VALUE:
        messageBuilder = FloatValue::newBuilder;
        fieldDescriptors.add(
            newPrimitiveFieldInfo(
                JavaType.FLOAT,
                FieldLiteDescriptor.Type.FLOAT));
        break;
      case INT32_VALUE:
        messageBuilder = Int32Value::newBuilder;
        fieldDescriptors.add(
            newPrimitiveFieldInfo(
                JavaType.INT,
                FieldLiteDescriptor.Type.INT32));
        break;
      case INT64_VALUE:
        messageBuilder = Int64Value::newBuilder;
        fieldDescriptors.add(
            newPrimitiveFieldInfo(
                JavaType.LONG,
                FieldLiteDescriptor.Type.INT64));
        break;
      case STRING_VALUE:
        messageBuilder = StringValue::newBuilder;
        fieldDescriptors.add(
            newPrimitiveFieldInfo(
                JavaType.STRING,
                FieldLiteDescriptor.Type.STRING));
        break;
      case UINT32_VALUE:
        messageBuilder = UInt32Value::newBuilder;
        fieldDescriptors.add(
            newPrimitiveFieldInfo(
                JavaType.INT,
                FieldLiteDescriptor.Type.UINT32));
        break;
      case UINT64_VALUE:
        messageBuilder = UInt64Value::newBuilder;
        fieldDescriptors.add(
            newPrimitiveFieldInfo(
                JavaType.LONG,
                FieldLiteDescriptor.Type.UINT64));
        break;
      case JSON_VALUE:
        messageBuilder = Value::newBuilder;
        break;
      case JSON_LIST_VALUE:
        messageBuilder = ListValue::newBuilder;
        break;
      case DURATION:
        messageBuilder = Duration::newBuilder;
        break;
      case TIMESTAMP:
        messageBuilder = Timestamp::newBuilder;
        // TODO: Complete these
        break;
      default:
        break;
    }

    return new MessageLiteDescriptor(
        wellKnownProto.typeName(),
        fieldDescriptors.build(),
        messageBuilder
      );
  }

  private static FieldLiteDescriptor newPrimitiveFieldInfo(
      JavaType javaType,
      FieldLiteDescriptor.Type protoFieldType) {
    return new FieldLiteDescriptor(
        1,
        "value",
        "",
        javaType.toString(),
        FieldLiteDescriptor.CelFieldValueType.SCALAR.toString(),
        protoFieldType.toString(),
        false,
        "");
  }

  private DefaultLiteDescriptorPool(ImmutableSet<CelLiteDescriptor> descriptors) {
    ImmutableMap.Builder<String, MessageLiteDescriptor> protoFqnMapBuilder = ImmutableMap.builder();
    for (WellKnownProto wellKnownProto : WellKnownProto.values()) {
      MessageLiteDescriptor wktMessageInfo = newMessageInfo(wellKnownProto);
      protoFqnMapBuilder.put(wellKnownProto.typeName(), wktMessageInfo);
    }

    for (CelLiteDescriptor descriptor : descriptors) {
      protoFqnMapBuilder.putAll(descriptor.getProtoTypeNamesToDescriptors());
    }

    this.protoFqnToMessageInfo = protoFqnMapBuilder.buildOrThrow();
  }
}
