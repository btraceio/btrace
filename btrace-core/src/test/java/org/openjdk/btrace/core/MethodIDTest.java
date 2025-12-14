/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 */
package org.openjdk.btrace.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MethodIDTest {

  @BeforeEach
  void resetState() throws Exception {
    // Reset the static fields for isolation between tests
    Field lastFld = MethodID.class.getDeclaredField("lastMethodId");
    Field mapFld = MethodID.class.getDeclaredField("methodIds");

    lastFld.setAccessible(true);
    mapFld.setAccessible(true);

    AtomicInteger last = (AtomicInteger) lastFld.get(null);
    @SuppressWarnings("unchecked")
    Map<String, Integer> map = (Map<String, Integer>) mapFld.get(null);

    last.set(1);
    map.clear();
  }

  @Test
  void singleThreadedConsistency() {
    int id1 = MethodID.getMethodId("A#foo#()V");
    int id2 = MethodID.getMethodId("A#foo#()V");
    int id3 = MethodID.getMethodId("A#bar#()V");

    assertEquals(id1, id2, "Same tag should return same ID");
    assertNotEquals(id1, id3, "Different tags should return different IDs");
  }

  @Test
  void concurrentGenerationHasNoDuplicates() throws Exception {
    int threads = 10;
    int idsPerThread = 1000;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    ConcurrentMap<Integer, String> reverse = new ConcurrentHashMap<>();
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      final int idx = t;
      pool.submit(
          () -> {
            try {
              start.await();
              for (int i = 0; i < idsPerThread; i++) {
                String tag = "C" + idx + "#m" + i + "#()V";
                int id = MethodID.getMethodId(tag);
                String prev = reverse.putIfAbsent(id, tag);
                if (prev != null && !prev.equals(tag)) {
                  fail("Duplicate ID assigned to different tags: " + id + " => " + prev + " vs " + tag);
                }
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              fail(e);
            } finally {
              done.countDown();
            }
          });
    }

    start.countDown();
    assertTrue(done.await(30, TimeUnit.SECONDS), "Tasks should complete timely");
    pool.shutdownNow();

    // Ensure expected cardinality
    assertEquals(threads * idsPerThread, reverse.size(), "Every tag should map to a unique ID");

    // Re-check stability: calling again returns same id
    Set<Integer> secondPass = new HashSet<>();
    for (Map.Entry<Integer, String> e : reverse.entrySet()) {
      int id = MethodID.getMethodId(e.getValue());
      assertTrue(secondPass.add(id), "IDs should still be unique on second pass");
    }
    assertEquals(reverse.size(), secondPass.size());
  }
}
