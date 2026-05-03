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
package io.btrace.extcli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicyFileTest {
  @TempDir Path tempDir;

  @Test
  void saveAndReloadPolicy() throws IOException {
    Path policy = tempDir.resolve("permissions.properties");
    PolicyFile pf = PolicyFile.fromArgs(new String[] {"--policy-file", policy.toString()});
    pf.updateFromArgs(
        new String[] {
          "--allowExtensions", "a,b", "--denyExtensions", "c", "--allowPrivileged", "true"
        });
    pf.save();

    PolicyFile reloaded = PolicyFile.fromArgs(new String[] {"--policy-file", policy.toString()});
    assertEquals(List.of("a", "b"), reloaded.getAllowList());
    assertEquals(List.of("c"), reloaded.getDenyList());
    assertTrue(reloaded.describe(true).contains("\"allowPrivileged\":\"true\""));
  }

  @Test
  void allowDenyAndClearUpdateLists() throws IOException {
    Path policy = tempDir.resolve("policy.properties");
    PolicyFile pf = PolicyFile.fromArgs(new String[] {"--policy-file", policy.toString()});

    pf.allow("ext-a");
    assertEquals(List.of("ext-a"), pf.getAllowList());
    assertEquals(List.of(), pf.getDenyList());

    pf.deny("ext-a");
    assertEquals(List.of(), pf.getAllowList());
    assertEquals(List.of("ext-a"), pf.getDenyList());

    pf.clear("ext-a");
    assertEquals(List.of(), pf.getAllowList());
    assertEquals(List.of(), pf.getDenyList());
  }

  @Test
  void rejectsMultipleTargets() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PolicyFile.fromArgs(new String[] {"--policy-file", "a", "--home"}));
  }

  @Test
  void classpathTargetResolvesToMetaInf() throws IOException {
    PolicyFile pf = PolicyFile.fromArgs(new String[] {"--classpath", tempDir.toString()});
    Path expected = tempDir.resolve("META-INF/btrace/permissions.properties");
    assertEquals(expected, pf.getTarget());
  }
}
