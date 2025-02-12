// Generated by the protocol buffer compiler.  DO NOT EDIT!
// NO CHECKED-IN PROTOBUF GENCODE
// source: google/api/expr/v1alpha1/checked.proto
// Protobuf Java Version: 4.29.3

package com.google.api.expr.v1alpha1;

public interface ReferenceOrBuilder extends
    // @@protoc_insertion_point(interface_extends:google.api.expr.v1alpha1.Reference)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * The fully qualified name of the declaration.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The name.
   */
  java.lang.String getName();
  /**
   * <pre>
   * The fully qualified name of the declaration.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The bytes for name.
   */
  com.google.protobuf.ByteString
      getNameBytes();

  /**
   * <pre>
   * For references to functions, this is a list of `Overload.overload_id`
   * values which match according to typing rules.
   *
   * If the list has more than one element, overload resolution among the
   * presented candidates must happen at runtime because of dynamic types. The
   * type checker attempts to narrow down this list as much as possible.
   *
   * Empty if this is not a reference to a
   * [Decl.FunctionDecl][google.api.expr.v1alpha1.Decl.FunctionDecl].
   * </pre>
   *
   * <code>repeated string overload_id = 3;</code>
   * @return A list containing the overloadId.
   */
  java.util.List<java.lang.String>
      getOverloadIdList();
  /**
   * <pre>
   * For references to functions, this is a list of `Overload.overload_id`
   * values which match according to typing rules.
   *
   * If the list has more than one element, overload resolution among the
   * presented candidates must happen at runtime because of dynamic types. The
   * type checker attempts to narrow down this list as much as possible.
   *
   * Empty if this is not a reference to a
   * [Decl.FunctionDecl][google.api.expr.v1alpha1.Decl.FunctionDecl].
   * </pre>
   *
   * <code>repeated string overload_id = 3;</code>
   * @return The count of overloadId.
   */
  int getOverloadIdCount();
  /**
   * <pre>
   * For references to functions, this is a list of `Overload.overload_id`
   * values which match according to typing rules.
   *
   * If the list has more than one element, overload resolution among the
   * presented candidates must happen at runtime because of dynamic types. The
   * type checker attempts to narrow down this list as much as possible.
   *
   * Empty if this is not a reference to a
   * [Decl.FunctionDecl][google.api.expr.v1alpha1.Decl.FunctionDecl].
   * </pre>
   *
   * <code>repeated string overload_id = 3;</code>
   * @param index The index of the element to return.
   * @return The overloadId at the given index.
   */
  java.lang.String getOverloadId(int index);
  /**
   * <pre>
   * For references to functions, this is a list of `Overload.overload_id`
   * values which match according to typing rules.
   *
   * If the list has more than one element, overload resolution among the
   * presented candidates must happen at runtime because of dynamic types. The
   * type checker attempts to narrow down this list as much as possible.
   *
   * Empty if this is not a reference to a
   * [Decl.FunctionDecl][google.api.expr.v1alpha1.Decl.FunctionDecl].
   * </pre>
   *
   * <code>repeated string overload_id = 3;</code>
   * @param index The index of the value to return.
   * @return The bytes of the overloadId at the given index.
   */
  com.google.protobuf.ByteString
      getOverloadIdBytes(int index);

  /**
   * <pre>
   * For references to constants, this may contain the value of the
   * constant if known at compile time.
   * </pre>
   *
   * <code>.google.api.expr.v1alpha1.Constant value = 4;</code>
   * @return Whether the value field is set.
   */
  boolean hasValue();
  /**
   * <pre>
   * For references to constants, this may contain the value of the
   * constant if known at compile time.
   * </pre>
   *
   * <code>.google.api.expr.v1alpha1.Constant value = 4;</code>
   * @return The value.
   */
  com.google.api.expr.v1alpha1.Constant getValue();
  /**
   * <pre>
   * For references to constants, this may contain the value of the
   * constant if known at compile time.
   * </pre>
   *
   * <code>.google.api.expr.v1alpha1.Constant value = 4;</code>
   */
  com.google.api.expr.v1alpha1.ConstantOrBuilder getValueOrBuilder();
}
