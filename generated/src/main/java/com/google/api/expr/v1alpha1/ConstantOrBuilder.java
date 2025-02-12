// Generated by the protocol buffer compiler.  DO NOT EDIT!
// NO CHECKED-IN PROTOBUF GENCODE
// source: google/api/expr/v1alpha1/syntax.proto
// Protobuf Java Version: 4.29.3

package com.google.api.expr.v1alpha1;

public interface ConstantOrBuilder extends
    // @@protoc_insertion_point(interface_extends:google.api.expr.v1alpha1.Constant)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * null value.
   * </pre>
   *
   * <code>.google.protobuf.NullValue null_value = 1;</code>
   * @return Whether the nullValue field is set.
   */
  boolean hasNullValue();
  /**
   * <pre>
   * null value.
   * </pre>
   *
   * <code>.google.protobuf.NullValue null_value = 1;</code>
   * @return The enum numeric value on the wire for nullValue.
   */
  int getNullValueValue();
  /**
   * <pre>
   * null value.
   * </pre>
   *
   * <code>.google.protobuf.NullValue null_value = 1;</code>
   * @return The nullValue.
   */
  com.google.protobuf.NullValue getNullValue();

  /**
   * <pre>
   * boolean value.
   * </pre>
   *
   * <code>bool bool_value = 2;</code>
   * @return Whether the boolValue field is set.
   */
  boolean hasBoolValue();
  /**
   * <pre>
   * boolean value.
   * </pre>
   *
   * <code>bool bool_value = 2;</code>
   * @return The boolValue.
   */
  boolean getBoolValue();

  /**
   * <pre>
   * int64 value.
   * </pre>
   *
   * <code>int64 int64_value = 3;</code>
   * @return Whether the int64Value field is set.
   */
  boolean hasInt64Value();
  /**
   * <pre>
   * int64 value.
   * </pre>
   *
   * <code>int64 int64_value = 3;</code>
   * @return The int64Value.
   */
  long getInt64Value();

  /**
   * <pre>
   * uint64 value.
   * </pre>
   *
   * <code>uint64 uint64_value = 4;</code>
   * @return Whether the uint64Value field is set.
   */
  boolean hasUint64Value();
  /**
   * <pre>
   * uint64 value.
   * </pre>
   *
   * <code>uint64 uint64_value = 4;</code>
   * @return The uint64Value.
   */
  long getUint64Value();

  /**
   * <pre>
   * double value.
   * </pre>
   *
   * <code>double double_value = 5;</code>
   * @return Whether the doubleValue field is set.
   */
  boolean hasDoubleValue();
  /**
   * <pre>
   * double value.
   * </pre>
   *
   * <code>double double_value = 5;</code>
   * @return The doubleValue.
   */
  double getDoubleValue();

  /**
   * <pre>
   * string value.
   * </pre>
   *
   * <code>string string_value = 6;</code>
   * @return Whether the stringValue field is set.
   */
  boolean hasStringValue();
  /**
   * <pre>
   * string value.
   * </pre>
   *
   * <code>string string_value = 6;</code>
   * @return The stringValue.
   */
  java.lang.String getStringValue();
  /**
   * <pre>
   * string value.
   * </pre>
   *
   * <code>string string_value = 6;</code>
   * @return The bytes for stringValue.
   */
  com.google.protobuf.ByteString
      getStringValueBytes();

  /**
   * <pre>
   * bytes value.
   * </pre>
   *
   * <code>bytes bytes_value = 7;</code>
   * @return Whether the bytesValue field is set.
   */
  boolean hasBytesValue();
  /**
   * <pre>
   * bytes value.
   * </pre>
   *
   * <code>bytes bytes_value = 7;</code>
   * @return The bytesValue.
   */
  com.google.protobuf.ByteString getBytesValue();

  /**
   * <pre>
   * protobuf.Duration value.
   *
   * Deprecated: duration is no longer considered a builtin cel type.
   * </pre>
   *
   * <code>.google.protobuf.Duration duration_value = 8 [deprecated = true];</code>
   * @deprecated google.api.expr.v1alpha1.Constant.duration_value is deprecated.
   *     See google/api/expr/v1alpha1/syntax.proto;l=325
   * @return Whether the durationValue field is set.
   */
  @java.lang.Deprecated boolean hasDurationValue();
  /**
   * <pre>
   * protobuf.Duration value.
   *
   * Deprecated: duration is no longer considered a builtin cel type.
   * </pre>
   *
   * <code>.google.protobuf.Duration duration_value = 8 [deprecated = true];</code>
   * @deprecated google.api.expr.v1alpha1.Constant.duration_value is deprecated.
   *     See google/api/expr/v1alpha1/syntax.proto;l=325
   * @return The durationValue.
   */
  @java.lang.Deprecated com.google.protobuf.Duration getDurationValue();
  /**
   * <pre>
   * protobuf.Duration value.
   *
   * Deprecated: duration is no longer considered a builtin cel type.
   * </pre>
   *
   * <code>.google.protobuf.Duration duration_value = 8 [deprecated = true];</code>
   */
  @java.lang.Deprecated com.google.protobuf.DurationOrBuilder getDurationValueOrBuilder();

  /**
   * <pre>
   * protobuf.Timestamp value.
   *
   * Deprecated: timestamp is no longer considered a builtin cel type.
   * </pre>
   *
   * <code>.google.protobuf.Timestamp timestamp_value = 9 [deprecated = true];</code>
   * @deprecated google.api.expr.v1alpha1.Constant.timestamp_value is deprecated.
   *     See google/api/expr/v1alpha1/syntax.proto;l=330
   * @return Whether the timestampValue field is set.
   */
  @java.lang.Deprecated boolean hasTimestampValue();
  /**
   * <pre>
   * protobuf.Timestamp value.
   *
   * Deprecated: timestamp is no longer considered a builtin cel type.
   * </pre>
   *
   * <code>.google.protobuf.Timestamp timestamp_value = 9 [deprecated = true];</code>
   * @deprecated google.api.expr.v1alpha1.Constant.timestamp_value is deprecated.
   *     See google/api/expr/v1alpha1/syntax.proto;l=330
   * @return The timestampValue.
   */
  @java.lang.Deprecated com.google.protobuf.Timestamp getTimestampValue();
  /**
   * <pre>
   * protobuf.Timestamp value.
   *
   * Deprecated: timestamp is no longer considered a builtin cel type.
   * </pre>
   *
   * <code>.google.protobuf.Timestamp timestamp_value = 9 [deprecated = true];</code>
   */
  @java.lang.Deprecated com.google.protobuf.TimestampOrBuilder getTimestampValueOrBuilder();

  com.google.api.expr.v1alpha1.Constant.ConstantKindCase getConstantKindCase();
}
