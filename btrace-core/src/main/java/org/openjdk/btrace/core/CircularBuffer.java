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
package org.openjdk.btrace.core;

public final class CircularBuffer<T> {
  private final T[] elements;
  private final int size;
  private long readIndex = 0;
  private long writeIndex = -1;
  private int length = 0;

  @SuppressWarnings("unchecked")
  public CircularBuffer(int size) {
    this.size = size;
    elements = (T[]) new Object[size];
  }

  public void add(T element) {
    int newIndex = (int) (++writeIndex) % size;
    elements[newIndex] = element;
    int nextIndex = (newIndex + 1) % size;
    if (elements[nextIndex] != null) {
      readIndex = nextIndex;
    }
    if (++length > size) {
      length = size;
    }
  }

  public boolean forEach(Function<T, Boolean> functor) {
    int cntr = 0;
    while (cntr < size && writeIndex >= readIndex) {
      if (functor.apply(elements[(int) readIndex % size])) {
        readIndex++;
        if (--length < 0) {
          length = 0;
        }
      } else {
        return false;
      }
      cntr++;
    }
    return true;
  }

  public boolean doNext(Function<T, Boolean> nextWork) {
    if (writeIndex >= readIndex) {
      if (nextWork.apply(elements[(int) readIndex % size])) {
        readIndex++;
        return true;
      }
    }
    return false;
  }

  public int getLength() {
    return length;
  }
}
