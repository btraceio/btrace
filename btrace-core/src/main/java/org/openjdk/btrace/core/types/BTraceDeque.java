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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;

/**
 * @author Jaroslav Bachorik
 */
public class BTraceDeque<V> implements Deque<V>, BTraceCollection<V>, Cloneable {
  private final Deque<V> delegate;

  public BTraceDeque(Deque<V> delegate) {
    this.delegate = delegate;
  }

  @Override
  public synchronized String toString() {
    return delegate.toString();
  }

  @Override
  public synchronized <T> T[] toArray(T[] a) {
    return delegate.toArray(a);
  }

  @Override
  public synchronized Object[] toArray() {
    return delegate.toArray();
  }

  @Override
  public synchronized boolean retainAll(Collection<?> c) {
    return delegate.retainAll(c);
  }

  @Override
  public synchronized boolean removeAll(Collection<?> c) {
    return delegate.removeAll(c);
  }

  @Override
  public synchronized boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public synchronized boolean containsAll(Collection<?> c) {
    return delegate.containsAll(c);
  }

  @Override
  public synchronized void clear() {
    delegate.clear();
  }

  @Override
  public synchronized boolean addAll(Collection<? extends V> c) {
    return delegate.addAll(c);
  }

  @Override
  public synchronized int size() {
    return delegate.size();
  }

  @Override
  public synchronized boolean removeLastOccurrence(Object o) {
    return delegate.removeLastOccurrence(o);
  }

  @Override
  public synchronized V removeLast() {
    return delegate.removeLast();
  }

  @Override
  public synchronized boolean removeFirstOccurrence(Object o) {
    return delegate.removeFirstOccurrence(o);
  }

  @Override
  public synchronized V removeFirst() {
    return delegate.removeFirst();
  }

  @Override
  public synchronized boolean remove(Object o) {
    return delegate.remove(o);
  }

  @Override
  public synchronized V remove() {
    return delegate.remove();
  }

  @Override
  public synchronized void push(V e) {
    delegate.push(e);
  }

  @Override
  public synchronized V pop() {
    return delegate.pop();
  }

  @Override
  public synchronized V pollLast() {
    return delegate.pollLast();
  }

  @Override
  public synchronized V pollFirst() {
    return delegate.pollFirst();
  }

  @Override
  public synchronized V poll() {
    return delegate.poll();
  }

  @Override
  public synchronized V peekLast() {
    return delegate.peekLast();
  }

  @Override
  public synchronized V peekFirst() {
    return delegate.peekFirst();
  }

  @Override
  public synchronized V peek() {
    return delegate.peek();
  }

  @Override
  public synchronized boolean offerLast(V e) {
    return delegate.offerLast(e);
  }

  @Override
  public synchronized boolean offerFirst(V e) {
    return delegate.offerFirst(e);
  }

  @Override
  public synchronized boolean offer(V e) {
    return delegate.offer(e);
  }

  @Override
  public synchronized Iterator<V> iterator() {
    return delegate.iterator();
  }

  @Override
  public synchronized V getLast() {
    return delegate.getLast();
  }

  @Override
  public synchronized V getFirst() {
    return delegate.getFirst();
  }

  @Override
  public synchronized V element() {
    return delegate.element();
  }

  @Override
  public synchronized Iterator<V> descendingIterator() {
    return delegate.descendingIterator();
  }

  @Override
  public synchronized boolean contains(Object o) {
    return delegate.contains(o);
  }

  @Override
  public synchronized void addLast(V e) {
    delegate.addLast(e);
  }

  @Override
  public synchronized void addFirst(V e) {
    delegate.addFirst(e);
  }

  @Override
  public synchronized boolean add(V e) {
    return delegate.add(e);
  }

  @Override
  public synchronized int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public synchronized boolean equals(Object obj) {
    return delegate.equals(obj);
  }

  @SuppressWarnings({"RedundantThrows", "MethodDoesntCallSuperMethod"})
  @Override
  protected Object clone() throws CloneNotSupportedException {
    return new BTraceDeque<>(new ArrayDeque<>());
  }
}
