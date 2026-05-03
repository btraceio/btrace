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
package io.btrace.compiler.oneliner;

import static org.junit.jupiter.api.Assertions.*;

import io.btrace.compiler.Compiler;
import io.btrace.compiler.oneliner.OnelinerAST.OnelinerNode;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Integration tests that verify oneliner-generated code compiles with the real BTrace compiler. */
class OnelinerIntegrationTest {

  @Test
  void testCompileSimpleEntry() {
    String oneliner = "javax.swing.JButton::setText @entry { print method }";
    byte[] bytecode = compileOneliner(oneliner);

    assertNotNull(bytecode);
    assertTrue(bytecode.length > 0);
  }

  @Test
  void testCompileReturnWithFilter() {
    String oneliner =
        "java.sql.Statement::execute @return if duration>100ms { print method, duration }";
    byte[] bytecode = compileOneliner(oneliner);

    assertNotNull(bytecode);
    assertTrue(bytecode.length > 0);
  }

  @Test
  void testCompileCount() {
    String oneliner = "java.util.HashMap::get @entry { count }";
    byte[] bytecode = compileOneliner(oneliner);

    assertNotNull(bytecode);
    assertTrue(bytecode.length > 0);
  }

  @Test
  void testCompileStack() {
    String oneliner = "java.lang.OutOfMemoryError::<init> @return { stack(10) }";
    byte[] bytecode = compileOneliner(oneliner);

    assertNotNull(bytecode);
    assertTrue(bytecode.length > 0);
  }

  @Test
  void testCompileMultipleActions() {
    String oneliner = "Test::test @entry { print method, count, stack }";
    byte[] bytecode = compileOneliner(oneliner);

    assertNotNull(bytecode);
    assertTrue(bytecode.length > 0);
  }

  @Test
  void testCompileArgFilter() {
    String oneliner = "Test::test @entry if args[0]==\"test\" { print }";
    byte[] bytecode = compileOneliner(oneliner);

    assertNotNull(bytecode);
    assertTrue(bytecode.length > 0);
  }

  @Test
  void testCompileRegexPattern() {
    String oneliner = "/com\\.myapp\\..*/::execute @entry { print }";
    byte[] bytecode = compileOneliner(oneliner);

    assertNotNull(bytecode);
    assertTrue(bytecode.length > 0);
  }

  @Test
  void testCompileError() {
    String oneliner = "java.lang.Exception::<init> @error { print self }";
    byte[] bytecode = compileOneliner(oneliner);

    assertNotNull(bytecode);
    assertTrue(bytecode.length > 0);
  }

  @Test
  void testCompileTime() {
    String oneliner = "Test::test @return { time }";
    byte[] bytecode = compileOneliner(oneliner);

    assertNotNull(bytecode);
    assertTrue(bytecode.length > 0);
  }

  @Test
  void testCompilePrintAllIdentifiers() {
    String oneliner = "Test::test @return { print method, class, args, duration, return, self }";
    byte[] bytecode = compileOneliner(oneliner);

    assertNotNull(bytecode);
    assertTrue(bytecode.length > 0);
  }

  /** Helper method to compile a oneliner end-to-end: parse -> validate -> generate -> compile */
  private byte[] compileOneliner(String oneliner) {
    try {
      // Parse
      OnelinerNode ast = OnelinerParser.parse(oneliner);

      // Validate
      OnelinerValidator.validate(ast, oneliner);

      // Generate Java source
      String javaSource = OnelinerCodeGenerator.generate(ast);

      // Print generated source for debugging (if test fails)
      if (System.getProperty("btrace.test.debug") != null) {
        System.out.println("=== Generated Java for: " + oneliner + " ===");
        System.out.println(javaSource);
        System.out.println("=== End Generated Java ===\n");
      }

      // Compile with real BTrace compiler
      Compiler compiler = new Compiler();
      StringWriter errorWriter = new StringWriter();
      Map<String, byte[]> compiled =
          compiler.compile(
              "BTraceOneliner.java",
              javaSource,
              new PrintWriter(errorWriter),
              ".",
              System.getProperty("java.class.path"));

      // Check for compilation errors
      String errors = errorWriter.toString();
      if (!errors.isEmpty()) {
        fail("Compilation failed for oneliner: " + oneliner + "\nErrors:\n" + errors);
      }

      assertNotNull(compiled, "Compiler returned null");
      assertFalse(compiled.isEmpty(), "No classes were compiled");

      // Return the first compiled class
      return compiled.values().iterator().next();
    } catch (Exception e) {
      fail("Failed to compile oneliner: " + oneliner + "\nException: " + e.getMessage(), e);
      return null;
    }
  }
}
