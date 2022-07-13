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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.openjdk.btrace.core.SharedSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents various strategies available for dumping BTrace output to a file.
 *
 * @author Jaroslav Bachorik
 */
abstract class TraceOutputWriter extends Writer {
  private static final Logger log = LoggerFactory.getLogger(TraceOutputWriter.class);

  protected TraceOutputWriter() {}

  /**
   * Plain file writer - all output will go to one specified file
   *
   * @param output The file to put the output to
   * @return Returns an appropriate {@linkplain TraceOutputWriter} instance or NULL
   */
  public static TraceOutputWriter fileWriter(File output) {
    TraceOutputWriter instance = null;
    try {
      instance = new SimpleFileWriter(output);
    } catch (IOException e) {
      // ignore
    }
    return instance;
  }

  /**
   * Time based rolling file writer. Defaults to 100 allowed output chunks.
   *
   * @param output The file to put the output to
   * @param settings The shared settings
   * @return Returns an appropriate {@linkplain TraceOutputWriter} instance or NULL
   */
  public static TraceOutputWriter rollingFileWriter(File output, SharedSettings settings) {
    TraceOutputWriter instance = null;
    try {
      instance = new TimeBasedRollingFileWriter(output, settings);
    } catch (IOException e) {
      // ignore
    }
    return instance;
  }

  private static void ensurePathExists(File f) {
    if (f == null || f.exists()) return;

    ensurePathExists(f.getParentFile());

    if (!f.getName().endsWith(".btrace")) { // not creating the actual file
      try {
        f.createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
        // ignore and continue
      }
    }
  }

  private static class SimpleFileWriter extends TraceOutputWriter {
    private static final Logger log = LoggerFactory.getLogger(SimpleFileWriter.class);

    private final FileWriter delegate;

    @SuppressWarnings("DefaultCharset")
    public SimpleFileWriter(File output) throws IOException {
      try {
        File parent = output.getParentFile();
        if (parent != null) {
          output.getParentFile().mkdirs();
        }
        delegate = new FileWriter(output);
      } catch (IOException e) {
        log.debug("Failed to create file output {}", output.getName(), e);
        throw e;
      }
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
      delegate.write(cbuf, off, len);
    }
  }

  @SuppressWarnings("DefaultCharset")
  private abstract static class RollingFileWriter extends TraceOutputWriter {
    private static final Logger log = LoggerFactory.getLogger(RollingFileWriter.class);
    protected final SharedSettings settings;
    private final ReentrantReadWriteLock writerLock = new ReentrantReadWriteLock();
    private final String path, baseName;
    // @GuardedBy writerLock
    private FileWriter currentFileWriter;
    private int counter = 1;

    public RollingFileWriter(File output, SharedSettings settings) throws IOException {
      try {
        output.getParentFile().mkdirs();
        currentFileWriter = new FileWriter(output);
        path = output.getParentFile().getAbsolutePath();
        baseName = output.getName();
        this.settings = settings;
      } catch (IOException e) {
        log.debug("Failed to create rolling file output {}", output.getName(), e);
        throw e;
      }
    }

    @Override
    public final void close() throws IOException {
      try {
        writerLock.readLock().lock();
        currentFileWriter.close();
      } finally {
        writerLock.readLock().unlock();
      }
    }

    @Override
    public final void flush() throws IOException {
      try {
        writerLock.readLock().lock();
        currentFileWriter.flush();
      } finally {
        writerLock.readLock().unlock();
      }

      if (needsRoll()) {
        nextWriter();
      }
    }

    @Override
    public final void write(char[] cbuf, int off, int len) throws IOException {
      try {
        writerLock.readLock().lock();
        currentFileWriter.write(cbuf, off, len);
      } finally {
        writerLock.readLock().unlock();
      }
    }

    private void nextWriter() {
      try {
        writerLock.writeLock().lock();
        currentFileWriter = getNextWriter();
      } catch (IOException e) {
        log.debug("Failed to roll over", e);
      } finally {
        writerLock.writeLock().unlock();
      }
    }

    private FileWriter getNextWriter() throws IOException {
      currentFileWriter.close();
      File scriptOutputFile_renameFrom = new File(path + File.separator + baseName);
      File scriptOutputFile_renameTo =
          new File(path + File.separator + baseName + "." + (counter++));

      if (scriptOutputFile_renameTo.exists()) {
        scriptOutputFile_renameTo.delete();
      }
      scriptOutputFile_renameFrom.renameTo(scriptOutputFile_renameTo);
      scriptOutputFile_renameFrom = new File(path + File.separator + baseName);
      if (counter > settings.getFileRollMaxRolls()) {
        counter = 1;
      }
      return new FileWriter(scriptOutputFile_renameFrom);
    }

    protected abstract boolean needsRoll();
  }

  private static class TimeBasedRollingFileWriter extends RollingFileWriter {
    private final TimeUnit unit = TimeUnit.MILLISECONDS;
    private long lastTimeStamp = System.currentTimeMillis();

    public TimeBasedRollingFileWriter(File output, SharedSettings settings) throws IOException {
      super(output, settings);
    }

    @Override
    protected boolean needsRoll() {
      long currTime = System.currentTimeMillis();
      long myInterval = currTime - lastTimeStamp;
      if (unit.convert(myInterval, TimeUnit.MILLISECONDS) >= settings.getFileRollMilliseconds()) {
        lastTimeStamp = currTime;
        return true;
      }
      return false;
    }
  }
}
