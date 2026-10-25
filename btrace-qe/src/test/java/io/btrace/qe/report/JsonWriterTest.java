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
package io.btrace.qe.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonWriterTest {

  @Test
  void writesStableOrderAndEscapes() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("id", "cli.example");
    root.put("count", 2);
    root.put("ok", true);
    root.put("nested", List.of("a\"b", "line1\nline2"));
    String expected =
        "{\n"
            + "  \"id\": \"cli.example\",\n"
            + "  \"count\": 2,\n"
            + "  \"ok\": true,\n"
            + "  \"nested\": [\n"
            + "    \"a\\\"b\",\n"
            + "    \"line1\\nline2\"\n"
            + "  ]\n"
            + "}";
    assertEquals(expected, new JsonWriter().write(root));
  }

  @Test
  void writesEmptyContainersCompactly() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("list", List.of());
    root.put("map", new LinkedHashMap<>());
    assertEquals("{\n  \"list\": [],\n  \"map\": {}\n}", new JsonWriter().write(root));
  }
}
