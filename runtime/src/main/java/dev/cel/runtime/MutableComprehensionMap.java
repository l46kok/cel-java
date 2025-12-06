// Copyright 2025 Google LLC
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

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A mutable map implementation used for map comprehension optimization.
 *
 * <p>This class allows O(1) insertions, avoiding the O(n) copy cost of {@code ImmutableMap} during
 * accumulation.
 */
public final class MutableComprehensionMap<K, V> implements Map<K, V> {
  private final Map<K, V> delegate;

  public MutableComprehensionMap() {
    this.delegate = new LinkedHashMap<>();
  }

  public MutableComprehensionMap(Map<? extends K, ? extends V> map) {
    this.delegate = new LinkedHashMap<>(map);
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return delegate.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return delegate.containsValue(value);
  }

  @Override
  public @Nullable V get(Object key) {
    return delegate.get(key);
  }

  @Override
  public @Nullable V put(K key, V value) {
    return delegate.put(key, value);
  }

  @Override
  public @Nullable V remove(Object key) {
    return delegate.remove(key);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    delegate.putAll(m);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public Set<K> keySet() {
    return delegate.keySet();
  }

  @Override
  public Collection<V> values() {
    return delegate.values();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return delegate.entrySet();
  }

  public ImmutableMap<K, V> toImmutableMap() {
    return ImmutableMap.copyOf(delegate);
  }
}
