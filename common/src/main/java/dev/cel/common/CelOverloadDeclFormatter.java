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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import dev.cel.common.annotations.Internal;
import dev.cel.common.types.CelType;

/**
 * Internal utility methods for formatting CEL function overloads into canonical overload ID signatures.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class CelOverloadDeclFormatter {

  /**
   * Generates a canonical overload ID / signature string for a {@link CelOverloadDecl}.
   *
   * <p>For example:
   *
   * <ul>
   *   <li>{@code format("add", globalOverload)} -> {@code "add(int,int)"}
   *   <li>{@code format("contains", memberOverload)} -> {@code "string.contains(string)"}
   * </ul>
   */
  public static String format(String functionName, CelOverloadDecl overload) {
    checkNotNull(functionName);
    checkNotNull(overload);
    ImmutableList<CelType> parameterTypes = overload.parameterTypes();
    boolean isInstanceFunction = overload.isInstanceFunction();
    StringBuilder result = new StringBuilder();
    if (isInstanceFunction) {
      checkArgument(!parameterTypes.isEmpty(), "Member function with no receiver");
      TypeSpecifierFormatter.appendTypeSignature(result, parameterTypes.get(0));
      result.append('.');
    }
    TypeSpecifierFormatter.appendEscaped(result, functionName, /* escapeDot= */ true);
    result.append('(');
    int startIndex = isInstanceFunction ? 1 : 0;
    for (int i = startIndex; i < parameterTypes.size(); i++) {
      if (i > startIndex) {
        result.append(',');
      }
      TypeSpecifierFormatter.appendTypeSignature(result, parameterTypes.get(i));
    }
    result.append(')');
    return result.toString();
  }

  private CelOverloadDeclFormatter() {}
}
