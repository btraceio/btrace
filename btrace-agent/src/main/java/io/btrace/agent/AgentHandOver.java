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

import static io.btrace.core.Args.AUTH_TOKEN_FILE;
import static io.btrace.core.Args.BIND_ADDRESS;
import static io.btrace.core.Args.PORT;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.comm.WireIO;
import io.btrace.runtime.BTraceRuntimeAccess;
import java.io.IOException;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * What happens when the agent is loaded into a JVM that already holds one. A JVM holds one agent:
 * the first load claims it, and a later load is handed over to that agent, which may start its
 * command server for it. No lambdas here: this class is used on the premain path.
 */
final class AgentHandOver {
  private AgentHandOver() {}

  static final String ALREADY_LOADED =
      "BTrace agent is already loaded in this JVM; this load and its arguments are ignored";

  static final String SERVER_STARTED_ON_ATTACH =
      "BTrace agent is already loaded in this JVM; its command server was started for this"
          + " attach, every other argument of this load is ignored";

  static final String SERVER_NOT_STARTED =
      "BTrace agent is already loaded in this JVM; its command server was not started: ";

  // How far this agent's initialization got. Written by Main under its INIT_LOCK.
  static boolean initialized;
  static boolean runtimeHooksInstalled;
  static boolean budgetValidAtInit;

  // What a later agent load in this JVM calls instead of initializing an agent of its own. An
  // anonymous class, not a lambda: Main's static initializer runs on the premain path.
  static final UnaryOperator<String> AGENT_CONTROL =
      new UnaryOperator<String>() {
        @Override
        public String apply(String args) {
          return startServerOnAttach(args);
        }
      };

  /**
   * Handles a later agent load on behalf of this, already loaded, agent. If this agent has no
   * command server, because it was launched without one, the server is started on the port and bind
   * address of the later load. Nothing else of that load is used: this agent's settings, startup
   * scripts and probe budget stay what they are. The clients of that server get those settings, a
   * launch-time {@code trusted=true} included, so the server of an agent loaded at JVM launch
   * requires the authentication token, as it would had it been started at launch.
   *
   * @return the line to print in the target
   */
  static String startServerOnAttach(String args) {
    // Also waits for an initialization that is still running.
    synchronized (Main.INIT_LOCK) {
      if (!initialized) {
        // The lock is held, so initialization is not still running: it failed. Once this agent
        // has installed itself into the shared runtime classes no other load can replace it;
        // before that it gave the JVM back, and this load only asked a moment too early.
        return SERVER_NOT_STARTED
            + (runtimeHooksInstalled
                ? "the agent failed to initialize; restart the JVM to use BTrace in it"
                : "the agent failed to initialize; load the agent again");
      }
      if (Main.controlServer != null) {
        return ALREADY_LOADED;
      }
      if (!budgetValidAtInit) {
        return SERVER_NOT_STARTED + "the probe budget set at launch is invalid";
      }
      ControlServer endpoint;
      try {
        endpoint =
            ControlServer.open(
                attachEndpointArgs(Main.parseAgentArgs(args), Main.argMap),
                "premain".equals(Main.agentMode));
      } catch (IOException | RuntimeException e) {
        return SERVER_NOT_STARTED + (e.getMessage() != null ? e.getMessage() : e.toString());
      }
      return serveOnAttach(endpoint, Main.serverThread(endpoint));
    }
  }

  /**
   * Makes {@code endpoint} this agent's command server and starts {@code agentThread} to serve it.
   * The caller holds {@code INIT_LOCK}.
   *
   * @return the line to print in the target
   */
  static String serveOnAttach(ControlServer endpoint, Thread agentThread) {
    System.setProperty("btrace.wireio", String.valueOf(WireIO.VERSION));
    Main.controlServer = endpoint;
    Main.serverRunning = true;
    agentThread.setDaemon(true);
    BTraceRuntime.enter();
    try {
      Main.installServerShutdownHook();
      agentThread.start();
    } catch (Throwable t) {
      // Nothing accepts on the endpoint: close it and take back what it published.
      Main.shutdownServer();
      return SERVER_NOT_STARTED + t;
    } finally {
      BTraceRuntime.leave();
    }
    return SERVER_STARTED_ON_ATTACH;
  }

  /**
   * The arguments of a command server started for a later agent load: where it listens comes from
   * that load, the token file from the arguments this agent was loaded with.
   */
  static ArgsMap attachEndpointArgs(ArgsMap laterLoad, ArgsMap ownLoad) {
    ArgsMap endpoint = new ArgsMap();
    copyArgument(laterLoad, endpoint, PORT);
    copyArgument(laterLoad, endpoint, BIND_ADDRESS);
    copyArgument(ownLoad, endpoint, AUTH_TOKEN_FILE);
    return endpoint;
  }

  private static void copyArgument(ArgsMap from, ArgsMap to, String key) {
    String value = from != null ? from.get(key) : null;
    if (value != null) {
      to.put(key, value);
    }
  }

  // Anonymous class, not a lambda: used on the premain path.
  static final Supplier<UnaryOperator<String>> AGENT_HOLDER =
      new Supplier<UnaryOperator<String>>() {
        @Override
        public UnaryOperator<String> get() {
          return BTraceRuntimeAccess.agentControl();
        }
      };

  /**
   * Claims the JVM for the agent load that {@code own} stands for, or hands the load over to the
   * agent that holds the JVM already.
   *
   * @param holder looks up the control of the agent holding the JVM
   * @return {@code null} when this load holds the JVM now and must initialize; otherwise the line
   *     to print in the target, and this load must do nothing more
   */
  static String claimOrHandOver(
      String args, UnaryOperator<String> own, Supplier<UnaryOperator<String>> holder) {
    while (!BTraceRuntimeAccess.claimAgent(own)) {
      // Every agent load gets its own class loader, so this is a different Main than the one
      // that loaded first. That one decides what this load may still do.
      UnaryOperator<String> loaded = holder.get();
      if (loaded == null) {
        // The holder failed to initialize and gave the JVM back in between: claim it again.
        continue;
      }
      String answer;
      try {
        answer = loaded.apply(args);
      } catch (Throwable t) {
        return SERVER_NOT_STARTED + t;
      }
      // The holder may be another version of the agent. No answer must not read as a claim.
      return answer != null ? answer : SERVER_NOT_STARTED + "the loaded agent gave no answer";
    }
    return null;
  }

  /** Whether this agent has a command server. Tests only. */
  static boolean hasCommandServerForTesting() {
    return Main.controlServer != null;
  }

  /** Sets how far this agent's initialization got, as a failed one would leave it. Tests only. */
  static void setRuntimeHooksInstalledForTesting(boolean installed) {
    synchronized (Main.INIT_LOCK) {
      runtimeHooksInstalled = installed;
    }
  }
}
