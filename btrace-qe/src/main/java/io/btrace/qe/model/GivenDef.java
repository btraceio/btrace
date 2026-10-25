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
    CLIENT
  }

  private final Mode mode;
  private final String target;
  private final String agentArgs;
  private final List<String> clientArgs;

  private GivenDef(Mode mode, String target, String agentArgs, List<String> clientArgs) {
    this.mode = mode;
    this.target = target;
    this.agentArgs = agentArgs;
    this.clientArgs = clientArgs;
  }

  public static GivenDef target(String target, String agentArgs) {
    return new GivenDef(Mode.TARGET, target, agentArgs, Collections.<String>emptyList());
  }

  public static GivenDef client(List<String> clientArgs) {
    return new GivenDef(
        Mode.CLIENT, null, null, Collections.unmodifiableList(new ArrayList<>(clientArgs)));
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
}
