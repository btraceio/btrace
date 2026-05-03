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
package io.btrace.core;

import java.util.Map;
import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.PermissionSet;

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
  public static final String GRANT_PERMISSIONS_KEY = "grantPermissions";
  public static final String DENY_PERMISSIONS_KEY = "denyPermissions";
  public static final String GRANT_ALL_KEY = "grantAll";

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
  private PermissionSet grantedPermissions = PermissionSet.empty();
  private PermissionSet deniedPermissions = PermissionSet.empty();
  private boolean grantAll = false;

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
    s = (String) params.get(GRANT_PERMISSIONS_KEY);
    if (s != null && !s.isEmpty()) {
      grantedPermissions = parsePermissions(s);
    }
    s = (String) params.get(DENY_PERMISSIONS_KEY);
    if (s != null && !s.isEmpty()) {
      deniedPermissions = parsePermissions(s);
    }
    b = (Boolean) params.get(GRANT_ALL_KEY);
    if (b != null) {
      grantAll = b;
    }
  }

  public static PermissionSet parsePermissions(String permissionString) {
    if (permissionString == null || permissionString.isEmpty()) {
      return PermissionSet.empty();
    }
    PermissionSet result = PermissionSet.empty();
    for (String name : permissionString.split(",")) {
      String trimmed = name.trim().toUpperCase();
      if (!trimmed.isEmpty()) {
        try {
          Permission p = Permission.valueOf(trimmed);
          result = result.with(p);
        } catch (IllegalArgumentException e) {
          // Ignore invalid permission names
        }
      }
    }
    return result;
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
    grantedPermissions = other.grantedPermissions;
    deniedPermissions = other.deniedPermissions;
    grantAll = other.grantAll;
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

  public PermissionSet getGrantedPermissions() {
    return grantedPermissions;
  }

  public void setGrantedPermissions(PermissionSet grantedPermissions) {
    this.grantedPermissions = grantedPermissions;
  }

  public PermissionSet getDeniedPermissions() {
    return deniedPermissions;
  }

  public void setDeniedPermissions(PermissionSet deniedPermissions) {
    this.deniedPermissions = deniedPermissions;
  }

  public boolean isGrantAll() {
    return grantAll;
  }

  public void setGrantAll(boolean grantAll) {
    this.grantAll = grantAll;
  }

  /**
   * Computes the effective permissions based on granted, denied, and grantAll settings.
   *
   * <p>Logic:
   *
   * <ul>
   *   <li>If grantAll is true, all permissions are granted
   *   <li>Otherwise, start with standard permissions (default + standard tier)
   *   <li>Add explicitly granted permissions
   *   <li>Remove explicitly denied permissions
   * </ul>
   *
   * @return the effective permission set
   */
  public PermissionSet getEffectivePermissions() {
    if (grantAll) {
      return PermissionSet.all();
    }
    PermissionSet effective = PermissionSet.standard();
    for (Permission p : grantedPermissions) {
      effective = effective.with(p);
    }
    for (Permission p : deniedPermissions) {
      effective = effective.without(p);
    }
    return effective;
  }
}
