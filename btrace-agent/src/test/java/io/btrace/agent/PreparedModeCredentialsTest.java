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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreparedModeCredentialsTest {
  @TempDir Path tempDir;

  @Test
  void generatedTokenIsOwnerProtectedAndDeleted() throws Exception {
    PreparedModeCredentials credentials = PreparedModeCredentials.create(null);
    Path path = credentials.getPath();
    try {
      assertTrue(Files.isRegularFile(path));
      assertTrue(credentials.copyToken().length >= 32);
      if (supportsPosix(path)) {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
        assertTrue(permissions.contains(PosixFilePermission.OWNER_READ));
        assertTrue(permissions.contains(PosixFilePermission.OWNER_WRITE));
        assertFalse(permissions.contains(PosixFilePermission.GROUP_READ));
        assertFalse(permissions.contains(PosixFilePermission.OTHERS_READ));
      }
    } finally {
      credentials.close();
    }
    assertFalse(Files.exists(path));
  }

  @Test
  void existingOwnerProtectedTokenIsPreserved() throws Exception {
    Assumptions.assumeTrue(supportsPosix(tempDir));
    Path path = tempDir.resolve("existing.token");
    byte[] token = "existing-secret".getBytes(StandardCharsets.UTF_8);
    Files.write(path, token);
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

    PreparedModeCredentials credentials = PreparedModeCredentials.create(path.toString());
    try {
      assertArrayEquals(token, credentials.copyToken());
    } finally {
      credentials.close();
    }
    assertTrue(Files.exists(path));
  }

  @Test
  void existingGroupReadableTokenFailsClosed() throws Exception {
    Assumptions.assumeTrue(supportsPosix(tempDir));
    Path path = tempDir.resolve("insecure.token");
    Files.write(path, "secret".getBytes(StandardCharsets.UTF_8));
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r-----"));

    assertThrows(IOException.class, () -> PreparedModeCredentials.create(path.toString()));
  }

  @Test
  void symbolicLinkTokenFailsClosed() throws Exception {
    Path target = tempDir.resolve("target.token");
    Path link = tempDir.resolve("link.token");
    Files.write(target, "secret".getBytes(StandardCharsets.UTF_8));
    try {
      Files.createSymbolicLink(link, target);
    } catch (IOException | UnsupportedOperationException unsupported) {
      Assumptions.assumeTrue(false, "symbolic links are unavailable");
    }

    assertThrows(IOException.class, () -> PreparedModeCredentials.create(link.toString()));
  }

  private static boolean supportsPosix(Path path) throws IOException {
    FileStore store = Files.getFileStore(path);
    return store.supportsFileAttributeView(PosixFileAttributeView.class);
  }
}
