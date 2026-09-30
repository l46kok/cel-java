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

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.exceptions.CelBadFormatException;
import dev.cel.common.exceptions.CelInvalidArgumentException;
import dev.cel.common.internal.CelCodePointArray;
import dev.cel.common.internal.DateTimeHelpers;
import dev.cel.common.types.CelType;
import dev.cel.common.types.TypeType;
import dev.cel.common.values.CelByteString;
import dev.cel.common.values.NullValue;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelEvaluationExceptionBuilder;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelLiteRuntimeBuilder;
import dev.cel.runtime.CelLiteRuntimeLibrary;
import dev.cel.runtime.TypeResolver;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Runtime implementation of CEL string extension functions. */
@Immutable
public final class CelStringRuntimeLibrary implements CelLiteRuntimeLibrary {

  // ROOT is equivalent to US locale. Formatting should be machine-oriented, not UI-oriented.
  private static final Locale DEFAULT_LOCALE = Locale.ROOT;

  // TODO: Make max precision limit configurable via CelStringExtensions options.
  private static final int MAX_PRECISION = 100;

  private static final BaseEncoding BASE16_LOWER = BaseEncoding.base16().lowerCase();

  // Minimum initial capacity for the buffer used by string.format.
  private static final int MIN_FORMAT_BUFFER_SIZE = 64;

  /** Enumeration of runtime function bindings for the String extension. */
  @SuppressWarnings({"unchecked"}) // Unchecked: Type-checker guarantees casting safety.
  public enum Function {
    CHAR_AT(
        "charAt",
        ImmutableSet.of(
            CelFunctionBinding.from(
                "string_char_at_int",
                String.class,
                Long.class,
                CelStringRuntimeLibrary::charAt))),
    FORMAT(
        "format",
        ImmutableSet.of(
            CelFunctionBinding.from(
                "string_format", String.class, List.class, CelStringRuntimeLibrary::format))),
    INDEX_OF(
        "indexOf",
        ImmutableSet.of(
            CelFunctionBinding.from(
                "string_index_of_string",
                String.class,
                String.class,
                CelStringRuntimeLibrary::indexOf),
            CelFunctionBinding.from(
                "string_index_of_string_int",
                ImmutableList.of(String.class, String.class, Long.class),
                CelStringRuntimeLibrary::indexOf))),
    JOIN(
        "join",
        ImmutableSet.of(
            CelFunctionBinding.from("list_join", List.class, CelStringRuntimeLibrary::join),
            CelFunctionBinding.from(
                "list_join_string", List.class, String.class, CelStringRuntimeLibrary::join))),
    LAST_INDEX_OF(
        "lastIndexOf",
        ImmutableSet.of(
            CelFunctionBinding.from(
                "string_last_index_of_string",
                String.class,
                String.class,
                CelStringRuntimeLibrary::lastIndexOf),
            CelFunctionBinding.from(
                "string_last_index_of_string_int",
                ImmutableList.of(String.class, String.class, Long.class),
                CelStringRuntimeLibrary::lastIndexOf))),
    LOWER_ASCII(
        "lowerAscii",
        ImmutableSet.of(
            CelFunctionBinding.from("string_lower_ascii", String.class, Ascii::toLowerCase))),
    QUOTE(
        "strings.quote",
        ImmutableSet.of(
            CelFunctionBinding.from(
                "strings_quote", String.class, CelStringRuntimeLibrary::quote))),
    REPLACE(
        "replace",
        ImmutableSet.of(
            CelFunctionBinding.from(
                "string_replace_string_string",
                ImmutableList.of(String.class, String.class, String.class),
                CelStringRuntimeLibrary::replaceAll),
            CelFunctionBinding.from(
                "string_replace_string_string_int",
                ImmutableList.of(String.class, String.class, String.class, Long.class),
                CelStringRuntimeLibrary::replace))),
    REVERSE(
        "reverse",
        ImmutableSet.of(
            CelFunctionBinding.from(
                "string_reverse", String.class, CelStringRuntimeLibrary::reverse))),
    SPLIT(
        "split",
        ImmutableSet.of(
            CelFunctionBinding.from(
                "string_split_string", String.class, String.class, CelStringRuntimeLibrary::split),
            CelFunctionBinding.from(
                "string_split_string_int",
                ImmutableList.of(String.class, String.class, Long.class),
                CelStringRuntimeLibrary::split))),
    SUBSTRING(
        "substring",
        ImmutableSet.of(
            CelFunctionBinding.from(
                "string_substring_int",
                String.class,
                Long.class,
                CelStringRuntimeLibrary::substring),
            CelFunctionBinding.from(
                "string_substring_int_int",
                ImmutableList.of(String.class, Long.class, Long.class),
                CelStringRuntimeLibrary::substring))),
    TRIM(
        "trim",
        ImmutableSet.of(
            CelFunctionBinding.from("string_trim", String.class, CelStringRuntimeLibrary::trim))),
    UPPER_ASCII(
        "upperAscii",
        ImmutableSet.of(
            CelFunctionBinding.from("string_upper_ascii", String.class, Ascii::toUpperCase)));

