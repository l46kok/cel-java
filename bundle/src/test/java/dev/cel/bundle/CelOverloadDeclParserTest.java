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

package dev.cel.bundle;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.bundle.CelEnvironment.TypeDecl;
import dev.cel.bundle.CelOverloadDeclParser.ParsedOverloadDecl;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class CelOverloadDeclParserTest {

  @SuppressWarnings("ImmutableEnumChecker") // Test only
  private enum ValidSignatureTestCase {
    GLOBAL_NO_ARG("now()", ParsedOverloadDecl.create("now", Optional.empty(), ImmutableList.of())),
    GLOBAL_MULTI_ARG_WITH_WHITESPACE(
        " add ( int , list < string > ) ",
        ParsedOverloadDecl.create(
            "add",
            Optional.empty(),
            ImmutableList.of(
                TypeDecl.create("int"),
                TypeDecl.newBuilder()
                    .setName("list")
                    .addParams(TypeDecl.create("string"))
                    .build()))),
    MEMBER_WITH_PARAMETERIZED_RECEIVER(
        "map<~K,~V>.get(~K)",
        ParsedOverloadDecl.create(
            "get",
            Optional.of(
                TypeDecl.newBuilder()
                    .setName("map")
                    .addParams(TypeDecl.ofTypeParam("K"), TypeDecl.ofTypeParam("V"))
                    .build()),
            ImmutableList.of(TypeDecl.ofTypeParam("K")))),
    MEMBER_WITH_SIMPLE_RECEIVER(
        "int.add(int)",
        ParsedOverloadDecl.create(
            "add", Optional.of(TypeDecl.create("int")), ImmutableList.of(TypeDecl.create("int")))),
    MEMBER_WITH_WRAPPER_RECEIVER(
        "int_wrapper.coalesce(int)",
        ParsedOverloadDecl.create(
            "coalesce",
            Optional.of(TypeDecl.create("int_wrapper")),
            ImmutableList.of(TypeDecl.create("int")))),
    MEMBER_WITH_QUALIFIED_RECEIVER(
        "google.protobuf.StringValue.isEmpty()",
        ParsedOverloadDecl.create(
            "isEmpty",
            Optional.of(TypeDecl.create("google.protobuf.StringValue")),
            ImmutableList.of())),
    NAMESPACED_GLOBAL_WITH_ESCAPED_DOT(
        "math\\.greatest(int,int)",
        ParsedOverloadDecl.create(
            "math.greatest",
            Optional.empty(),
            ImmutableList.of(TypeDecl.create("int"), TypeDecl.create("int")))),
    OPERATOR(
        "_==_(int, int)",
        ParsedOverloadDecl.create(
            "_==_",
            Optional.empty(),
            ImmutableList.of(TypeDecl.create("int"), TypeDecl.create("int")))),
    ESCAPED_SPECIAL_CHARS_IN_FUNCTION_NAME(
        "\\~foo\\(bar\\)\\<baz\\>\\\"qux\\,\\.\\ (int)",
        ParsedOverloadDecl.create(
            "~foo(bar)<baz>\"qux,. ",
            Optional.empty(),
            ImmutableList.of(TypeDecl.create("int")))),
    ESCAPED_SPACE_WITH_TRAILING_WHITESPACE(
        "\\  (int)",
        ParsedOverloadDecl.create(
            " ",
            Optional.empty(),
            ImmutableList.of(TypeDecl.create("int")))),
    ;

    private final String signature;
    private final ParsedOverloadDecl expected;

    ValidSignatureTestCase(String signature, ParsedOverloadDecl expected) {
      this.signature = signature;
      this.expected = expected;
    }
  }

  @Test
  public void parse_validSignature_success(@TestParameter ValidSignatureTestCase testCase) {
    assertThat(CelOverloadDeclParser.parse(testCase.signature)).isEqualTo(testCase.expected);
  }

  private enum InvalidSignatureTestCase {
    EMPTY("", "Empty function signature"),
    MISSING_OPEN_PAREN("foo", "Invalid function signature, missing '(': foo"),
    MISSING_CLOSE_PAREN("foo(int", "Invalid function signature, missing closing ')': foo(int"),
    TRAILING_AFTER_CLOSE_PAREN(
        "foo(int)extra", "Invalid function signature, missing closing ')': foo(int)extra"),
    EMPTY_FUNCTION_NAME("(int)", "Invalid function signature: empty function name"),
    EMPTY_MEMBER_FUNCTION_NAME("string.(int)", "Invalid function signature: empty function name"),
    MISMATCHED_BRACKETS_RECEIVER(
        "list<int.foo()", "Invalid function signature: mismatched brackets"),
    MISMATCHED_CLOSING_BRACKET_RECEIVER(
        "list>int<.foo()", "Invalid function signature: mismatched brackets"),
    UNESCAPED_TILDE_IN_FUNCTION_NAME("~foo(int)", "Invalid function name: ~foo"),
    UNESCAPED_QUOTE_IN_FUNCTION_NAME("\"foo(int)", "Invalid function name: \"foo"),
    UNESCAPED_COMMA_IN_FUNCTION_NAME("foo,bar(int)", "Invalid function name: foo,bar"),
    UNESCAPED_CLOSE_PAREN_IN_FUNCTION_NAME("foo)bar(int)", "Invalid function name: foo)bar"),
    UNESCAPED_BRACKETS_IN_FUNCTION_NAME("foo<int>(int)", "Invalid function name: foo<int>"),
    TRAILING_BACKSLASH(
        "foo(int)\\", "Invalid function signature, missing closing ')': foo(int)\\"),
    MISSING_COMMA_BETWEEN_ARGS(
        "foo(int string)", "unexpected character 's' at position 4 in \"int string\""),
    UNESCAPED_SPACE_IN_FUNCTION_NAME("fo o(int)", "Invalid function name: fo o"),
    UNESCAPED_SPACE_IN_RECEIVER(
        "int string.foo()", "unexpected character 's' at position 4 in \"int string\""),
    ;

    private final String signature;
    private final String expectedError;

    InvalidSignatureTestCase(String signature, String expectedError) {
      this.signature = signature;
      this.expectedError = expectedError;
    }
  }

  @Test
  public void parse_invalidSignature_throws(@TestParameter InvalidSignatureTestCase testCase) {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> CelOverloadDeclParser.parse(testCase.signature));
    assertThat(e).hasMessageThat().isEqualTo(testCase.expectedError);
  }
}
