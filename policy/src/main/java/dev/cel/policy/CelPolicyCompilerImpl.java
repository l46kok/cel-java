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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.cel.bundle.Cel;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelIssue;
import dev.cel.common.CelSource;
import dev.cel.common.CelSourceLocation;
import dev.cel.common.CelValidationException;
import dev.cel.common.CelVarDecl;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import dev.cel.optimizer.CelOptimizationException;
import dev.cel.optimizer.CelOptimizer;
import dev.cel.optimizer.CelOptimizerFactory;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch;
import dev.cel.policy.CelCompiledRule.CelCompiledMatch.Result;
import dev.cel.policy.CelCompiledRule.CelCompiledVariable;
import dev.cel.policy.CelPolicy.Match;
import dev.cel.policy.CelPolicy.Variable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Package-private implementation for policy compiler. */
final class CelPolicyCompilerImpl implements CelPolicyCompiler {
  private static final String DEFAULT_VARIABLE_PREFIX = "variables.";
  private final Cel cel;
  private final String variablesPrefix;

  @Override
  public CelAbstractSyntaxTree compile(CelPolicy policy) throws CelPolicyValidationException {
    CompilerContext compilerContext = new CompilerContext(policy.policySource());
    CelCompiledRule compiledRule = compileRule(policy.rule(), cel, compilerContext);
    if (compilerContext.hasError()) {
      throw new CelPolicyValidationException(compilerContext.getIssueString());
    }

    CelOptimizer optimizer =
        CelOptimizerFactory.standardCelOptimizerBuilder(compiledRule.cel())
            .addAstOptimizers(
                RuleComposer.newInstance(
                    compiledRule, compilerContext.newVariableDeclarations, variablesPrefix))
            .build();

    CelAbstractSyntaxTree ast;

    try {
      ast = cel.compile("true").getAst();
      ast = optimizer.optimize(ast);
    } catch (CelValidationException | CelOptimizationException e) {
      // TODO: Surface these errors better
      throw new CelPolicyValidationException("Failed composing the rules", e);
    }

    return ast;
  }

  private CelCompiledRule compileRule(
      CelPolicy.Rule rule, Cel ruleCel, CompilerContext compilerContext) {
    ImmutableList.Builder<CelCompiledVariable> variableBuilder = ImmutableList.builder();
    for (Variable variable : rule.variables()) {
      ValueString expression = variable.expression();
      CelAbstractSyntaxTree varAst;
      CelType outputType = SimpleType.DYN;
      try {
        varAst = ruleCel.compile(expression.value()).getAst();
        outputType = varAst.getResultType();
      } catch (CelValidationException e) {
        compilerContext.addIssue(expression.id(), e.getErrors());
        varAst = newErrorAst();
      }
      String variableName = variable.name().value();
      CelVarDecl newVariable =
          CelVarDecl.newVarDeclaration(variablesPrefix + variableName, outputType);
      compilerContext.addNewVarDecl(newVariable);
      ruleCel = ruleCel.toCelBuilder().addVarDeclarations(newVariable).build();
      variableBuilder.add(CelCompiledVariable.create(variableName, varAst));
    }

    ImmutableList.Builder<CelCompiledMatch> matchBuilder = ImmutableList.builder();
    for (Match match : rule.matches()) {
      CelAbstractSyntaxTree conditionAst;
      try {
        conditionAst = ruleCel.compile(match.condition().value()).getAst();
      } catch (CelValidationException e) {
        compilerContext.addIssue(match.condition().id(), e.getErrors());
        continue;
      }

      Result matchResult;
      switch (match.result().kind()) {
        case OUTPUT:
          CelAbstractSyntaxTree outputAst;
          try {
            outputAst = ruleCel.compile(match.result().output().value()).getAst();
          } catch (CelValidationException e) {
            compilerContext.addIssue(match.result().output().id(), e.getErrors());
            continue;
          }

          matchResult = Result.ofOutput(outputAst);
          break;
        case RULE:
          CelCompiledRule nestedRule = compileRule(match.result().rule(), ruleCel, compilerContext);
          matchResult = Result.ofRule(nestedRule);
          break;
        default:
          throw new IllegalArgumentException("Unexpected kind: " + match.result().kind());
      }

      matchBuilder.add(CelCompiledMatch.create(conditionAst, matchResult));
    }

    return CelCompiledRule.create(variableBuilder.build(), matchBuilder.build(), cel);
  }

