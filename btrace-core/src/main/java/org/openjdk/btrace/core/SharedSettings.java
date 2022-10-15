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
package org.openjdk.btrace.core;

import java.util.Map;

/**
 * @author Jaroslav Bachorik
 */
public final class SharedSettings {
  public static final String DEBUG_KEY = "debug";
  public static final String DUMP_DIR_KEY = "dumpDir";
  @Deprecated public static final String UNSAFE_KEY = "unsafe";
  public static final String TRUSTED_KEY = "trusted";
  public static final String TRACK_RETRANSFORMS_KEY = "trackRetransforms";
  public static final String PROBE_DESC_PATH_KEY = "probeDescPath";
  public static final String STATSD_HOST_KEY = "statsdHost";
  public static final String STATSD_PORT_KEY = "statsdPort";
  public static final String FILEROLL_INTERVAL_KEY = "fileRollMilliseconds";
  public static final String FILEROLL_MAXROLLS_KEY = "fileRollMaxRolls";
  public static final String OUTPUT_FILE_KEY = "scriptOutputFile";
  public static final String OUTPUT_DIR_KEY = "scriptOutputDir";

  public static final SharedSettings GLOBAL = new SharedSettings();

  private boolean debug = false;
  private boolean trusted = false;
  private boolean trackRetransforms = false;
  private boolean retransformStartup = true;
  private String dumpDir = null;
  private String probeDescPath = ".";
  private String bootClassPath = "";
  private final String systemClassPath = "";
  private String statsdHost = null;
  private int statsdPort = 8125; // default statsd port
  private int fileRollMilliseconds = Integer.MIN_VALUE;
  private int fileRollMaxRolls = 5; // default hold max 100 logs
  private String outputFile;
  private String scriptDir;
  private String scriptOutputDir;
  private String clientName;

  public void from(Map<String, Object> params) {
    Boolean b = (Boolean) params.get(DEBUG_KEY);
    if (b != null) {
      debug = b;
    }
    b = (Boolean) params.get(TRACK_RETRANSFORMS_KEY);
    if (b != null) {
      trackRetransforms = b;
    }
    b = (Boolean) params.get(UNSAFE_KEY);
    if (b != null) {
      trusted = b;
    }
    b = (Boolean) params.get(TRUSTED_KEY);
    if (b != null) {
      trusted |= b;
    }
    String s = (String) params.get(DUMP_DIR_KEY);
    if (s != null && !s.isEmpty()) {
      dumpDir = s;
    }
    s = (String) params.get(PROBE_DESC_PATH_KEY);
    if (s != null && !s.isEmpty()) {
      probeDescPath = s;
    }

    s = (String) params.get(Args.BOOT_CLASS_PATH);
    if (s != null && !s.isEmpty()) {
      bootClassPath = s;
    }

    s = (String) params.get(STATSD_HOST_KEY);
    if (s != null && !s.isEmpty()) {
      statsdHost = s;
    }
    Integer i = (Integer) params.get(STATSD_PORT_KEY);
    if (i != null) {
      statsdPort = i;
    }
    i = (Integer) params.get(FILEROLL_INTERVAL_KEY);
    if (i != null) {
      fileRollMilliseconds = i;
    }
    i = (Integer) params.get(FILEROLL_MAXROLLS_KEY);
    if (i != null) {
      fileRollMaxRolls = i;
    }
    s = (String) params.get(OUTPUT_FILE_KEY);
    if (s != null && !s.isEmpty()) {
      outputFile = s;
    }
    s = (String) params.get(OUTPUT_DIR_KEY);
    if (s != null && !s.isEmpty()) {
      scriptOutputDir = s;
    }
  }

  public void from(SharedSettings other) {
    clientName = other.clientName;
    debug = other.debug;
    dumpDir = other.dumpDir;
    fileRollMilliseconds = other.fileRollMilliseconds;
    fileRollMaxRolls = other.fileRollMaxRolls;
    outputFile = other.outputFile;
    scriptDir = other.scriptDir;
    scriptOutputDir = other.scriptOutputDir;
    probeDescPath = other.probeDescPath;
    bootClassPath = other.bootClassPath;
    retransformStartup = other.retransformStartup;
    statsdHost = other.statsdHost;
    statsdPort = other.statsdPort;
    trackRetransforms = other.trackRetransforms;
    trusted = other.trusted;
  }

  public boolean isDebug() {
    return debug;
  }

  public void setDebug(boolean value) {
    debug = value;
  }

  public boolean isDumpClasses() {
    return dumpDir != null;
  }

  @Deprecated
  /* @deprecated use {@linkplain SharedSettings#isTrusted()} instead */
  public boolean isUnsafe() {
    return trusted;
  }

  public boolean isTrusted() {
    return trusted;
  }

  public void setTrusted(boolean value) {
    trusted = value;
  }

  public String getDumpDir() {
    return dumpDir;
  }

  public void setDumpDir(String value) {
    dumpDir = value;
  }

  public boolean isTrackRetransforms() {
    return trackRetransforms;
  }

  public void setTrackRetransforms(boolean value) {
    trackRetransforms = value;
  }

  public String getProbeDescPath() {
    return probeDescPath;
  }

  public void setProbeDescPath(String probeDescPath) {
    this.probeDescPath = probeDescPath;
  }

  public String getBootClassPath() {
    return bootClassPath;
  }

  public void setBootClassPath(String bootClassPath) {
    this.bootClassPath = bootClassPath;
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

  public boolean isRetransformStartup() {
    return retransformStartup;
  }

  public void setRetransformStartup(boolean val) {
    retransformStartup = val;
  }

  public String getScriptDir() {
    return scriptDir;
  }

  public String getOutputFile() {
    return outputFile;
  }

  public void setOutputFile(String outputFile) {
    this.outputFile = outputFile;
  }

  public String getScriptOutputDir() {
    return scriptOutputDir;
  }

  public void setScriptOutputDir(String scriptOutputDir) {
    this.scriptOutputDir = scriptOutputDir;
  }

  public String getClientName() {
    return clientName;
  }

  public void setClientName(String clientName) {
    this.clientName = clientName;
  }
}