    private final String functionName;
    private final ImmutableSet<CelFunctionBinding> functionBindings;

    public String getFunction() {
      return functionName;
    }

    public ImmutableSet<CelFunctionBinding> getFunctionBindings() {
      return functionBindings;
    }

    Function(String functionName, ImmutableSet<CelFunctionBinding> bindings) {
      this.functionName = functionName;
      this.functionBindings = bindings;
    }
  }

  private static final CelStringRuntimeLibrary VERSION_0 =
      new CelStringRuntimeLibrary(ImmutableSet.copyOf(Function.values()));

  /** Returns the latest version of the 'strings' runtime functions. */
  public static CelStringRuntimeLibrary strings() {
    return VERSION_0;
  }

  /** Returns the specified version of the 'strings' runtime functions. */
  public static CelStringRuntimeLibrary strings(int version) {
    switch (version) {
      case 0:
      case Integer.MAX_VALUE:
        return VERSION_0;
      default:
        throw new IllegalArgumentException("Unsupported 'strings' extension version " + version);
    }
  }

  /** Returns the 'strings' runtime functions with only the specified functions. */
  public static CelStringRuntimeLibrary strings(Function... functions) {
    return strings(ImmutableSet.copyOf(functions));
  }

  /** Returns the 'strings' runtime functions with only the specified functions. */
  public static CelStringRuntimeLibrary strings(Set<Function> functions) {
    return new CelStringRuntimeLibrary(functions);
  }

  private final ImmutableSet<Function> functions;

  CelStringRuntimeLibrary(Set<Function> functions) {
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public void setRuntimeOptions(CelLiteRuntimeBuilder runtimeBuilder) {
    runtimeBuilder.addFunctionBindings(newFunctionBindings());
  }

  /** Creates the {@link CelFunctionBinding}s for the configured string functions. */
  public ImmutableSet<CelFunctionBinding> newFunctionBindings() {
    ImmutableSet.Builder<CelFunctionBinding> builder = ImmutableSet.builder();
    for (Function function : functions) {
      if (!function.functionBindings.isEmpty()) {
        builder.addAll(
            CelFunctionBinding.fromOverloads(function.functionName, function.functionBindings));
      }
    }
    return builder.build();
  }

  private static String charAt(String s, long i) throws CelEvaluationException {
    int index;
    try {
      index = Math.toIntExact(i);
    } catch (ArithmeticException e) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "charAt failure: Index must not exceed the int32 range: %d", i)
          .setCause(e)
          .build();
    }

