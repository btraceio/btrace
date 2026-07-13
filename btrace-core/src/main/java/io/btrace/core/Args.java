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

public final class Args {
  public static final String SYSTEM_CLASS_PATH = "systemClassPath";
  public static final String BOOT_CLASS_PATH = "bootClassPath";
  public static final String AGENT_JAR = "agentJar";
  public static final String CONFIG = "config";
  public static final String SCRIPT = "script";
  public static final String SCRIPT_DIR = "scriptdir";
  public static final String STARTUP_RETRANSFORM = "startupRetransform";
  public static final String DUMP_DIR = "dumpDir";
  public static final String DUMP_CLASSES = "dumpClasses";
  public static final String CMD_QUEUE_LIMIT = "cmdQueueLimit";
  public static final String TRACK_RETRANSFORMS = "trackRetransforms";
  public static final String SCRIPT_OUTPUT_FILE = "scriptOutputFile";
  public static final String SCRIPT_OUTPUT_DIR = "scriptOutputDir";
  public static final String FILE_ROLL_MILLISECONDS = "fileRollMilliseconds";
  public static final String FILE_ROLL_MAX_ROLLS = "fileRollMaxRolls";
  public static final String TRUSTED = "trusted";
  public static final String STATSD = "statsd";
  public static final String PROBE_DESC_PATH = "probeDescPath";
  public static final String DEBUG = "debug";
  public static final String TELEMETRY = "telemetry";
  public static final String PORT = "port";
  public static final String STDOUT = "stdout";
  public static final String NO_SERVER = "noServer";
  public static final String HELP = "help";
  public static final String LIBS = "libs";
  public static final String GRANT = "grant";
  public static final String DENY = "deny";
  public static final String GRANT_ALL = "grantAll";
  public static final String ALLOW_EXTENSIONS = "allowExtensions";
  public static final String DENY_EXTENSIONS = "denyExtensions";
  public static final String ALLOW_PRIVILEGED = "allowPrivileged";

  // Embedded extension probe arguments
  public static final String PROBES = "probes";
  public static final String OUTPUT = "output";
}
