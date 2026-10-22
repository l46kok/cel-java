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

package dev.cel.common;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import dev.cel.common.types.CelType;
import dev.cel.common.types.CelTypes;
import dev.cel.common.types.EnumType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.NullableType;
import dev.cel.common.types.OpaqueType;
import dev.cel.common.types.OptionalType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.common.types.TypeParamType;
import dev.cel.common.types.TypeType;
import dev.cel.common.types.UnspecifiedType;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class TypeSpecifierFormatterTest {

  private enum FormatTypeTestCase {
    NULL(SimpleType.NULL_TYPE, "null"),
    DYN(SimpleType.DYN, "dyn"),
    BOOL(SimpleType.BOOL, "bool"),
    INT(SimpleType.INT, "int"),
    UINT(SimpleType.UINT, "uint"),
    DOUBLE(SimpleType.DOUBLE, "double"),
    STRING(SimpleType.STRING, "string"),
    BYTES(SimpleType.BYTES, "bytes"),
    ANY(SimpleType.ANY, "any"),
    TIMESTAMP(SimpleType.TIMESTAMP, "timestamp"),
    DURATION(SimpleType.DURATION, "duration"),
    BOOL_WRAPPER(NullableType.create(SimpleType.BOOL), "bool_wrapper"),
    INT_WRAPPER(NullableType.create(SimpleType.INT), "int_wrapper"),
    UINT_WRAPPER(NullableType.create(SimpleType.UINT), "uint_wrapper"),
    DOUBLE_WRAPPER(NullableType.create(SimpleType.DOUBLE), "double_wrapper"),
    STRING_WRAPPER(NullableType.create(SimpleType.STRING), "string_wrapper"),
    BYTES_WRAPPER(NullableType.create(SimpleType.BYTES), "bytes_wrapper"),
    LIST_INT(ListType.create(SimpleType.INT), "list<int>"),
    LIST_DYN(ListType.create(SimpleType.DYN), "list<dyn>"),
    MAP_STRING_INT(MapType.create(SimpleType.STRING, SimpleType.INT), "map<string,int>"),
    TYPE_INT(TypeType.create(SimpleType.INT), "type<int>"),
    TYPE_DYN(TypeType.create(SimpleType.DYN), "type<dyn>"),
    TYPE_PARAM_T(TypeParamType.create("T"), "~T"),
    TYPE_PARAM_ESCAPED(TypeParamType.create("foo.bar<baz>"), "~foo\\.bar\\<baz\\>"),
    OPTIONAL_STRING(OptionalType.create(SimpleType.STRING), "optional_type<string>"),
    OPAQUE_SIMPLE(OpaqueType.create("my.pkg.CustomType"), "my.pkg.CustomType"),
    OPAQUE_PARAMETERIZED(
        OpaqueType.create("my.pkg.CustomType", SimpleType.INT, TypeParamType.create("T")),
        "my.pkg.CustomType<int,~T>"),
    FUNCTION_NO_ARGS(
        CelTypes.createFunctionType(SimpleType.INT, ImmutableList.of()), "function<int>"),
    FUNCTION_WITH_ARGS(
        CelTypes.createFunctionType(
            SimpleType.BOOL, ImmutableList.of(SimpleType.STRING, SimpleType.INT)),
        "function<bool,string,int>"),
    NESTED_COMPLEX(
        MapType.create(
            SimpleType.STRING, ListType.create(OptionalType.create(TypeParamType.create("V")))),
        "map<string,list<optional_type<~V>>>"),
    LIST_ESCAPED_TYPE_PARAM(
        ListType.create(TypeParamType.create("a,b<c>")), "list<~a\\,b\\<c\\>>");

    private final CelType type;
    private final String expectedSignature;

    FormatTypeTestCase(CelType type, String expectedSignature) {
      this.type = type;
      this.expectedSignature = expectedSignature;
    }
  }

  @Test
  public void format_success(@TestParameter FormatTypeTestCase testCase) {
    assertThat(TypeSpecifierFormatter.format(testCase.type)).isEqualTo(testCase.expectedSignature);
  }

  @Test
  public void format_structAndEnum() {
    CelType structType = StructTypeReference.create("cel.expr.conformance.proto3.TestAllTypes");
    CelType enumType =
        EnumType.create(
            "cel.expr.conformance.proto3.GlobalEnum", ImmutableMap.of("GOO", 0, "BAR", 1));

    assertThat(TypeSpecifierFormatter.format(structType))
        .isEqualTo("cel.expr.conformance.proto3.TestAllTypes");
    assertThat(TypeSpecifierFormatter.format(enumType))
        .isEqualTo("cel.expr.conformance.proto3.GlobalEnum");
  }

  @Test
  public void format_unparameterizedListAndFunction() {
    assertThat(TypeSpecifierFormatter.format(ListType.create())).isEqualTo("list<dyn>");
    assertThat(TypeSpecifierFormatter.format(OpaqueType.create("function")))
        .isEqualTo("function<dyn>");
  }

  @Test
  public void format_unsupportedTypes_throws() {
    CelType unspecifiedType = UnspecifiedType.create();
    CelType durationWrapperType = NullableType.create(SimpleType.DURATION);
    assertThrows(
        IllegalArgumentException.class, () -> TypeSpecifierFormatter.format(SimpleType.ERROR));
    assertThrows(
        IllegalArgumentException.class, () -> TypeSpecifierFormatter.format(unspecifiedType));
    assertThrows(
        IllegalArgumentException.class, () -> TypeSpecifierFormatter.format(durationWrapperType));
  }
}
