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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelMutableAst;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelSource;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.navigation.CelNavigableMutableAst;
import dev.cel.common.types.CelType;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.extensions.CelOptionalLibrary.Function;
import dev.cel.optimizer.AstMutator;
import dev.cel.optimizer.CelAstOptimizer;
import dev.cel.parser.Operator;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch.OutputValue;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch.Result.Kind;
import dev.cel.policy.CelCompiledRule.CelCompiledVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toCollection;

/** Package-private class for composing various rules into a single expression using optimizer. */
final class RuleComposer implements CelAstOptimizer {
  private final CelCompiledRule compiledRule;
  private final String variablePrefix;
  private final AstMutator astMutator;
  private final boolean enableCelBlock;

  @Override
  public OptimizationResult optimize(CelAbstractSyntaxTree ast, Cel cel) {
    CelMutableAst mutableOptimizedAst = optimizeRule(cel, compiledRule);
    if (enableCelBlock) {
      ImmutableList<CelCompiledVariable> policyVariables = collectAllPolicyVariables(compiledRule);
      ImmutableList.Builder<CelVarDecl> newBlockIndexDecls = ImmutableList.builder();
      ImmutableList.Builder<CelMutableAst> subexpressions = ImmutableList.builder();
      HashMap<String, String> replacedVars = new HashMap<>();
      for (int i = 0; i < policyVariables.size(); i++) {
        String blockIdentifier = "@index" + i;
        CelCompiledVariable variable = policyVariables.get(i);
        String policyVariableName = variablePrefix + variable.name();
        CelMutableAst varMutableAst = CelMutableAst.fromCelAst(variable.ast());
        replacedVars.put(policyVariableName, blockIdentifier);

        // Replace index in subexpression
        CelNavigableMutableAst.fromAst(varMutableAst).getRoot().allNodes()
                .filter(node -> node.getKind().equals(CelExpr.ExprKind.Kind.IDENT))
                .filter(node -> replacedVars.containsKey(node.expr().ident().name()))
                .forEach(policyVar -> policyVar.expr().ident().setName(replacedVars.get(policyVar.expr().ident().name())));

        // Replace index in main ast
        CelNavigableMutableAst.fromAst(mutableOptimizedAst).getRoot().allNodes()
                .filter(node -> node.getKind().equals(CelExpr.ExprKind.Kind.IDENT))
                .filter(node -> node.expr().ident().name().equals(policyVariableName))
                .forEach(policyVar -> policyVar.expr().ident().setName(blockIdentifier));

        subexpressions.add(CelMutableAst.fromCelAst(varMutableAst.toParsedAst()));

        // TODO: Typecheck subexpressions
        newBlockIndexDecls.add(CelVarDecl.newVarDeclaration(blockIdentifier, SimpleType.DYN));
      }

      mutableOptimizedAst = astMutator.wrapAstWithNewCelBlock(mutableOptimizedAst, subexpressions.build());

      CelAbstractSyntaxTree optimizedAst = mutableOptimizedAst.toParsedAst();
      return OptimizationResult.create(optimizedAst, newBlockIndexDecls.build(), ImmutableList.of(newCelBlockFunctionDecl(optimizedAst.getResultType())));
    } else {
      return OptimizationResult.create(mutableOptimizedAst.toParsedAst());
    }
  }

  private ImmutableList<CelCompiledVariable> collectAllPolicyVariables(CelCompiledRule compiledRule) {
    ImmutableList.Builder<CelCompiledVariable> builder = ImmutableList.builder();
    builder.addAll(compiledRule.variables());
    for (CelCompiledMatch match : compiledRule.matches()) {
      if (match.result().kind().equals(CelCompiledMatch.Result.Kind.RULE)) {
        builder.addAll(collectAllPolicyVariables(match.result().rule()));
      }
    }

    return builder.build();
  }

