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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class BootstrapCallGraphTest {

  // --- fixtures -----------------------------------------------------------------------------

  static final class NoCallsNoIndy {
    static void root() {
      int x = 1 + 1;
    }
  }

  static final class DirectIndy {
    static void root() {
      ((Supplier<String>) () -> "hi");
    }
  }

  static final class Transitive {
    static void root() {
      Transitive.helper();
    }

    static void helper() {
      ((Supplier<String>) () -> "hi").get();
    }
  }

  static final class Cyclic {
    static void root() {
      Cyclic.a();
    }

    static void a() {
      Cyclic.b();
    }

    static void b() {
      ((Supplier<String>) () -> "hi");
      Cyclic.a(); // cycle back to a() -- must not infinite-loop
    }
  }

  static final class CallOnlyReachableThroughLambdaBody {
    static void root() {
      ((Runnable)
          () -> {
            // This call is only reachable via the invokedynamic-created Runnable's body -- there
            // is no direct MethodInsnNode from root() to innerOnlyCalledFromLambda(). A walker
            // that doesn't follow into lambda implementation methods misses this entirely.
            CallOnlyReachableThroughLambdaBody.innerOnlyCalledFromLambda();
          });
    }

    static void innerOnlyCalledFromLambda() {
      ((Supplier<String>) () -> "found me").get();
    }
  }

  static final class OutOfScopeCall {
    static void root() {
      // Calls a JDK method (out of the "io/btrace/..." scope predicate below) -- the walker
      // must not try to load/scan java.lang.Object's bytecode.
      Object o = new Object();
      o.toString();
    }
  }

  private static final ClassLoader LOADER = BootstrapCallGraphTest.class.getClassLoader();
  private static final java.util.function.Predicate<String> IN_SCOPE =
      owner -> owner.startsWith("io/btrace/");

  private static String internalName(Class<?> c) {
    return c.getName().replace('.', '/');
  }

  // --- tests ----------------------------------------------------------------------------------

  @Test
  void noCallsNoIndyYieldsEmptyResult() {
    BootstrapCallGraph.Result r =
        BootstrapCallGraph.scan(internalName(NoCallsNoIndy.class), "root", "()V", IN_SCOPE, LOADER);
    assertTrue(r.indySites.isEmpty(), "expected no indy sites: " + r.indySites);
  }

  @Test
  void findsIndySiteDirectlyInRootMethod() {
    BootstrapCallGraph.Result r =
        BootstrapCallGraph.scan(internalName(DirectIndy.class), "root", "()V", IN_SCOPE, LOADER);
    assertEquals(1, r.indySites.size(), "expected one indy site: " + r.indySites);
  }

  @Test
  void findsIndySiteTransitivelyThroughACalledMethod() {
    BootstrapCallGraph.Result r =
        BootstrapCallGraph.scan(internalName(Transitive.class), "root", "()V", IN_SCOPE, LOADER);
    assertEquals(1, r.indySites.size(), "expected the helper()'s indy site: " + r.indySites);
    assertEquals("helper", r.indySites.get(0).methodName);
  }

  @Test
  void doesNotInfiniteLoopOnACallCycle() {
    BootstrapCallGraph.Result r =
        BootstrapCallGraph.scan(internalName(Cyclic.class), "root", "()V", IN_SCOPE, LOADER);
    assertEquals(
        1, r.indySites.size(), "expected b()'s indy site found exactly once: " + r.indySites);
  }

  @Test
  void followsCallsMadeOnlyFromInsideALambdaBody() {
    BootstrapCallGraph.Result r =
        BootstrapCallGraph.scan(
            internalName(CallOnlyReachableThroughLambdaBody.class),
            "root",
            "()V",
            IN_SCOPE,
            LOADER);
    // root()'s own Runnable-creation site, plus innerOnlyCalledFromLambda()'s Supplier site --
    // the second is only reachable by following the first indy's implementation-method handle.
    assertEquals(
        2, r.indySites.size(), "expected both the outer and nested indy sites: " + r.indySites);
    assertTrue(
        r.indySites.stream().anyMatch(s -> s.methodName.equals("innerOnlyCalledFromLambda")),
        "expected the call made only from inside the lambda body to be reached: " + r.indySites);
  }

  @Test
  void doesNotRecurseIntoOutOfScopeCalls() {
    BootstrapCallGraph.Result r =
        BootstrapCallGraph.scan(
            internalName(OutOfScopeCall.class), "root", "()V", IN_SCOPE, LOADER);
    assertTrue(r.indySites.isEmpty(), "expected no indy sites: " + r.indySites);
    // java/lang/Object must not appear as a visited or unresolved in-scope class.
    assertTrue(
        r.visitedMethods.stream().noneMatch(m -> m.startsWith("java/lang/Object")),
        "walker must not have recursed into java/lang/Object: " + r.visitedMethods);
  }
}
