/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.samples;

import com.sun.btrace.annotations.*;
import static com.sun.btrace.BTraceUtils.*;
import java.lang.management.MemoryUsage;

/**
 * This sample traces memory threshold exceeds.
 * You need to specify the memory pool to watch
 * out and the usage threshold. You can write 
 * script that dumps heap by dumpHeap on crossing
 * the threshold instead of just printing a message.
 * Note that the name of the old gen is dependent on
 * GC algorithm. With ParallelGC, the name is "PS Old Gen".
 */
@BTrace 
public class MemAlerter {
  @OnLowMemory(
      pool = "Tenured Gen",
      threshold=6000000     
  )
  public static void onLowMem(MemoryUsage mu) {
      println(mu); 
  }
}
