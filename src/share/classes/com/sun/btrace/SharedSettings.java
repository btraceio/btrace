/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.btrace;

import java.util.Map;

/**
 *
 * @author Jaroslav Bachorik
 */
final public class SharedSettings {
    public static final String DEBUG_KEY = "debug";
    public static final String DUMP_DIR_KEY = "dumpDir";
    public static final String UNSAFE_KEY = "unsafe";
    public static final String TRACK_RETRANSFORMS_KEY = "trackRetransforms";
    public static final String PROBE_DESC_PATH_KEY = "probeDescPath";
    public static final String STATSD_HOST_KEY = "statsdHost";
    public static final String STATSD_PORT_KEY = "statsdPort";
    public static final String FILEROLL_INTERVAL_KEY = "fileRollMilliseconds";
    public static final String FILEROLL_MAXROLLS_KEY = "fileRollMaxRolls";

    public static final SharedSettings GLOBAL = new SharedSettings();

    private boolean debug = false;
    private boolean unsafe = false;
    private boolean trackRetransforms = false;
    private boolean retransformStartup = true;
    private String dumpDir = null;
    private String probeDescPath = null;
    private String statsdHost = null;
    private int statsdPort = 8125; // default statsd port
    private int fileRollMilliseconds = Integer.MIN_VALUE;
    private int fileRollMaxRolls = 5; // default hold max 100 logs

    public void from(Map<String, Object> params) {
        Boolean b = (Boolean)params.get(DEBUG_KEY);
        if (b != null) {
            debug = b;
        }
        b = (Boolean)params.get(TRACK_RETRANSFORMS_KEY);
        if (b != null) {
            trackRetransforms = b;
        }
        b = (Boolean)params.get(UNSAFE_KEY);
        if (b != null) {
            unsafe = b;
        }
        String s = (String)params.get(DUMP_DIR_KEY);
        if (s != null && !s.isEmpty()) {
            dumpDir = s;
        }
        s = (String)params.get(PROBE_DESC_PATH_KEY);
        if (s != null && !s.isEmpty()) {
            probeDescPath = s;
        }
        s = (String)params.get(STATSD_HOST_KEY);
        if (s != null && !s.isEmpty()) {
            statsdHost = s;
        }
        Integer i = (Integer)params.get(STATSD_PORT_KEY);
        if (i != null) {
            statsdPort = i;
        }
        i = (Integer)params.get(FILEROLL_INTERVAL_KEY);
        if (i != null) {
            fileRollMilliseconds = i;
        }
        i = (Integer)params.get(FILEROLL_MAXROLLS_KEY);
        if (i != null) {
            fileRollMaxRolls = i;
        }
    }

    public void from(SharedSettings other) {
        debug = other.debug;
        dumpDir = other.dumpDir;
        trackRetransforms = other.trackRetransforms;
        unsafe = other.unsafe;
        probeDescPath = other.probeDescPath;
        statsdHost = other.statsdHost;
        statsdPort = other.statsdPort;
        fileRollMilliseconds = other.fileRollMilliseconds;
        fileRollMaxRolls = other.fileRollMaxRolls;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isDumpClasses() {
        return dumpDir != null;
    }

    public boolean isUnsafe() {
        return unsafe;
    }

    public String getDumpDir() {
        return dumpDir;
    }

    public boolean isTrackRetransforms() {
        return trackRetransforms;
    }

    public String getProbeDescPath() {
        return probeDescPath;
    }

    public void setDebug(boolean value) {
        debug = value;
    }

    public void setUnsafe(boolean value) {
        unsafe = value;
    }

    public void setDumpDir(String value) {
        dumpDir = value;
    }

    public void setTrackRetransforms(boolean value) {
        this.trackRetransforms = value;
    }

    public void setProbeDescPath(String probeDescPath) {
        this.probeDescPath = probeDescPath;
    }

    public String getStatsdHost() {
        return statsdHost;
    }

    public void setStatsdHost(String statsdHost) {
        this.statsdHost = statsdHost;
    }

    public int getStatsdPort() {
        return statsdPort;
    }

    public void setStatsdPort(int statsdPort) {
        this.statsdPort = statsdPort;
    }

    public int getFileRollMilliseconds() {
        return fileRollMilliseconds;
    }

    public void setFileRollMilliseconds(int fileRollMilliseconds) {
        this.fileRollMilliseconds = fileRollMilliseconds;
    }

    public int getFileRollMaxRolls() {
        return fileRollMaxRolls;
    }

    public void setFileRollMaxRolls(int fileRollMaxRolls) {
        this.fileRollMaxRolls = fileRollMaxRolls;
    }

    public void setRetransformStartup(boolean val) {
        this.retransformStartup = val;
    }

    public boolean isRetransformStartup() {
        return retransformStartup;
    }
}
