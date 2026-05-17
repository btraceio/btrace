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
package io.btrace.agent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.btrace.core.SharedSettings;

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
        log.debug("Failed to create directory: {}", f, e);
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
