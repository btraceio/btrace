/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package tests.harness;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks the JVMs an integration test has spawned, so that {@link StallWatchdog} can dump their
 * threads when a test stops making progress.
 *
 * <p>Targets are registered at launch rather than once they report a pid. A target that wedges
 * during startup never prints its {@code ready:} line, and that is precisely a case where its stack
 * is worth having; registering late would lose it. Liveness is decided at capture time via {@link
 * Process#isAlive()}, which means callers need no deregistration hook and a test that forgets to
 * stop its target costs nothing.
 *
 * <p>Source level here is Java 8, so {@code Process.pid()} is reached reflectively.
 */
public final class TargetRegistry {
  private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

  private TargetRegistry() {}

  /**
   * Registers a freshly launched target JVM.
   *
   * @param process the target
   * @param label how the target should be named in a dump
   * @param jcmdPath the {@code jcmd} binary able to attach to this target. Passed in rather than
   *     resolved centrally: targets are not all launched from the same JDK, and a mismatched-major
   *     {@code jcmd} cannot attach.
   */
  public static Handle register(Process process, String label, String jcmdPath) {
    Entry entry = new Entry(process, label, jcmdPath);
    ENTRIES.add(entry);
    return entry;
  }

  /** Live targets, in registration order. Dead ones are dropped as they are noticed. */
  public static List<Snapshot> liveTargets() {
    List<Snapshot> live = new ArrayList<>();
    List<Entry> dead = new ArrayList<>();
    for (Entry entry : ENTRIES) {
      if (entry.process.isAlive()) {
        live.add(new Snapshot(entry.label, entry.resolvePid(), entry.process, entry.jcmdPath));
      } else {
        dead.add(entry);
      }
    }
    ENTRIES.removeAll(dead);
    return Collections.unmodifiableList(live);
  }

  /** Drops every entry. For tests, which must not see targets left by their neighbours. */
  public static void clear() {
    ENTRIES.clear();
  }

  /** Lets a launch site publish the pid its target reported. */
  public interface Handle {
    void setPid(String pid);
  }

  /** An immutable view of one live target. */
  public static final class Snapshot {
    private final String label;
    private final String pid;
    private final Process process;
    private final String jcmdPath;

    Snapshot(String label, String pid, Process process, String jcmdPath) {
      this.label = label;
      this.pid = pid;
      this.process = process;
      this.jcmdPath = jcmdPath;
    }

    public String getLabel() {
      return label;
    }

    /**
     * The target's pid, or {@code null} if it never reported one and reflection could not find it.
     */
    public String getPid() {
      return pid;
    }

    public Process getProcess() {
      return process;
    }

    public String getJcmdPath() {
      return jcmdPath;
    }
  }

  private static final class Entry implements Handle {
    private final Process process;
    private final String label;
    private final String jcmdPath;
    private volatile String pid;
    private volatile boolean pidLookupAttempted;

    Entry(Process process, String label, String jcmdPath) {
      this.process = process;
      this.label = label;
      this.jcmdPath = jcmdPath;
    }

    @Override
    public void setPid(String pid) {
      this.pid = pid;
    }

    /**
     * The reported pid, falling back to {@code Process.pid()} for a target that never got far
     * enough to report one.
     */
    String resolvePid() {
      String reported = pid;
      if (reported != null) {
        return reported;
      }
      if (pidLookupAttempted) {
        return null;
      }
      pidLookupAttempted = true;
      try {
        Method pidMethod = Process.class.getMethod("pid");
        Object value = pidMethod.invoke(process);
        if (value != null) {
          pid = String.valueOf(value);
        }
      } catch (Throwable ignored) {
        // Java 8 target JVM, or a Process implementation without pid(). A dump without this
        // target is still worth producing.
      }
      return pid;
    }
  }
}
