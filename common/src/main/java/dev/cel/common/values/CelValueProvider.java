// Copyright 2023 Google LLC
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

package dev.cel.common.values;

import com.google.errorprone.annotations.Immutable;
import java.util.Map;
import java.util.Optional;

/** CelValueProvider is an interface for constructing new struct values. */
@Immutable
public interface CelValueProvider {

  /**
   * Constructs a new struct value.
   *
   * <p>Note that the return type is defined as CelValue rather than StructValue to account for
   * special cases such as wrappers where its primitive is returned.
   */
  Optional<CelValue> newValue(String structType, Map<String, Object> fields);
}
