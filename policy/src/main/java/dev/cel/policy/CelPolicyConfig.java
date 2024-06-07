package dev.cel.policy;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelVarDecl;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypeProvider;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import dev.cel.extensions.CelExtensions;
import dev.cel.extensions.CelOptionalLibrary;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

@AutoValue
public abstract class CelPolicyConfig {

  abstract String name();

  abstract String description();

  abstract String container();

  abstract ImmutableSet<ExtensionConfig> extensions();

  abstract ImmutableSet<VariableDecl> variables();

  abstract ImmutableSet<FunctionDecl> functions();

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(String name);

    public abstract Builder setDescription(String description);

    public abstract Builder setContainer(String container);

    public abstract Builder setExtensions(ImmutableSet<ExtensionConfig> extensions);

    public abstract Builder setVariables(ImmutableSet<VariableDecl> variables);

    public abstract Builder setFunctions(ImmutableSet<FunctionDecl> functions);

    @CheckReturnValue
    public abstract CelPolicyConfig build();
  }

  public static Builder newBuilder() {
    return new AutoValue_CelPolicyConfig.Builder()
        .setName("")
        .setDescription("")
        .setContainer("")
        .setExtensions(ImmutableSet.of())
        .setVariables(ImmutableSet.of())
        .setFunctions(ImmutableSet.of());
  }

  /**
   * TODO
   */
  public Cel extend(Cel cel, CelOptions celOptions) throws CelPolicyValidationException {
    try {
      CelTypeProvider celTypeProvider = cel.getTypeProvider();
      CelBuilder celBuilder = cel.toCelBuilder()
          .setTypeProvider(celTypeProvider)
          .setContainer(container())
          .addVarDeclarations(
              variables().stream().map(v -> v.toCelVarDecl(celTypeProvider))
                  .collect(toImmutableList())
          )
          .addFunctionDeclarations(
              functions().stream().map(f -> f.toCelFunctionDecl(celTypeProvider))
                  .collect(toImmutableList())
          );

      addAllExtensions(celBuilder, celOptions);

      return celBuilder.build();
    } catch (Exception e) {
      throw new CelPolicyValidationException(e.getMessage(), e);
    }
  }

  private void addAllExtensions(CelBuilder celBuilder, CelOptions celOptions) {
    for (ExtensionConfig extensionConfig : extensions()) {
      switch (extensionConfig.name()) {
        case "bindings":
          celBuilder.addCompilerLibraries(CelExtensions.bindings());
          break;
        case "encoders":
          celBuilder.addCompilerLibraries(CelExtensions.encoders());
          celBuilder.addRuntimeLibraries(CelExtensions.encoders());
          break;
        case "math":
          celBuilder.addCompilerLibraries(CelExtensions.math(celOptions));
          celBuilder.addRuntimeLibraries(CelExtensions.math(celOptions));
          break;
        case "optional":
          celBuilder.addCompilerLibraries(CelOptionalLibrary.INSTANCE);
          celBuilder.addRuntimeLibraries(CelOptionalLibrary.INSTANCE);
          break;
        case "protos":
          celBuilder.addCompilerLibraries(CelExtensions.protos());
          break;
        case "strings":
          celBuilder.addCompilerLibraries(CelExtensions.strings());
          celBuilder.addRuntimeLibraries(CelExtensions.strings());
          break;
        default:
          throw new IllegalArgumentException(
              "Unrecognized extension: " + extensionConfig.name());
      }
    }
  }

  @AutoValue
  public static abstract class VariableDecl {

    /**
     * Fully qualified variable name.
     */
    public abstract String name();

    /**
     * The type of the variable.
     */
    public abstract TypeDecl type();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Optional<String> name();

      abstract Optional<TypeDecl> type();

      abstract Builder setName(String name);

      abstract Builder setType(TypeDecl typeDecl);

      ImmutableList<String> getMissingRequiredFieldNames() {
        return getMissingRequiredFields(
                        new AbstractMap.SimpleEntry<>("name", name()),
                        new AbstractMap.SimpleEntry<>("type", type())
                );
      }

      abstract VariableDecl build();
    }

    static Builder newBuilder() {
      return new AutoValue_CelPolicyConfig_VariableDecl.Builder();
    }

    public static VariableDecl create(String name, TypeDecl type) {
      return newBuilder().setName(name).setType(type).build();
    }

    public CelVarDecl toCelVarDecl(CelTypeProvider celTypeProvider) {
      return CelVarDecl.newVarDeclaration(name(), type().toCelType(celTypeProvider));
    }
  }

  @AutoValue
  public static abstract class FunctionDecl {

    public abstract String name();

    public abstract ImmutableSet<OverloadDecl> overloads();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Optional<String> name();

      abstract Optional<ImmutableSet<OverloadDecl>> overloads();

      abstract Builder setName(String name);

      abstract Builder setOverloads(ImmutableSet<OverloadDecl> overloads);

      ImmutableList<String> getMissingRequiredFieldNames() {
        return getMissingRequiredFields(
                new AbstractMap.SimpleEntry<>("name", name()),
                new AbstractMap.SimpleEntry<>("overloads", overloads())
        );
      }

      abstract FunctionDecl build();
    }

    static Builder newBuilder() {
      return new AutoValue_CelPolicyConfig_FunctionDecl.Builder();
    }

    public static FunctionDecl create(String name, ImmutableSet<OverloadDecl> overloads) {
      return newBuilder().setName(name).setOverloads(overloads).build();
    }

    public CelFunctionDecl toCelFunctionDecl(CelTypeProvider celTypeProvider) {
      return CelFunctionDecl.newFunctionDeclaration(
          name(),
          overloads().stream().map(o -> o.toCelOverloadDecl(celTypeProvider))
              .collect(toImmutableList())
      );
    }
  }

  @AutoValue
  public static abstract class OverloadDecl {

    public abstract String id();

    public abstract Optional<TypeDecl> target();

    public abstract ImmutableList<TypeDecl> arguments();

    public abstract TypeDecl returnType();

    @AutoValue.Builder
    public abstract static class Builder {

      abstract Optional<String> id();
      abstract Optional<TypeDecl> returnType();

      public abstract Builder setId(String overloadId);

      public abstract Builder setTarget(TypeDecl target);

      abstract ImmutableList.Builder<TypeDecl> argumentsBuilder();

      abstract Builder setArguments(ImmutableList<TypeDecl> args);

      @CanIgnoreReturnValue
      public Builder addArguments(Iterable<TypeDecl> args) {
        this.argumentsBuilder().addAll(checkNotNull(args));
        return this;
      }

      @CanIgnoreReturnValue
      public Builder addArguments(TypeDecl... args) {
        return addArguments(Arrays.asList(args));
      }

      public abstract Builder setReturnType(TypeDecl returnType);

      ImmutableList<String> getMissingRequiredFieldNames() {
        return getMissingRequiredFields(
                new AbstractMap.SimpleEntry<>("id", id()),
                new AbstractMap.SimpleEntry<>("return", returnType())
        );
      }

      @CheckReturnValue
      public abstract OverloadDecl build();
    }

    public static Builder newBuilder() {
      return new AutoValue_CelPolicyConfig_OverloadDecl.Builder().setArguments(ImmutableList.of());
    }

    public CelOverloadDecl toCelOverloadDecl(CelTypeProvider celTypeProvider) {
      CelOverloadDecl.Builder builder = CelOverloadDecl.newBuilder()
          .setIsInstanceFunction(false)
          .setOverloadId(id())
          .setResultType(returnType().toCelType(celTypeProvider));

      target().ifPresent(t -> {
        builder.setIsInstanceFunction(true);
        builder.addParameterTypes(t.toCelType(celTypeProvider));
      });

      for (TypeDecl type : arguments()) {
        builder.addParameterTypes(type.toCelType(celTypeProvider));
      }

      return builder.build();
    }
  }

  @AutoValue
  public static abstract class TypeDecl {

    public abstract String name();

    public abstract ImmutableList<TypeDecl> params();

    public abstract Boolean isTypeParam();

    @AutoValue.Builder
    public abstract static class Builder {

      abstract String name();

      public abstract Builder setName(String name);

      abstract Builder setParams(ImmutableList<TypeDecl> typeDecls);

      abstract ImmutableList.Builder<TypeDecl> paramsBuilder();

      @CanIgnoreReturnValue
      public Builder addParams(TypeDecl... params) {
        return addParams(Arrays.asList(params));
      }

      @CanIgnoreReturnValue
      public Builder addParams(Iterable<TypeDecl> params) {
        this.paramsBuilder().addAll(checkNotNull(params));
        return this;
      }

      public abstract Builder setIsTypeParam(boolean isTypeParam);

      @CheckReturnValue
      public abstract TypeDecl build();
    }

    public static TypeDecl create(String name) {
      return newBuilder().setName(name).build();
    }

    public static Builder newBuilder() {
      return new AutoValue_CelPolicyConfig_TypeDecl.Builder().setIsTypeParam(false);
    }

    public CelType toCelType(CelTypeProvider celTypeProvider) {
      switch (name()) {
        case "list":
          if (params().size() != 1) {
            throw new IllegalArgumentException(
                "List type has unexpected param count: " + params().size());
          }

          CelType elementType = params().get(0).toCelType(celTypeProvider);
          return ListType.create(elementType);
        case "map":
          if (params().size() != 2) {
            throw new IllegalArgumentException(
                "Map type has unexpected param count: " + params().size());
          }

          CelType keyType = params().get(0).toCelType(celTypeProvider);
          CelType valueType = params().get(1).toCelType(celTypeProvider);
          return MapType.create(keyType, valueType);
        default:
          if (isTypeParam()) {
            return TypeParamType.create(name());
          }

          CelType simpleType = SimpleType.findByName(name()).orElse(null);
          if (simpleType != null) {
            return simpleType;
          }

          if (OptionalType.NAME.equals(name())) {
            checkState(params().size() == 1,
                "Optional type must have exactly 1 parameter. Found " + params().size());
            return OptionalType.create(params().get(0).toCelType(celTypeProvider));
          }

          return celTypeProvider.findType(name())
              .orElseThrow(() -> new IllegalArgumentException("Undefined type name: " + name()));
      }
    }
  }

  @AutoValue
  public static abstract class ExtensionConfig {

    /**
     * Name of the extension (ex: bindings, optional, math, etc).".
     */
    abstract String name();

    /**
     * Version of the extension. Presently, this field is ignored as CEL-Java extensions are not
     * versioned.
     */
    abstract Integer version();

    @AutoValue.Builder
    abstract static class Builder {

      abstract Optional<String> name();

      abstract Optional<Integer> version();

      abstract Builder setName(String name);

      abstract Builder setVersion(Integer version);

      ImmutableList<String> getMissingRequiredFieldNames() {
        return getMissingRequiredFields(
                new AbstractMap.SimpleEntry<>("name", name())
        );
      }

      abstract ExtensionConfig build();
    }

    static Builder newBuilder() {
      return new AutoValue_CelPolicyConfig_ExtensionConfig.Builder().setVersion(0);
    }

    public static ExtensionConfig of(String name) {
      return of(name, 0);
    }

    public static ExtensionConfig of(String name, int version) {
      return newBuilder().setName(name).setVersion(version).build();
    }
  }

  @SafeVarargs
  private static ImmutableList<String> getMissingRequiredFields(AbstractMap.SimpleEntry<String, Optional<?>>... requiredFields) {
    return Stream.of(requiredFields)
            .filter(entry -> !entry.getValue().isPresent())
            .map(AbstractMap.SimpleEntry::getKey)
            .collect(toImmutableList());
  }
}