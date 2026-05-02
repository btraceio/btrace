package org.openjdk.btrace.extcli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyFileTest {
  @TempDir
  Path tempDir;

  @Test
  void saveAndReloadPolicy() throws IOException {
    Path policy = tempDir.resolve("permissions.properties");
    PolicyFile pf = PolicyFile.fromArgs(new String[] {"--policy-file", policy.toString()});
    pf.updateFromArgs(new String[] {"--allowExtensions", "a,b", "--denyExtensions", "c", "--allowPrivileged", "true"});
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
