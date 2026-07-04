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
package io.btrace.core;

/**
 * A bounded ring buffer keeping the newest {@code size} elements; adding to a full buffer
 * overwrites the oldest one. Consumption ({@link #forEach}, {@link #doNext}) removes elements.
 *
 * <p>Thread-safe: producers and consumers run on different threads (e.g. the runtime command
 * dispatch vs. the per-client command handler in the agent), so all state transitions are
 * synchronized. The functor is invoked while holding the buffer monitor, which also serializes
 * consumption against concurrent adds.
 */
public final class CircularBuffer<T> {
  private final T[] elements;
  private final int size;
  private int head = 0; // index of the oldest live element
  private int length = 0; // number of live elements

  @SuppressWarnings("unchecked")
  public CircularBuffer(int size) {
    this.size = size;
    elements = (T[]) new Object[size];
  }

  public synchronized void add(T element) {
    int writePos = (head + length) % size;
    elements[writePos] = element;
    if (length == size) {
      // buffer was full - the oldest element has just been overwritten
      head = (head + 1) % size;
    } else {
      length++;
    }
  }

  public boolean forEach(Function<T, Boolean> functor) {
    synchronized (this) {
      while (length > 0) {
        if (!functor.apply(elements[head])) {
          return false;
        }
        // consumed slots are released so they cannot be re-delivered after wrap-around
        elements[head] = null;
        head = (head + 1) % size;
        length--;
      }
      return true;
    }
  }

  public synchronized boolean doNext(Function<T, Boolean> nextWork) {
    if (length > 0 && nextWork.apply(elements[head])) {
      elements[head] = null;
      head = (head + 1) % size;
      length--;
      return true;
    }
    return false;
  }

  public synchronized int getLength() {
    return length;
  }
}
