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

import static org.junit.jupiter.api.Assertions.*;

import io.btrace.runtime.BTraceBootstrap;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;

public class CoreOpRegistrationTest {

  @SuppressWarnings("unchecked")
  private static ConcurrentHashMap<String, Object> opTable() throws Exception {
    Field field = BTraceBootstrap.class.getDeclaredField("OP_TABLE");
    field.setAccessible(true);
    return (ConcurrentHashMap<String, Object>) field.get(null);
  }

  @BeforeEach
  void clearTable() throws Exception {
    opTable().clear();
  }

  @Test
  void allCoreOpsRegistered() throws Exception {
    Main.registerCoreOps();

    String[] expected = {
      "print(Ljava/lang/String;)V",
      "println(Ljava/lang/String;)V",
      "println()V",
      "str(Ljava/lang/Object;)Ljava/lang/String;",
      "str(J)Ljava/lang/String;",
      "concat(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
      "substr(Ljava/lang/String;II)Ljava/lang/String;",
      "timestamp()J",
      "monotonic()J",
      "threadName(Ljava/lang/Thread;)Ljava/lang/String;",
      "threadId(Ljava/lang/Thread;)J",
      "className(Ljava/lang/Object;)Ljava/lang/String;",
      "identity(Ljava/lang/Object;)I",
      "exit(I)V",
    };
    ConcurrentHashMap<String, Object> table = opTable();
    for (String op : expected) {
      assertTrue(table.containsKey(op), "Missing: " + op);
    }
  }

  @Test
  void allBTraceMethodsRegistered() throws Exception {
    Main.registerCoreOps();

    String[] allExpected = {
      // Output
      "print(Ljava/lang/String;)V",
      "println(Ljava/lang/String;)V",
      "println()V",
      "printf(Ljava/lang/String;[Ljava/lang/Object;)V",
      // Strings
      "str(Ljava/lang/Object;)Ljava/lang/String;",
      "str(Z)Ljava/lang/String;",
      "str(I)Ljava/lang/String;",
      "str(J)Ljava/lang/String;",
      "str(F)Ljava/lang/String;",
      "str(D)Ljava/lang/String;",
      "concat(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
      "substr(Ljava/lang/String;II)Ljava/lang/String;",
      "matches(Ljava/lang/String;Ljava/lang/String;)Z",
      "startsWith(Ljava/lang/String;Ljava/lang/String;)Z",
      "endsWith(Ljava/lang/String;Ljava/lang/String;)Z",
      "length(Ljava/lang/String;)I",
      // Numbers
      "abs(J)J",
      "abs(D)D",
      "min(JJ)J",
      "max(JJ)J",
      "min(DD)D",
      "max(DD)D",
      // Time
      "timestamp()J",
      "monotonic()J",
      // Threads
      "currentThread()Ljava/lang/Thread;",
      "threadName(Ljava/lang/Thread;)Ljava/lang/String;",
      "threadId(Ljava/lang/Thread;)J",
      // Stack
      "stackTrace()Ljava/lang/String;",
      "printStack()V",
      "stackDepth()I",
      // Object
      "className(Ljava/lang/Object;)Ljava/lang/String;",
      "identity(Ljava/lang/Object;)I",
      "size(Ljava/lang/Object;)J",
      // Control
      "exit(I)V",
    };
    ConcurrentHashMap<String, Object> table = opTable();
    for (String op : allExpected) {
      assertTrue(table.containsKey(op), "Missing: " + op);
    }

    // Duplicate registration is idempotent — second call must not throw
    assertDoesNotThrow(Main::registerCoreOps);
  }
}
