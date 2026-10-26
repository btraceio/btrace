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
package io.btrace.qe.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GivenDef {
  public enum Mode {
    TARGET,
    CLIENT,
    /** A target launched without the agent; the real CLI attaches to it by PID. */
    ATTACH
  }

  /** Readiness is the first stdout line matching this; the PID is parsed from such a line. */
  public static final String DEFAULT_READY_REGEX = "^ready:";

  private final Mode mode;
  private final String target;
  private final String agentArgs;
  private final List<String> clientArgs;
  private final List<String> jvmArgs;
  private final String readyRegex;

  private GivenDef(
      Mode mode,
      String target,
      String agentArgs,
      List<String> clientArgs,
      List<String> jvmArgs,
      String readyRegex) {
    this.mode = mode;
    this.target = target;
    this.agentArgs = agentArgs;
    this.clientArgs = clientArgs;
    this.jvmArgs = jvmArgs;
    this.readyRegex = readyRegex;
  }

  public static GivenDef target(String target, String agentArgs) {
    return new GivenDef(
        Mode.TARGET,
        target,
        agentArgs,
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        DEFAULT_READY_REGEX);
  }

  public static GivenDef client(List<String> clientArgs) {
    return new GivenDef(
        Mode.CLIENT,
        null,
        null,
        copy(clientArgs),
        Collections.<String>emptyList(),
        DEFAULT_READY_REGEX);
  }

  public static GivenDef attach(String target, List<String> clientArgs) {
    return new GivenDef(
        Mode.ATTACH,
        target,
        null,
        copy(clientArgs),
        Collections.<String>emptyList(),
        DEFAULT_READY_REGEX);
  }

  /** Options placed before the main class or source file on the target's java command line. */
  public GivenDef withJvmArgs(List<String> jvmArgs) {
    return new GivenDef(mode, target, agentArgs, clientArgs, copy(jvmArgs), readyRegex);
  }

  public GivenDef withReadyRegex(String readyRegex) {
    return new GivenDef(
        mode,
        target,
        agentArgs,
        clientArgs,
        jvmArgs,
        readyRegex == null ? DEFAULT_READY_REGEX : readyRegex);
  }

  private static List<String> copy(List<String> list) {
    return list == null
        ? Collections.<String>emptyList()
        : Collections.unmodifiableList(new ArrayList<>(list));
  }

  /**
   * A target naming a {@code .java} file (relative to the repository root) is run with single-file
   * source launch from that file's directory; any other target is a class in {@code qe.targets}.
   */
  public boolean isSourceTarget() {
    return target != null && target.endsWith(".java");
  }

  public Mode mode() {
    return mode;
  }

  public String target() {
    return target;
  }

  public String agentArgs() {
    return agentArgs;
  }

  public List<String> clientArgs() {
    return clientArgs;
  }

  public List<String> jvmArgs() {
    return jvmArgs;
  }

  public String readyRegex() {
    return readyRegex;
  }
}
