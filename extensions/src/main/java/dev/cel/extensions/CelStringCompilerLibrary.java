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

package dev.cel.extensions;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dev.cel.checker.CelCheckerBuilder;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerLibrary;
import java.util.Set;

/** Internal implementation of CEL string compile-time extensions. */
@Immutable
public final class CelStringCompilerLibrary
    implements CelCompilerLibrary, CelExtensionLibrary.FeatureSet {

  /** Enumeration of functions for String compile-time extension. */
  public enum Function {
    CHAR_AT(
        CelFunctionDecl.newFunctionDeclaration(
            "charAt",
            CelOverloadDecl.newMemberOverload(
                "string_char_at_int",
                "Returns the character at the given position. If the position is negative, or"
                    + " greater than the length of the string, the function will produce an error.",
                SimpleType.STRING,
                ImmutableList.of(SimpleType.STRING, SimpleType.INT)))),
    FORMAT(
        CelFunctionDecl.newFunctionDeclaration(
            "format",
            CelOverloadDecl.newMemberOverload(
                "string_format",
                "Formats the string using the provided arguments.",
                SimpleType.STRING,
                ImmutableList.of(SimpleType.STRING, ListType.create(SimpleType.DYN))))),
    INDEX_OF(
        CelFunctionDecl.newFunctionDeclaration(
            "indexOf",
            CelOverloadDecl.newMemberOverload(
                "string_index_of_string",
                "Returns the integer index of the first occurrence of the search string. If the"
                    + " search string is not found the function returns -1.",
                SimpleType.INT,
                ImmutableList.of(SimpleType.STRING, SimpleType.STRING)),
            CelOverloadDecl.newMemberOverload(
                "string_index_of_string_int",
                "Returns the integer index of the first occurrence of the search string from the"
                    + " given offset. If the search string is not found the function returns"
                    + " -1. If the substring is the empty string, the index where the search starts"
                    + " is returned (zero or custom).",
                SimpleType.INT,
                ImmutableList.of(SimpleType.STRING, SimpleType.STRING, SimpleType.INT)))),
    JOIN(
        CelFunctionDecl.newFunctionDeclaration(
            "join",
            CelOverloadDecl.newMemberOverload(
                "list_join",
                "Returns a new string where the elements of string list are concatenated.",
                SimpleType.STRING,
                ListType.create(SimpleType.STRING)),
            CelOverloadDecl.newMemberOverload(
                "list_join_string",
                "Returns a new string where the elements of string list are concatenated using the"
                    + " separator.",
                SimpleType.STRING,
                ImmutableList.of(ListType.create(SimpleType.STRING), SimpleType.STRING)))),
    LAST_INDEX_OF(
        CelFunctionDecl.newFunctionDeclaration(
            "lastIndexOf",
            CelOverloadDecl.newMemberOverload(
                "string_last_index_of_string",
                "Returns the integer index of the last occurrence of the search string. If the"
                    + " search string is not found the function returns -1.",
                SimpleType.INT,
                ImmutableList.of(SimpleType.STRING, SimpleType.STRING)),
            CelOverloadDecl.newMemberOverload(
                "string_last_index_of_string_int",
                "Returns the integer index of the last occurrence of the search string from the"
                    + " given offset. If the search string is not found the function returns -1. If"
                    + " the substring is the empty string, the index where the search starts is"
                    + " returned (string length or custom).",
                SimpleType.INT,
                ImmutableList.of(SimpleType.STRING, SimpleType.STRING, SimpleType.INT)))),
    LOWER_ASCII(
        CelFunctionDecl.newFunctionDeclaration(
            "lowerAscii",
            CelOverloadDecl.newMemberOverload(
                "string_lower_ascii",
                "Returns a new string where all ASCII characters are lower-cased. This function"
                    + " does not perform Unicode case-mapping for characters outside the ASCII"
                    + " range.",
                SimpleType.STRING,
                SimpleType.STRING))),
    QUOTE(
        CelFunctionDecl.newFunctionDeclaration(
            "strings.quote",
            CelOverloadDecl.newGlobalOverload(
                "strings_quote",
                "Takes the given string and makes it safe to print (without any formatting"
                    + " due to escape sequences). If any invalid UTF-8 characters are"
                    + " encountered, they are replaced with \\uFFFD.",
                SimpleType.STRING,
                ImmutableList.of(SimpleType.STRING)))),
    REPLACE(
        CelFunctionDecl.newFunctionDeclaration(
            "replace",
            CelOverloadDecl.newMemberOverload(
                "string_replace_string_string",
                "Returns a new string based on the target, which replaces the occurrences of a"
                    + " search string with a replacement string if present.",
                SimpleType.STRING,
                ImmutableList.of(SimpleType.STRING, SimpleType.STRING, SimpleType.STRING)),
            CelOverloadDecl.newMemberOverload(
                "string_replace_string_string_int",
                "Returns a new string based on the target, which replaces the occurrences of a"
                    + " search string with a replacement string if present. The function accepts a"
                    + " limit on the number of substring replacements to be made. When the"
                    + " replacement limit is 0, the result is the original string. When the limit"
                    + " is a negative number, the function behaves the same as replace all.",
                SimpleType.STRING,
                ImmutableList.of(
                    SimpleType.STRING, SimpleType.STRING, SimpleType.STRING, SimpleType.INT)))),
    REVERSE(
        CelFunctionDecl.newFunctionDeclaration(
            "reverse",
            CelOverloadDecl.newMemberOverload(
                "string_reverse",
                "Returns a new string whose characters are the same as the target string,"
                    + " only formatted in reverse order.",
                SimpleType.STRING,
                SimpleType.STRING))),
    SPLIT(
        CelFunctionDecl.newFunctionDeclaration(
            "split",
            CelOverloadDecl.newMemberOverload(
                "string_split_string",
                "Returns a mutable list of strings split from the input by the given separator.",
                ListType.create(SimpleType.STRING),
                ImmutableList.of(SimpleType.STRING, SimpleType.STRING)),
            CelOverloadDecl.newMemberOverload(
                "string_split_string_int",
                "Returns a mutable list of strings split from the input by the given separator with"
                    + " the specified limit on the number of substrings produced by the split.",
                ListType.create(SimpleType.STRING),
                ImmutableList.of(SimpleType.STRING, SimpleType.STRING, SimpleType.INT)))),
    SUBSTRING(
        CelFunctionDecl.newFunctionDeclaration(
            "substring",
            CelOverloadDecl.newMemberOverload(
                "string_substring_int",
                "returns a string that is a substring of this string. The substring begins with the"
                    + " character at the specified index and extends to the end of this string.",
                SimpleType.STRING,
                ImmutableList.of(SimpleType.STRING, SimpleType.INT)),
            CelOverloadDecl.newMemberOverload(
                "string_substring_int_int",
                "returns a string that is a substring of this string. The substring begins at the"
                    + " specified beginIndex and extends to the character at index endIndex - 1."
                    + " Thus the length of the substring is {@code endIndex-beginIndex}.",
                SimpleType.STRING,
                ImmutableList.of(SimpleType.STRING, SimpleType.INT, SimpleType.INT)))),
    TRIM(
        CelFunctionDecl.newFunctionDeclaration(
            "trim",
            CelOverloadDecl.newMemberOverload(
                "string_trim",
                "Returns a new string which removes the leading and trailing whitespace in the"
                    + " target string. The trim function uses the Unicode definition of whitespace"
                    + " which does not include the zero-width spaces. ",
                SimpleType.STRING,
                SimpleType.STRING))),
    UPPER_ASCII(
        CelFunctionDecl.newFunctionDeclaration(
            "upperAscii",
            CelOverloadDecl.newMemberOverload(
                "string_upper_ascii",
                "Returns a new string where all ASCII characters are upper-cased. This function"
                    + " does not perform Unicode case-mapping for characters outside the ASCII"
                    + " range.",
                SimpleType.STRING,
                SimpleType.STRING)));

    private final CelFunctionDecl functionDecl;

    public String getFunction() {
      return functionDecl.name();
    }

    public CelFunctionDecl getFunctionDecl() {
      return functionDecl;
    }

    Function(CelFunctionDecl functionDecl) {
      this.functionDecl = functionDecl;
    }
  }

  private static final class Library implements CelExtensionLibrary<CelStringCompilerLibrary> {
    private final CelStringCompilerLibrary version0;

    Library() {
      version0 = new CelStringCompilerLibrary(0, ImmutableSet.copyOf(Function.values()));
    }

    @Override
    public String name() {
      return "strings";
    }

    @Override
    public ImmutableSet<CelStringCompilerLibrary> versions() {
      return ImmutableSet.of(version0);
    }
  }

  private static final Library LIBRARY = new Library();

  public static CelExtensionLibrary<CelStringCompilerLibrary> library() {
    return LIBRARY;
  }

  /** Returns the latest version of the 'strings' compiler extension. */
  public static CelStringCompilerLibrary strings() {
    return library().latest();
  }

  /** Returns the specified version of the 'strings' compiler extension. */
  public static CelStringCompilerLibrary strings(int version) {
    return library().version(version);
  }

  /** Returns the 'strings' compiler extension with only the specified functions. */
  public static CelStringCompilerLibrary strings(Function... functions) {
    return strings(ImmutableSet.copyOf(functions));
  }

  /** Returns the 'strings' compiler extension with only the specified functions. */
  public static CelStringCompilerLibrary strings(Set<Function> functions) {
    return new CelStringCompilerLibrary(functions);
  }

  private final ImmutableSet<Function> functions;
  private final int version;

  CelStringCompilerLibrary(Set<Function> functions) {
    this(-1, functions);
  }

  private CelStringCompilerLibrary(int version, Set<Function> functions) {
    this.version = version;
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public int version() {
    return version;
  }

  @Override
  public ImmutableSet<CelFunctionDecl> functions() {
    return functions.stream().map(Function::getFunctionDecl).collect(toImmutableSet());
  }

  @Override
  public void setCheckerOptions(CelCheckerBuilder checkerBuilder) {
    functions.forEach(function -> checkerBuilder.addFunctionDeclarations(function.functionDecl));
  }
}
