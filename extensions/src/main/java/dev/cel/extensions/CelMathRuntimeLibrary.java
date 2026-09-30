// Copyright 2023 Google LLC
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

import static com.google.common.collect.Comparators.max;
import static com.google.common.collect.Comparators.min;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.exceptions.CelNumericOverflowException;
import dev.cel.common.internal.ComparisonFunctions;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelLiteRuntimeBuilder;
import dev.cel.runtime.CelLiteRuntimeLibrary;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Runtime implementation of CEL Math extension functions.
 *
 * <p>Note: For equal numbers with different types, the result is always the first argument e.g.:
 * math.greatest(1u, 1.0) -> 1u
 */
@SuppressWarnings({"rawtypes", "unchecked"}) // Use of raw Comparables.
@Immutable
public final class CelMathRuntimeLibrary implements CelLiteRuntimeLibrary {

  private static final String MATH_MAX_FUNCTION = "math.@max";
  private static final String MATH_MIN_FUNCTION = "math.@min";

  // Rounding Functions
  private static final String MATH_CEIL_FUNCTION = "math.ceil";
  private static final String MATH_FLOOR_FUNCTION = "math.floor";
  private static final String MATH_ROUND_FUNCTION = "math.round";
  private static final String MATH_TRUNC_FUNCTION = "math.trunc";

  // Floating Point Functions
  private static final String MATH_ISFINITE_FUNCTION = "math.isFinite";
  private static final String MATH_ISNAN_FUNCTION = "math.isNaN";
  private static final String MATH_ISINF_FUNCTION = "math.isInf";

  // Signedness Functions
  private static final String MATH_ABS_FUNCTION = "math.abs";
  private static final String MATH_SIGN_FUNCTION = "math.sign";

  // Bitwise Functions
  private static final String MATH_BIT_AND_FUNCTION = "math.bitAnd";
  private static final String MATH_BIT_OR_FUNCTION = "math.bitOr";
  private static final String MATH_BIT_XOR_FUNCTION = "math.bitXor";
  private static final String MATH_BIT_NOT_FUNCTION = "math.bitNot";
  private static final String MATH_BIT_LEFT_SHIFT_FUNCTION = "math.bitShiftLeft";
  private static final String MATH_BIT_RIGHT_SHIFT_FUNCTION = "math.bitShiftRight";

  private static final String MATH_SQRT_FUNCTION = "math.sqrt";

  private static final int MAX_BIT_SHIFT = 63;

  /**
   * Returns the proper comparison function to use for a math function call involving different
   * argument types.
   *
   * <p>Example: (uint, int) -> {@link ComparisonFunctions#compareUintInt(UnsignedLong, long)}
   */
  private static final ImmutableTable<Class, Class, BiFunction<Object, Object, Integer>>
      CLASSES_TO_COMPARATORS = newComparatorTable();

  private static ImmutableTable<Class, Class, BiFunction<Object, Object, Integer>>
      newComparatorTable() {
    return ImmutableTable.<Class, Class, BiFunction<Object, Object, Integer>>builder()
        .put(
            Long.class,
            Double.class,
            (x, y) -> ComparisonFunctions.compareIntDouble((Long) x, (Double) y))
        .put(
            Double.class,
            Long.class,
            (x, y) -> ComparisonFunctions.compareDoubleInt((Double) x, (Long) y))
        .put(
            Double.class,
            UnsignedLong.class,
            (x, y) -> ComparisonFunctions.compareDoubleUint((Double) x, (UnsignedLong) y))
        .put(
            UnsignedLong.class,
            Double.class,
            (x, y) -> ComparisonFunctions.compareUintDouble((UnsignedLong) x, (Double) y))
        .put(
            Long.class,
            UnsignedLong.class,
            (x, y) -> ComparisonFunctions.compareIntUint((Long) x, (UnsignedLong) y))
        .put(
            UnsignedLong.class,
            Long.class,
            (x, y) -> ComparisonFunctions.compareUintInt((UnsignedLong) x, (Long) y))
        .buildOrThrow();
  }

