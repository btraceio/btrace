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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class IndyScannerTest {

  // Fixture with a known lambda: compiles to one invokedynamic call site bootstrapped via
  // LambdaMetafactory.
  static final class HasLambda {
    Supplier<String> supplier() {
      return () -> "hello";
    }
  }

  // Fixture with no lambda/method-ref at all: no invokedynamic call sites.
  static final class HasNoIndy {
    String plain() {
      return "hello";
    }
  }

  private static byte[] classBytesOf(Class<?> clazz) throws IOException {
    String resource = clazz.getName().replace('.', '/') + ".class";
    try (InputStream in = clazz.getClassLoader().getResourceAsStream(resource)) {
      assertTrue(in != null, "class bytes not found for " + clazz.getName());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    }
  }

  @Test
  void findsInvokedynamicInMethodContainingALambda() throws IOException {
    List<IndyScanner.IndySite> sites = IndyScanner.scan(classBytesOf(HasLambda.class));
    assertEquals(1, sites.size(), "expected exactly one indy call site: " + sites);
    IndyScanner.IndySite site = sites.get(0);
    assertEquals("supplier", site.methodName);
    assertTrue(
        site.bootstrapMethodOwner.contains("LambdaMetafactory"),
        "expected LambdaMetafactory bootstrap, got: " + site.bootstrapMethodOwner);
  }

  @Test
  void findsNoInvokedynamicInAPlainMethod() throws IOException {
    List<IndyScanner.IndySite> sites = IndyScanner.scan(classBytesOf(HasNoIndy.class));
    assertEquals(0, sites.size(), "expected no indy call sites: " + sites);
  }
}
