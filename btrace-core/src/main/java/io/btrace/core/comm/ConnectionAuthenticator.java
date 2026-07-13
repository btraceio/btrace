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
package io.btrace.core.comm;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;

/** Performs the prepared-mode authentication preamble before V1 or V2 protocol negotiation. */
public final class ConnectionAuthenticator {
  public static final int MAX_TOKEN_LENGTH = 4096;

  private static final byte[] REQUEST_MAGIC = {'B', 'T', 'A', '1'};
  private static final byte[] ACCEPT_MAGIC = {'B', 'T', 'A', 'K'};
  private static final byte[] REJECT_MAGIC = {'B', 'T', 'A', 'F'};

  private ConnectionAuthenticator() {}

  public static void authenticateClient(InputStream input, OutputStream output, byte[] token)
      throws IOException {
    validateToken(token);

    DataOutputStream dataOutput = new DataOutputStream(output);
    dataOutput.write(REQUEST_MAGIC);
    dataOutput.writeInt(token.length);
    dataOutput.write(token);
    dataOutput.flush();

    byte[] response = new byte[ACCEPT_MAGIC.length];
    if (readFully(input, response) != response.length
        || !MessageDigest.isEqual(ACCEPT_MAGIC, response)) {
      throw new IOException("Prepared-mode authentication rejected");
    }
  }

  public static void authenticateAgent(InputStream input, OutputStream output, byte[] expectedToken)
      throws IOException {
    validateToken(expectedToken);

    byte[] suppliedToken = null;
    try {
      byte[] requestMagic = new byte[REQUEST_MAGIC.length];
      if (readFully(input, requestMagic) != requestMagic.length
          || !MessageDigest.isEqual(REQUEST_MAGIC, requestMagic)) {
        throw new IOException("Invalid authentication preamble");
      }

      int tokenLength = new DataInputStream(input).readInt();
      if (tokenLength < 1 || tokenLength > MAX_TOKEN_LENGTH) {
        throw new IOException("Invalid authentication token length");
      }

      suppliedToken = new byte[tokenLength];
      if (readFully(input, suppliedToken) != suppliedToken.length
          || !MessageDigest.isEqual(expectedToken, suppliedToken)) {
        throw new IOException("Invalid authentication token");
      }

      output.write(ACCEPT_MAGIC);
      output.flush();
    } catch (IOException | RuntimeException failure) {
      writeRejection(output);
      throw new IOException("Prepared-mode authentication failed");
    } finally {
      if (suppliedToken != null) {
        Arrays.fill(suppliedToken, (byte) 0);
      }
    }
  }

  public static byte[] readToken(Path path) throws IOException {
    byte[] fileBytes = new byte[MAX_TOKEN_LENGTH + 3];
    int length = 0;
    try (InputStream input =
        Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      while (length < fileBytes.length) {
        int read = input.read(fileBytes, length, fileBytes.length - length);
        if (read < 0) {
          break;
        }
        length += read;
      }
      if (length == fileBytes.length) {
        throw invalidToken();
      }

      int tokenLength = length;
      if (tokenLength > 0 && fileBytes[tokenLength - 1] == '\n') {
        tokenLength--;
        if (tokenLength > 0 && fileBytes[tokenLength - 1] == '\r') {
          tokenLength--;
        }
      }
      if (tokenLength < 1 || tokenLength > MAX_TOKEN_LENGTH) {
        throw invalidToken();
      }
      for (int i = 0; i < tokenLength; i++) {
        if (fileBytes[i] == '\r' || fileBytes[i] == '\n') {
          throw invalidToken();
        }
      }

      byte[] token = Arrays.copyOf(fileBytes, tokenLength);
      validateToken(token);
      return token;
    } finally {
      Arrays.fill(fileBytes, (byte) 0);
    }
  }

  private static void validateToken(byte[] token) throws IOException {
    if (token == null || token.length < 1 || token.length > MAX_TOKEN_LENGTH) {
      throw invalidToken();
    }
  }

  private static IOException invalidToken() {
    return new IOException("Prepared-mode authentication token is invalid");
  }

  private static void writeRejection(OutputStream output) {
    try {
      output.write(REJECT_MAGIC);
      output.flush();
    } catch (IOException ignored) {
    }
  }

  private static int readFully(InputStream input, byte[] buffer) throws IOException {
    int offset = 0;
    while (offset < buffer.length) {
      int read = input.read(buffer, offset, buffer.length - offset);
      if (read < 0) {
        break;
      }
      if (read == 0) {
        int value = input.read();
        if (value < 0) {
          break;
        }
        buffer[offset++] = (byte) value;
        continue;
      }
      offset += read;
    }
    return offset;
  }
}
