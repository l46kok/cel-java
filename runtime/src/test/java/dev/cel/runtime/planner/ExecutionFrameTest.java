// Copyright 2026 Google LLC
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

package dev.cel.runtime.planner;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import dev.cel.common.CelOptions;
import dev.cel.common.values.CelValueConverter;
import dev.cel.runtime.CelFunctionResolver;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ExecutionFrameTest {

  @Test
  public void toTraversalTarget_sameRootAfterSelfAdaptingRoot_reusesAdaptation() {
    ExecutionFrame frame =
        ExecutionFrame.create(
            CelFunctionResolver.EMPTY,
            CelOptions.DEFAULT,
            /* partialVars= */ null,
            /* listener= */ null);
    CelValueConverter converter = CelValueConverter.getDefaultInstance();
    // Adapts to a new OptionalValue each time, while a map adapts to itself.
    Optional<String> root = Optional.of("a");
    Object adapted = frame.toTraversalTarget(root, converter);
    frame.toTraversalTarget(ImmutableMap.of(), converter);

    Object readapted = frame.toTraversalTarget(root, converter);

    assertThat(readapted).isSameInstanceAs(adapted);
  }
}
