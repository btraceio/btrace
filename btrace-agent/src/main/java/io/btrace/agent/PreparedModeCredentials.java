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

import io.btrace.core.comm.ConnectionAuthenticator;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class PreparedModeCredentials implements Closeable {
  private static final Set<PosixFilePermission> OWNER_READ_WRITE =
      Collections.unmodifiableSet(
          EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

  private final Path path;
  private final boolean generated;
  private final byte[] token;

  private PreparedModeCredentials(Path path, boolean generated, byte[] token) {
    this.path = path;
    this.generated = generated;
    this.token = token;
  }

  static PreparedModeCredentials create(String configuredPath) throws IOException {
    Path path = null;
    boolean generated = false;
    byte[] token = null;
    try {
      if (configuredPath == null || configuredPath.trim().isEmpty()) {
        Path directory = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath();
        path = createSecureTempFile(directory);
        generated = true;
      } else {
        path = Paths.get(configuredPath).toAbsolutePath().normalize();
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
          if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Prepared-mode token path must be a regular file");
          }
          verifyOwnerOnly(path);
        } else {
          Path parent = path.getParent();
          if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Prepared-mode token directory does not exist");
          }
          createSecureFile(path);
          generated = true;
        }
      }

      if (generated) {
        token = generateToken();
        Files.write(path, token, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        verifyOwnerOnly(path);
      } else {
        token = ConnectionAuthenticator.readToken(path);
      }
      return new PreparedModeCredentials(path, generated, token);
    } catch (IOException | RuntimeException failure) {
      if (token != null) {
        Arrays.fill(token, (byte) 0);
      }
      if (generated && path != null) {
        Files.deleteIfExists(path);
      }
      throw failure;
    }
  }

  Path getPath() {
    return path;
  }

  byte[] copyToken() {
    return token.clone();
  }

  @Override
  public void close() throws IOException {
    Arrays.fill(token, (byte) 0);
    if (generated) {
      Files.deleteIfExists(path);
    }
  }

  private static byte[] generateToken() {
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    try {
      return Base64.getUrlEncoder().withoutPadding().encode(random);
    } finally {
      Arrays.fill(random, (byte) 0);
    }
  }

  private static Path createSecureTempFile(Path directory) throws IOException {
    if (supportsPosix(directory)) {
      return Files.createTempFile(
          directory, "btrace-", ".token", PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE));
    }
    Path path = Files.createTempFile(directory, "btrace-", ".token");
    try {
      restrictAclToOwner(path);
      verifyOwnerOnly(path);
      return path;
    } catch (IOException | RuntimeException failure) {
      Files.deleteIfExists(path);
      throw failure;
    }
  }

  private static void createSecureFile(Path path) throws IOException {
    if (supportsPosix(path.getParent())) {
      Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE));
      return;
    }
    Files.createFile(path);
    try {
      restrictAclToOwner(path);
      verifyOwnerOnly(path);
    } catch (IOException | RuntimeException failure) {
      Files.deleteIfExists(path);
      throw failure;
    }
  }

  private static boolean supportsPosix(Path path) throws IOException {
    FileStore store = Files.getFileStore(path);
    return store.supportsFileAttributeView(PosixFileAttributeView.class);
  }

  private static void restrictAclToOwner(Path path) throws IOException {
    AclFileAttributeView view =
        Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new IOException("Owner-only token-file permissions are unsupported");
    }
    UserPrincipal owner = view.getOwner();
    AclEntry ownerEntry =
        AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(owner)
            .setPermissions(
                AclEntryPermission.READ_DATA,
                AclEntryPermission.WRITE_DATA,
                AclEntryPermission.APPEND_DATA,
                AclEntryPermission.READ_NAMED_ATTRS,
                AclEntryPermission.WRITE_NAMED_ATTRS,
                AclEntryPermission.READ_ATTRIBUTES,
                AclEntryPermission.WRITE_ATTRIBUTES,
                AclEntryPermission.DELETE,
                AclEntryPermission.READ_ACL,
                AclEntryPermission.WRITE_ACL,
                AclEntryPermission.SYNCHRONIZE)
            .build();
    view.setAcl(Collections.singletonList(ownerEntry));
  }

  private static void verifyOwnerOnly(Path path) throws IOException {
    PosixFileAttributeView posix =
        Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (posix != null) {
      Set<PosixFilePermission> permissions = posix.readAttributes().permissions();
      if (!permissions.contains(PosixFilePermission.OWNER_READ)
          || permissions.contains(PosixFilePermission.GROUP_READ)
          || permissions.contains(PosixFilePermission.GROUP_WRITE)
          || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
          || permissions.contains(PosixFilePermission.OTHERS_READ)
          || permissions.contains(PosixFilePermission.OTHERS_WRITE)
          || permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
        throw new IOException("Prepared-mode token file is not owner-protected");
      }
      return;
    }

    AclFileAttributeView acl =
        Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (acl == null) {
      throw new IOException("Owner-only token-file permissions cannot be verified");
    }
    UserPrincipal owner = acl.getOwner();
    boolean ownerCanRead = false;
    List<AclEntry> entries = acl.getAcl();
    for (AclEntry entry : entries) {
      if (entry.type() != AclEntryType.ALLOW) {
        continue;
      }
      if (entry.principal().equals(owner)) {
        ownerCanRead |= entry.permissions().contains(AclEntryPermission.READ_DATA);
      } else if (!entry.permissions().isEmpty()) {
        throw new IOException("Prepared-mode token file is not owner-protected");
      }
    }
    if (!ownerCanRead) {
      throw new IOException("Prepared-mode token file is not owner-readable");
    }
  }
}
