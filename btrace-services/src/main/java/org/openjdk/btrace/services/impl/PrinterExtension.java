/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.btrace.services.impl;

import org.openjdk.btrace.core.comm.MessageCommand;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.core.extensions.ExtensionContext;
import org.openjdk.btrace.core.extensions.ExtensionDescriptor;

/**
 * Printer extension for BTrace.
 *
 * <p>A simple printer service that sends messages to the BTrace client.
 *
 * <p>Use the following code to obtain an instance:
 *
 * <pre>
 * <code>
 * {@literal @}Injected(ServiceType.EXTENSION)
 * private static PrinterExtension printer;
 * </code>
 * </pre>
 */
@ExtensionDescriptor(
    name = "printer",
    version = "1.0",
    description = "Simple printer service for BTrace")
public final class PrinterExtension extends Extension {
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  private volatile ExtensionContext ctx;

  @Override
  public void initialize(ExtensionContext ctx) {
    super.initialize(ctx);
    this.ctx = ctx;
  }

  /**
   * Prints a string message.
   *
   * @param str the message to print
   */
  public void print(String str) {
    send(str);
  }

  /**
   * Prints a string message followed by a line separator.
   *
   * @param str the message to print
   */
  public void println(String str) {
    print(str + LINE_SEPARATOR);
  }

  /** Prints a line separator. */
  public void println() {
    send(LINE_SEPARATOR);
  }

  private void send(String msg) {
    if (ctx != null) {
      ctx.send(new MessageCommand(0L, msg));
    }
  }
}
