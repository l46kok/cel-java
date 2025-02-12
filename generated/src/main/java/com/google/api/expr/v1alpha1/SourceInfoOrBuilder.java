// Generated by the protocol buffer compiler.  DO NOT EDIT!
// NO CHECKED-IN PROTOBUF GENCODE
// source: google/api/expr/v1alpha1/syntax.proto
// Protobuf Java Version: 4.29.3

package com.google.api.expr.v1alpha1;

public interface SourceInfoOrBuilder extends
    // @@protoc_insertion_point(interface_extends:google.api.expr.v1alpha1.SourceInfo)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * The syntax version of the source, e.g. `cel1`.
   * </pre>
   *
   * <code>string syntax_version = 1;</code>
   * @return The syntaxVersion.
   */
  java.lang.String getSyntaxVersion();
  /**
   * <pre>
   * The syntax version of the source, e.g. `cel1`.
   * </pre>
   *
   * <code>string syntax_version = 1;</code>
   * @return The bytes for syntaxVersion.
   */
  com.google.protobuf.ByteString
      getSyntaxVersionBytes();

  /**
   * <pre>
   * The location name. All position information attached to an expression is
   * relative to this location.
   *
   * The location could be a file, UI element, or similar. For example,
   * `acme/app/AnvilPolicy.cel`.
   * </pre>
   *
   * <code>string location = 2;</code>
   * @return The location.
   */
  java.lang.String getLocation();
  /**
   * <pre>
   * The location name. All position information attached to an expression is
   * relative to this location.
   *
   * The location could be a file, UI element, or similar. For example,
   * `acme/app/AnvilPolicy.cel`.
   * </pre>
   *
   * <code>string location = 2;</code>
   * @return The bytes for location.
   */
  com.google.protobuf.ByteString
      getLocationBytes();

  /**
   * <pre>
   * Monotonically increasing list of code point offsets where newlines
   * `&#92;n` appear.
   *
   * The line number of a given position is the index `i` where for a given
   * `id` the `line_offsets[i] &lt; id_positions[id] &lt; line_offsets[i+1]`. The
   * column may be derivd from `id_positions[id] - line_offsets[i]`.
   * </pre>
   *
   * <code>repeated int32 line_offsets = 3;</code>
   * @return A list containing the lineOffsets.
   */
  java.util.List<java.lang.Integer> getLineOffsetsList();
  /**
   * <pre>
   * Monotonically increasing list of code point offsets where newlines
   * `&#92;n` appear.
   *
   * The line number of a given position is the index `i` where for a given
   * `id` the `line_offsets[i] &lt; id_positions[id] &lt; line_offsets[i+1]`. The
   * column may be derivd from `id_positions[id] - line_offsets[i]`.
   * </pre>
   *
   * <code>repeated int32 line_offsets = 3;</code>
   * @return The count of lineOffsets.
   */
  int getLineOffsetsCount();
  /**
   * <pre>
   * Monotonically increasing list of code point offsets where newlines
   * `&#92;n` appear.
   *
   * The line number of a given position is the index `i` where for a given
   * `id` the `line_offsets[i] &lt; id_positions[id] &lt; line_offsets[i+1]`. The
   * column may be derivd from `id_positions[id] - line_offsets[i]`.
   * </pre>
   *
   * <code>repeated int32 line_offsets = 3;</code>
   * @param index The index of the element to return.
   * @return The lineOffsets at the given index.
   */
  int getLineOffsets(int index);

  /**
   * <pre>
   * A map from the parse node id (e.g. `Expr.id`) to the code point offset
   * within the source.
   * </pre>
   *
   * <code>map&lt;int64, int32&gt; positions = 4;</code>
   */
  int getPositionsCount();
  /**
   * <pre>
   * A map from the parse node id (e.g. `Expr.id`) to the code point offset
   * within the source.
   * </pre>
   *
   * <code>map&lt;int64, int32&gt; positions = 4;</code>
   */
  boolean containsPositions(
      long key);
  /**
   * Use {@link #getPositionsMap()} instead.
   */
  @java.lang.Deprecated
  java.util.Map<java.lang.Long, java.lang.Integer>
  getPositions();
  /**
   * <pre>
   * A map from the parse node id (e.g. `Expr.id`) to the code point offset
   * within the source.
   * </pre>
   *
   * <code>map&lt;int64, int32&gt; positions = 4;</code>
   */
  java.util.Map<java.lang.Long, java.lang.Integer>
  getPositionsMap();
  /**
   * <pre>
   * A map from the parse node id (e.g. `Expr.id`) to the code point offset
   * within the source.
   * </pre>
   *
   * <code>map&lt;int64, int32&gt; positions = 4;</code>
   */
  int getPositionsOrDefault(
      long key,
      int defaultValue);
  /**
   * <pre>
   * A map from the parse node id (e.g. `Expr.id`) to the code point offset
   * within the source.
   * </pre>
   *
   * <code>map&lt;int64, int32&gt; positions = 4;</code>
   */
  int getPositionsOrThrow(
      long key);

  /**
   * <pre>
   * A map from the parse node id where a macro replacement was made to the
   * call `Expr` that resulted in a macro expansion.
   *
   * For example, `has(value.field)` is a function call that is replaced by a
   * `test_only` field selection in the AST. Likewise, the call
   * `list.exists(e, e &gt; 10)` translates to a comprehension expression. The key
   * in the map corresponds to the expression id of the expanded macro, and the
   * value is the call `Expr` that was replaced.
   * </pre>
   *
   * <code>map&lt;int64, .google.api.expr.v1alpha1.Expr&gt; macro_calls = 5;</code>
   */
  int getMacroCallsCount();
  /**
   * <pre>
   * A map from the parse node id where a macro replacement was made to the
   * call `Expr` that resulted in a macro expansion.
   *
   * For example, `has(value.field)` is a function call that is replaced by a
   * `test_only` field selection in the AST. Likewise, the call
   * `list.exists(e, e &gt; 10)` translates to a comprehension expression. The key
   * in the map corresponds to the expression id of the expanded macro, and the
   * value is the call `Expr` that was replaced.
   * </pre>
   *
   * <code>map&lt;int64, .google.api.expr.v1alpha1.Expr&gt; macro_calls = 5;</code>
   */
  boolean containsMacroCalls(
      long key);
  /**
   * Use {@link #getMacroCallsMap()} instead.
   */
  @java.lang.Deprecated
  java.util.Map<java.lang.Long, com.google.api.expr.v1alpha1.Expr>
  getMacroCalls();
  /**
   * <pre>
   * A map from the parse node id where a macro replacement was made to the
   * call `Expr` that resulted in a macro expansion.
   *
   * For example, `has(value.field)` is a function call that is replaced by a
   * `test_only` field selection in the AST. Likewise, the call
   * `list.exists(e, e &gt; 10)` translates to a comprehension expression. The key
   * in the map corresponds to the expression id of the expanded macro, and the
   * value is the call `Expr` that was replaced.
   * </pre>
   *
   * <code>map&lt;int64, .google.api.expr.v1alpha1.Expr&gt; macro_calls = 5;</code>
   */
  java.util.Map<java.lang.Long, com.google.api.expr.v1alpha1.Expr>
  getMacroCallsMap();
  /**
   * <pre>
   * A map from the parse node id where a macro replacement was made to the
   * call `Expr` that resulted in a macro expansion.
   *
   * For example, `has(value.field)` is a function call that is replaced by a
   * `test_only` field selection in the AST. Likewise, the call
   * `list.exists(e, e &gt; 10)` translates to a comprehension expression. The key
   * in the map corresponds to the expression id of the expanded macro, and the
   * value is the call `Expr` that was replaced.
   * </pre>
   *
   * <code>map&lt;int64, .google.api.expr.v1alpha1.Expr&gt; macro_calls = 5;</code>
   */
  /* nullable */
