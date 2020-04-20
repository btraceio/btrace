/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return new BTraceMap(isWeak ? new WeakHashMap() : new HashMap());
  }
}
