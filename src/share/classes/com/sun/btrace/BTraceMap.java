/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** 
 * Instances of this class are used to store  aggregate 
 * tracing data in BTrace. 
 *
 * @author A. Sundararajan
 */
final class BTraceMap<K,V> implements Map<K,V> {
    //private int numItems;
    private final Map<K,V> m;
    BTraceMap(Map<K,V> m) {
        if (m == null) {
            throw new NullPointerException();
        }
        this.m = m;
    }

    public synchronized int size() {
        return m.size();
    }

    public synchronized boolean isEmpty(){
        return m.isEmpty();
    }

    public synchronized boolean containsKey(Object key) {
        return m.containsKey(key);
    }

    public synchronized boolean containsValue(Object value){
        return m.containsValue(value);
    }

    public synchronized V get(Object key) {
        return m.get(key);
    }

    public synchronized V put(K key, V value) {
        return m.put(key, value);
    }

    public synchronized V remove(Object key) {
        return m.remove(key);
    }

    public synchronized void putAll(Map<? extends K, ? extends V> map) {
        m.putAll(map);
    }

    public synchronized void clear() {
        m.clear();
    }

    private transient Set<K> keySet = null;
    private transient Set<Map.Entry<K,V>> entrySet = null;
    private transient Collection<V> values = null;

    public synchronized Set<K> keySet() {
        if (keySet == null) {
            keySet = m.keySet();
        }
        return keySet;
    }
 
    public synchronized Set<Map.Entry<K,V>> entrySet() {
        if (entrySet == null) {
            entrySet = m.entrySet();
        }
        return entrySet;
    }

    public synchronized Collection<V> values() {
        if (values == null) {
            values = m.values();
        }
        return values;
    }

    public synchronized boolean equals(Object o) {
        return m.equals(o);
    }

    public synchronized int hashCode() {
        return m.hashCode();
    }

    public synchronized String toString() {
        return m.toString();
    }
}