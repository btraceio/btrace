package org.openjdk.btrace.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PrefixMapTest {
  @Test
  void addAndContainsWorkWithDuplicates() {
    PrefixMap pm = new PrefixMap();
    pm.add("abc");
    pm.add("abc"); // duplicate
    pm.add("abcd");

    assertTrue(pm.contains("abc"));
    assertTrue(pm.contains("abcdef"));
    assertTrue(pm.contains("abcd"));
    assertFalse(pm.contains("ab"));
    assertFalse(pm.contains("abX"));
  }
}

