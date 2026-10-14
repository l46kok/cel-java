// Copyright 2023 Google LLC
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

package dev.cel.common.values;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.common.types.CelType;
import dev.cel.common.types.StructTypeReference;
import java.util.Optional;

/** ProtoMessageValue is a struct value with protobuf support. */
@AutoValue
@Immutable
public abstract class ProtoMessageValue extends StructValue<String, Message>
    implements OptimizedSelectable {

  @Override
  public abstract Message value();

  abstract CelDescriptorPool celDescriptorPool();

  abstract ProtoCelValueConverter protoCelValueConverter();

  abstract boolean enableJsonFieldNames();

  @Override
  public CelType celType() {
    // Built on demand, since one of these values is created on every hop but rarely asked its type.
    return StructTypeReference.create(value().getDescriptorForType().getFullName());
  }

  @Override
  public boolean isZeroValue() {
    return value().getDefaultInstanceForType().equals(value());
  }

  @Override
  public final String toString() {
    // AutoValue's would print the message's contents, which may be sensitive.
    return "ProtoMessageValue{protoTypeName=" + value().getDescriptorForType().getFullName() + "}";
  }

  @Override
  public Object select(String field) {
    FieldDescriptor fieldDescriptor =
        findField(celDescriptorPool(), value().getDescriptorForType(), field);

    return protoCelValueConverter().fromProtoMessageFieldToCelValue(value(), fieldDescriptor);
  }

  @Override
  public Optional<Object> find(String field) {
    FieldDescriptor fieldDescriptor =
        findField(celDescriptorPool(), value().getDescriptorForType(), field);

    return findFieldValue(fieldDescriptor);
  }

  @Override
  public Object selectByFieldNumber(SelectField field) {
    return selectField(value(), field);
  }

  @Override
  public boolean hasFieldByNumber(SelectField field) {
    FieldDescriptor fieldDescriptor = findFieldByNumber(value().getDescriptorForType(), field);

    return isFieldPresent(fieldDescriptor);
  }

  @Override
  public Optional<Object> findByFieldNumber(SelectField field) {
    FieldDescriptor fieldDescriptor = findFieldByNumber(value().getDescriptorForType(), field);

    return findFieldValue(fieldDescriptor);
  }

  @Override
  public Object selectPath(ImmutableList<SelectField> fields, int from) {
    Message message = value();
    if (message instanceof DynamicMessage) {
      // Each hop adapts a dynamic message into its generated class, if it has one.
      return OptimizedSelectTraversal.qualify(
          selectByFieldNumber(fields.get(from)), fields, from + 1);
    }
    int i = from;
    // Walks nested messages without wrapping them, up to the one holding the last field.
    while (i < fields.size() - 1) {
      FieldDescriptor fieldDescriptor =
          findFieldByNumber(message.getDescriptorForType(), fields.get(i));
      if (!isWalkable(fieldDescriptor)) {
        break;
      }
      message = (Message) message.getField(fieldDescriptor);
      i++;
    }
    return OptimizedSelectTraversal.qualify(selectField(message, fields.get(i)), fields, i + 1);
  }

  // A singular message field that isn't of a well-known type, which the converter would adapt.
  private static boolean isWalkable(FieldDescriptor fieldDescriptor) {
    return fieldDescriptor.getJavaType() == FieldDescriptor.JavaType.MESSAGE
        && !fieldDescriptor.isRepeated()
        && !WellKnownProto.getByTypeName(fieldDescriptor.getMessageType().getFullName())
            .isPresent();
  }

  private Object selectField(Message message, SelectField field) {
    FieldDescriptor fieldDescriptor = findFieldByNumber(message.getDescriptorForType(), field);

    return protoCelValueConverter().fromProtoMessageFieldToCelValue(message, fieldDescriptor);
  }

  public static ProtoMessageValue create(
      Message value,
      CelDescriptorPool celDescriptorPool,
      ProtoCelValueConverter protoCelValueConverter,
      boolean enableJsonFieldNames) {
    checkNotNull(value);
    checkNotNull(celDescriptorPool);
    checkNotNull(protoCelValueConverter);
    return new AutoValue_ProtoMessageValue(
        value, celDescriptorPool, protoCelValueConverter, enableJsonFieldNames);
  }

  private Optional<Object> findFieldValue(FieldDescriptor fieldDescriptor) {
    if (!isFieldPresent(fieldDescriptor)) {
      return Optional.empty();
    }

    return Optional.of(
        protoCelValueConverter().fromProtoMessageFieldToCelValue(value(), fieldDescriptor));
  }

  private boolean isFieldPresent(FieldDescriptor fieldDescriptor) {
    // Selecting a field on a protobuf message yields a default value even if the field is not
    // declared. Therefore, we must exhaustively test whether they are actually declared.
    if (fieldDescriptor.isRepeated()) {
      return value().getRepeatedFieldCount(fieldDescriptor) > 0;
    }
    return value().hasField(fieldDescriptor);
  }

  private static FieldDescriptor findFieldByNumber(Descriptor descriptor, SelectField field) {
    FieldDescriptor fieldDescriptor = descriptor.findFieldByNumber(field.fieldNumber());
    if (fieldDescriptor != null) {
      return fieldDescriptor;
    }

    throw new IllegalArgumentException(
        String.format(
            "field '%s' (number %d) is not declared in message '%s'",
            field.fieldName(), field.fieldNumber(), descriptor.getFullName()));
  }

  private FieldDescriptor findField(
      CelDescriptorPool celDescriptorPool, Descriptor descriptor, String fieldName) {
    if (enableJsonFieldNames()) {
      for (FieldDescriptor fd : descriptor.getFields()) {
        if (fd.getJsonName().equals(fieldName)) {
          return fd;
        }
      }
    }

    FieldDescriptor fieldDescriptor = descriptor.findFieldByName(fieldName);
    if (fieldDescriptor != null) {
      return fieldDescriptor;
    }

    return celDescriptorPool
        .findExtensionDescriptor(descriptor, fieldName)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format(
                        "field '%s' is not declared in message '%s'",
                        fieldName, descriptor.getFullName())));
  }

  ProtoMessageValue() {}
}
