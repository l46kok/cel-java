// Copyright 2024 Google LLC
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

package dev.cel.policy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelMutableAst;
import dev.cel.common.CelValidationException;
import dev.cel.common.ast.CelConstant.Kind;
import dev.cel.extensions.CelOptionalLibrary.Function;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.parser.CelUnparserFactory;
import dev.cel.parser.Operator;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch.OutputValue;
import dev.cel.policy.CelCompiledRule.CelCompiledVariable;
import java.util.Arrays;
import java.util.List;

/** Package-private class for composing various rules into a single expression using optimizer. */
final class RuleComposer implements CelAstOptimizer {
  private static final int AST_MUTATOR_ITERATION_LIMIT = 1000;
  private final CelCompiledRule compiledRule;
  private final String variablePrefix;
  private final AstMutator astMutator;

  @Override
  public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) {
    RuleOptimizationResult result = optimizeRule(cel, compiledRule);
    return OptimizationResult.create(result.ast().toParsedAst());
  }

  @AutoValue
  abstract static class RuleOptimizationResult {
    abstract CelMutableAst ast();

    abstract Boolean isOptionalResult();

    static RuleOptimizationResult create(CelMutableAst ast, boolean isOptionalResult) {
      return new AutoValue_RuleComposer_RuleOptimizationResult(ast, isOptionalResult);
    }
  }

  private RuleOptimizationResult optimizeRule(Cel cel, CelCompiledRule compiledRule)
      throws RuleCompositionException {
    cel = cel
        .toCelBuilder()
        .addVarDeclarations(compiledRule.variables().stream()
            .map(CelCompiledVariable::celVarDecl)
            .collect(toImmutableList())).build();

    CelMutableAst matchAst = astMutator.newGlobalCall(Function.OPTIONAL_NONE.getFunction());
    boolean isOptionalResult = true;
    long lastOutputId = 0;
    for (CelCompiledMatch match : Lists.reverse(compiledRule.matches())) {
      CelAbstractSyntaxTree conditionAst = match.condition();
      boolean isTriviallyTrue =
          conditionAst.getExpr().constantOrDefault().getKind().equals(Kind.BOOLEAN_VALUE)
              && conditionAst.getExpr().constant().booleanValue();
      switch (match.result().kind()) {
        case OUTPUT:
          OutputValue matchOutput = match.result().output();
          CelMutableAst outAst = CelMutableAst.fromCelAst(matchOutput.ast());
          if (isTriviallyTrue) {
            matchAst = outAst;
            isOptionalResult = false;
            lastOutputId = matchOutput.id();
            continue;
          }
          if (isOptionalResult) {
            outAst = astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), outAst);
          }

          matchAst =
              astMutator.newGlobalCall(
                  Operator.CONDITIONAL.getFunction(),
                  CelMutableAst.fromCelAst(conditionAst),
                  outAst,
                  matchAst);
          assertComposedAstIsValid(cel, matchAst, "conflicting output types found.", matchOutput.id(), lastOutputId);
          lastOutputId = matchOutput.id();
          continue;
        case RULE:
          RuleOptimizationResult nestedRule = optimizeRule(cel, match.result().rule());
          CelMutableAst nestedRuleAst = nestedRule.ast();
          if (isOptionalResult && !nestedRule.isOptionalResult()) {
            nestedRuleAst =
                astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), nestedRuleAst);
          }
          if (!isOptionalResult && nestedRule.isOptionalResult()) {
            matchAst = astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), matchAst);
            isOptionalResult = true;
          }
          if (!isOptionalResult && !nestedRule.isOptionalResult()) {
            throw new IllegalArgumentException("Subrule early terminates policy");
          }
          matchAst = astMutator.newMemberCall(nestedRuleAst, Function.OR.getFunction(), matchAst);
          assertComposedAstIsValid(cel, matchAst, "failed composing the subrule.", lastOutputId);
          break;
      }
    }

    CelMutableAst result = matchAst;
    for (CelCompiledVariable variable : Lists.reverse(compiledRule.variables())) {
      result =
          astMutator.replaceSubtreeWithNewBindMacro(
              result,
              variablePrefix + variable.name(),
              CelMutableAst.fromCelAst(variable.ast()),
              result.expr(),
              result.expr().id(),
              true);
    }

    result = astMutator.renumberIdsConsecutively(result);

    return RuleOptimizationResult.create(result, isOptionalResult);
  }

  private void assertComposedAstIsValid(Cel cel, CelMutableAst composedAst, String failureMessage, Long... ids) {
    try {
      cel.check(composedAst.toParsedAst()).getAst();
    } catch (CelValidationException e) {
      String unparsed = CelUnparserFactory.newUnparser().unparse(composedAst.toParsedAst());
      System.out.println(unparsed);
      throw new RuleCompositionException(failureMessage, e, ids);
    }
  }

  static RuleComposer newInstance(CelCompiledRule compiledRule, String variablePrefix) {
    return new RuleComposer(compiledRule, variablePrefix);
  }

  private RuleComposer(CelCompiledRule compiledRule, String variablePrefix) {
    this.compiledRule = checkNotNull(compiledRule);
    this.variablePrefix = variablePrefix;
    this.astMutator = AstMutator.newInstance(AST_MUTATOR_ITERATION_LIMIT);
  }

  static final class RuleCompositionException extends RuntimeException {
    final String failureReason;
    final List<Long> errorIds;
    final CelValidationException compileException;

    private RuleCompositionException(String failureReason, CelValidationException e, Long... errorIds) {
      super(e);
      this.failureReason = failureReason;
      this.errorIds = Arrays.asList(errorIds);
      this.compileException = e;
    }
  }
}
