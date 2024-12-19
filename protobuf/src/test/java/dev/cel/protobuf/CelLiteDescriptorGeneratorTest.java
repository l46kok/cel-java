package dev.cel.protobuf;
import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.values.CelLiteProtoMessageValueProvider;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.expr.conformance.proto3.TestAllTypes;
import dev.cel.expr.conformance.proto3.TestAllTypesCelLiteDescriptor;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class CelLiteDescriptorGeneratorTest {
  private static final CelCompiler CEL_COMPILER =
      CelCompilerFactory.standardCelCompilerBuilder()
          .addMessageTypes(TestAllTypes.getDescriptor())
          .build();

  private static final CelRuntime CEL_RUNTIME =
      CelRuntimeFactory.standardCelRuntimeBuilder()
          .setOptions(CelOptions.current().enableCelValue(true).build())
          .setValueProvider(CelLiteProtoMessageValueProvider.newInstance(
              TestAllTypesCelLiteDescriptor.getDescriptor()))
          .build();

  @Test
  public void messageCreation() throws Exception {
    CelAbstractSyntaxTree ast = CEL_COMPILER.compile("cel.expr.conformance.proto3.TestAllTypes{}").getAst();

    TestAllTypes simpleTest = (TestAllTypes) CEL_RUNTIME.createProgram(ast).eval();

    assertThat(simpleTest).isEqualTo(TestAllTypes.getDefaultInstance());
  }
}
