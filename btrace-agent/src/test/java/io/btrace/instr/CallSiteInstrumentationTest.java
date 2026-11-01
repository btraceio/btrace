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
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

/** {@code Kind.CALL} locations whose call site pattern matches every call in the method. */
class CallSiteInstrumentationTest extends InstrumentorTestBase {
  @Test
  void durationAfterAnyCallSiteSkipsTheCallsItAdds() throws Exception {
    loadTargetClass("OnMethodTest");
    // Timing a call adds a System.nanoTime() call after it, which "/.*/" matches as well
    transform("onmethod/MethodCallAnySiteDuration");

    assertNotSame(originalBC, transformedBC, "the probe was not applied");
    assertVerifies();
  }
}
