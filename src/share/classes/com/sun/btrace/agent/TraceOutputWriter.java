/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.agent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class represents various strategies available for dumping BTrace
 * output to a file.
 *
 * @author Jaroslav Bachorik
 */
abstract public class TraceOutputWriter extends Writer {
    static private class SimpleFileOutput extends TraceOutputWriter {
        final private FileWriter delegate;

        public SimpleFileOutput(File output) throws IOException {
            delegate = new FileWriter(output);
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

    static abstract private class RollingFileWriter extends TraceOutputWriter {
        final private ReentrantReadWriteLock writerLock = new ReentrantReadWriteLock();
        // @GuardedBy writerLock
        private FileWriter currentFileWriter;
        private String path, baseName;
        private int counter = 1;
        private int maxRolls;

        public RollingFileWriter(File output) throws IOException {
            this(output, 5);
        }
        
        public RollingFileWriter(File output, int maxRolls) throws IOException {
            currentFileWriter = new FileWriter(output);
            path = output.getParentFile().getAbsolutePath();
            baseName = output.getName();
            this.maxRolls = maxRolls;
        }

        @Override
        final public void close() throws IOException {
            try {
                writerLock.readLock().lock();
                currentFileWriter.close();
            } finally {
                writerLock.readLock().unlock();
            }
        }

        @Override
        final public void flush() throws IOException {
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
        final public void write(char[] cbuf, int off, int len) throws IOException {
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
                Main.debugPrint(e);
            } finally {
                writerLock.writeLock().unlock();
            }
        }

        private FileWriter getNextWriter() throws IOException {
            File f = new File(path + File.separator + baseName + "." + (counter++));
            if (f.exists()) {
                f.delete();
            }
            if (counter > maxRolls) {
                counter = 1;
            }
            return new FileWriter(f);
        }

        abstract protected boolean needsRoll();
    }

    static private class TimeBasedRollingFileWriter extends RollingFileWriter {
        private long lastTimeStamp = System.currentTimeMillis();
        private long interval;
        private TimeUnit unit;

        public TimeBasedRollingFileWriter(File output, int maxRolls, long interval, TimeUnit unit) throws IOException {
            super(output, maxRolls);
            this.interval = interval;
            this.unit = unit;
        }

        public TimeBasedRollingFileWriter(File output, long interval, TimeUnit unit) throws IOException {
            super(output);
            this.interval = interval;
            this.unit = unit;
        }

        protected boolean needsRoll() {
            long currTime = System.currentTimeMillis();
            long myInterval =  currTime- lastTimeStamp;
            if (unit.convert(myInterval, TimeUnit.MILLISECONDS) >= interval) {
                lastTimeStamp = currTime;
                return true;
            }
            return false;
        }
    }

    /**
     * Plain file writer - all output will go to one specified file
     * @param output The file to put the output to
     * @return Returns an appropriate {@linkplain  TraceOutputWriter} instance or NULL
     */
    public static TraceOutputWriter fileWriter(File output) {
        TraceOutputWriter instance = null;
        try {
            instance = new SimpleFileOutput(output);
        } catch (IOException e) {
            Main.debugPrint(e);
        }
        return instance;
    }

    /**
     * Time based rolling file writer. Defaults to 100 allowed output chunks.
     * @param output The file to put the output to
     * @param interval The interval between rolling the output file
     * @param unit The {@linkplain TimeUnit} value the interval is represented in
     * @return Returns an appropriate {@linkplain  TraceOutputWriter} instance or NULL
     */
    public static TraceOutputWriter rollingFileWriter(File output, long interval, TimeUnit unit) {
        TraceOutputWriter instance = null;
        try {
            instance = new TimeBasedRollingFileWriter(null, interval, unit);
        } catch (IOException e) {
            Main.debugPrint(e);
        }
        return instance;
    }

    /**
     * Time based rolling file writer.
     * @param output The file to put the output to
     * @param maxRolls Maximum number of roll chunks
     * @param interval The interval between rolling the output file
     * @param unit The {@linkplain TimeUnit} value the interval is represented in
     * @return Returns an appropriate {@linkplain  TraceOutputWriter} instance or NULL
     */
    public static TraceOutputWriter rollingFileWriter(File output, int maxRolls, long interval, TimeUnit unit) {
        TraceOutputWriter instance = null;
        try {
            instance = new TimeBasedRollingFileWriter(output, maxRolls, interval, unit);
        } catch (IOException e) {
            Main.debugPrint(e);
        }
        return instance;
    }
}
