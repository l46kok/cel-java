// Generated by the protocol buffer compiler.  DO NOT EDIT!
// NO CHECKED-IN PROTOBUF GENCODE
// source: cel/expr/syntax.proto
// Protobuf Java Version: 4.29.3

package dev.cel.expr;

public interface ExprOrBuilder extends
    // @@protoc_insertion_point(interface_extends:cel.expr.Expr)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Required. An id assigned to this node by the parser which is unique in a
   * given expression tree. This is used to associate type information and other
   * attributes to a node in the parse tree.
   * </pre>
   *
   * <code>int64 id = 2;</code>
   * @return The id.
   */
  long getId();

  /**
   * <pre>
   * A constant expression.
   * </pre>
   *
   * <code>.cel.expr.Constant const_expr = 3;</code>
   * @return Whether the constExpr field is set.
   */
  boolean hasConstExpr();
  /**
   * <pre>
   * A constant expression.
   * </pre>
   *
   * <code>.cel.expr.Constant const_expr = 3;</code>
   * @return The constExpr.
   */
  dev.cel.expr.Constant getConstExpr();
  /**
   * <pre>
   * A constant expression.
   * </pre>
   *
   * <code>.cel.expr.Constant const_expr = 3;</code>
   */
  dev.cel.expr.ConstantOrBuilder getConstExprOrBuilder();

  /**
   * <pre>
   * An identifier expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.Ident ident_expr = 4;</code>
   * @return Whether the identExpr field is set.
   */
  boolean hasIdentExpr();
  /**
   * <pre>
   * An identifier expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.Ident ident_expr = 4;</code>
   * @return The identExpr.
   */
  dev.cel.expr.Expr.Ident getIdentExpr();
  /**
   * <pre>
   * An identifier expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.Ident ident_expr = 4;</code>
   */
  dev.cel.expr.Expr.IdentOrBuilder getIdentExprOrBuilder();

  /**
   * <pre>
   * A field selection expression, e.g. `request.auth`.
   * </pre>
   *
   * <code>.cel.expr.Expr.Select select_expr = 5;</code>
   * @return Whether the selectExpr field is set.
   */
  boolean hasSelectExpr();
  /**
   * <pre>
   * A field selection expression, e.g. `request.auth`.
   * </pre>
   *
   * <code>.cel.expr.Expr.Select select_expr = 5;</code>
   * @return The selectExpr.
   */
  dev.cel.expr.Expr.Select getSelectExpr();
  /**
   * <pre>
   * A field selection expression, e.g. `request.auth`.
   * </pre>
   *
   * <code>.cel.expr.Expr.Select select_expr = 5;</code>
   */
  dev.cel.expr.Expr.SelectOrBuilder getSelectExprOrBuilder();

  /**
   * <pre>
   * A call expression, including calls to predefined functions and operators.
   * </pre>
   *
   * <code>.cel.expr.Expr.Call call_expr = 6;</code>
   * @return Whether the callExpr field is set.
   */
  boolean hasCallExpr();
  /**
   * <pre>
   * A call expression, including calls to predefined functions and operators.
   * </pre>
   *
   * <code>.cel.expr.Expr.Call call_expr = 6;</code>
   * @return The callExpr.
   */
  dev.cel.expr.Expr.Call getCallExpr();
  /**
   * <pre>
   * A call expression, including calls to predefined functions and operators.
   * </pre>
   *
   * <code>.cel.expr.Expr.Call call_expr = 6;</code>
   */
  dev.cel.expr.Expr.CallOrBuilder getCallExprOrBuilder();

  /**
   * <pre>
   * A list creation expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.CreateList list_expr = 7;</code>
   * @return Whether the listExpr field is set.
   */
  boolean hasListExpr();
  /**
   * <pre>
   * A list creation expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.CreateList list_expr = 7;</code>
   * @return The listExpr.
   */
  dev.cel.expr.Expr.CreateList getListExpr();
  /**
   * <pre>
   * A list creation expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.CreateList list_expr = 7;</code>
   */
  dev.cel.expr.Expr.CreateListOrBuilder getListExprOrBuilder();

  /**
   * <pre>
   * A map or message creation expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.CreateStruct struct_expr = 8;</code>
   * @return Whether the structExpr field is set.
   */
  boolean hasStructExpr();
  /**
   * <pre>
   * A map or message creation expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.CreateStruct struct_expr = 8;</code>
   * @return The structExpr.
   */
  dev.cel.expr.Expr.CreateStruct getStructExpr();
  /**
   * <pre>
   * A map or message creation expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.CreateStruct struct_expr = 8;</code>
   */
  dev.cel.expr.Expr.CreateStructOrBuilder getStructExprOrBuilder();

  /**
   * <pre>
   * A comprehension expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.Comprehension comprehension_expr = 9;</code>
   * @return Whether the comprehensionExpr field is set.
   */
  boolean hasComprehensionExpr();
  /**
   * <pre>
   * A comprehension expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.Comprehension comprehension_expr = 9;</code>
   * @return The comprehensionExpr.
   */
  dev.cel.expr.Expr.Comprehension getComprehensionExpr();
  /**
   * <pre>
   * A comprehension expression.
   * </pre>
   *
   * <code>.cel.expr.Expr.Comprehension comprehension_expr = 9;</code>
   */
  dev.cel.expr.Expr.ComprehensionOrBuilder getComprehensionExprOrBuilder();

  dev.cel.expr.Expr.ExprKindCase getExprKindCase();
}