  /** Enumeration of runtime function bindings for the Math extension. */
  public enum Function {
    MAX(
        MATH_MAX_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from("math_@max_double", Double.class, x -> x),
            CelFunctionBinding.from("math_@max_int", Long.class, x -> x),
            CelFunctionBinding.from(
                "math_@max_double_double",
                Double.class,
                Double.class,
                CelMathRuntimeLibrary::maxPair),
            CelFunctionBinding.from(
                "math_@max_int_int", Long.class, Long.class, CelMathRuntimeLibrary::maxPair),
            CelFunctionBinding.from(
                "math_@max_int_double", Long.class, Double.class, CelMathRuntimeLibrary::maxPair),
            CelFunctionBinding.from(
                "math_@max_double_int", Double.class, Long.class, CelMathRuntimeLibrary::maxPair),
            CelFunctionBinding.from(
                "math_@max_list_dyn", List.class, CelMathRuntimeLibrary::maxList),
            CelFunctionBinding.from("math_@max_uint", UnsignedLong.class, x -> x),
            CelFunctionBinding.from(
                "math_@max_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathRuntimeLibrary::maxPair),
            CelFunctionBinding.from(
                "math_@max_double_uint",
                Double.class,
                UnsignedLong.class,
                CelMathRuntimeLibrary::maxPair),
            CelFunctionBinding.from(
                "math_@max_uint_int",
                UnsignedLong.class,
                Long.class,
                CelMathRuntimeLibrary::maxPair),
            CelFunctionBinding.from(
                "math_@max_uint_double",
                UnsignedLong.class,
                Double.class,
                CelMathRuntimeLibrary::maxPair),
            CelFunctionBinding.from(
                "math_@max_int_uint",
                Long.class,
                UnsignedLong.class,
                CelMathRuntimeLibrary::maxPair))),
    MIN(
        MATH_MIN_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from("math_@min_double", Double.class, x -> x),
            CelFunctionBinding.from("math_@min_int", Long.class, x -> x),
            CelFunctionBinding.from(
                "math_@min_double_double",
                Double.class,
                Double.class,
                CelMathRuntimeLibrary::minPair),
            CelFunctionBinding.from(
                "math_@min_int_int", Long.class, Long.class, CelMathRuntimeLibrary::minPair),
            CelFunctionBinding.from(
                "math_@min_int_double", Long.class, Double.class, CelMathRuntimeLibrary::minPair),
            CelFunctionBinding.from(
                "math_@min_double_int", Double.class, Long.class, CelMathRuntimeLibrary::minPair),
            CelFunctionBinding.from(
                "math_@min_list_dyn", List.class, CelMathRuntimeLibrary::minList),
            CelFunctionBinding.from("math_@min_uint", UnsignedLong.class, x -> x),
            CelFunctionBinding.from(
                "math_@min_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathRuntimeLibrary::minPair),
            CelFunctionBinding.from(
                "math_@min_double_uint",
                Double.class,
                UnsignedLong.class,
                CelMathRuntimeLibrary::minPair),
            CelFunctionBinding.from(
                "math_@min_uint_int",
                UnsignedLong.class,
                Long.class,
                CelMathRuntimeLibrary::minPair),
            CelFunctionBinding.from(
                "math_@min_uint_double",
                UnsignedLong.class,
                Double.class,
                CelMathRuntimeLibrary::minPair),
            CelFunctionBinding.from(
                "math_@min_int_uint",
                Long.class,
                UnsignedLong.class,
                CelMathRuntimeLibrary::minPair))),
    CEIL(
        MATH_CEIL_FUNCTION,
        ImmutableSet.of(CelFunctionBinding.from("math_ceil_double", Double.class, Math::ceil))),
    FLOOR(
        MATH_FLOOR_FUNCTION,
        ImmutableSet.of(CelFunctionBinding.from("math_floor_double", Double.class, Math::floor))),
    ROUND(
        MATH_ROUND_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_round_double", Double.class, CelMathRuntimeLibrary::round))),
    TRUNC(
        MATH_TRUNC_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_trunc_double", Double.class, CelMathRuntimeLibrary::trunc))),
    ISFINITE(
        MATH_ISFINITE_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from("math_isFinite_double", Double.class, Double::isFinite))),
    ISNAN(
        MATH_ISNAN_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_isNaN_double", Double.class, CelMathRuntimeLibrary::isNaN))),
    ISINF(
        MATH_ISINF_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_isInf_double", Double.class, CelMathRuntimeLibrary::isInfinite))),
    ABS(
        MATH_ABS_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from("math_abs_double", Double.class, Math::abs),
            CelFunctionBinding.from("math_abs_int", Long.class, CelMathRuntimeLibrary::absExact),
            CelFunctionBinding.from("math_abs_uint", UnsignedLong.class, x -> x))),
    SIGN(
        MATH_SIGN_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from("math_sign_double", Double.class, CelMathRuntimeLibrary::sign),
            CelFunctionBinding.from("math_sign_int", Long.class, CelMathRuntimeLibrary::sign),
            CelFunctionBinding.from(
                "math_sign_uint", UnsignedLong.class, CelMathRuntimeLibrary::sign))),
    BITAND(
        MATH_BIT_AND_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitAnd_int_int", Long.class, Long.class, CelMathRuntimeLibrary::intBitAnd),
            CelFunctionBinding.from(
                "math_bitAnd_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathRuntimeLibrary::uintBitAnd))),
    BITOR(
        MATH_BIT_OR_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitOr_int_int", Long.class, Long.class, CelMathRuntimeLibrary::intBitOr),
            CelFunctionBinding.from(
                "math_bitOr_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathRuntimeLibrary::uintBitOr))),
    BITXOR(
        MATH_BIT_XOR_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitXor_int_int", Long.class, Long.class, CelMathRuntimeLibrary::intBitXor),
            CelFunctionBinding.from(
                "math_bitXor_uint_uint",
                UnsignedLong.class,
                UnsignedLong.class,
                CelMathRuntimeLibrary::uintBitXor))),
    BITNOT(
        MATH_BIT_NOT_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitNot_int_int", Long.class, CelMathRuntimeLibrary::intBitNot),
            CelFunctionBinding.from(
                "math_bitNot_uint_uint", UnsignedLong.class, CelMathRuntimeLibrary::uintBitNot))),
    BITSHIFTLEFT(
        MATH_BIT_LEFT_SHIFT_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitShiftLeft_int_int",
                Long.class,
                Long.class,
                CelMathRuntimeLibrary::intBitShiftLeft),
            CelFunctionBinding.from(
                "math_bitShiftLeft_uint_int",
                UnsignedLong.class,
                Long.class,
                CelMathRuntimeLibrary::uintBitShiftLeft))),
    BITSHIFTRIGHT(
        MATH_BIT_RIGHT_SHIFT_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_bitShiftRight_int_int",
                Long.class,
                Long.class,
                CelMathRuntimeLibrary::intBitShiftRight),
            CelFunctionBinding.from(
                "math_bitShiftRight_uint_int",
                UnsignedLong.class,
                Long.class,
                CelMathRuntimeLibrary::uintBitShiftRight))),
    SQRT(
        MATH_SQRT_FUNCTION,
        ImmutableSet.of(
            CelFunctionBinding.from(
                "math_sqrt_double", Double.class, CelMathRuntimeLibrary::sqrtDouble),
            CelFunctionBinding.from("math_sqrt_int", Long.class, CelMathRuntimeLibrary::sqrtInt),
            CelFunctionBinding.from(
                "math_sqrt_uint", UnsignedLong.class, CelMathRuntimeLibrary::sqrtUint)));

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

