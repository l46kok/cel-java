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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import dev.cel.bundle.CelEnvironment.TypeDecl;
import java.util.Optional;

/** Parser for function overload signatures into {@link ParsedOverloadDecl}. */
final class CelOverloadDeclParser {

  @AutoValue
  abstract static class ParsedOverloadDecl {
    abstract String functionName();

    abstract Optional<TypeDecl> target();

    abstract ImmutableList<TypeDecl> arguments();

    static ParsedOverloadDecl create(
        String functionName, Optional<TypeDecl> target, Iterable<TypeDecl> arguments) {
      return new AutoValue_CelOverloadDeclParser_ParsedOverloadDecl(
          functionName, target, ImmutableList.copyOf(arguments));
    }
  }

  static ParsedOverloadDecl parse(String signature) {
    checkNotNull(signature);
    String trimmed = signature.trim();
    checkArgument(!trimmed.isEmpty(), "Empty function signature");

    int openParen =
        findTopLevelChar(trimmed, '(', /* findFirst= */ true, "Invalid function signature");
    checkArgument(openParen != -1, "Invalid function signature, missing '(': %s", trimmed);
    checkArgument(
        trimmed.charAt(trimmed.length() - 1) == ')',
        "Invalid function signature, missing closing ')': %s",
        trimmed);

    String prefix = trimTrailingUnescapedWhitespace(trimmed.substring(0, openParen));
    String argsStr = trimmed.substring(openParen + 1, trimmed.length() - 1);

    Optional<TypeDecl> target;
    String rawFunctionName;

    int dotPos =
        findTopLevelChar(prefix, '.', /* findFirst= */ false, "Invalid function signature");
    if (dotPos != -1) {
      String receiverStr = prefix.substring(0, dotPos);
      rawFunctionName = prefix.substring(dotPos + 1);
      target = Optional.of(TypeSpecifierParser.parse(receiverStr));
    } else {
      rawFunctionName = prefix;
      target = Optional.empty();
    }

    checkArgument(!rawFunctionName.isEmpty(), "Invalid function signature: empty function name");
    checkArgument(
        !hasUnescapedSpecialChar(rawFunctionName),
        "Invalid function name: %s",
        rawFunctionName);
    String functionName = unescape(rawFunctionName);

    ImmutableList<TypeDecl> arguments = TypeSpecifierParser.parseTypeList(argsStr);

    return ParsedOverloadDecl.create(functionName, target, arguments);
  }

  private static int findTopLevelChar(
      String str, char target, boolean findFirst, String errorPrefix) {
    int bracketDepth = 0;
    boolean escaped = false;
    int foundPos = -1;
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (escaped) {
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
      } else {
        if (c == target && bracketDepth == 0) {
          if (findFirst) {
            return i;
          }
          foundPos = i;
        }
        if (c == '<') {
          bracketDepth++;
        } else if (c == '>') {
          bracketDepth--;
          if (bracketDepth < 0) {
            throw new IllegalArgumentException(errorPrefix + ": mismatched brackets");
          }
        }
      }
    }
    if (bracketDepth != 0) {
      throw new IllegalArgumentException(errorPrefix + ": mismatched brackets");
    }
    return foundPos;
  }

  private static boolean hasUnescapedSpecialChar(String str) {
    boolean escaped = false;
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (escaped) {
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
      } else if (Character.isWhitespace(c)
          || c == ')'
          || c == '<'
          || c == '"'
          || c == ','
          || c == '~') {
        return true;
      }
    }
    return false;
  }

  private static String unescape(String str) {
    int firstEscape = str.indexOf('\\');
    if (firstEscape == -1) {
      return str;
    }
    StringBuilder result = new StringBuilder(str.length());
    result.append(str, 0, firstEscape);
    boolean escaped = false;
    for (int i = firstEscape; i < str.length(); i++) {
      char c = str.charAt(i);
      if (escaped) {
        result.append(c);
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  private static String trimTrailingUnescapedWhitespace(String str) {
    int lastNonWhitespaceEnd = 0;
    boolean escaped = false;
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (escaped) {
        escaped = false;
        lastNonWhitespaceEnd = i + 1;
      } else if (c == '\\') {
        escaped = true;
      } else if (!Character.isWhitespace(c)) {
        lastNonWhitespaceEnd = i + 1;
      }
    }
    return str.substring(0, lastNonWhitespaceEnd);
  }

  private CelOverloadDeclParser() {}
}
