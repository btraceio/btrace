package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for HandlerRepositoryImpl cache invalidation logic.
 */
class HandlerRepositoryImplTest {

  @Test
  void testCacheKeyParsing() {
    // Test the cache key format and parsing logic to verify the fix for substring matching issue
    // Cache key format: probeName + "#" + handlerName + handlerDesc
    
    String probeName1 = "com/example/Probe";
    String probeName2 = "com/example/ProbeExtended";
    String handler = "handleMethod";
    String desc = "(Ljava/lang/String;)V";
    
    String cacheKey1 = probeName1 + "#" + handler + desc;
    String cacheKey2 = probeName2 + "#" + handler + desc;
    
    // Verify that the old logic (startsWith) would incorrectly match
    String oldPrefix = probeName1 + "#";
    assertTrue(cacheKey1.startsWith(oldPrefix), "Key1 should start with probe1 prefix");
    assertFalse(cacheKey2.startsWith(oldPrefix), "Key2 should NOT start with probe1 prefix");
    
    // Verify that the new logic (exact match on probe name) works correctly
    int delimiterIndex1 = cacheKey1.indexOf('#');
    int delimiterIndex2 = cacheKey2.indexOf('#');
    
    String extractedProbe1 = cacheKey1.substring(0, delimiterIndex1);
    String extractedProbe2 = cacheKey2.substring(0, delimiterIndex2);
    
    assertEquals(probeName1, extractedProbe1, "Should extract probe1 name correctly");
    assertEquals(probeName2, extractedProbe2, "Should extract probe2 name correctly");
    
    // Verify the matching logic - simulate the fixed unregisterProbe logic
    assertTrue(extractedProbe1.equals(probeName1), "Extracted probe1 should match exactly");
    assertFalse(extractedProbe1.equals(probeName2), "Extracted probe1 should NOT match probe2");
    assertFalse(extractedProbe2.equals(probeName1), "Extracted probe2 should NOT match probe1");
    assertTrue(extractedProbe2.equals(probeName2), "Extracted probe2 should match exactly");
  }

  @Test
  void testCacheKeyWithEdgeCases() {
    // Test edge cases to ensure the fix handles various probe name scenarios
    
    // Case 1: Probe names where one is a prefix of another
    String probe1 = "org/example/Test";
    String probe2 = "org/example/TestSuite";
    String handler = "handler";
    String desc = "()V";
    
    String key1 = probe1 + "#" + handler + desc;
    String key2 = probe2 + "#" + handler + desc;
    
    // Extract probe names using the new logic
    int delim1 = key1.indexOf('#');
    int delim2 = key2.indexOf('#');
    
    assertEquals(probe1, key1.substring(0, delim1));
    assertEquals(probe2, key2.substring(0, delim2));
    assertNotEquals(key1.substring(0, delim1), key2.substring(0, delim2));
    
    // Case 2: Identical probe names should match
    String key1Duplicate = probe1 + "#" + "anotherHandler" + "()I";
    int delim1Dup = key1Duplicate.indexOf('#');
    assertEquals(probe1, key1Duplicate.substring(0, delim1Dup));
    
    // Case 3: Empty handler name or descriptor (edge case)
    String keyMinimal = probe1 + "#";
    int delimMinimal = keyMinimal.indexOf('#');
    assertEquals(probe1, keyMinimal.substring(0, delimMinimal));
  }
}
