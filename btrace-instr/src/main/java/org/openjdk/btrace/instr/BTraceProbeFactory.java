/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 *
 * Copyright (c) 2017, 2018, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.SharedSettings;

/**
 * A factory class for {@link BTraceProbeNode} instances
 *
 * @author Jaroslav Bachorik
 */
public final class BTraceProbeFactory {
  private final SharedSettings settings;
  private final DebugSupport debug;
  private final boolean canLoadPack;

  public BTraceProbeFactory(SharedSettings settings) {
    this(settings, true);
  }

  public BTraceProbeFactory(SharedSettings settings, boolean canLoadPack) {
    this.settings = settings;
    debug = new DebugSupport(settings);
    this.canLoadPack = canLoadPack;
  }

  private static void applyArgs(BTraceProbe bp, ArgsMap argsMap) {
    if (bp != null && argsMap != null && !argsMap.isEmpty()) {
      bp.applyArgs(argsMap);
    }
  }

  SharedSettings getSettings() {
    return settings;
  }

  public BTraceProbe createProbe(byte[] code) {
    return createProbe(code, null);
  }

  public BTraceProbe createProbe(byte[] code, ArgsMap argsMap) {
    BTraceProbe bp = null;

    int mgc =
        ((code[0] & 0xff) << 24)
            | ((code[1] & 0xff) << 16)
            | ((code[2] & 0xff) << 8)
            | ((code[3] & 0xff));
    if (mgc == BTraceProbePersisted.MAGIC) {
      if (canLoadPack) {
        BTraceProbePersisted bpp = new BTraceProbePersisted(this);
        try (DataInputStream dis =
            new DataInputStream(
                new ByteArrayInputStream(Arrays.copyOfRange(code, 4, code.length)))) {
          bpp.read(dis);
          bp = bpp;
        } catch (IOException e) {
          debug.debug(e);
        }
      }
    } else {
      bp = new BTraceProbeNode(this, code);
    }

    applyArgs(bp, argsMap);
    return bp;
  }

  public BTraceProbe createProbe(InputStream code) {
    return createProbe(code, null);
  }

  public BTraceProbe createProbe(InputStream code, ArgsMap argsMap) {
    BTraceProbe bp = null;
    try (DataInputStream dis = new DataInputStream(code)) {
      dis.mark(0);
      int mgc = dis.readInt();
      if (mgc == BTraceProbePersisted.MAGIC) {
        BTraceProbePersisted bpp = new BTraceProbePersisted(this);
        bpp.read(dis);
        bp = bpp;
      } else {
        code.reset();
        bp = new BTraceProbeNode(this, code);
      }
    } catch (IOException e) {
      if (debug.isDebug()) {
        debug.debug(e);
      }
    }

    applyArgs(bp, argsMap);
    return bp;
  }
}
