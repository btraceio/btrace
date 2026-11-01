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
package io.btrace.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BTraceUtilsStrTest {
  enum Verdict {
    APPROVE,
    HOLD {
      @Override
      public String toString() {
        throw new AssertionError("str() must not run the target's code");
      }
    }
  }

  @Test
  void enumConstantPrintsItsName() {
    assertEquals("APPROVE", BTraceUtils.str(Verdict.APPROVE));
  }

  @Test
  void enumConstantWithABodyPrintsItsNameWithoutCallingToString() {
    assertEquals("HOLD", BTraceUtils.str((Object) Verdict.HOLD));
  }

  @Test
  void enumConstantsInAnArrayPrintTheirNames() {
    assertEquals("[APPROVE, HOLD]", BTraceUtils.str(Verdict.values()));
  }

  @Test
  void bootstrapEnumStillUsesToString() {
    assertEquals(TimeUnit.SECONDS.toString(), BTraceUtils.str(TimeUnit.SECONDS));
  }

  @Test
  void otherApplicationObjectStillPrintsItsIdentity() {
    assertTrue(BTraceUtils.str(new Object() {}).contains("@"));
  }
}
