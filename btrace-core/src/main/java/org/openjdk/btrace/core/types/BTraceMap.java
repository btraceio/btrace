/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openjdk.btrace.core.types;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Instances of this class are used to store aggregate tracing data in BTrace.
 *
 * @author A. Sundararajan
 */
public final class BTraceMap<K, V> implements Map<K, V>, Cloneable {
  // private int numItems;
  private final Map<K, V> m;
  private final boolean isWeak;
  private transient Set<K> keySet = null;
  private transient Set<Map.Entry<K, V>> entrySet = null;
  private transient Collection<V> values = null;

  public BTraceMap(Map<K, V> m) {
    if (m == null) {
      throw new NullPointerException();
    }
    this.m = m;
    isWeak = (m instanceof WeakHashMap);
  }

  @Override
  public synchronized int size() {
    return m.size();
  }

  @Override
  public synchronized boolean isEmpty() {
    return m.isEmpty();
  }

  @Override
  public synchronized boolean containsKey(Object key) {
    return m.containsKey(key);
  }

  @Override
  public synchronized boolean containsValue(Object value) {
    return m.containsValue(value);
  }

  @Override
  public synchronized V get(Object key) {
    return m.get(key);
  }

  @Override
  public synchronized V put(K key, V value) {
    return m.put(key, value);
  }

  @Override
  public synchronized V remove(Object key) {
    return m.remove(key);
  }

  @Override
  public synchronized void putAll(Map<? extends K, ? extends V> map) {
    m.putAll(map);
  }

  @Override
  public synchronized void clear() {
    m.clear();
  }

  @Override
  public synchronized Set<K> keySet() {
    if (keySet == null) {
      keySet = m.keySet();
    }
    return keySet;
  }

  @Override
  public synchronized Set<Map.Entry<K, V>> entrySet() {
    if (entrySet == null) {
      entrySet = m.entrySet();
    }
    return entrySet;
  }

  @Override
  public synchronized Collection<V> values() {
    if (values == null) {
      values = m.values();
    }
    return values;
  }

  @Override
  public synchronized boolean equals(Object o) {
    return m.equals(o);
  }

  @Override
  public synchronized int hashCode() {
    return m.hashCode();
  }

  @Override
  public synchronized String toString() {
    return m.toString();
  }

  @SuppressWarnings({"RedundantThrows", "MethodDoesntCallSuperMethod"})
  @Override
  protected Object clone() throws CloneNotSupportedException {
    return new BTraceMap<>(isWeak ? new WeakHashMap<>() : new HashMap<>());
  }
}
