package dev.cel.protobuf;


import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.internal.ProtoJavaQualifiedNames;
import dev.cel.common.internal.WellKnownProto;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo;
import dev.cel.protobuf.CelLiteDescriptor.FieldInfo.ValueType;
import dev.cel.protobuf.CelLiteDescriptor.MessageInfo;
import dev.cel.protobuf.JavaFileGenerator.JavaFileGeneratorOption;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;

final class CelLiteDescriptorGenerator implements Callable<Integer> {

  @Option(names = {"--out"}, description = "Outpath for the CelLiteDescriptor")
  private String outPath = "";

  @Option(names = {"--descriptor"}, description = "Path to the descriptor (from proto_library) that the CelLiteDescriptor is to be generated from")
  private String targetDescriptorPath = "";

  @Option(names = {"--transitive_descriptor_set"}, description = "Path to the transitive set of descriptors")
  private String transitiveDescriptorSetPath = "";

  @Option(names = {"--descriptor_class_name"}, description = "Class name for the CelLiteDescriptor")
  private String descriptorClassName = "";

  @Option(names = {"--version"}, description = "CEL-Java version")
  private String version = "";

  @Option(names = {"--debug"}, description = "Prints debug output")
  private boolean debug = false;

  @Override
  public Integer call() throws Exception {
    String targetDescriptorProtoPath = extractProtoPath(targetDescriptorPath);
    print("Target descriptor proto path: " + targetDescriptorProtoPath);

    FileDescriptor targetFileDescriptor = null;
    ImmutableSet<FileDescriptor> transitiveFileDescriptors = CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(load(transitiveDescriptorSetPath));
    for (FileDescriptor fd : transitiveFileDescriptors) {
      if (fd.getFullName().equals(targetDescriptorProtoPath)) {
        print("Transitive Descriptor Path: " + fd.getFullName());
        targetFileDescriptor = fd;
        break;
      }
    }

    if (targetFileDescriptor == null) {
      throw new IllegalArgumentException(String.format("Target descriptor %s not found from transitive set of descriptors!", targetDescriptorProtoPath));
    }

    codegenCelLiteDescriptor(targetFileDescriptor);

    return 0;
  }

  @VisibleForTesting
  ImmutableList<MessageInfo> collectMessageInfo(FileDescriptor targetFileDescriptor) {
    ImmutableList.Builder<MessageInfo> messageInfoListBuilder = ImmutableList.builder();
    CelDescriptors celDescriptors =
        CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(ImmutableList.of(targetFileDescriptor), /* resolveTypeDependencies= */ false);
    ImmutableSet<Descriptor> messageTypes = celDescriptors.messageTypeDescriptors()
        .stream().filter(d -> WellKnownProto.getByTypeName(d.getFullName()) == null)
        .collect(toImmutableSet());

    for (Descriptor descriptor : messageTypes) {
      ImmutableMap.Builder<String, FieldInfo> fieldMap = ImmutableMap.builder();
      for (FieldDescriptor fieldDescriptor : descriptor.getFields()) {
        String methodSuffixName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, fieldDescriptor.getName());

        String javaType = fieldDescriptor.getJavaType().toString();
        String embeddedFieldJavaClassName = "";
        String embeddedFieldProtoTypeName = "";
        switch (javaType) {
          case "ENUM":
            embeddedFieldJavaClassName = ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(fieldDescriptor.getEnumType());
            embeddedFieldProtoTypeName = fieldDescriptor.getEnumType().getFullName();
            break;
          case "MESSAGE":
            embeddedFieldJavaClassName = ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(fieldDescriptor.getMessageType());
            embeddedFieldProtoTypeName = fieldDescriptor.getMessageType().getFullName();
            break;
          default:
            break;
        }

        ValueType fieldValueType;
        if (fieldDescriptor.isMapField()) {
          fieldValueType = ValueType.MAP;
        } else if (fieldDescriptor.isRepeated()) {
          fieldValueType = ValueType.LIST;
        } else {
          fieldValueType = ValueType.SCALAR;
        }

        fieldMap.put(fieldDescriptor.getName(),
            new FieldInfo(
                fieldDescriptor.getFullName(),
                javaType,
                methodSuffixName,
                embeddedFieldJavaClassName,
                fieldValueType.toString(),
                fieldDescriptor.getType().toString(),
                String.valueOf(fieldDescriptor.hasPresence()),
                embeddedFieldProtoTypeName
              )
        );

        print(String.format("Method suffix name in %s, for field %s: %s", descriptor.getFullName(), fieldDescriptor.getFullName(), methodSuffixName));
        print(String.format("FieldType: %s", fieldValueType));
        if (!embeddedFieldJavaClassName.isEmpty()) {
          print(String.format("Java class name for field %s: %s", fieldDescriptor.getName(), embeddedFieldJavaClassName));
        }
      }

      messageInfoListBuilder.add(
          new MessageInfo(
              descriptor.getFullName(),
              ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(descriptor),
              fieldMap.build()
          ));
    }

    return messageInfoListBuilder.build();
  }

  private void codegenCelLiteDescriptor(FileDescriptor targetFileDescriptor)
      throws Exception {
    String javaPackageName = ProtoJavaQualifiedNames.getJavaPackageName(targetFileDescriptor);

    JavaFileGenerator.createFile(outPath,
        JavaFileGeneratorOption.newBuilder()
            .setVersion(version)
            .setDescriptorClassName(descriptorClassName)
            .setPackageName(javaPackageName)
            .setMessageInfoList(collectMessageInfo(targetFileDescriptor))
            .build());
  }

  private String extractProtoPath(String descriptorPath) {
    FileDescriptorSet fds = load(descriptorPath);
    FileDescriptorProto fileDescriptorProto = Iterables.getOnlyElement(fds.getFileList());
    return fileDescriptorProto.getName();
  }

  private FileDescriptorSet load(String descriptorSetPath) {
    try {
      byte[] descriptorBytes = Files.toByteArray(new File(descriptorSetPath));
      // TODO Extensions?
      return FileDescriptorSet.parseFrom(descriptorBytes, ExtensionRegistry.getEmptyRegistry());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load FileDescriptorSet from path: " + descriptorSetPath);
    }
  }

  private void printAllFlags(CommandLine cmd) {
    print("Flag values:");
    print("-------------------------------------------------------------");
    for (OptionSpec option : cmd.getCommandSpec().options()) {
      print(option.longestName() + ": " + option.getValue().toString());
    }
    print("-------------------------------------------------------------");
  }

  private void print(String message) {
    if (debug) {
      System.out.println(Ansi.ON.string("@|cyan [CelLiteDescriptorGenerator] |@" + message));
    }
  }


  public static void main(String[] args) throws Exception {
    CelLiteDescriptorGenerator celLiteDescriptorGenerator = new CelLiteDescriptorGenerator();
    CommandLine cmd = new CommandLine(celLiteDescriptorGenerator);
    cmd.parseArgs(args);
    celLiteDescriptorGenerator.printAllFlags(cmd);

    int exitCode = cmd.execute(args);
    System.exit(exitCode);
  }


  CelLiteDescriptorGenerator() {}
}