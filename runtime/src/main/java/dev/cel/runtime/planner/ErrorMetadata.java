package dev.cel.runtime.planner;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.Metadata;

import static com.google.common.base.Preconditions.checkNotNull;

@Immutable
final class ErrorMetadata implements Metadata {

  private final ImmutableMap<Long, Integer> exprIdToPositionMap;
  private final String location;

  @Override
  public String getLocation() {
    return location;
  }

  @Override
  public int getPosition(long exprId) {
    return exprIdToPositionMap.getOrDefault(exprId, 0);
  }

  @Override
  public boolean hasPosition(long exprId) {
    return exprIdToPositionMap.containsKey(exprId);
  }

  static ErrorMetadata create(ImmutableMap<Long, Integer> exprIdToPositionMap, String location) {
    return new ErrorMetadata(exprIdToPositionMap, location);
  }

  private ErrorMetadata(ImmutableMap<Long, Integer> exprIdToPositionMap, String location) {
    this.exprIdToPositionMap = checkNotNull(exprIdToPositionMap);
    this.location = checkNotNull(location);
  }
}
