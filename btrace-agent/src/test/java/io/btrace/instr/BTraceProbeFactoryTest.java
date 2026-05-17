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
package io.btrace.instr;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BTraceProbeFactoryTest {

  @Test
  void canLoadNullFile() {
    assertFalse(BTraceProbeFactory.canLoad(null));
  }

  @Test
  void canLoadNonExistingFile() {
    assertFalse(BTraceProbeFactory.canLoad("!invalid path"));
  }

  @Test
  void canLoadBTracePack() throws Exception {
    URL rsrc = BTraceProbeFactory.class.getResource("/resources/classdata/AllStuff.btrc");
    assertNotNull(rsrc);

    assertTrue(BTraceProbeFactory.canLoad(new File(rsrc.toURI()).getPath()));
  }

  @Test
  void canLoadClass() throws Exception {
    URL rsrc = BTraceProbeFactory.class.getResource("BTraceProbeFactoryTest.class");
    assertNotNull(rsrc);

    assertTrue(BTraceProbeFactory.canLoad(new File(rsrc.toURI()).getPath()));
  }

  @Test
  void refuseUnknown() throws Exception {
    URL rsrc = BTraceProbeFactory.class.getResource("/plain.txt");
    assertNotNull(rsrc);

    assertFalse(BTraceProbeFactory.canLoad(new File(rsrc.toURI()).getPath()));
  }

  @Test
  void canLoadFromPack() throws Exception {
    URL jarUrl = BTraceProbeFactoryTest.class.getResource("/packed/test-pack.jar");
    assertNotNull(jarUrl);

    try (URLClassLoader cl = new URLClassLoader(new URL[] {jarUrl})) {
      assertTrue(BTraceProbeFactory.canLoad("io/btrace/btrace_test/AllMethods.class", cl));
    }
  }
}
