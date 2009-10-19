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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTraceDeque<V> implements Deque<V>, BTraceCollection<V>, Cloneable {
    private final Deque<V> delegate;

    public BTraceDeque(Deque<V> delegate) {
        this.delegate = delegate;
    }

    public synchronized String toString() {
        return delegate.toString();
    }

    public synchronized <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    public synchronized Object[] toArray() {
        return delegate.toArray();
    }

    public synchronized boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    public synchronized boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    public synchronized boolean isEmpty() {
        return delegate.isEmpty();
    }

    public synchronized boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    public synchronized void clear() {
        delegate.clear();
    }

    public synchronized boolean addAll(Collection<? extends V> c) {
        return delegate.addAll(c);
    }

    public synchronized int size() {
        return delegate.size();
    }

    public synchronized boolean removeLastOccurrence(Object o) {
        return delegate.removeLastOccurrence(o);
    }

    public synchronized V removeLast() {
        return delegate.removeLast();
    }

    public synchronized  boolean removeFirstOccurrence(Object o) {
        return delegate.removeFirstOccurrence(o);
    }

    public synchronized V removeFirst() {
        return delegate.removeFirst();
    }

    public synchronized boolean remove(Object o) {
        return delegate.remove(o);
    }

    public synchronized V remove() {
        return delegate.remove();
    }

    public synchronized void push(V e) {
        delegate.push(e);
    }

    public synchronized V pop() {
        return delegate.pop();
    }

    public synchronized V pollLast() {
        return delegate.pollLast();
    }

    public synchronized V pollFirst() {
        return delegate.pollFirst();
    }

    public synchronized V poll() {
        return delegate.poll();
    }

    public synchronized V peekLast() {
        return delegate.peekLast();
    }

    public synchronized V peekFirst() {
        return delegate.peekFirst();
    }

    public synchronized V peek() {
        return delegate.peek();
    }

    public synchronized boolean offerLast(V e) {
        return delegate.offerLast(e);
    }

    public synchronized boolean offerFirst(V e) {
        return delegate.offerFirst(e);
    }

    public synchronized boolean offer(V e) {
        return delegate.offer(e);
    }

    public synchronized Iterator<V> iterator() {
        return delegate.iterator();
    }

    public synchronized V getLast() {
        return delegate.getLast();
    }

    public synchronized V getFirst() {
        return delegate.getFirst();
    }

    public synchronized V element() {
        return delegate.element();
    }

    public synchronized Iterator<V> descendingIterator() {
        return delegate.descendingIterator();
    }

    public synchronized boolean contains(Object o) {
        return delegate.contains(o);
    }

    public synchronized void addLast(V e) {
        delegate.addLast(e);
    }

    public synchronized void addFirst(V e) {
        delegate.addFirst(e);
    }

    public synchronized boolean add(V e) {
        return delegate.add(e);
    }

    public synchronized int hashCode() {
        return delegate.hashCode();
    }

    public synchronized boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return new BTraceDeque(new ArrayDeque());
    }

}
