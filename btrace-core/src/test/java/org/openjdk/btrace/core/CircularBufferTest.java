package org.openjdk.btrace.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

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
