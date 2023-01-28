/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package resources;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @author Jaroslav Bachorik
 */
public abstract class TestApp implements TestPrinter {
  public final void start() throws Exception {
    Thread t =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                startWork();
              }
            },
            "Worker Thread");
    System.out.println("ready:" + getPID());
    System.out.flush();
    t.setDaemon(true);
    t.start();

    do {
      String resp =
          new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
      System.out.println("Received " + resp + " - " + "done".contains(resp));
      if ("done".contains(resp)) {
        System.out.flush();
        System.out.println(System.currentTimeMillis() + ":  Interrupting the worker thread");
        t.interrupt();
        System.out.println(
            System.currentTimeMillis() + ": Waiting for the worker thread to finish");
        t.join(1000);
        if (t.isAlive()) {
          Thread.dumpStack();
          throw new RuntimeException("Dangling worker thread");
        }
        System.out.println(System.currentTimeMillis() + ": Worker thread finished");
        break;
      }
    } while (true);
  }

  private static long getPID() {
    String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    return Long.parseLong(processName.split("@")[0]);
  }

  /** The work here should be done repeatedly until the thread gets interrupted */
  protected abstract void startWork();
}
