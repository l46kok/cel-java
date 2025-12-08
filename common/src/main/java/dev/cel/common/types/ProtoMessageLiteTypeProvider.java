package dev.cel.common.types;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.FieldLiteDescriptor;
import dev.cel.protobuf.CelLiteDescriptor.MessageLiteDescriptor;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
/**
 * TODO
 */
public final class ProtoMessageLiteTypeProvider implements CelTypeProvider {
  private static final ImmutableMap<FieldLiteDescriptor.Type, CelType> PROTO_TYPE_TO_CEL_TYPE = ImmutableMap.<FieldLiteDescriptor.Type, CelType>builder()
      .put(FieldLiteDescriptor.Type.DOUBLE, SimpleType.DOUBLE)
      .put(FieldLiteDescriptor.Type.FLOAT, SimpleType.DOUBLE)
      .put(FieldLiteDescriptor.Type.INT64, SimpleType.INT)
      .put(FieldLiteDescriptor.Type.INT32, SimpleType.INT)
      .put(FieldLiteDescriptor.Type.SFIXED32, SimpleType.INT)
      .put(FieldLiteDescriptor.Type.SFIXED64, SimpleType.INT)
      .put(FieldLiteDescriptor.Type.SINT32, SimpleType.INT)
      .put(FieldLiteDescriptor.Type.SINT64, SimpleType.INT)
      .put(FieldLiteDescriptor.Type.BOOL, SimpleType.BOOL)
      .put(FieldLiteDescriptor.Type.STRING, SimpleType.STRING)
      .put(FieldLiteDescriptor.Type.BYTES, SimpleType.BYTES)
      .put(FieldLiteDescriptor.Type.FIXED32, SimpleType.UINT)
      .put(FieldLiteDescriptor.Type.FIXED64, SimpleType.UINT)
      .put(FieldLiteDescriptor.Type.UINT32, SimpleType.UINT)
      .put(FieldLiteDescriptor.Type.UINT64, SimpleType.UINT)
      .buildOrThrow()
      ;

  private final ImmutableMap<String, CelType> allTypes;

  @Override
  public ImmutableCollection<CelType> types() {
    return null;
  }

  @Override
  public Optional<CelType> findType(String typeName) {
    return Optional.empty();
  }

  public static ProtoMessageLiteTypeProvider newInstance(Set<CelLiteDescriptor> celLiteDescriptors) {
    return new ProtoMessageLiteTypeProvider(celLiteDescriptors);
  }

  private ProtoMessageLiteTypeProvider(Set<CelLiteDescriptor> celLiteDescriptors) {
    ImmutableMap.Builder<String, CelType> builder = ImmutableMap.builder();
    for (CelLiteDescriptor descriptor : celLiteDescriptors) {
      for (Entry<String, MessageLiteDescriptor> entry : descriptor.getProtoTypeNamesToDescriptors().entrySet()) {
        builder.put(entry.getKey(), createMessageType(entry.getValue()));
      }
    }

    this.allTypes = builder.buildOrThrow();
  }

  private static ProtoMessageType createMessageType(MessageLiteDescriptor messageLiteDescriptor) {
    ImmutableMap<String, FieldLiteDescriptor> fields = messageLiteDescriptor.getFieldDescriptors().stream().collect(toImmutableMap(
        FieldLiteDescriptor::getFieldName, Function.identity()));

    return new ProtoMessageType(
        messageLiteDescriptor.getProtoTypeName(),
        fields.keySet(),
        new FieldResolver(fields),
        extensionFieldName -> {
          throw new UnsupportedOperationException("Proto extensions are not yet supported in MessageLite.");
        }
    );
  }

  private static class FieldResolver implements StructType.FieldResolver {
    private final ImmutableMap<String, FieldLiteDescriptor> fields;

    @Override
    public Optional<CelType> findField(String fieldName) {
      FieldLiteDescriptor fieldDescriptor = fields.get(fieldName);
      if (fieldDescriptor == null) {
        return Optional.empty();
      }

      FieldLiteDescriptor.Type fieldType = fieldDescriptor.getProtoFieldType();
      switch (fieldDescriptor.getProtoFieldType()) {
        default:
          return Optional.of(PROTO_TYPE_TO_CEL_TYPE.get(fieldType));
      }
    }

    private FieldResolver(ImmutableMap<String, FieldLiteDescriptor> fields) {
      this.fields = fields;
    }
  }
}
