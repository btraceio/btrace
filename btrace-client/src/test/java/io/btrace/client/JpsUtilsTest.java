/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class JpsUtilsTest {
  private static final int SELF = 100;

  private static final List<JpsUtils.VmInfo> VMS =
      Arrays.asList(
          new JpsUtils.VmInfo(
              SELF, "Main", "io.btrace.client.Main ~RagSearch AiWorkloadProbe.java"),
          new JpsUtils.VmInfo(
              200,
              "SourceLauncher",
              "jdk.compiler/com.sun.tools.javac.launcher.SourceLauncher" + " RagSearchApp.java"),
          new JpsUtils.VmInfo(300, "checkout-service.jar", "checkout-service.jar --port 8080"),
          new JpsUtils.VmInfo(
              400, "GradleDaemon", "org.gradle.launcher.daemon.bootstrap.GradleDaemon"),
          new JpsUtils.VmInfo(401, "GradleWorkerMain", "worker.org.gradle.GradleWorkerMain"),
          new JpsUtils.VmInfo(500, "Main", "io.btrace.client.Main -lp ~RagSearch"));

  @Test
  void regexMatchesSourceLaunchedAppByCommandLine() {
    assertEquals(200, JpsUtils.findVmByRegex("RagSearch", VMS, SELF, null));
  }

  @Test
  void regexMatchesJarAndArguments() {
    assertEquals(300, JpsUtils.findVmByRegex("checkout-.*\\.jar", VMS, SELF, null));
    assertEquals(300, JpsUtils.findVmByRegex("--port 8080", VMS, SELF, null));
  }

  @Test
  void regexIgnoresOwnJvmAndOtherBTraceClients() {
    assertNull(JpsUtils.findVmByRegex("AiWorkloadProbe", VMS, SELF, null));
    assertNull(JpsUtils.findVmByRegex("-lp", VMS, SELF, null));
  }

  @Test
  void regexWithoutMatchYieldsNull() {
    assertNull(JpsUtils.findVmByRegex("NoSuchApp", VMS, SELF, null));
  }

  @Test
  void ambiguousRegexFailsAndListsCandidates() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> JpsUtils.findVmByRegex("Gradle", VMS, SELF, null));
    assertTrue(e.getMessage().contains("~Gradle"), e.getMessage());
    assertTrue(e.getMessage().contains("-x"), e.getMessage());
    assertTrue(e.getMessage().contains("400"), e.getMessage());
    assertTrue(e.getMessage().contains("401"), e.getMessage());
    assertFalse(e.getMessage().contains("RagSearchApp"), e.getMessage());
  }

  @Test
  void invalidRegexFailsWithReadableMessage() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> JpsUtils.findVmByRegex("Rag(", VMS, SELF, null));
    assertTrue(e.getMessage().contains("Invalid JVM name pattern"), e.getMessage());
  }

  @Test
  void regexTargetIsRecognizedByTildePrefix() {
    assertTrue(JpsUtils.isRegexTarget("~RagSearch"));
    assertFalse(JpsUtils.isRegexTarget("RagSearchApp"));
    assertFalse(JpsUtils.isRegexTarget("1234"));
  }

  @Test
  void ambiguousRegexUsesChooserPick() {
    assertEquals(
        401,
        JpsUtils.findVmByRegex(
            "Gradle",
            VMS,
            SELF,
            (target, candidates) -> {
              assertEquals("~Gradle", target);
              assertEquals(2, candidates.size());
              return candidates.get(1);
            }));
  }

  @Test
  void uniqueRegexDoesNotAskChooser() {
    assertEquals(
        200,
        JpsUtils.findVmByRegex(
            "RagSearch",
            VMS,
            SELF,
            (target, candidates) -> {
              throw new AssertionError("must not ask for a single match");
            }));
  }

  @Test
  void ambiguousRegexFailsWhenChooserAborts() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> JpsUtils.findVmByRegex("Gradle", VMS, SELF, (target, candidates) -> null));
    assertTrue(e.getMessage().contains("No JVM selected"), e.getMessage());
  }

  @Test
  void promptListsCandidatesAndRetriesInvalidAnswers() {
    StringWriter out = new StringWriter();
    JpsUtils.VmInfo chosen =
        JpsUtils.promptingChooser(answers("x", "0", "3", " 2 "), new PrintWriter(out))
            .choose("~Gradle", gradleVms());
    assertEquals(401, chosen.pid);
    String listing = out.toString();
    assertTrue(listing.contains("[1] 400 GradleDaemon"), listing);
    assertTrue(listing.contains("[2] 401 GradleWorkerMain"), listing);
    assertTrue(listing.contains("Invalid choice: x"), listing);
    assertTrue(listing.contains("Invalid choice: 3"), listing);
  }

  @Test
  void promptAbortsOnEmptyQuitOrEndOfInput() {
    PrintWriter out = new PrintWriter(new StringWriter());
    assertNull(JpsUtils.promptingChooser(answers(""), out).choose("~Gradle", gradleVms()));
    assertNull(JpsUtils.promptingChooser(answers("q"), out).choose("~Gradle", gradleVms()));
    assertNull(JpsUtils.promptingChooser(answers(), out).choose("~Gradle", gradleVms()));
  }

  private static List<JpsUtils.VmInfo> gradleVms() {
    return VMS.subList(3, 5);
  }

  /** Answers in order, then end of input. */
  @Test
  void inaccessibleJvmstatFallsBackToAttachApi() {
    List<JpsUtils.VmInfo> fallback =
        Arrays.asList(new JpsUtils.VmInfo(42, "DemoApp", "DemoApp --fast"));
    List<JpsUtils.VmInfo> vms =
        JpsUtils.activeVms(
            attachableOnly -> {
              throw new IllegalAccessError(
                  "class io.btrace.client.JvmstatVms cannot access class"
                      + " sun.jvmstat.monitor.MonitoredHost");
            },
            attachableOnly -> fallback,
            true);
    assertEquals(fallback, vms);
  }

  @Test
  void workingJvmstatIsPreferred() {
    List<JpsUtils.VmInfo> primary = Arrays.asList(new JpsUtils.VmInfo(7, "A", "A"));
    List<JpsUtils.VmInfo> vms =
        JpsUtils.activeVms(
            attachableOnly -> primary,
            attachableOnly -> {
              throw new AssertionError("fallback must not be used");
            },
            false);
    assertEquals(primary, vms);
  }

  @Test
  void failingFallbackYieldsNoJvmsInsteadOfThrowing() {
    List<JpsUtils.VmInfo> vms =
        JpsUtils.activeVms(
            attachableOnly -> {
              throw new NoClassDefFoundError("sun/jvmstat/monitor/MonitoredHost");
            },
            attachableOnly -> {
              throw new IllegalStateException("attach provider missing");
            },
            true);
    assertTrue(vms.isEmpty());
  }

  @Test
  void attachDescriptorMapsToVmInfo() {
    JpsUtils.VmInfo vm = JpsUtils.fromDescriptor("1234", "com.acme.DemoApp --port 8080");
    assertEquals(1234, vm.pid);
    assertEquals("DemoApp", vm.mainClass);
    assertEquals("com.acme.DemoApp --port 8080", vm.commandLine);

    JpsUtils.VmInfo jar = JpsUtils.fromDescriptor("55", "/opt/app/checkout-service.jar -v");
    assertEquals("checkout-service.jar", jar.mainClass);

    JpsUtils.VmInfo module =
        JpsUtils.fromDescriptor(
            "56", "jdk.compiler/com.sun.tools.javac.launcher.SourceLauncher RagSearchApp.java");
    assertEquals("SourceLauncher", module.mainClass);

    assertNull(JpsUtils.fromDescriptor("not-a-pid", "X"));
    assertEquals("Unknown", JpsUtils.fromDescriptor("9", "").commandLine);
  }

  @Test
  void attachApiListsThisJvm() {
    String name = ManagementFactory.getRuntimeMXBean().getName();
    int self = Integer.parseInt(name.substring(0, name.indexOf('@')));
    boolean found = false;
    for (JpsUtils.VmInfo vm : JpsUtils.attachApiVms(true)) {
      found |= vm.pid == self;
    }
    assertTrue(found, "the Attach API should list the test JVM itself");
  }

  private static Function<String, String> answers(String... lines) {
    Deque<String> queue = new ArrayDeque<>(Arrays.asList(lines));
    return prompt -> queue.poll();
  }
}
