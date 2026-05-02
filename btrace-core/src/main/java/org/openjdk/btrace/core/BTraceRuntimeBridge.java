/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

package org.openjdk.btrace.core;

/**
 * Minimal bootstrap-visible bridge used by injected code to reach the runtime implementation.
 */
public interface BTraceRuntimeBridge {
  void start();

  void leave();

  void handleException(Throwable th);

  boolean isDisabled();

  void newPerfCounter(Object value, String name, String desc);

  int getPerfInt(String name);

  void putPerfInt(int value, String name);

  float getPerfFloat(String name);

  void putPerfFloat(float value, String name);

  long getPerfLong(String name);

  void putPerfLong(long value, String name);

  String getPerfString(String name);

  void putPerfString(String value, String name);
}
