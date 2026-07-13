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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import org.junit.jupiter.api.Test;

class BundledProbeResourceTest {
  @Test
  void nestedBinaryNameMapsInsideCanonicalResourceRoot() {
    assertEquals(
        "META-INF/btrace-probes/com/example/NestedProbe.class",
        Main.bundledProbeResourcePath("com.example.NestedProbe"));
  }

  @Test
  void innerClassBinaryNameIsAllowed() {
    assertEquals(
        "META-INF/btrace-probes/com/example/Probe$Nested.class",
        Main.bundledProbeResourcePath("com.example.Probe$Nested"));
  }

  @Test
  void traversalAndResourceSyntaxAreRejected() {
    String[] invalid = {
      "../Probe", "com/example/Probe", "com..example.Probe", ".Probe", "Probe.class", ""
    };
    for (String name : invalid) {
      assertThrows(Main.BundledProbeException.class, () -> Main.bundledProbeResourcePath(name));
    }
    assertThrows(Main.BundledProbeException.class, () -> Main.bundledProbeResourcePath(null));
  }

  @Test
  void missingNamedProbeFailsLoudly() {
    assertThrows(
        Main.BundledProbeException.class,
        () -> Main.loadStandaloneProbes(Collections.singletonList("missing.NoSuchProbe"), false));
  }
}