  private static final CelMathRuntimeLibrary VERSION_0 =
      new CelMathRuntimeLibrary(ImmutableSet.of(Function.MIN, Function.MAX));

  private static final CelMathRuntimeLibrary VERSION_1 =
      new CelMathRuntimeLibrary(
          ImmutableSet.<Function>builder()
              .addAll(VERSION_0.functions)
              .add(
                  Function.CEIL,
                  Function.FLOOR,
                  Function.ROUND,
                  Function.TRUNC,
                  Function.ISINF,
                  Function.ISNAN,
                  Function.ISFINITE,
                  Function.ABS,
                  Function.SIGN,
                  Function.BITAND,
                  Function.BITOR,
                  Function.BITXOR,
                  Function.BITNOT,
                  Function.BITSHIFTLEFT,
                  Function.BITSHIFTRIGHT)
              .build());

  private static final CelMathRuntimeLibrary VERSION_2 =
      new CelMathRuntimeLibrary(
          ImmutableSet.<Function>builder().addAll(VERSION_1.functions).add(Function.SQRT).build());

  /** Returns the latest version of the 'math' runtime functions. */
  public static CelMathRuntimeLibrary math() {
    return VERSION_2;
  }

  /** Returns the specified version of the 'math' runtime functions. */
  public static CelMathRuntimeLibrary math(int version) {
    switch (version) {
      case 0:
        return VERSION_0;
      case 1:
        return VERSION_1;
      case 2:
      case Integer.MAX_VALUE:
        return VERSION_2;
      default:
        throw new IllegalArgumentException("Unsupported 'math' extension version " + version);
    }
  }

