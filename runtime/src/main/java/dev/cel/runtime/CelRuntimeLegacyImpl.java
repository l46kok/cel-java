// Copyright 2022 Google LLC
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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import javax.annotation.concurrent.ThreadSafe;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelDescriptorUtil;
import dev.cel.common.CelDescriptors;
import dev.cel.common.CelOptions;
import dev.cel.common.annotations.Internal;
import dev.cel.common.internal.CelDescriptorPool;
import dev.cel.common.internal.CombinedDescriptorPool;
import dev.cel.common.internal.DefaultDescriptorPool;
import dev.cel.common.internal.DefaultLiteDescriptorPool;
import dev.cel.common.internal.DefaultMessageFactory;
import dev.cel.common.internal.DynamicProto;
// CEL-Internal-3
import dev.cel.common.internal.ProtoLiteAdapter;
import dev.cel.common.internal.ProtoMessageFactory;
import dev.cel.common.types.CelTypes;
import dev.cel.common.values.CelValueProvider;
import dev.cel.common.values.CelValueProvider.CombinedCelValueProvider;
import dev.cel.common.values.ProtoCelValueConverter;
import dev.cel.common.values.ProtoLiteCelValueConverter;
import dev.cel.common.values.ProtoMessageLiteValueProvider;
import dev.cel.common.values.ProtoMessageValueProvider;
import dev.cel.protobuf.CelLiteDescriptor;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Arithmetic;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Comparison;
import dev.cel.runtime.CelStandardFunctions.StandardFunction.Overload.Conversions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * {@code CelRuntime} implementation based on the legacy CEL-Java stack.
 *
 * <p>CEL Library Internals. Do Not Use. Consumers should use factories, such as {@link
 * CelRuntimeFactory} instead to instantiate a runtime.
 */
@ThreadSafe
@Internal
public final class CelRuntimeLegacyImpl implements CelRuntime {

  private final Interpreter interpreter;
  private final CelOptions options;

  private final boolean standardEnvironmentEnabled;

  // Extension registry is thread-safe. Just not marked as such from Protobuf's implementation.
  // CEL-Internal-4
  private final ExtensionRegistry extensionRegistry;

  // A user-provided custom type factory should presumably be thread-safe. This is documented, but
  // not enforced.
  // CEL-Internal-4
  private final Function<String, Message.Builder> customTypeFactory;

  private final CelStandardFunctions overriddenStandardFunctions;
  private final CelValueProvider celValueProvider;
  private final ImmutableSet<FileDescriptor> fileDescriptors;

  // This does not affect the evaluation behavior in any manner.
  // CEL-Internal-4
  private final ImmutableSet<CelRuntimeLibrary> celRuntimeLibraries;

  private final ImmutableList<CelFunctionBinding> celFunctionBindings;

  @Override
  public CelRuntime.Program createProgram(CelAbstractSyntaxTree ast) {
    checkState(ast.isChecked(), "programs must be created from checked expressions");
    return CelRuntime.Program.from(interpreter.createInterpretable(ast), options);
  }

  @Override
  public CelRuntimeBuilder toRuntimeBuilder() {
    CelRuntimeBuilder builder =
        new Builder()
            .setOptions(options)
            .setStandardEnvironmentEnabled(standardEnvironmentEnabled)
            .setExtensionRegistry(extensionRegistry)
            .addFileTypes(fileDescriptors)
            .addLibraries(celRuntimeLibraries)
            .addFunctionBindings(celFunctionBindings);

    if (customTypeFactory != null) {
      builder.setTypeFactory(customTypeFactory);
    }

    if (overriddenStandardFunctions != null) {
      builder.setStandardFunctions(overriddenStandardFunctions);
    }

    if (celValueProvider != null) {
      builder.setValueProvider(celValueProvider);
    }

    return builder;
  }

  /** Create a new builder for constructing a {@code CelRuntime} instance. */
  public static CelRuntimeBuilder newBuilder() {
    return new Builder();
  }

  /** Builder class for {@code CelRuntimeLegacyImpl}. */
  public static final class Builder implements CelRuntimeBuilder {

    // The following properties are for testing purposes only. Do not expose to public.
    @VisibleForTesting final ImmutableSet.Builder<FileDescriptor> fileTypes;

    @VisibleForTesting final HashMap<String, CelFunctionBinding> customFunctionBindings;
    private final ImmutableSet.Builder<CelLiteDescriptor> celLiteDescriptorBuilder;