com.google.api.expr.v1alpha1.Expr getMacroCallsOrDefault(
      long key,
      /* nullable */
com.google.api.expr.v1alpha1.Expr defaultValue);
  /**
   * <pre>
   * A map from the parse node id where a macro replacement was made to the
   * call `Expr` that resulted in a macro expansion.
   *
   * For example, `has(value.field)` is a function call that is replaced by a
   * `test_only` field selection in the AST. Likewise, the call
   * `list.exists(e, e &gt; 10)` translates to a comprehension expression. The key
   * in the map corresponds to the expression id of the expanded macro, and the
   * value is the call `Expr` that was replaced.
   * </pre>
   *
   * <code>map&lt;int64, .google.api.expr.v1alpha1.Expr&gt; macro_calls = 5;</code>
   */
  com.google.api.expr.v1alpha1.Expr getMacroCallsOrThrow(
      long key);

  /**
   * <pre>
   * A list of tags for extensions that were used while parsing or type checking
   * the source expression. For example, optimizations that require special
   * runtime support may be specified.
   *
   * These are used to check feature support between components in separate
   * implementations. This can be used to either skip redundant work or
   * report an error if the extension is unsupported.
   * </pre>
   *
   * <code>repeated .google.api.expr.v1alpha1.SourceInfo.Extension extensions = 6;</code>
   */
  java.util.List<com.google.api.expr.v1alpha1.SourceInfo.Extension> 
      getExtensionsList();
  /**
   * <pre>
   * A list of tags for extensions that were used while parsing or type checking
   * the source expression. For example, optimizations that require special
   * runtime support may be specified.
   *
   * These are used to check feature support between components in separate
   * implementations. This can be used to either skip redundant work or
   * report an error if the extension is unsupported.
   * </pre>
   *
   * <code>repeated .google.api.expr.v1alpha1.SourceInfo.Extension extensions = 6;</code>
   */
  com.google.api.expr.v1alpha1.SourceInfo.Extension getExtensions(int index);
  /**
   * <pre>
   * A list of tags for extensions that were used while parsing or type checking
   * the source expression. For example, optimizations that require special
   * runtime support may be specified.
   *
   * These are used to check feature support between components in separate
   * implementations. This can be used to either skip redundant work or
   * report an error if the extension is unsupported.
   * </pre>
   *
   * <code>repeated .google.api.expr.v1alpha1.SourceInfo.Extension extensions = 6;</code>
   */
  int getExtensionsCount();
  /**
   * <pre>
   * A list of tags for extensions that were used while parsing or type checking
   * the source expression. For example, optimizations that require special
   * runtime support may be specified.
   *
   * These are used to check feature support between components in separate
   * implementations. This can be used to either skip redundant work or
   * report an error if the extension is unsupported.
   * </pre>
   *
   * <code>repeated .google.api.expr.v1alpha1.SourceInfo.Extension extensions = 6;</code>
   */
  java.util.List<? extends com.google.api.expr.v1alpha1.SourceInfo.ExtensionOrBuilder> 
      getExtensionsOrBuilderList();
  /**
   * <pre>
   * A list of tags for extensions that were used while parsing or type checking
   * the source expression. For example, optimizations that require special
   * runtime support may be specified.
   *
   * These are used to check feature support between components in separate
   * implementations. This can be used to either skip redundant work or
   * report an error if the extension is unsupported.
   * </pre>
   *
   * <code>repeated .google.api.expr.v1alpha1.SourceInfo.Extension extensions = 6;</code>
   */
  com.google.api.expr.v1alpha1.SourceInfo.ExtensionOrBuilder getExtensionsOrBuilder(
      int index);
}