  /** Returns the 'math' runtime functions with only the specified functions. */
  public static CelMathRuntimeLibrary math(Function... functions) {
    return math(ImmutableSet.copyOf(functions));
  }

  /** Returns the 'math' runtime functions with only the specified functions. */
  public static CelMathRuntimeLibrary math(Set<Function> functions) {
    return new CelMathRuntimeLibrary(functions);
  }

  private final ImmutableSet<Function> functions;

  CelMathRuntimeLibrary(Set<Function> functions) {
    this.functions = ImmutableSet.copyOf(functions);
  }

  @Override
  public void setRuntimeOptions(CelLiteRuntimeBuilder runtimeBuilder) {
    runtimeBuilder.addFunctionBindings(newFunctionBindings());
  }

  /** Creates the {@link CelFunctionBinding}s for the configured math functions. */
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

  private static Comparable maxPair(Comparable x, Comparable y) {
    if (x.getClass().equals(y.getClass())) {
      return max(x, y);
    }

    return CLASSES_TO_COMPARATORS.get(x.getClass(), y.getClass()).apply(x, y) >= 0 ? x : y;
  }

  private static Comparable maxList(List<Comparable> list) {
    Preconditions.checkArgument(!list.isEmpty(), "math.@max(list) argument must not be empty");

    Comparable max = list.get(0);
    for (int i = 1; i < list.size(); i++) {
      max = maxPair(max, list.get(i));
    }

    return max;
  }

  private static Comparable minPair(Comparable x, Comparable y) {
    if (x.getClass().equals(y.getClass())) {
      return min(x, y);
    }

    return CLASSES_TO_COMPARATORS.get(x.getClass(), y.getClass()).apply(x, y) <= 0 ? x : y;
  }

  private static long absExact(long x) {
    if (x == Long.MIN_VALUE) {
      // The only case where standard Math.abs overflows silently
      throw new CelNumericOverflowException("integer overflow");
    }
    return Math.abs(x);
  }

  private static boolean isNaN(double x) {
    return Double.isNaN(x);
  }

  private static Double trunc(Double x) {
    if (isNaN(x) || isInfinite(x)) {
      return x;
    }
    return (double) x.longValue();
  }

  private static boolean isInfinite(double x) {
    return Double.isInfinite(x);
  }

  private static double round(double x) {
    if (isNaN(x) || isInfinite(x)) {
      return x;
    }
    return DoubleMath.roundToLong(x, RoundingMode.HALF_UP);
  }

