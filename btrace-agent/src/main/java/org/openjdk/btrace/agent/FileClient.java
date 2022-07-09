/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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
package org.openjdk.btrace.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.security.CodeSigner;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.ExitCommand;
import org.openjdk.btrace.core.comm.InstrumentCommand;
import org.openjdk.btrace.core.comm.PrintableCommand;
import org.openjdk.btrace.instr.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a local client communicated by trace file. The trace script is specified as a File of
 * a .class file or a byte array containing bytecode of the trace script.
 *
 * @author A. Sundararajan
 * @author J.Bachorik
 */
class FileClient extends Client {
  private static final Logger log = LoggerFactory.getLogger(FileClient.class);

  private final AtomicBoolean noOutputNotified = new AtomicBoolean(false);

  private boolean canLoadPack = true;

  FileClient(ClientContext ctx, File scriptFile) throws IOException {
    super(ctx);
    if (!init(readScript(scriptFile))) {
      log.warn("Unable to load BTrace script {}", scriptFile);
    }
  }

  private static byte[] readAll(InputStream is, long size) throws IOException {
    if (is == null) throw new NullPointerException();

    byte[] buf = new byte[size != -1 ? Math.min((int) size, 512 * 1024 * 1024) : 8192];
    int bufsize = buf.length;
    int off = 0;
    int read;
    while ((read = is.read(buf, off, bufsize - off)) > -1) {
      off += read;
      if (off >= bufsize) {
        buf = Arrays.copyOf(buf, bufsize * 2);
        bufsize = buf.length;
      }
    }
    return Arrays.copyOf(buf, off);
  }

  private boolean init(byte[] code) throws IOException {
    InstrumentCommand cmd = new InstrumentCommand(code, argsMap);
    boolean ret = loadClass(cmd, canLoadPack) != null;
    if (ret) {
      initialize();
    }
    return ret;
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  public void onCommand(Command cmd) throws IOException {
    if (log.isDebugEnabled()) {
      log.debug("client {}: got {}", getClassName(), cmd);
    }
    switch (cmd.getType()) {
      case Command.EXIT:
        onExit(((ExitCommand) cmd).getExitCode());
        break;
      default:
        if (cmd instanceof PrintableCommand) {
          if (out == null) {
            if (noOutputNotified.compareAndSet(false, true)) {
              log.debug("No output stream. DataCommand output is ignored.");
            }
          } else {
            ((PrintableCommand) cmd).print(out);
          }
        }
        break;
    }
  }

  private byte[] readScript(File file) throws IOException {
    String path = file.getPath();
    if (path.startsWith(Constants.EMBEDDED_BTRACE_SECTION_HEADER)) {
      return settings.isTrusted() ? loadQuick(path) : loadWithSecurity(path);
    } else {
      int size = (int) file.length();
      try (FileInputStream fis = new FileInputStream(file)) {
        return readAll(fis, size);
      }
    }
  }

  private byte[] loadQuick(String path) throws IOException {
    try (InputStream is = ClassLoader.getSystemResourceAsStream(path)) {
      return readAll(is, -1);
    }
  }

  private byte[] loadWithSecurity(String path) throws IOException {
    URL scriptUrl = ClassLoader.getSystemResource(path);
    if (scriptUrl.getProtocol().equals("jar")) {
      String jarPath = scriptUrl.getPath().substring(5, scriptUrl.getPath().indexOf("!"));
      JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
      Enumeration<JarEntry> ens = jar.entries();

      while (ens.hasMoreElements()) {
        JarEntry en = ens.nextElement();

        if (!en.isDirectory()) {
          if (en.toString().equals(path)) {
            byte[] data = readAll(jar.getInputStream(en), en.getSize());
            CodeSigner[] signers = en.getCodeSigners();
            canLoadPack = signers != null && signers.length != 0;
            return data;
          }
        }
      }
    }
    return null;
  }
}
