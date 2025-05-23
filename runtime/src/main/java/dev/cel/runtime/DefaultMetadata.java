// Copyright 2022 Google LLC
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

package dev.cel.runtime;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.annotations.Internal;

/**
 * Metadata implementation based on {@link CelAbstractSyntaxTree}.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Immutable
@Internal
public final class DefaultMetadata implements Metadata {

  private final CelAbstractSyntaxTree ast;

  public DefaultMetadata(CelAbstractSyntaxTree ast) {
    this.ast = Preconditions.checkNotNull(ast);
  }

  @Override
  public String getLocation() {
    return ast.getSource().getDescription();
  }

  @Override
  public int getPosition(long exprId) {
    ImmutableMap<Long, Integer> positions = ast.getSource().getPositionsMap();
    return positions.getOrDefault(exprId, 0);
  }

  @Override
  public boolean hasPosition(long exprId) {
    return ast.getSource().getPositionsMap().containsKey(exprId);
  }
}
