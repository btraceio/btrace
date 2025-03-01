package org.openjdk.btrace.core.collections;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

class FilteredCollectionTest {
    static MethodHandle filterMh;
    static Collection<String> strings = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        filterMh = MethodHandles.lookup().findStatic(FilteredCollectionTest.class, "filter", MethodType.methodType(boolean.class, String.class));
        strings.add("a");
        strings.add("x");
        strings.add("b");
    }

    @Test
    void size() {
        assertEquals(0, new FilteredCollection<String>(Collections.emptyList(), filterMh).size());
        assertEquals(2, new FilteredCollection<>(strings, filterMh).size());
    }

    @Test
    void isEmpty() {
        assertTrue(new FilteredCollection<String>(Collections.emptyList(), filterMh).isEmpty());
        assertFalse(new FilteredCollection<>(strings, filterMh).isEmpty());
    }

    @Test
    void contains() {
        Collection<String> x = new FilteredCollection<>(strings, filterMh);
        assertTrue(x.contains("a"));
        assertTrue(x.contains("b"));
        assertFalse(x.contains("x"));
    }

    @Test
    void iterator() {
        Iterator<String> it = new FilteredCollection<>(strings, filterMh).iterator();
        int cnt = 0;
        while (it.hasNext()) {
            assertNotNull(it.next());
            cnt++;
        }
        assertEquals(2, cnt);
    }

    static boolean filter(String val) {
        return !val.contains("x");
    }
}