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

import dev.cel.common.types.ListType;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.TypeParamType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CelOverloadDeclFormatterTest {

  @Test
  public void format_globalNoArg() {
    CelOverloadDecl overload = CelOverloadDecl.newGlobalOverload("now", SimpleType.TIMESTAMP);
    assertThat(CelOverloadDeclFormatter.format("now", overload)).isEqualTo("now()");
  }

  @Test
  public void format_globalMultiArg() {
    CelOverloadDecl overload =
        CelOverloadDecl.newGlobalOverload(
            "add",
            SimpleType.INT,
            SimpleType.INT,
            ListType.create(SimpleType.STRING));
    assertThat(CelOverloadDeclFormatter.format("add", overload)).isEqualTo("add(int,list<string>)");
  }

  @Test
  public void format_memberWithParameterizedReceiver() {
    CelOverloadDecl overload =
        CelOverloadDecl.newMemberOverload(
            "get",
            TypeParamType.create("V"),
            MapType.create(TypeParamType.create("K"), TypeParamType.create("V")),
            TypeParamType.create("K"));
    assertThat(CelOverloadDeclFormatter.format("get", overload)).isEqualTo("map<~K,~V>.get(~K)");
  }

  @Test
  public void format_namespacedGlobal() {
    CelOverloadDecl overload =
        CelOverloadDecl.newGlobalOverload(
            "math.greatest", SimpleType.INT, SimpleType.INT, SimpleType.INT);
    assertThat(CelOverloadDeclFormatter.format("math.greatest", overload))
        .isEqualTo("math\\.greatest(int,int)");
  }

  @Test
  public void format_memberWithNoArgs_throws() {
    CelOverloadDecl overload = CelOverloadDecl.newMemberOverload("size", SimpleType.INT);
    assertThrows(
        IllegalArgumentException.class, () -> CelOverloadDeclFormatter.format("size", overload));
  }

  @Test
  public void format_nullArguments_throws() {
    CelOverloadDecl overload = CelOverloadDecl.newGlobalOverload("now", SimpleType.TIMESTAMP);
    assertThrows(
        NullPointerException.class, () -> CelOverloadDeclFormatter.format(null, overload));
    assertThrows(
        NullPointerException.class, () -> CelOverloadDeclFormatter.format("now", null));
  }
}