    @VisibleForTesting final ImmutableSet.Builder<CelRuntimeLibrary> celRuntimeLibraries;

    @VisibleForTesting Function<String, Message.Builder> customTypeFactory;
    @VisibleForTesting CelValueProvider celValueProvider;
    @VisibleForTesting CelStandardFunctions overriddenStandardFunctions;

    private CelOptions options;

    private ExtensionRegistry extensionRegistry;

    private boolean standardEnvironmentEnabled;

    @Override
    public CelRuntimeBuilder setOptions(CelOptions options) {
      this.options = options;
      return this;
    }

    @Override
    public CelRuntimeBuilder addFunctionBindings(CelFunctionBinding... bindings) {
      return addFunctionBindings(Arrays.asList(bindings));
    }

    @Override
    public CelRuntimeBuilder addFunctionBindings(Iterable<CelFunctionBinding> bindings) {
      bindings.forEach(o -> customFunctionBindings.putIfAbsent(o.getOverloadId(), o));
      return this;
    }

    @Override
    public CelRuntimeBuilder addMessageTypes(Descriptor... descriptors) {
      return addMessageTypes(Arrays.asList(descriptors));
    }

    @Override
    public CelRuntimeBuilder addMessageTypes(Iterable<Descriptor> descriptors) {
      return addFileTypes(CelDescriptorUtil.getFileDescriptorsForDescriptors(descriptors));
    }

    @Override
    public CelRuntimeBuilder addCelLiteDescriptors(CelLiteDescriptor... descriptors) {
      return addCelLiteDescriptors(Arrays.asList(descriptors));
    }

    @Override
    public CelRuntimeBuilder addCelLiteDescriptors(Iterable<CelLiteDescriptor> descriptors) {
      this.celLiteDescriptorBuilder.addAll(descriptors);
      return this;
    }

    @Override
    public CelRuntimeBuilder addFileTypes(FileDescriptor... fileDescriptors) {
      return addFileTypes(Arrays.asList(fileDescriptors));
    }

    @Override
    public CelRuntimeBuilder addFileTypes(Iterable<FileDescriptor> fileDescriptors) {
      this.fileTypes.addAll(checkNotNull(fileDescriptors));
      return this;
    }

    @Override
    public CelRuntimeBuilder addFileTypes(FileDescriptorSet fileDescriptorSet) {
      return addFileTypes(
          CelDescriptorUtil.getFileDescriptorsFromFileDescriptorSet(fileDescriptorSet));
    }

    @Override
    public CelRuntimeBuilder setTypeFactory(Function<String, Message.Builder> typeFactory) {
      this.customTypeFactory = typeFactory;
      return this;
    }

    @Override
    public CelRuntimeBuilder setValueProvider(CelValueProvider celValueProvider) {
      this.celValueProvider = celValueProvider;
      return this;
    }

    @Override
    public CelRuntimeBuilder setStandardEnvironmentEnabled(boolean value) {
      standardEnvironmentEnabled = value;
      return this;
    }

    @Override
    public CelRuntimeBuilder setStandardFunctions(CelStandardFunctions standardFunctions) {
      this.overriddenStandardFunctions = standardFunctions;
      return this;
    }

    @Override
    public CelRuntimeBuilder addLibraries(CelRuntimeLibrary... libraries) {
      checkNotNull(libraries);
      return this.addLibraries(Arrays.asList(libraries));
    }

    @Override
    public CelRuntimeBuilder addLibraries(Iterable<? extends CelRuntimeLibrary> libraries) {
      checkNotNull(libraries);
      this.celRuntimeLibraries.addAll(libraries);
      return this;
    }

    @Override
    public CelRuntimeBuilder setExtensionRegistry(ExtensionRegistry extensionRegistry) {
      checkNotNull(extensionRegistry);
      this.extensionRegistry = extensionRegistry.getUnmodifiable();
      return this;
    }

