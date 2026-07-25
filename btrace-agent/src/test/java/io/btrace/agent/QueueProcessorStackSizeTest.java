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
package io.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The command-queue processor thread needs an enlarged stack on JDK 26+.
 *
 * <p>ClassFile API's {@code StackMapGenerator} can exhaust the default stack depth while processing
 * complex methods on class-file major 70 and above. Older JVMs keep the JVM-chosen default, because
 * an explicit stack size there was intermittently delaying test startup on JDK 8/11/21 CI machines.
 *
 * <p>This sizing has been dropped by an accidental revert once already; these tests pin both halves
 * of the trade-off.
 */
class QueueProcessorStackSizeTest {

  private static final long FOUR_MB = 4L * 1024 * 1024;

  @Test
  @DisplayName("JDK 26+ class files get an enlarged stack")
  void enlargedStackOnJdk26AndLater() {
    assertEquals(FOUR_MB, Main.resolveQueueProcessorStackSize("70.0"), "JDK 26 (major 70)");
    assertEquals(FOUR_MB, Main.resolveQueueProcessorStackSize("71.0"), "JDK 27 (major 71)");
    assertEquals(FOUR_MB, Main.resolveQueueProcessorStackSize("99"), "far-future JVM");
  }

  @Test
  @DisplayName("older JVMs keep the JVM-chosen default")
  void defaultStackBelowJdk26() {
    assertEquals(0L, Main.resolveQueueProcessorStackSize("52.0"), "JDK 8 (major 52)");
    assertEquals(0L, Main.resolveQueueProcessorStackSize("55.0"), "JDK 11 (major 55)");
    assertEquals(0L, Main.resolveQueueProcessorStackSize("65.0"), "JDK 21 (major 65)");
    assertEquals(0L, Main.resolveQueueProcessorStackSize("69.0"), "JDK 25 (major 69)");
  }

  @Test
  @DisplayName("an unparseable class version falls back to the default")
  void malformedClassVersionFallsBack() {
    assertEquals(0L, Main.resolveQueueProcessorStackSize(""));
    assertEquals(0L, Main.resolveQueueProcessorStackSize("not-a-version"));
    assertEquals(0L, Main.resolveQueueProcessorStackSize(".7"));
  }

  @Test
  @DisplayName("the constant is wired to the running JVM's class version")
  void constantMatchesRunningJvm() {
    assertEquals(
        Main.resolveQueueProcessorStackSize(System.getProperty("java.class.version", "52.0")),
        Main.QUEUE_PROCESSOR_STACK_SIZE,
        "QUEUE_PROCESSOR_STACK_SIZE must be derived from java.class.version");
  }
}