    CelCodePointArray codePointArray = CelCodePointArray.fromString(s);
    if (index == codePointArray.length()) {
      return "";
    }
    if (index < 0 || index > codePointArray.length()) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "charAt failure: Index out of range: %d", index)
          .build();
    }

    return codePointArray.slice(index, index + 1).toString();
  }

  private static Long indexOf(String str, String substr) throws CelEvaluationException {
    Object[] params = {str, substr, 0L};
    return indexOf(params);
  }

  /**
   * @param args Object array with indices of: [0: string], [1: substring], [2: offset]
   */
  private static Long indexOf(Object[] args) throws CelEvaluationException {
    String str = (String) args[0];
    String substr = (String) args[1];
    long offsetInLong = (Long) args[2];
    int offset;
    try {
      offset = Math.toIntExact(offsetInLong);
    } catch (ArithmeticException e) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "indexOf failure: Offset must not exceed the int32 range: %d", offsetInLong)
          .setCause(e)
          .build();
    }

    return indexOf(str, substr, offset);
  }

  private static Long indexOf(String str, String substr, int offset) throws CelEvaluationException {
    if (substr.isEmpty()) {
      return (long) offset;
    }

    CelCodePointArray strCpa = CelCodePointArray.fromString(str);
    CelCodePointArray substrCpa = CelCodePointArray.fromString(substr);

    if (offset < 0 || offset >= strCpa.length()) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "indexOf failure: Offset out of range: %d", offset)
          .build();
    }

    return safeIndexOf(strCpa, substrCpa, offset);
  }

  /** Retrieves the index of the substring in a given string without throwing. */
  private static Long safeIndexOf(CelCodePointArray str, CelCodePointArray substr, int offset) {
    for (int i = offset; i < str.length() - (substr.length() - 1); i++) {
      int j;
      for (j = 0; j < substr.length(); j++) {
        if (str.get(i + j) != substr.get(j)) {
          break;
        }
      }

      if (j == substr.length()) {
        return (long) i;
      }
    }

    // Offset is out of bound.
    return -1L;
  }

  private static String join(List<String> stringList) {
    return join(stringList, "");
  }

  private static String join(List<String> stringList, String separator) {
    return Joiner.on(separator).join(stringList);
  }

  private static String format(String formatSpecifier, List<?> args) {
    // Formatted values are typically longer than their format specifiers. Over-allocate to avoid
    // repeatedly resizing the underlying char array.
    StringBuilder builtStr =
        new StringBuilder(max(formatSpecifier.length() * 2, MIN_FORMAT_BUFFER_SIZE));
    int i = 0;
    int argIndex = 0;
    while (i < formatSpecifier.length()) {
      if (formatSpecifier.charAt(i) != '%') {
        builtStr.append(formatSpecifier.charAt(i++));
        continue;
      }

      if (i + 1 < formatSpecifier.length() && formatSpecifier.charAt(i + 1) == '%') {
        builtStr.append('%');
        i += 2;
        continue;
      }

      i = parseAndFormatClause(formatSpecifier, i + 1, args, argIndex++, builtStr);
    }
    return builtStr.toString();
  }

  /**
   * Parses and formats a single format clause after '%', starting at index {@code offset}. Returns
   * the new index in {@code formatSpecifier} after consuming the clause.
   */
  private static int parseAndFormatClause(
      String formatSpecifier, int offset, List<?> args, int argIndex, StringBuilder builtStr) {
    int i = offset;
    int precision = -1;
    if (i < formatSpecifier.length() && formatSpecifier.charAt(i) == '.') {
      i++;
      int start = i;
      while (i < formatSpecifier.length() && Character.isDigit(formatSpecifier.charAt(i))) {
        i++;
      }
      if (i >= formatSpecifier.length()) {
        throw new CelBadFormatException("unexpected end of string");
      }
      if (i == start) {
        throw new CelBadFormatException("empty precision is not allowed");
      } else {
        try {
          precision = Integer.parseInt(formatSpecifier.substring(start, i));
        } catch (NumberFormatException e) {
          throw new CelBadFormatException(
              "invalid precision format: " + formatSpecifier.substring(start, i), e);
        }
        // TODO: Make max precision limit configurable via CelStringExtensions options.
        if (precision > MAX_PRECISION) {
          throw new CelInvalidArgumentException(
              "precision " + precision + " exceeds maximum allowed (" + MAX_PRECISION + ")");
        }
      }
    }
    if (i >= formatSpecifier.length()) {
      throw new CelBadFormatException("unexpected end of string");
    }
    char verb = formatSpecifier.charAt(i++);

    if (argIndex >= args.size()) {
      throw new CelBadFormatException("index " + argIndex + " out of range");
    }

    Object arg = args.get(argIndex);

    switch (verb) {
      case 's':
        builtStr.append(formatString(arg));
        break;
      case 'd':
        builtStr.append(formatDecimal(arg));
        break;
      case 'f':
        builtStr.append(formatFixed(arg, precision));
        break;
      case 'e':
        builtStr.append(formatScientific(arg, precision));
        break;
      case 'b':
        builtStr.append(formatBinary(arg));
        break;
      case 'x':
      case 'X':
        builtStr.append(formatHex(arg, verb == 'X'));
        break;
      case 'o':
        builtStr.append(formatOctal(arg));
        break;
      default:
        throw new CelBadFormatException("unrecognized formatting clause \"" + verb + "\"");
    }
    return i;
  }

  private static String formatString(Object val) {
    Preconditions.checkNotNull(val);
    if (val instanceof String
        || val instanceof Instant
        || val instanceof Boolean
        || val instanceof Long
        || val instanceof UnsignedLong) {
      return val.toString();
    }
    if (val instanceof CelByteString) {
      return formatByteString((CelByteString) val);
    }
    if (val instanceof Duration) {
      return DateTimeHelpers.toString((Duration) val);
    }
    if (val instanceof Double) {
      return formatDouble((Double) val);
    }
    if (val instanceof List) {
      return formatList((List<?>) val);
    }
    if (val instanceof Map) {
      return formatMap((Map<?, ?>) val);
    }
    if (val instanceof NullValue) {
      return "null";
    }
    if (val instanceof TypeType) {
      return ((TypeType) val).containingTypeName();
    }
    if (val instanceof CelType) {
      return ((CelType) val).name();
    }
    throw new CelInvalidArgumentException(
        "could not convert argument " + TypeResolver.resolveTypeName(val) + " to string");
  }

  private static String formatByteString(CelByteString byteString) {
    if (byteString.isValidUtf8()) {
      return byteString.toStringUtf8();
    }
    return decodeUtf8Lossy(byteString.toByteArray());
  }

  /**
   * Decodes a byte array as UTF-8 in a lossy manner.
   *
   * <p>This custom decoding is necessary because Java natively uses UTF-16 internally, while CEL
   * requires strict adherence to UTF-8 semantics, particularly regarding invalid byte sequences and
   * how they are replaced with the Unicode replacement character (U+FFFD).
   */
  private static String decodeUtf8Lossy(byte[] bytes) {
    if (bytes.length == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder(bytes.length);
    boolean inInvalidSequence = false;
    int i = 0;

    while (i < bytes.length) {
      int codePoint = tryDecodeUtf8(bytes, i);
      if (codePoint >= 0) {
        if (inInvalidSequence) {
          sb.append('\uFFFD');
          inInvalidSequence = false;
        }
        sb.appendCodePoint(codePoint);
        i += utf8ByteLength(codePoint);
      } else {
        // Encountered an invalid byte; per Unicode specification, discard 1 byte
        // and flag the invalid sequence to emit a single replacement character.
        inInvalidSequence = true;
        i++;
      }
    }
    if (inInvalidSequence) {
      sb.append('\uFFFD');
    }
    return sb.toString();
  }

  private static int utf8ByteLength(int codePoint) {
    if (codePoint <= 0x7F) {
      return 1;
    }
    if (codePoint <= 0x7FF) {
      return 2;
    }
    if (codePoint <= 0xFFFF) {
      return 3;
    }
    return 4;
  }

  private static int tryDecodeUtf8(byte[] bytes, int i) {
    int len = bytes.length;
    int b0 = bytes[i] & 0xFF;

    // 1-byte ASCII (U+0000 - U+007F)
    if (b0 < 0x80) {
      return b0;
    }

    // 2-byte sequence (U+0080 - U+07FF). Starts at 0xC2 to avoid overlongs of ASCII.
    if (b0 >= 0xC2 && b0 <= 0xDF) {
      if (i + 1 < len) {
        int b1 = bytes[i + 1] & 0xFF;
        if (b1 >= 0x80 && b1 <= 0xBF) {
          return ((b0 & 0x1F) << 6) | (b1 & 0x3F);
        }
      }
    } else if (b0 >= 0xE0 && b0 <= 0xEF) {
      // 3-byte sequence (U+0800 - U+FFFF)
      if (i + 2 < len) {
        int b1 = bytes[i + 1] & 0xFF;
        int b2 = bytes[i + 2] & 0xFF;
        boolean valid =
            (b2 >= 0x80 && b2 <= 0xBF)
                && ((b0 == 0xE0
                        && b1 >= 0xA0
                        && b1 <= 0xBF) // U+0800 - U+0FFF (b1 < 0xA0 would be overlong)
                    || (b0 >= 0xE1 && b0 <= 0xEC && b1 >= 0x80 && b1 <= 0xBF) // U+1000 - U+CFFF
                    || (b0 == 0xED
                        && b1 >= 0x80
                        && b1 <= 0x9F) // U+D000 - U+D7FF (b1 >= 0xA0 would be UTF-16 surrogate)
                    || (b0 >= 0xEE && b0 <= 0xEF && b1 >= 0x80 && b1 <= 0xBF)); // U+E000 - U+FFFF
        if (valid) {
          return ((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F);
        }
      }
    } else if (b0 >= 0xF0 && b0 <= 0xF4) {
      // 4-byte sequence (U+10000 - U+10FFFF)
      if (i + 3 < len) {
        int b1 = bytes[i + 1] & 0xFF;
        int b2 = bytes[i + 2] & 0xFF;
        int b3 = bytes[i + 3] & 0xFF;
        boolean valid =
            (b2 >= 0x80 && b2 <= 0xBF)
                && (b3 >= 0x80 && b3 <= 0xBF)
                && ((b0 == 0xF0
                        && b1 >= 0x90
                        && b1 <= 0xBF) // U+10000 - U+3FFFF (b1 < 0x90 would be overlong)
                    || (b0 >= 0xF1 && b0 <= 0xF3 && b1 >= 0x80 && b1 <= 0xBF) // U+40000 - U+FFFFF
                    || (b0 == 0xF4
                        && b1 >= 0x80
                        && b1 <= 0x8F)); // U+100000 - U+10FFFF (b1 > 0x8F would exceed max Code
        // Point)
        if (valid) {
          return ((b0 & 0x07) << 18) | ((b1 & 0x3F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
        }
      }
    }
    return -1;
  }

  private static String formatList(List<?> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
      sb.append(formatString(list.get(i)));
      if (i < list.size() - 1) {
        sb.append(", ");
      }
    }
    sb.append("]");
    return sb.toString();
  }

  private static class MapEntry {
    final String keyStr;
    final String valStr;

    MapEntry(String keyStr, String valStr) {
      this.keyStr = keyStr;
      this.valStr = valStr;
    }
  }

  private static String formatMap(Map<?, ?> map) {
    List<MapEntry> entries = new ArrayList<>(map.size());
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      entries.add(new MapEntry(formatString(entry.getKey()), formatString(entry.getValue())));
    }
    entries.sort(Comparator.comparing(e -> e.keyStr, CelStringRuntimeLibrary::compareCodePoints));

    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < entries.size(); i++) {
      MapEntry entry = entries.get(i);
      sb.append(entry.keyStr).append(": ").append(entry.valStr);
      if (i < entries.size() - 1) {
        sb.append(", ");
      }
    }
    sb.append("}");
    return sb.toString();
  }

  private static String formatDecimal(Object arg) {
    if (arg instanceof Double) {
      return formatDouble((Double) arg);
    }
    if (arg instanceof Long || arg instanceof UnsignedLong) {
      return arg.toString();
    }
    throw new CelInvalidArgumentException(
        "decimal clause can only be used on numbers, was given "
            + TypeResolver.resolveTypeName(arg));
  }

  private static Optional<String> formatNonFinite(double val) {
    if (Double.isNaN(val)) {
      return Optional.of("NaN");
    }
    if (Double.isInfinite(val)) {
      return Optional.of(val > 0 ? "Infinity" : "-Infinity");
    }
    return Optional.empty();
  }

  private static String formatDouble(double val) {
    Optional<String> nonFinite = formatNonFinite(val);
    if (nonFinite.isPresent()) {
      return nonFinite.get();
    }
    if (val == 0.0) {
      return Double.doubleToRawLongBits(val) < 0 ? "-0" : "0";
    }
    return BigDecimal.valueOf(val).stripTrailingZeros().toPlainString();
  }

  private static double getDoubleValue(Object arg, String clauseName) {
    if (arg instanceof Double) {
      return (Double) arg;
    }
    if (arg instanceof Long) {
      return ((Long) arg).doubleValue();
    }
    if (arg instanceof UnsignedLong) {
      return ((UnsignedLong) arg).doubleValue();
    }
    throw new CelInvalidArgumentException(
        clauseName
            + " clause can only be used on doubles, integers, and unsigned integers, was given "
            + TypeResolver.resolveTypeName(arg));
  }

  private static String formatFixed(Object arg, int precision) {
    double val = getDoubleValue(arg, "fixed point");
    Optional<String> nonFinite = formatNonFinite(val);
    if (nonFinite.isPresent()) {
      return nonFinite.get();
    }
    int p = precision >= 0 ? precision : 6;
    // Constructs a BigDecimal from the exact IEEE-754 binary representation of the double
    // (retaining double precision fuzziness) and applies HALF_EVEN (Banker's) rounding.
    // This is strictly required for cross-stack parity with Go and C++, because Java's
    // String.format natively uses HALF_UP rounding (e.g. 1.2345 -> 1.235 instead of 1.234).
    BigDecimal bd = new BigDecimal(val);
    BigDecimal rounded = bd.setScale(p, RoundingMode.HALF_EVEN);
    String result = rounded.toPlainString();
    if (Double.doubleToRawLongBits(val) < 0 && !result.startsWith("-")) {
      return "-" + result;
    }
    return result;
  }

  private static String formatScientific(Object arg, int precision) {
    double val = getDoubleValue(arg, "scientific");
    Optional<String> nonFinite = formatNonFinite(val);
    if (nonFinite.isPresent()) {
      return nonFinite.get();
    }
    int p = precision >= 0 ? precision : 6;

    // Constructs a BigDecimal from the exact IEEE-754 binary representation of the double
    // (retaining double precision fuzziness) and applies HALF_EVEN (Banker's) rounding.
    // This is strictly required for cross-stack parity with Go and C++, because Java's
    // String.format natively uses HALF_UP rounding (e.g. 1.2345 -> 1.235e+00 instead of 1.234e+00).
    BigDecimal bd = new BigDecimal(val);
    BigDecimal rounded = bd.round(new MathContext(p + 1, RoundingMode.HALF_EVEN));
    String result = String.format(DEFAULT_LOCALE, "%." + p + "e", rounded);
    // BigDecimal has no notion of negative zero, so the sign bit must be reapplied by hand.
    if (Double.doubleToRawLongBits(val) < 0 && !result.startsWith("-")) {
      return "-" + result;
    }
    return result;
  }

  private static String formatBinary(Object arg) {
    if (arg instanceof Long) {
      long val = (long) arg;

      // Negating Long.MIN_VALUE overflows back to itself, but Long.toBinaryString interprets its
      // argument as unsigned, so the digits still spell out the magnitude 2^63.
      return val < 0 ? "-" + Long.toBinaryString(-val) : Long.toBinaryString(val);
    }
    if (arg instanceof UnsignedLong) {
      UnsignedLong ulong = (UnsignedLong) arg;
      return ulong.toString(2);
    }
    if (arg instanceof Boolean) {
      Boolean b = (Boolean) arg;
      return b ? "1" : "0";
    }
    throw new CelInvalidArgumentException(
        "binary clause can only be used on integers and bools, was given "
            + TypeResolver.resolveTypeName(arg));
  }

  private static String formatHex(Object arg, boolean upper) {
    String result;
    if (arg instanceof Long) {
      long val = (long) arg;
      // See formatBinary: negating Long.MIN_VALUE is a no-op, but the unsigned reading is correct.
      result = val < 0 ? "-" + Long.toHexString(-val) : Long.toHexString(val);
    } else if (arg instanceof UnsignedLong) {
      UnsignedLong unsignedLong = (UnsignedLong) arg;
      result = unsignedLong.toString(16);
    } else if (arg instanceof CelByteString) {
      CelByteString byteString = (CelByteString) arg;
      result = BASE16_LOWER.encode(byteString.toByteArray());
    } else if (arg instanceof String) {
      String str = (String) arg;
      result = BASE16_LOWER.encode(str.getBytes(UTF_8));
    } else {
      throw new CelInvalidArgumentException(
          "hex clause can only be used on integers, byte buffers, and strings, was given "
              + TypeResolver.resolveTypeName(arg));
    }
    return upper ? result.toUpperCase(DEFAULT_LOCALE) : result;
  }

  private static String formatOctal(Object arg) {
    if (arg instanceof Long) {
      long val = (long) arg;
      // See formatBinary: negating Long.MIN_VALUE is a no-op, but the unsigned reading is correct.
      return val < 0 ? "-" + Long.toOctalString(-val) : Long.toOctalString(val);
    }
    if (arg instanceof UnsignedLong) {
      UnsignedLong ulong = (UnsignedLong) arg;
      return ulong.toString(8);
    }
    throw new CelInvalidArgumentException(
        "octal clause can only be used on integers, was given "
            + TypeResolver.resolveTypeName(arg));
  }

  private static Long lastIndexOf(String str, String substr) throws CelEvaluationException {
    CelCodePointArray strCpa = CelCodePointArray.fromString(str);
    CelCodePointArray substrCpa = CelCodePointArray.fromString(substr);
    if (substrCpa.isEmpty()) {
      return (long) strCpa.length();
    }

    if (strCpa.length() < substrCpa.length()) {
      return -1L;
    }

    return lastIndexOf(strCpa, substrCpa, (long) strCpa.length() - 1);
  }

  private static Long lastIndexOf(Object[] args) throws CelEvaluationException {
    CelCodePointArray strCpa = CelCodePointArray.fromString((String) args[0]);
    CelCodePointArray substrCpa = CelCodePointArray.fromString((String) args[1]);
    long offset = (long) args[2];

    return lastIndexOf(strCpa, substrCpa, offset);
  }

  private static Long lastIndexOf(CelCodePointArray str, CelCodePointArray substr, long offset)
      throws CelEvaluationException {
    if (substr.isEmpty()) {
      return offset;
    }

    int off;
    try {
      off = Math.toIntExact(offset);
    } catch (ArithmeticException e) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "lastIndexOf failure: Offset must not exceed the int32 range: %d", offset)
          .setCause(e)
          .build();
    }

    if (off < 0 || off >= str.length()) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "lastIndexOf failure: Offset out of range: %d", offset)
          .build();
    }

    if (off > str.length() - substr.length()) {
      off = str.length() - substr.length();
    }

    for (int i = off; i >= 0; i--) {
      int j;
      for (j = 0; j < substr.length(); j++) {
        if (str.get(i + j) != substr.get(j)) {
          break;
        }
      }

      if (j == substr.length()) {
        return (long) i;
      }
    }

    return -1L;
  }

  private static String quote(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 2);
    sb.append('"');
    for (int i = 0; i < s.length(); ) {
      int codePoint = s.codePointAt(i);
      if (isMalformedUtf16(s, i)) {
        sb.append('\uFFFD');
        i++;
        continue;
      }
      switch (codePoint) {
        case '\u0007':
          sb.append("\\a");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\u000B':
          sb.append("\\v");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '"':
          sb.append("\\\"");
          break;
        default:
          sb.appendCodePoint(codePoint);
          break;
      }
      i += Character.charCount(codePoint);
    }
    sb.append('"');
    return sb.toString();
  }

  private static boolean isMalformedUtf16(String s, int index) {
    char currentChar = s.charAt(index);
    if (Character.isLowSurrogate(currentChar)) {
      return true;
    }
    // Check for unpaired high surrogate
    return Character.isHighSurrogate(currentChar)
        && (index + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(index + 1)));
  }

  private static String replaceAll(Object[] objects) {
    return replace((String) objects[0], (String) objects[1], (String) objects[2], -1);
  }

  private static String replace(Object[] objects) throws CelEvaluationException {
    Long indexInLong = (Long) objects[3];
    int index;
    try {
      index = Math.toIntExact(indexInLong);
    } catch (ArithmeticException e) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "replace failure: Index must not exceed the int32 range: %d", indexInLong)
          .setCause(e)
          .build();
    }

    return replace((String) objects[0], (String) objects[1], (String) objects[2], index);
  }

  private static String replace(String text, String searchString, String replacement, int limit) {
    if (searchString.equals(replacement) || limit == 0) {
      return text;
    }

    if (text.isEmpty()) {
      return searchString.isEmpty() ? replacement : "";
    }

    CelCodePointArray textCpa = CelCodePointArray.fromString(text);
    CelCodePointArray searchCpa = CelCodePointArray.fromString(searchString);
    CelCodePointArray replaceCpa = CelCodePointArray.fromString(replacement);

    int start = 0;
    int end = Math.toIntExact(safeIndexOf(textCpa, searchCpa, 0));
    if (end < 0) {
      return text;
    }

    // The minimum length of 1 handles the case of searchString being empty, where every character
    // would be matched. This ensures the window is always moved forward to continue the search.
    int minSearchLength = max(searchCpa.length(), 1);
    StringBuilder sb =
        new StringBuilder(textCpa.length() - searchCpa.length() + replaceCpa.length());

    do {
      CelCodePointArray sliced = textCpa.slice(start, end);
      sb.append(sliced).append(replaceCpa);
      start = end + searchCpa.length();
      limit--;
    } while (limit != 0
        && (end = Math.toIntExact(safeIndexOf(textCpa, searchCpa, end + minSearchLength))) > 0);

    return sb.append(textCpa.slice(start, textCpa.length())).toString();
  }

  private static String reverse(String s) {
    return new StringBuilder(s).reverse().toString();
  }

  private static ImmutableList<String> split(String str, String separator) {
    return split(str, separator, Integer.MAX_VALUE);
  }

  /**
   * @param args Object array with indices of: [0: string], [1: separator], [2: limit]
   */
  private static ImmutableList<String> split(Object[] args) throws CelEvaluationException {
    long limitInLong = (Long) args[2];
    int limit;
    try {
      limit = Math.toIntExact(limitInLong);
    } catch (ArithmeticException e) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "split failure: Limit must not exceed the int32 range: %d", limitInLong)
          .setCause(e)
          .build();
    }

    return split((String) args[0], (String) args[1], limit);
  }

  /** Returns an immutable list of strings split on the separator */
  private static ImmutableList<String> split(String str, String separator, int limit) {
    if (limit == 0) {
      return ImmutableList.of();
    }

    if (limit == 1) {
      return ImmutableList.of(str);
    }

    if (limit < 0) {
      limit = str.length();
    }

    if (separator.isEmpty()) {
      return explode(str, limit);
    }

    Iterable<String> splitString = Splitter.on(separator).limit(limit).split(str);
    return ImmutableList.copyOf(splitString);
  }

  /**
   * Explodes a given string up to a limit
   *
   * <p>Example 1: "a가b😁" (no limit or negative limit) -> ["a", "가", "b", "😁"]
   *
   * <p>Example 2: "a가b😁" (limit 2) -> ["a", "가", "b😁"]
   *
   * <p>This exists because neither the built-in String.split nor Guava's splitter is able to deal
   * with separating single printable characters.
   */
  private static ImmutableList<String> explode(String str, int limit) {
    ImmutableList.Builder<String> exploded = ImmutableList.builder();
    CelCodePointArray codePointArray = CelCodePointArray.fromString(str);
    if (limit > 0) {
      limit -= 1;
    }
    int charCount = min(codePointArray.length(), limit);
    for (int i = 0; i < charCount; i++) {
      exploded.add(codePointArray.slice(i, i + 1).toString());
    }
    if (codePointArray.length() > limit) {
      exploded.add(codePointArray.slice(limit, codePointArray.length()).toString());
    }
    return exploded.build();
  }

  private static Object substring(String s, long i) throws CelEvaluationException {
    int beginIndex;
    try {
      beginIndex = Math.toIntExact(i);
    } catch (ArithmeticException e) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "substring failure: Index must not exceed the int32 range: %d", i)
          .setCause(e)
          .build();
    }

    CelCodePointArray codePointArray = CelCodePointArray.fromString(s);

    boolean indexIsInRange = beginIndex <= codePointArray.length() && beginIndex >= 0;
    if (!indexIsInRange) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "substring failure: Range [%d, %d) out of bounds",
              beginIndex, codePointArray.length())
          .build();
    }

    if (beginIndex == codePointArray.length()) {
      return "";
    }

    return codePointArray.slice(beginIndex, codePointArray.length()).toString();
  }

  /**
   * @param args Object array with indices of [0: string], [1: beginIndex], [2: endIndex]
   */
  private static String substring(Object[] args) throws CelEvaluationException {
    Long beginIndexInLong = (Long) args[1];
    Long endIndexInLong = (Long) args[2];
    int beginIndex;
    int endIndex;
    try {
      beginIndex = Math.toIntExact(beginIndexInLong);
      endIndex = Math.toIntExact(endIndexInLong);
    } catch (ArithmeticException e) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "substring failure: Indices must not exceed the int32 range: [%d, %d)",
              beginIndexInLong, endIndexInLong)
          .setCause(e)
          .build();
    }

    String s = (String) args[0];
    CelCodePointArray codePointArray = CelCodePointArray.fromString(s);

    boolean indicesIsInRange =
        beginIndex <= endIndex
            && beginIndex >= 0
            && beginIndex <= codePointArray.length()
            && endIndex <= codePointArray.length();
    if (!indicesIsInRange) {
      throw CelEvaluationExceptionBuilder.newBuilder(
              "substring failure: Range [%d, %d) out of bounds", beginIndex, endIndex)
          .build();
    }

    if (beginIndex == endIndex) {
      return "";
    }

    return codePointArray.slice(beginIndex, endIndex).toString();
  }

  private static String trim(String text) {
    CelCodePointArray textCpa = CelCodePointArray.fromString(text);
    int left = indexOfNonWhitespace(textCpa);
    if (left == textCpa.length()) {
      return "";
    }
    int right = lastIndexOfNonWhitespace(textCpa);
    return textCpa.slice(left, right + 1).toString();
  }

  /**
   * Finds the first index of the non-whitespace character found in the string. See {@link
   * #isWhitespace} for definition of a whitespace char.
   *
   * @return index of first non-whitespace character found (ex: " test " -> 0). Length of the string
   *     is returned instead if a non-whitespace character is not found.
   */
  private static int indexOfNonWhitespace(CelCodePointArray textCpa) {
    for (int i = 0; i < textCpa.length(); i++) {
      if (!isWhitespace(textCpa.get(i))) {
        return i;
      }
    }
    return textCpa.length();
  }

  /**
   * Finds the last index of the non-whitespace character found in the string. See {@link
   * #isWhitespace} for definition of a whitespace char.
   *
   * @return index of last non-whitespace character found. (ex: " test " -> 5). 0 is returned
   *     instead if a non-whitespace char is not found. -1 is returned for an empty string ("").
   */
  private static int lastIndexOfNonWhitespace(CelCodePointArray textCpa) {
    if (textCpa.isEmpty()) {
      return -1;
    }

    for (int i = textCpa.length() - 1; i >= 0; i--) {
      if (!isWhitespace(textCpa.get(i))) {
        return i;
      }
    }

    return 0;
  }

  /**
   * Checks if a provided codepoint is a whitespace according to Unicode's standard
   * (White_Space=yes).
   *
   * <p>This exists because Java's native Character.isWhitespace does not follow the Unicode's
   * standard of whitespace definition.
   *
   * <p>See <a href="https://en.wikipedia.org/wiki/Whitespace_character">link<a> for the full list.
   */
  private static boolean isWhitespace(int codePoint) {
    return (codePoint >= 0x0009 && codePoint <= 0x000D) // Control characters (TAB, LF, VT, FF, CR)
        || codePoint == 0x0020 // SPACE
        || codePoint == 0x0085 // NEL (Next Line)
        || codePoint == 0x00A0 // NBSP (No-Break Space)
        || codePoint == 0x1680 // OGHAM SPACE MARK
        || (codePoint >= 0x2000 && codePoint <= 0x200A) // EN QUAD to HAIR SPACE
        || codePoint == 0x2028 // LINE SEPARATOR
        || codePoint == 0x2029 // PARAGRAPH SEPARATOR
        || codePoint == 0x202F // NARROW NO-BREAK SPACE
        || codePoint == 0x205F // MEDIUM MATHEMATICAL SPACE
        || codePoint == 0x3000; // IDEOGRAPHIC SPACE
  }

  private static int compareCodePoints(String s1, String s2) {
    int len1 = s1.length();
    int len2 = s2.length();
    int limit = Math.min(len1, len2);
    int k = 0;
    while (k < limit) {
      int cp1 = s1.codePointAt(k);
      int cp2 = s2.codePointAt(k);
      if (cp1 != cp2) {
        return Integer.compare(cp1, cp2);
      }
      k += Character.charCount(cp1);
    }
    return Integer.compare(len1, len2);
  }
}