    /** Build a new {@code CelRuntimeLegacyImpl} instance from the builder config. */
    @Override
    public CelRuntimeLegacyImpl build() {
      if (standardEnvironmentEnabled && overriddenStandardFunctions != null) {
        throw new IllegalArgumentException(
            "setStandardEnvironmentEnabled must be set to false to override standard function"
                + " bindings.");
      }

      ImmutableSet<CelRuntimeLibrary> runtimeLibraries = celRuntimeLibraries.build();
      // Add libraries, such as extensions
      runtimeLibraries.forEach(celLibrary -> celLibrary.setRuntimeOptions(this));

      ImmutableSet<FileDescriptor> fileDescriptors = fileTypes.build();
      CelDescriptors celDescriptors =
          CelDescriptorUtil.getAllDescriptorsFromFileDescriptor(
              fileDescriptors, options.resolveTypeDependencies());

      CelDescriptorPool celDescriptorPool =
          newDescriptorPool(
              celDescriptors,
              extensionRegistry);

      @SuppressWarnings("Immutable")
      ProtoMessageFactory runtimeTypeFactory =
          customTypeFactory != null
              ? messageName ->
                  CelTypes.isWellKnownType(
                          messageName) // Let DefaultMessageFactory handle WKT constructions
                      ? Optional.empty()
                      : Optional.ofNullable(customTypeFactory.apply(messageName))
              : null;
      runtimeTypeFactory =
          maybeCombineMessageFactory(
              runtimeTypeFactory, DefaultMessageFactory.create(celDescriptorPool));

      DynamicProto dynamicProto = DynamicProto.create(runtimeTypeFactory);
      RuntimeEquality runtimeEquality = ProtoMessageRuntimeEquality.create(dynamicProto, options);

      ImmutableMap.Builder<String, CelFunctionBinding> functionBindingsBuilder =
          ImmutableMap.builder();
      for (CelFunctionBinding standardFunctionBinding :
          newStandardFunctionBindings(runtimeEquality)) {
        functionBindingsBuilder.put(
            standardFunctionBinding.getOverloadId(), standardFunctionBinding);
      }

      functionBindingsBuilder.putAll(customFunctionBindings);

      DefaultDispatcher dispatcher = DefaultDispatcher.create();
      functionBindingsBuilder
          .buildOrThrow()
          .forEach(
              (String overloadId, CelFunctionBinding func) ->
                  dispatcher.add(
                      overloadId, func.getArgTypes(), (args) -> func.getDefinition().apply(args)));

      RuntimeTypeProvider runtimeTypeProvider;

      if (options.enableCelValue()) {
        ImmutableSet<CelLiteDescriptor> liteDescriptors = celLiteDescriptorBuilder.build();
        if (liteDescriptors.isEmpty()) {
          CelValueProvider messageValueProvider =
              ProtoMessageValueProvider.newInstance(dynamicProto);
          if (celValueProvider != null) {
            messageValueProvider =
                CombinedCelValueProvider.newInstance(celValueProvider, messageValueProvider);
          }

          ProtoCelValueConverter protoCelValueConverter =
              ProtoCelValueConverter.newInstance(celDescriptorPool, dynamicProto);

          runtimeTypeProvider =
              new RuntimeTypeProviderLegacyImpl(messageValueProvider, protoCelValueConverter);
        } else {
          DefaultLiteDescriptorPool celLiteDescriptorPool =
              DefaultLiteDescriptorPool.newInstance(liteDescriptors);

          // TODO: instantiate these dependencies within ProtoMessageLiteValueProvider.
          // For now, they need to be outside to instantiate the RuntimeTypeProviderLegacyImpl
          // adapter.
          ProtoLiteAdapter protoLiteAdapter = new ProtoLiteAdapter(options.enableUnsignedLongs());
          ProtoLiteCelValueConverter protoLiteCelValueConverter =
              ProtoLiteCelValueConverter.newInstance(celLiteDescriptorPool);
          CelValueProvider messageValueProvider =
              ProtoMessageLiteValueProvider.newInstance(
                  protoLiteCelValueConverter, protoLiteAdapter, celLiteDescriptorPool);
          if (celValueProvider != null) {
            messageValueProvider =
                CombinedCelValueProvider.newInstance(celValueProvider, messageValueProvider);
          }

          runtimeTypeProvider =
              new RuntimeTypeProviderLegacyImpl(messageValueProvider, protoLiteCelValueConverter);
        }

      } else {
        runtimeTypeProvider = new DescriptorMessageProvider(runtimeTypeFactory, options);
      }

      DefaultInterpreter interpreter =
          new DefaultInterpreter(
              DescriptorTypeResolver.create(),
              runtimeTypeProvider,
              dispatcher.immutableCopy(),
              options);

      return new CelRuntimeLegacyImpl(
          interpreter,
          options,
          standardEnvironmentEnabled,
          extensionRegistry,
          customTypeFactory,
          overriddenStandardFunctions,
          celValueProvider,
          fileDescriptors,
          runtimeLibraries,
          ImmutableList.copyOf(customFunctionBindings.values()));
    }

