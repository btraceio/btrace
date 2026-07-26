/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.core.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Parsing of the textual permission list shared by the manifest attribute, the legacy properties
 * file and extension inspection. It lives here rather than being restated by each caller so the
 * three cannot disagree about what a permission list means.
 */
class PermissionSetParseTest {

  @Test
  @DisplayName("comma separated names are parsed")
  void parsesCommaSeparated() {
    PermissionSet set = PermissionSet.parse("NETWORK,THREADS");

    assertTrue(set.has(Permission.NETWORK));
    assertTrue(set.has(Permission.THREADS));
    assertEquals(2, set.size());
  }

  @Test
  @DisplayName("whitespace and mixed separators are tolerated")
  void parsesWhitespaceSeparated() {
    PermissionSet set = PermissionSet.parse("NETWORK ,  THREADS");

    assertTrue(set.has(Permission.NETWORK));
    assertTrue(set.has(Permission.THREADS));
  }

  @ParameterizedTest(name = "\"{0}\" parses to the empty set")
  @ValueSource(strings = {"", "   ", ","})
  @DisplayName("blank input yields no permissions")
  void blankInputIsEmpty(String value) {
    assertTrue(PermissionSet.parse(value).isEmpty());
  }

  @Test
  @DisplayName("null input yields no permissions")
  void nullInputIsEmpty() {
    assertTrue(PermissionSet.parse(null).isEmpty());
  }

  @Test
  @DisplayName("an unknown name is skipped rather than failing the whole list")
  void unknownNamesAreIgnored() {
    // An extension built against a newer BTrace may name a permission this runtime has never heard
    // of; the permissions it does understand must still be honoured.
    PermissionSet set = PermissionSet.parse("NETWORK,TIME_TRAVEL");

    assertTrue(set.has(Permission.NETWORK));
    assertEquals(1, set.size());
  }

  @Test
  @DisplayName("a list of only unknown names yields no permissions")
  void allUnknownNamesYieldEmpty() {
    assertTrue(PermissionSet.parse("TIME_TRAVEL,TELEPATHY").isEmpty());
  }
}
