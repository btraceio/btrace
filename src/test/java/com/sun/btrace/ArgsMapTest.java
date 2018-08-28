package com.sun.btrace;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArgsMapTest {
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String VALUE1 = "value1";
    private static final String VALUE2 = "value2";

    private ArgsMap instance;

    @Before
    public void setUp() {
        instance = new ArgsMap();
        instance.put(KEY1, VALUE1);
        instance.put(KEY2, VALUE2);
    }

    @Test
    public void templateExisting() {
        String value = instance.template(KEY1 + "=${" + KEY1 + "}");
        assertEquals(KEY1 + "=" + VALUE1, value);
    }

    @Test
    public void templateNonExisting() {
        String orig = KEY1 + "=${key3}";
        String value = instance.template(orig);
        assertEquals(orig, value);
    }

    @Test
    public void templateTrailing$() {
        String orig = KEY1 + "$";
        String value = instance.template(orig);
        assertEquals(orig, value);
    }

    @Test
    public void templateUnclosedPlaceholder() {
        String orig = KEY1 + "${";
        String value = instance.template(orig);
        assertEquals(orig, value);
    }

    @Test
    public void templateSingle$() {
        String orig = KEY1 + "$" + KEY2;
        String value = instance.template(orig);
        assertEquals(orig, value);
    }
}