  private static Number sign(Number x) {
    if (x instanceof Double) {
      double val = x.doubleValue();
      if (isNaN(val)) {
        return val;
      }
      if (val == 0) {
        return 0.0;
      }
      return val > 0 ? 1.0 : -1.0;
    }

    if (x instanceof Long) {
      long val = x.longValue();
      if (val == 0) {
        return 0L;
      }
      return val > 0 ? 1L : -1L;
    }

    if (x instanceof UnsignedLong) {
      UnsignedLong val = (UnsignedLong) x;
      if (val.equals(UnsignedLong.ZERO)) {
        return val;
      }
      return UnsignedLong.ONE;
    }

    throw new IllegalArgumentException("Unsupported type: " + x.getClass());
  }

  private static Long intBitAnd(long x, long y) {
    return x & y;
  }

  private static UnsignedLong uintBitAnd(UnsignedLong x, UnsignedLong y) {
    return UnsignedLong.fromLongBits(x.longValue() & y.longValue());
  }

  private static Long intBitOr(long x, long y) {
    return x | y;
  }

  private static UnsignedLong uintBitOr(UnsignedLong x, UnsignedLong y) {
    return UnsignedLong.fromLongBits(x.longValue() | y.longValue());
  }

  private static Long intBitXor(long x, long y) {
    return x ^ y;
  }

  private static UnsignedLong uintBitXor(UnsignedLong x, UnsignedLong y) {
    return UnsignedLong.fromLongBits(x.longValue() ^ y.longValue());
  }

  private static Long intBitNot(long x) {
    return ~x;
  }

  private static UnsignedLong uintBitNot(UnsignedLong x) {
    return UnsignedLong.fromLongBits(~x.longValue());
  }

  private static Long intBitShiftLeft(long value, long shiftAmount) {
    if (shiftAmount < 0) {
      throw new IllegalArgumentException("math.bitShiftLeft() negative offset:" + shiftAmount);
    }

    if (shiftAmount > MAX_BIT_SHIFT) {
      return 0L;
    }
    return value << shiftAmount;
  }

  private static UnsignedLong uintBitShiftLeft(UnsignedLong value, long shiftAmount) {
    if (shiftAmount < 0) {
      throw new IllegalArgumentException("math.bitShiftLeft() negative offset:" + shiftAmount);
    }

    if (shiftAmount > MAX_BIT_SHIFT) {
      return UnsignedLong.ZERO;
    }
    return UnsignedLong.fromLongBits(value.longValue() << shiftAmount);
  }

  private static Long intBitShiftRight(long value, long shiftAmount) {
    if (shiftAmount < 0) {
      throw new IllegalArgumentException("math.bitShiftRight() negative offset:" + shiftAmount);
    }

    if (shiftAmount > MAX_BIT_SHIFT) {
      return 0L;
    }
    return value >>> shiftAmount;
  }

  private static UnsignedLong uintBitShiftRight(UnsignedLong value, long shiftAmount) {
    if (shiftAmount < 0) {
      throw new IllegalArgumentException("math.bitShiftRight() negative offset:" + shiftAmount);
    }

    if (shiftAmount > MAX_BIT_SHIFT) {
      return UnsignedLong.ZERO;
    }
    return UnsignedLong.fromLongBits(value.longValue() >>> shiftAmount);
  }

  private static Double sqrtDouble(double x) {
    return Math.sqrt(x);
  }

  private static Double sqrtInt(Long x) {
    return sqrtDouble(x.doubleValue());
  }

  private static Double sqrtUint(UnsignedLong x) {
    return sqrtDouble(x.doubleValue());
  }

  private static Comparable minList(List<Comparable> list) {
    Preconditions.checkArgument(!list.isEmpty(), "math.@min(list) argument must not be empty");

    Comparable min = list.get(0);
    for (int i = 1; i < list.size(); i++) {
      min = minPair(min, list.get(i));
    }

    return min;
  }
}
