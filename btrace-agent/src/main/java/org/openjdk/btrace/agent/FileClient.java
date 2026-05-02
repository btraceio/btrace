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
    boolean ret = loadClass(cmd) != null;
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