  private static CelAbstractSyntaxTree newErrorAst() {
    return CelAbstractSyntaxTree.newParsedAst(
        CelExpr.ofConstant(0, CelConstant.ofValue("*error*")), CelSource.newBuilder().build());
  }

  private static final class CompilerContext {
    private final ArrayList<CelIssue> issues;
    private final ArrayList<CelVarDecl> newVariableDeclarations;
    private final CelPolicySource celPolicySource;

    private void addIssue(long id, List<CelIssue> issues) {
      for (CelIssue issue : issues) {
        // Compute relative source and add them into the issues set
        int position = Optional.ofNullable(celPolicySource.getPositionsMap().get(id)).orElse(-1);
        int relativeOffset = celPolicySource.getLocationOffset(issue.getSourceLocation().getLine(), issue.getSourceLocation().getColumn()).get();
        CelSourceLocation location = celPolicySource.getOffsetLocation(position).get();
        CelSourceLocation normalizedLocation = celPolicySource.getOffsetLocation(position + issue.getSourceLocation().getColumn()).get();
        int normalizedLine = normalizedLocation.getLine();
        int offset = celPolicySource.getContent().lineOffsets().get(normalizedLine - 2);

        position = offset + issue.getSourceLocation().getColumn();


        // int position = Optional.ofNullable(celPolicySource.getPositionsMap().get(id)).orElse(-1);
        // Optional<CelSourceLocation> location = celPolicySource.getOffsetLocation(position);
        // System.out.println(location);
        // for (int i = 0; i < issue.getSourceLocation().getLine();  i++) {
        //   position += issue.getSourceLocation().getColumn();
        // }

        // TODO remove
        // position = 85;

        // temp
        for (int i = 0; i < 150; i++) {
          CelSourceLocation loc =
              celPolicySource.getOffsetLocation(i).orElse(CelSourceLocation.NONE);
          String formatted = CelIssue.formatError(loc, issue.getMessage()).toDisplayString(celPolicySource);
          System.out.println("i: " + i + " : " + formatted);
        }
        System.out.println("position: " + position);
        CelSourceLocation loc =
            celPolicySource.getOffsetLocation(position).orElse(CelSourceLocation.NONE);
        this.issues.add(CelIssue.formatError(loc, issue.getMessage()));
      }
    }

    private void addNewVarDecl(CelVarDecl newVarDecl) {
      newVariableDeclarations.add(newVarDecl);
    }

    private boolean hasError() {
      return !issues.isEmpty();
    }

    private String getIssueString() {
      return CelIssue.toDisplayString(issues, celPolicySource);
    }

    private CompilerContext(CelPolicySource celPolicySource) {
      this.issues = new ArrayList<>();
      this.newVariableDeclarations = new ArrayList<>();
      this.celPolicySource = celPolicySource;
    }
  }

  static final class Builder implements CelPolicyCompilerBuilder {
    private final Cel cel;
    private String variablesPrefix;

    private Builder(Cel cel) {
      this.cel = cel;
    }

    @Override
    @CanIgnoreReturnValue
    public Builder setVariablesPrefix(String prefix) {
      this.variablesPrefix = checkNotNull(prefix);
      return this;
    }

    @Override
    public CelPolicyCompiler build() {
      return new CelPolicyCompilerImpl(cel, this.variablesPrefix);
    }
  }

  static Builder newBuilder(Cel cel) {
    return new Builder(cel).setVariablesPrefix(DEFAULT_VARIABLE_PREFIX);
  }

  private CelPolicyCompilerImpl(Cel cel, String variablesPrefix) {
    this.cel = checkNotNull(cel);
    this.variablesPrefix = checkNotNull(variablesPrefix);
  }
}
