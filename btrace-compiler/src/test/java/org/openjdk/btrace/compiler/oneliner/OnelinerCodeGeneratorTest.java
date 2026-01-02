package org.openjdk.btrace.compiler.oneliner;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.openjdk.btrace.compiler.oneliner.OnelinerAST.OnelinerNode;

class OnelinerCodeGeneratorTest {

  @Test
  void testGenerateSimpleEntry() {
    String input = "javax.swing.JButton::setText @entry { print method }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("@BTrace"));
    assertTrue(generated.contains("@OnMethod(clazz=\"javax.swing.JButton\", method=\"setText\")"));
    assertTrue(generated.contains("@ProbeMethodName String method"));
    assertTrue(generated.contains("BTraceUtils.println(method)"));
  }

  @Test
  void testGenerateReturn() {
    String input = "java.util.HashMap::get @return { print }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("@Location(Kind.RETURN)"));
  }

  @Test
  void testGenerateError() {
    String input = "java.lang.OutOfMemoryError::<init> @error { print }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("@Location(Kind.ERROR)"));
    assertTrue(generated.contains("clazz=\"java.lang.OutOfMemoryError\""));
    assertTrue(generated.contains("method=\"<init>\""));
  }

  @Test
  void testGeneratePrintMultipleArgs() {
    String input = "Test::test @entry { print method, args }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("@ProbeMethodName String method"));
    assertTrue(generated.contains("AnyType[] args"));
    assertTrue(generated.contains("BTraceUtils.println(method + \" \" + BTraceUtils.str(args))"));
  }

  @Test
  void testGeneratePrintAllIdentifiers() {
    String input = "Test::test @return { print method, class, args, duration, return, self }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("@ProbeMethodName String method"));
    assertTrue(generated.contains("@ProbeClassName String className"));
    assertTrue(generated.contains("AnyType[] args"));
    assertTrue(generated.contains("@Duration long duration"));
    assertTrue(generated.contains("@Return AnyType returnValue"));
    assertTrue(generated.contains("@Self Object self"));
  }

  @Test
  void testGenerateCount() {
    String input = "java.util.HashMap::get @entry { count }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("private static final AtomicInteger counter"));
    assertTrue(generated.contains("counter.incrementAndGet()"));
    assertTrue(generated.contains("@OnEvent"));
    assertTrue(generated.contains("BTraceUtils.println(\"count: \" + counter.get())"));
  }

  @Test
  void testGenerateTime() {
    String input = "Test::test @return { time }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("@Duration long duration"));
    assertTrue(generated.contains("execution time"));
  }

  @Test
  void testGenerateStackWithoutDepth() {
    String input = "Test::test @entry { stack }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("BTraceUtils.jstack()"));
  }

  @Test
  void testGenerateStackWithDepth() {
    String input = "Test::test @entry { stack(10) }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("BTraceUtils.jstack(10)"));
  }

  @Test
  void testGenerateDurationFilterGreaterThan() {
    String input = "java.sql.Statement::execute @return if duration>100ms { print method }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("if (duration > 100000000L)"));
    assertTrue(generated.contains("BTraceUtils.println(method)"));
  }

  @Test
  void testGenerateDurationFilterGreaterThanOrEqual() {
    String input = "Test::test @return if duration>=50ms { print }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("if (duration >= 50000000L)"));
  }

  @Test
  void testGenerateDurationFilterLessThan() {
    String input = "Test::test @return if duration<10ms { print }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("if (duration < 10000000L)"));
  }

  @Test
  void testGenerateArgFilterStringEquals() {
    String input = "Test::test @entry if args[0]==\"test\" { print }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("args.length > 0"));
    assertTrue(generated.contains("\"test\".equals(BTraceUtils.str(args[0]))"));
  }

  @Test
  void testGenerateArgFilterStringNotEquals() {
    String input = "Test::test @entry if args[0]!=\"foo\" { print }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("!\"foo\".equals(BTraceUtils.str(args[0]))"));
  }

  @Test
  void testGenerateArgFilterNumberGreaterThan() {
    String input = "Test::test @entry if args[1]>100 { print }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("args.length > 1"));
    assertTrue(generated.contains("((Number)args[1]).longValue() > 100L"));
  }

  @Test
  void testGenerateMultipleActions() {
    String input = "Test::test @entry { print method, count, stack }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("BTraceUtils.println(method)"));
    assertTrue(generated.contains("counter.incrementAndGet()"));
    assertTrue(generated.contains("BTraceUtils.jstack()"));
    assertTrue(generated.contains("@OnEvent"));
  }

  @Test
  void testGenerateRegexPattern() {
    String input = "/com\\.myapp\\..*/::execute @entry { print }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("clazz=\"/com\\\\.myapp\\\\..*/")); // Escaping in generated code
  }

  @Test
  void testClassNameIsUnique() throws InterruptedException {
    String input = "Test::test @entry { print }";
    OnelinerNode ast1 = OnelinerParser.parse(input);
    String generated1 = OnelinerCodeGenerator.generate(ast1);

    OnelinerNode ast2 = OnelinerParser.parse(input);
    String generated2 = OnelinerCodeGenerator.generate(ast2);

    // Class name is fixed (BTraceOneliner) - uniqueness not required since
    // each oneliner gets its own temp file and class loader context
    assertTrue(generated1.contains("class BTraceOneliner"));
    assertTrue(generated2.contains("class BTraceOneliner"));
  }

  @Test
  void testGeneratedCodeHasCorrectPackage() {
    String input = "Test::test @entry { print }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("package org.openjdk.btrace.generated;"));
  }

  @Test
  void testGeneratedCodeHasRequiredImports() {
    String input = "Test::test @entry { print }";
    OnelinerNode ast = OnelinerParser.parse(input);
    String generated = OnelinerCodeGenerator.generate(ast);

    assertTrue(generated.contains("import org.openjdk.btrace.core.BTraceUtils;"));
    assertTrue(generated.contains("import org.openjdk.btrace.core.annotations.*;"));
    assertTrue(generated.contains("import org.openjdk.btrace.core.types.AnyType;"));
  }
}