  private CelMutableAst optimizeRule(Cel cel, CelCompiledRule compiledRule) {
    cel =
        cel.toCelBuilder()
            .addVarDeclarations(
                compiledRule.variables().stream()
                    .map(CelCompiledVariable::celVarDecl)
                    .collect(toImmutableList()))
            .build();

    CelMutableAst matchAst = astMutator.newGlobalCall(Function.OPTIONAL_NONE.getFunction());
    boolean isOptionalResult = true;
    // Keep track of the last output ID that might cause type-check failure while attempting to
    // compose the subgraphs.
    long lastOutputId = 0;
    for (CelCompiledMatch match : Lists.reverse(compiledRule.matches())) {
      CelAbstractSyntaxTree conditionAst = match.condition();
      // If the condition is trivially true, none of the matches in the rule causes the result
      // to become optional, and the rule is not the last match, then this will introduce
      // unreachable outputs or rules.
      boolean isTriviallyTrue = match.isConditionTriviallyTrue();

      switch (match.result().kind()) {
        // For the match's output, determine whether the output should be wrapped
        // into an optional value, a conditional, or both.
        case OUTPUT:
          OutputValue matchOutput = match.result().output();
          CelMutableAst outAst = CelMutableAst.fromCelAst(matchOutput.ast());
          if (isTriviallyTrue) {
            matchAst = outAst;
            isOptionalResult = false;
            lastOutputId = matchOutput.sourceId();
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
          assertComposedAstIsValid(
              cel,
              matchAst,
              "conflicting output types found.",
              matchOutput.sourceId(),
              lastOutputId);
          lastOutputId = matchOutput.sourceId();
          continue;
        case RULE:
          // If the match has a nested rule, then compute the rule and whether it has
          // an optional return value.
          CelCompiledRule matchNestedRule = match.result().rule();
          CelMutableAst nestedRuleAst = optimizeRule(cel, matchNestedRule);
          boolean nestedHasOptional = matchNestedRule.hasOptionalOutput();
          if (isOptionalResult && !nestedHasOptional) {
            nestedRuleAst =
                astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), nestedRuleAst);
          }
          if (!isOptionalResult && nestedHasOptional) {
            matchAst = astMutator.newGlobalCall(Function.OPTIONAL_OF.getFunction(), matchAst);
            isOptionalResult = true;
          }
          // If either the nested rule or current condition output are optional then
          // use optional.or() to specify the combination of the first and second results
          // Note, the argument order is reversed due to the traversal of matches in
          // reverse order.
          if (isOptionalResult && isTriviallyTrue) {
            matchAst = astMutator.newMemberCall(nestedRuleAst, Function.OR.getFunction(), matchAst);
          } else {
            matchAst =
                astMutator.newGlobalCall(
                    Operator.CONDITIONAL.getFunction(),
                    CelMutableAst.fromCelAst(conditionAst),
                    nestedRuleAst,
                    matchAst);
          }

          // assertComposedAstIsValid(
          //     cel,
          //     matchAst,
          //     String.format(
          //         "failed composing the subrule '%s' due to conflicting output types.",
          //         matchNestedRule.ruleId().map(ValueString::value).orElse("")),
          //     lastOutputId);

          assertComposedAstResultTypesAgree(compiledRule);
          break;
      }
    }

    CelMutableAst result = matchAst;
    if (!enableCelBlock) {
        for (CelCompiledVariable variable : Lists.reverse(compiledRule.variables())) {
          String policyVariableName = variablePrefix + variable.name();
          result =
                  astMutator.replaceSubtreeWithNewBindMacro(
                          result,
                          policyVariableName,
                          CelMutableAst.fromCelAst(variable.ast()),
                          result.expr(),
                          result.expr().id(),
                          true);
      }
    }

    result = astMutator.renumberIdsConsecutively(result);

    return result;
  }

  private static CelFunctionDecl newCelBlockFunctionDecl(CelType resultType) {
    return CelFunctionDecl.newFunctionDeclaration(
            "cel.@block",
            CelOverloadDecl.newGlobalOverload(
                    "cel_block_list", resultType, ListType.create(SimpleType.DYN), resultType));
  }

  static RuleComposer newInstance(
      CelCompiledRule compiledRule, String variablePrefix, int iterationLimit, boolean enableCelBlock) {
    return new RuleComposer(compiledRule, variablePrefix, iterationLimit, enableCelBlock);
  }

  private void assertComposedAstIsValid(
      Cel cel, CelMutableAst composedAst, String failureMessage, Long... ids) {
    assertComposedAstIsValid(cel, composedAst, failureMessage, Arrays.asList(ids));
  }

  private void assertComposedAstIsValid(
      Cel cel, CelMutableAst composedAst, String failureMessage, List<Long> ids) {
    try {
      cel.check(composedAst.toParsedAst()).getAst();
    } catch (CelValidationException e) {
      ids = ids.stream().filter(id -> id > 0).collect(toCollection(ArrayList::new));
      throw new RuleCompositionException(failureMessage, e, ids);
    }
  }

  private void assertComposedAstResultTypesAgree(CelCompiledRule rule) {
    CelType expectedOutputType = null;
    for (CelCompiledMatch match : rule.matches()) {
      if (!match.result().kind().equals(Kind.OUTPUT)) {
        continue;
      }

      OutputValue matchOutput = match.result().output();
      if (expectedOutputType == null) {
        expectedOutputType = matchOutput.ast().getResultType();
        continue;
      }

      CelType matchOutputType = matchOutput.ast().getResultType();
      // Handle assignability as the output type is assignable to the match output or vice versa.
      // During composition, this is roughly how the type-checker will handle the type agreement check.
      boolean outputTypesAgree = matchOutputType.isAssignableFrom(expectedOutputType) || expectedOutputType.isAssignableFrom(matchOutputType);
      if (!outputTypesAgree) {
        throw new RuleCompositionException("Incompatible output types");
      }
    }
  }

  private RuleComposer(CelCompiledRule compiledRule, String variablePrefix, int iterationLimit, boolean enableCelBlock) {
    this.compiledRule = checkNotNull(compiledRule);
    this.variablePrefix = variablePrefix;
    this.astMutator = AstMutator.newInstance(iterationLimit);
    this.enableCelBlock = enableCelBlock;
  }

  static final class RuleCompositionException extends RuntimeException {
    final String failureReason;
    final List<Long> errorIds;
    final CelValidationException compileException;

    private RuleCompositionException(String failureReason) {
      this.failureReason = failureReason;
      this.errorIds = ImmutableList.of();
      this.compileException = new CelValidationException(CelSource.newBuilder().build(), ImmutableList.of());
    }

    private RuleCompositionException(
        String failureReason, CelValidationException e, List<Long> errorIds) {
      super(e);
      this.failureReason = failureReason;
      this.errorIds = errorIds;
      this.compileException = e;
    }
  }
}