    private ImmutableSet<CelFunctionBinding> newStandardFunctionBindings(
        RuntimeEquality runtimeEquality) {
      CelStandardFunctions celStandardFunctions;
      if (standardEnvironmentEnabled) {
        celStandardFunctions =
            CelStandardFunctions.newBuilder()
                .filterFunctions(
                    (standardFunction, standardOverload) -> {
                      switch (standardFunction) {
                        case INT:
                          if (standardOverload.equals(Conversions.INT64_TO_INT64)) {
                            // Note that we require UnsignedLong flag here to avoid ambiguous
                            // overloads against "uint64_to_int64", because they both use the same
                            // Java Long class. We skip adding this identity function if the flag is
                            // disabled.
                            return options.enableUnsignedLongs();
                          }
                          break;
                        case TIMESTAMP:
                          // TODO: Remove this flag guard once the feature has been
                          // auto-enabled.
                          if (standardOverload.equals(Conversions.INT64_TO_TIMESTAMP)) {
                            return options.enableTimestampEpoch();
                          }
                          break;
                        case STRING:
                          return options.enableStringConversion();
                        case ADD:
                          Arithmetic arithmetic = (Arithmetic) standardOverload;
                          if (arithmetic.equals(Arithmetic.ADD_STRING)) {
                            return options.enableStringConcatenation();
                          }
                          if (arithmetic.equals(Arithmetic.ADD_LIST)) {
                            return options.enableListConcatenation();
                          }
                          break;
                        default:
                          if (standardOverload instanceof Comparison
                              && !options.enableHeterogeneousNumericComparisons()) {
                            Comparison comparison = (Comparison) standardOverload;
                            return !comparison.isHeterogeneousComparison();
                          }
                          break;
                      }

                      return true;
                    })
                .build();
      } else if (overriddenStandardFunctions != null) {
        celStandardFunctions = overriddenStandardFunctions;
      } else {
        return ImmutableSet.of();
      }

      return celStandardFunctions.newFunctionBindings(runtimeEquality, options);
    }

    private static CelDescriptorPool newDescriptorPool(
        CelDescriptors celDescriptors,
        ExtensionRegistry extensionRegistry) {
      ImmutableList.Builder<CelDescriptorPool> descriptorPools = new ImmutableList.Builder<>();

      descriptorPools.add(DefaultDescriptorPool.create(celDescriptors, extensionRegistry));

      return CombinedDescriptorPool.create(descriptorPools.build());
    }

    @CanIgnoreReturnValue
    private static ProtoMessageFactory maybeCombineMessageFactory(
        @Nullable ProtoMessageFactory parentFactory, ProtoMessageFactory childFactory) {
      if (parentFactory == null) {
        return childFactory;
      }
      return new ProtoMessageFactory.CombinedMessageFactory(
          ImmutableList.of(parentFactory, childFactory));
    }

    private Builder() {
      this.options = CelOptions.newBuilder().build();
      this.fileTypes = ImmutableSet.builder();
      this.customFunctionBindings = new HashMap<>();
      this.celRuntimeLibraries = ImmutableSet.builder();
      this.celLiteDescriptorBuilder = ImmutableSet.builder();
      this.extensionRegistry = ExtensionRegistry.getEmptyRegistry();
      this.customTypeFactory = null;
    }
  }

  private CelRuntimeLegacyImpl(
      Interpreter interpreter,
      CelOptions options,
      boolean standardEnvironmentEnabled,
      ExtensionRegistry extensionRegistry,
      @Nullable Function<String, Message.Builder> customTypeFactory,
      @Nullable CelStandardFunctions overriddenStandardFunctions,
      @Nullable CelValueProvider celValueProvider,
      ImmutableSet<FileDescriptor> fileDescriptors,
      ImmutableSet<CelRuntimeLibrary> celRuntimeLibraries,
      ImmutableList<CelFunctionBinding> celFunctionBindings) {
    this.interpreter = interpreter;
    this.options = options;
    this.standardEnvironmentEnabled = standardEnvironmentEnabled;
    this.extensionRegistry = extensionRegistry;
    this.customTypeFactory = customTypeFactory;
    this.overriddenStandardFunctions = overriddenStandardFunctions;
    this.celValueProvider = celValueProvider;
    this.fileDescriptors = fileDescriptors;
    this.celRuntimeLibraries = celRuntimeLibraries;
    this.celFunctionBindings = celFunctionBindings;
  }
}
