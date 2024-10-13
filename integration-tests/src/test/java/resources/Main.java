/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @author Jaroslav Bachorik
 */
public class Main extends TestApp {
  private String id = "xxx";
    private String field;
    private static String sField;

  public static void main(String[] args) throws Exception {
    Main i = new Main();
    i.start();
  }

  @Override
  protected void startWork() {
    while (!Thread.currentThread().isInterrupted()) {
      callA();
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void callA() {
    field = "AAA";
        sField = "BBB";print("i=" + callB(1, "Hello World"));
  }

  private int callB(int i, String s) {
    print("[" + i + "] = " + s + ", field = " + field + ", sField = " + sField);
    return i + 1;
  }

  @Override
  public void print(String msg) {
    System.out.println(msg);
    System.out.flush();
  }
}
