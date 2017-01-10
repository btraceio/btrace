/*
 * Copyright (c) 2017, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Copyright owner designates
 * this particular file as subject to the "Classpath" exception as provided
 * by the owner in the LICENSE file that accompanied this code.
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
 */
package com.sun.btrace.util;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrerfixMapTest {
    private PrefixMap instance;

    public PrerfixMapTest() {
    }

    @Before
    public void setUp() {
        instance = new PrefixMap();
    }

    @Test
    public void testAdd() {
        System.out.println("add");
        CharSequence val = "test/package/AClass.class";
        instance.add(val);
    }

    @Test
    public void testContains() {
        System.out.println("contains");
        CharSequence pkg1 = "test";
        CharSequence pkg2 = "test/package";

        instance.add(pkg1);
        instance.add(pkg2);
        boolean result = instance.contains("test/package/AClass.class");
        assertEquals(true, result);
    }

}
