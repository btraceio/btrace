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
package io.btrace.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionRegistryClientTest {

  @TempDir Path tempDir;

  @Test
  void resolvesEntryByIdFromRegistryDocument() throws Exception {
    Path registry = writeRegistry(validRegistryJson());

    ExtensionRegistryClient client =
        new ExtensionRegistryClient(RegistrySource.local(registry), tempDir.resolve("cache.json"));

    ExtensionRegistryEntry entry = client.findById("btrace-metrics");

    assertEquals("btrace-metrics", entry.getId());
    assertEquals("io.btrace", entry.getMaven().getGroupId());
    assertEquals("btrace-metrics", entry.getMaven().getArtifactId());
    assertEquals("2.3.0", entry.getMaven().getVersion());
    assertEquals(List.of("metrics", "latency"), entry.getTags());
  }

  @Test
  void listsEntriesInDocumentOrder() throws Exception {
    Path registry = writeRegistry(validRegistryJson());

    ExtensionRegistryClient client =
        new ExtensionRegistryClient(RegistrySource.local(registry), tempDir.resolve("cache.json"));

    List<ExtensionRegistryEntry> entries = client.list();

    assertEquals(2, entries.size());
    assertEquals("btrace-utils", entries.get(0).getId());
    assertEquals("btrace-metrics", entries.get(1).getId());
  }

  @Test
  void rejectsDuplicateIds() throws Exception {
    Path registry =
        writeRegistry(
            "{\n"
                + "  \"schema_version\": 1,\n"
                + "  \"extensions\": [\n"
                + "    {\n"
                + "      \"id\": \"dup\",\n"
                + "      \"name\": \"One\",\n"
                + "      \"description\": \"One\",\n"
                + "      \"owner\": \"btraceio\",\n"
                + "      \"source_repo\": \"https://github.com/btraceio/one\",\n"
                + "      \"maven\": {\"groupId\": \"io.btrace\", \"artifactId\": \"one\", \"version\": \"1.0\"}\n"
                + "    },\n"
                + "    {\n"
                + "      \"id\": \"dup\",\n"
                + "      \"name\": \"Two\",\n"
                + "      \"description\": \"Two\",\n"
                + "      \"owner\": \"btraceio\",\n"
                + "      \"source_repo\": \"https://github.com/btraceio/two\",\n"
                + "      \"maven\": {\"groupId\": \"io.btrace\", \"artifactId\": \"two\", \"version\": \"1.0\"}\n"
                + "    }\n"
                + "  ]\n"
                + "}\n");

    ExtensionRegistryClient client =
        new ExtensionRegistryClient(RegistrySource.local(registry), tempDir.resolve("cache.json"));

    IllegalStateException ex = assertThrows(IllegalStateException.class, client::list);

    assertTrue(ex.getMessage().contains("Duplicate extension id"));
  }

  @Test
  void rejectsUnknownId() throws Exception {
    Path registry = writeRegistry(validRegistryJson());

    ExtensionRegistryClient client =
        new ExtensionRegistryClient(RegistrySource.local(registry), tempDir.resolve("cache.json"));

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> client.findById("missing"));

    assertTrue(ex.getMessage().contains("Unknown extension id"));
  }

  private Path writeRegistry(String json) throws IOException {
    Path file = tempDir.resolve("extensions.json");
    Files.writeString(file, json, StandardCharsets.UTF_8);
    return file;
  }

  private String validRegistryJson() {
    return "{\n"
        + "  \"schema_version\": 1,\n"
        + "  \"extensions\": [\n"
        + "    {\n"
        + "      \"id\": \"btrace-utils\",\n"
        + "      \"name\": \"BTrace Utilities\",\n"
        + "      \"description\": \"Utility services\",\n"
        + "      \"owner\": \"btraceio\",\n"
        + "      \"source_repo\": \"https://github.com/btraceio/btrace\",\n"
        + "      \"maven\": {\"groupId\": \"io.btrace\", \"artifactId\": \"btrace-utils\", \"version\": \"2.3.0\"},\n"
        + "      \"tags\": [\"utility\"]\n"
        + "    },\n"
        + "    {\n"
        + "      \"id\": \"btrace-metrics\",\n"
        + "      \"name\": \"BTrace HDR Histogram\",\n"
        + "      \"description\": \"Metrics\",\n"
        + "      \"owner\": \"btraceio\",\n"
        + "      \"source_repo\": \"https://github.com/btraceio/btrace\",\n"
        + "      \"maven\": {\"groupId\": \"io.btrace\", \"artifactId\": \"btrace-metrics\", \"version\": \"2.3.0\"},\n"
        + "      \"tags\": [\"metrics\", \"latency\"]\n"
        + "    }\n"
        + "  ]\n"
        + "}\n";
  }
}
