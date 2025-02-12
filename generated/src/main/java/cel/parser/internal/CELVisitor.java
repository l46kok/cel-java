// Generated from CEL.g4 by ANTLR 4.13.2
package cel.parser.internal;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link CELParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface CELVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link CELParser#start}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStart(CELParser.StartContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr(CELParser.ExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#conditionalOr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionalOr(CELParser.ConditionalOrContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#conditionalAnd}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionalAnd(CELParser.ConditionalAndContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#relation}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelation(CELParser.RelationContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#calc}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCalc(CELParser.CalcContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberExpr}
	 * labeled alternative in {@link CELParser#unary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberExpr(CELParser.MemberExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code LogicalNot}
	 * labeled alternative in {@link CELParser#unary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogicalNot(CELParser.LogicalNotContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Negate}
	 * labeled alternative in {@link CELParser#unary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNegate(CELParser.NegateContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberCall}
	 * labeled alternative in {@link CELParser#member}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberCall(CELParser.MemberCallContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Select}
	 * labeled alternative in {@link CELParser#member}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelect(CELParser.SelectContext ctx);
	/**
	 * Visit a parse tree produced by the {@code PrimaryExpr}
	 * labeled alternative in {@link CELParser#member}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimaryExpr(CELParser.PrimaryExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Index}
	 * labeled alternative in {@link CELParser#member}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIndex(CELParser.IndexContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Ident}
	 * labeled alternative in {@link CELParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdent(CELParser.IdentContext ctx);
	/**
	 * Visit a parse tree produced by the {@code GlobalCall}
	 * labeled alternative in {@link CELParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGlobalCall(CELParser.GlobalCallContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Nested}
	 * labeled alternative in {@link CELParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNested(CELParser.NestedContext ctx);
	/**
	 * Visit a parse tree produced by the {@code CreateList}
	 * labeled alternative in {@link CELParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreateList(CELParser.CreateListContext ctx);
	/**
	 * Visit a parse tree produced by the {@code CreateMap}
	 * labeled alternative in {@link CELParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreateMap(CELParser.CreateMapContext ctx);
	/**
	 * Visit a parse tree produced by the {@code CreateMessage}
	 * labeled alternative in {@link CELParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCreateMessage(CELParser.CreateMessageContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ConstantLiteral}
	 * labeled alternative in {@link CELParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstantLiteral(CELParser.ConstantLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#exprList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExprList(CELParser.ExprListContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#listInit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitListInit(CELParser.ListInitContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#fieldInitializerList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFieldInitializerList(CELParser.FieldInitializerListContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#optField}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOptField(CELParser.OptFieldContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#mapInitializerList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMapInitializerList(CELParser.MapInitializerListContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SimpleIdentifier}
	 * labeled alternative in {@link CELParser#escapeIdent}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSimpleIdentifier(CELParser.SimpleIdentifierContext ctx);
	/**
	 * Visit a parse tree produced by the {@code EscapedIdentifier}
	 * labeled alternative in {@link CELParser#escapeIdent}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEscapedIdentifier(CELParser.EscapedIdentifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link CELParser#optExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOptExpr(CELParser.OptExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Int}
	 * labeled alternative in {@link CELParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInt(CELParser.IntContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Uint}
	 * labeled alternative in {@link CELParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUint(CELParser.UintContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Double}
	 * labeled alternative in {@link CELParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDouble(CELParser.DoubleContext ctx);
	/**
	 * Visit a parse tree produced by the {@code String}
	 * labeled alternative in {@link CELParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitString(CELParser.StringContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Bytes}
	 * labeled alternative in {@link CELParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBytes(CELParser.BytesContext ctx);
	/**
	 * Visit a parse tree produced by the {@code BoolTrue}
	 * labeled alternative in {@link CELParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBoolTrue(CELParser.BoolTrueContext ctx);
	/**
	 * Visit a parse tree produced by the {@code BoolFalse}
	 * labeled alternative in {@link CELParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBoolFalse(CELParser.BoolFalseContext ctx);
	/**
	 * Visit a parse tree produced by the {@code Null}
	 * labeled alternative in {@link CELParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNull(CELParser.NullContext ctx);
}