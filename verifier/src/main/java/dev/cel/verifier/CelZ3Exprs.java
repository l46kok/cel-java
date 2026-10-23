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

package dev.cel.verifier;

import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Helpers for building Z3 expressions that are independent of the CEL encoding. */
public final class CelZ3Exprs {

  /**
   * Helper to construct a flattened logical OR expression to avoid deep left-leaning ASTs.
   *
   * <p>Returns {@code false} if the list is empty.
   */
  public static BoolExpr mkOrFlattened(Context ctx, BoolExpr... args) {
    return mkOrFlattened(ctx, Arrays.asList(args));
  }

  /**
   * Helper to construct a flattened logical OR expression to avoid deep left-leaning ASTs.
   *
   * <p>Returns {@code false} if the list is empty.
   */
  public static BoolExpr mkOrFlattened(Context ctx, List<BoolExpr> args) {
    // Pruning true/false constants in Java is significantly faster than building
    // larger ASTs and letting Z3 process them natively.
    List<BoolExpr> filteredArgs = new ArrayList<>();
    for (BoolExpr arg : args) {
      if (arg.isTrue()) {
        return ctx.mkTrue();
      }
      if (!arg.isFalse()) {
        filteredArgs.add(arg);
      }
    }
    if (filteredArgs.isEmpty()) {
      return ctx.mkFalse();
    }
    if (filteredArgs.size() == 1) {
      return filteredArgs.get(0);
    }
    return ctx.mkOr(filteredArgs.toArray(new BoolExpr[0]));
  }

  /**
   * Helper to construct a flattened logical AND expression to avoid deep left-leaning ASTs.
   *
   * <p>Returns {@code true} if the list is empty.
   */
  public static BoolExpr mkAndFlattened(Context ctx, BoolExpr... args) {
    return mkAndFlattened(ctx, Arrays.asList(args));
  }

  public static BoolExpr mkAndFlattened(Context ctx, List<BoolExpr> args) {
    // Pruning true/false constants in Java is significantly faster than building
    // larger ASTs and letting Z3 process them natively.
    List<BoolExpr> filteredArgs = new ArrayList<>();
    for (BoolExpr arg : args) {
      if (arg.isFalse()) {
        return ctx.mkFalse();
      }
      if (!arg.isTrue()) {
        filteredArgs.add(arg);
      }
    }
    if (filteredArgs.isEmpty()) {
      return ctx.mkTrue();
    }
    if (filteredArgs.size() == 1) {
      return filteredArgs.get(0);
    }
    return ctx.mkAnd(filteredArgs.toArray(new BoolExpr[0]));
  }

  /** Helper to construct a logical NOT expression while avoiding redundant NOT operations. */
  public static BoolExpr mkNotFlattened(Context ctx, BoolExpr arg) {
    if (arg.isTrue()) {
      return ctx.mkFalse();
    }
    if (arg.isFalse()) {
      return ctx.mkTrue();
    }
    return ctx.mkNot(arg);
  }

  /**
   * Helper to build a chain of nested ITE (If-Then-Else) conditions.
   *
   * <p>Conditions are evaluated in the order they are added. Branches with {@code isFalse()}
   * conditions are skipped, and redundant {@code ITE(condition, X, X)} creations are omitted to
   * avoid allocating dead AST paths in Z3.
   */
  public static final class SwitchBuilder {

    private static final class SwitchCase {
      private final BoolExpr condition;
      private final Expr<?> value;

      private SwitchCase(BoolExpr condition, Expr<?> value) {
        this.condition = condition;
        this.value = value;
      }
    }

    private final Context ctx;
    private final List<SwitchCase> cases;

    public static SwitchBuilder newBuilder(Context ctx) {
      return new SwitchBuilder(ctx);
    }

    @CanIgnoreReturnValue
    public SwitchBuilder addCase(BoolExpr condition, Expr<?> value) {
      // Skip branches that can never be hit (e.g. `isFalse()` probes).
      if (condition.isFalse()) {
        return this;
      }
      cases.add(new SwitchCase(condition, value));
      return this;
    }

    public Expr<?> build(Expr<?> defaultFallback) {
      Expr<?> result = defaultFallback;
      for (SwitchCase c : Lists.reverse(cases)) {
        // ITE(condition, X, X) simplifies to X; skip calling into native C++ Z3_mk_ite.
        if (c.value.equals(result)) {
          continue;
        }
        result = ctx.mkITE(c.condition, c.value, result);
      }
      return result;
    }

    private SwitchBuilder(Context ctx) {
      this.ctx = ctx;
      this.cases = new ArrayList<>();
    }
  }

  private CelZ3Exprs() {}
}
