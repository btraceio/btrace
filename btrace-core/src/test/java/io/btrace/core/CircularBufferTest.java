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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CircularBufferTest {

  @Test
  public void testAddOverflow() {
    CircularBuffer<Integer> cb = new CircularBuffer<>(3);
    cb.add(1);
    cb.add(2);
    cb.add(3);
    cb.add(4);

    assertEquals(3, cb.getLength());
    final List<Integer> elements = new ArrayList<>();
    cb.forEach(
        new Function<Integer, Boolean>() {
          @Override
          public Boolean apply(Integer value) {
            elements.add(value);
            return true;
          }
        });

    assertEquals(0, cb.getLength());
    assertEquals(Arrays.asList(2, 3, 4), elements);
  }

  @Test
  public void testAddOverflowSeveral() {
    CircularBuffer<Integer> cb = new CircularBuffer<>(2);
    cb.add(1);
    cb.add(2);
    cb.add(3);
    cb.add(4);
    cb.add(5);
    cb.add(6);
    cb.add(7);

    assertEquals(2, cb.getLength());
    final List<Integer> elements = new ArrayList<>();
    cb.forEach(
        new Function<Integer, Boolean>() {
          @Override
          public Boolean apply(Integer value) {
            elements.add(value);
            return true;
          }
        });
    assertEquals(0, cb.getLength());

    assertEquals(Arrays.asList(6, 7), elements);
  }

  @Test
  public void testAdd() {
    CircularBuffer<Integer> cb = new CircularBuffer<>(2);

    cb.add(1);
    assertEquals(1, cb.getLength());
    final List<Integer> elements = new ArrayList<>();
    cb.forEach(
        new Function<Integer, Boolean>() {
          @Override
          public Boolean apply(Integer value) {
            elements.add(value);
            return true;
          }
        });
    assertEquals(0, cb.getLength());
    assertEquals(Arrays.asList(1), elements);
  }

  @Test
  public void testAddFull() {
    CircularBuffer<Integer> cb = new CircularBuffer<>(2);

    cb.add(1);
    cb.add(2);
    assertEquals(2, cb.getLength());
    final List<Integer> elements = new ArrayList<>();
    cb.forEach(
        new Function<Integer, Boolean>() {
          @Override
          public Boolean apply(Integer value) {
            elements.add(value);
            return true;
          }
        });
    assertEquals(0, cb.getLength());
    assertEquals(Arrays.asList(1, 2), elements);
  }

  @Test
  public void testDrainThenRefillNoStaleRedelivery() {
    // Regression: after a full wrap-around and drain, a subsequent add must not
    // resurrect already-consumed elements.
    CircularBuffer<Integer> cb = new CircularBuffer<>(3);
    cb.add(1);
    cb.add(2);
    cb.add(3);

    final List<Integer> first = new ArrayList<>();
    cb.forEach(
        new Function<Integer, Boolean>() {
          @Override
          public Boolean apply(Integer value) {
            first.add(value);
            return true;
          }
        });
    assertEquals(Arrays.asList(1, 2, 3), first);
    assertEquals(0, cb.getLength());

    cb.add(4);
    assertEquals(1, cb.getLength());

    final List<Integer> second = new ArrayList<>();
    cb.forEach(
        new Function<Integer, Boolean>() {
          @Override
          public Boolean apply(Integer value) {
            second.add(value);
            return true;
          }
        });
    assertEquals(Arrays.asList(4), second);
    assertEquals(0, cb.getLength());
  }

  @Test
  public void testPartialConsumptionKeepsRemainder() {
    CircularBuffer<Integer> cb = new CircularBuffer<>(3);
    cb.add(1);
    cb.add(2);
    cb.add(3);

    final List<Integer> consumed = new ArrayList<>();
    // consume only the first element, then refuse
    assertFalse(
        cb.forEach(
            new Function<Integer, Boolean>() {
              @Override
              public Boolean apply(Integer value) {
                if (consumed.isEmpty()) {
                  consumed.add(value);
                  return true;
                }
                return false;
              }
            }));
    // the refused element (2) and everything after it must be retained
    // (only 1 was consumed - but the old contract retains the element the functor rejected)
    assertEquals(Arrays.asList(1), consumed);

    final List<Integer> rest = new ArrayList<>();
    cb.forEach(
        new Function<Integer, Boolean>() {
          @Override
          public Boolean apply(Integer value) {
            rest.add(value);
            return true;
          }
        });
    assertEquals(Arrays.asList(2, 3), rest);
  }

  @Test
  public void testDoNext() {
    CircularBuffer<Integer> cb = new CircularBuffer<>(2);
    cb.add(1);
    cb.add(2);

    final List<Integer> seen = new ArrayList<>();
    Function<Integer, Boolean> take =
        new Function<Integer, Boolean>() {
          @Override
          public Boolean apply(Integer value) {
            seen.add(value);
            return true;
          }
        };
    assertTrue(cb.doNext(take));
    assertTrue(cb.doNext(take));
    assertFalse(cb.doNext(take));
    assertEquals(Arrays.asList(1, 2), seen);
    assertEquals(0, cb.getLength());
  }

  @Test
  public void testEmpty() {
    CircularBuffer<Integer> cb = new CircularBuffer<>(2);

    final List<Integer> elements = new ArrayList<>();
    cb.forEach(
        new Function<Integer, Boolean>() {
          @Override
          public Boolean apply(Integer value) {
            elements.add(value);
            return true;
          }
        });
    assertTrue(elements.isEmpty());
  }
}
