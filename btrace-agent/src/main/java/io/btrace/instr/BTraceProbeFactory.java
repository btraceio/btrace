/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import io.btrace.core.ArgsMap;
import io.btrace.core.SharedSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory class for {@link BTraceProbeNode} instances
 *
 * @author Jaroslav Bachorik
 */
public final class BTraceProbeFactory {
  private static final Logger log = LoggerFactory.getLogger(BTraceProbeFactory.class);

  private static final int CLASS_MAGIC = 0xCAFEBABE;

  private final SharedSettings settings;

  public BTraceProbeFactory(SharedSettings settings) {
    this.settings = settings;
  }

  private static void applyArgs(BTraceProbe bp, ArgsMap argsMap) {
    if (bp != null && argsMap != null && !argsMap.isEmpty()) {
      bp.applyArgs(argsMap);
    }
  }

  /**
   * Check if a particular file can be loaded as a BTrace probe. Currently only the plain class file
   * and BTrace probe pack are supported.
   *
   * @param filePath the file path
   * @return {@literal true} if a BTrace probe can be reconstructed from data in the given file
   */
  public static boolean canLoad(String filePath) {
    return canLoad(filePath, null);
  }

  public static boolean canLoad(String filePath, ClassLoader cl) {
    if (filePath == null) {
      return false;
    }
    Path path = Paths.get(filePath);
    InputStream is = null;
    try {
      if (!Files.exists(path)) {
        // try to load from the classpath
        if (cl == null) {
          cl = ClassLoader.getSystemClassLoader();
        }
        is = cl.getResourceAsStream("META-INF/btrace/" + filePath);
      } else {
        is = Files.newInputStream(path);
      }
      if (is != null) {
        try {
          try (DataInputStream dis = new DataInputStream(is)) {
            int magic = dis.readInt();
            return magic == CLASS_MAGIC || magic == BTraceProbePersisted.MAGIC;
          }
        } catch (IOException ignored) {
          is = null;
        }
      }
    } catch (IOException ignored) {
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (IOException ignored) {
        }
      }
    }
    return false;
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
      BTraceProbePersisted bpp = new BTraceProbePersisted(this);
      try (DataInputStream dis =
          new DataInputStream(new ByteArrayInputStream(Arrays.copyOfRange(code, 4, code.length)))) {
        bpp.read(dis);
        bp = bpp;
      } catch (IOException e) {
        log.debug("Failed to read BTrace pack", e);
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
      log.debug("Failed to create a probe", e);
    }

    applyArgs(bp, argsMap);
    return bp;
  }
}
