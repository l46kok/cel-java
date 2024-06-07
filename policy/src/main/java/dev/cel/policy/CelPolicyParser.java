package dev.cel.policy;

public interface CelPolicyParser {

  CelPolicy parse(CelPolicySource source) throws CelPolicyValidationException;

  interface TagVisitor<T> {

    default void visitPolicyTag(ParserContext<T> ctx, long id, String fieldName, T node,
        CelPolicy.Builder policyBuilder) {
      ctx.reportError(id, String.format("Unsupported policy tag: %s", fieldName));
    }

    default void visitRuleTag(ParserContext<T> ctx, long id, String fieldName, T node,
        CelPolicy.Rule.Builder ruleBuilder) {
      ctx.reportError(id, String.format("Unsupported rule tag: %s", fieldName));
    }

    default void visitMatchTag(ParserContext<T> ctx, long id, String fieldName, T node,
        CelPolicy.Match.Builder matchBuilder) {
      ctx.reportError(id, String.format("Unsupported match tag: %s", fieldName));
    }

    default void visitVariableTag(ParserContext<T> ctx, long id, String fieldName, T node,
        CelPolicy.Variable.Builder variableBuilder) {
      ctx.reportError(id, String.format("Unsupported variable tag: %s", fieldName));
    }
  }
}