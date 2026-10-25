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
package io.btrace.qe.exec;

import io.btrace.client.Client;
import io.btrace.core.comm.MessageCommand;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Attaches a BTrace client to a target and runs the canary probe with a bounded lifetime. */
public final class ClientProbe {
  private static final long DETACH_GRACE_MS = 5_000L;

  public boolean runCanary(TargetProcess target, QeEnvironment env, long timeoutMs) {
    if (target == null || target.pid() <= 0) {
      // a step-order or harness fault, not a product failure: surface it as a harness error
      throw new IllegalStateException(
          "canary needs a launched target with a known pid (did awaitReady run first?)");
    }
    if (!target.isAlive()) {
      return false;
    }
    final Client client =
        new Client(
            target.assignedPort(),
            null,
            ".",
            false,
            false,
            false,
            false,
            null,
            null,
            env.btraceJarPath().toString());
    final CountDownLatch seen = new CountDownLatch(1);
    Thread session = null;
    try {
      // attach and the probe read can block without bound, so they run on the session thread; the
      // caller only ever waits on the latch.
      session =
          new Thread(
              () -> {
                try {
                  client.attach(String.valueOf(target.pid()), null, null);
                  final byte[] code =
                      Files.readAllBytes(
                          env.probeClassesDir().resolve("io/btrace/qe/probe/QeCanaryProbe.class"));
                  client.submit(
                      "QeCanaryProbe.class",
                      code,
                      new String[0],
                      cmd -> {
                        if (cmd instanceof MessageCommand
                            && ((MessageCommand) cmd).getMessage().contains("qe-canary-ok")) {
                          seen.countDown();
                        }
                      });
                } catch (Exception ignored) {
                  // a failed session simply never counts down
                }
              },
              "qe-canary-session");
      session.setDaemon(true);
      session.start();
      return seen.await(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      return false;
    } finally {
      try {
        client.sendExit(0);
      } catch (Exception ignored) {
        // already disconnected
      }
      if (session != null) {
        try {
          session.join(DETACH_GRACE_MS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
      // Releases a session thread parked in a protocol read and its socket. Client.close() is the
      // only synchronized Client method, so it cannot deadlock against a session thread that is
      // inside attach/submit (neither holds the monitor while blocked).
      try {
        client.close();
      } catch (Exception ignored) {
        // best effort
      }
    }
  }
}
