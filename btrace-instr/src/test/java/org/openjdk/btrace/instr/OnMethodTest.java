/*
 * Copyright (c) 2018, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package org.openjdk.btrace.instr;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.SharedSettings;

public class OnMethodTest {
  private ArgsMap instance;

  @Before
  public void setUp() {
    DebugSupport debug = new DebugSupport(SharedSettings.GLOBAL);
    instance = new ArgsMap(new String[] {"className=ClassA", "methodName=methodA"}, debug);
  }

  @Test
  public void testApplyArgsValid() {
    assertEquals("/.*\\.ClassA/", instance.template("/.*\\.${className}/"));
    assertEquals("methodA", instance.template("${methodName}"));
    assertEquals("${undefined}", instance.template("${undefined}"));
  }
}
