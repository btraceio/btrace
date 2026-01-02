package org.openjdk.btrace.compiler.oneliner;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.openjdk.btrace.compiler.oneliner.OnelinerAST.*;

class OnelinerParserTest {

  @Test
  void testSimpleEntryProbe() {
    String input = "javax.swing.*::setText @entry { print }";
    OnelinerNode ast = OnelinerParser.parse(input);

    assertNotNull(ast);
    assertEquals("javax.swing.*", ast.probe.probeSpec.classPattern);
    assertEquals("setText", ast.probe.probeSpec.methodPattern);
    assertEquals(Location.ENTRY, ast.probe.probeSpec.location);
    assertNull(ast.probe.filter);
    assertEquals(1, ast.probe.actionBlock.actions.size());
    assertTrue(ast.probe.actionBlock.actions.get(0) instanceof PrintAction);
  }

  @Test
  void testReturnProbe() {
    String input = "java.util.HashMap::get @return { print }";
    OnelinerNode ast = OnelinerParser.parse(input);

    assertEquals(Location.RETURN, ast.probe.probeSpec.location);
  }

  @Test
  void testErrorProbe() {
    String input = "java.lang.OutOfMemoryError::<init> @error { print }";
    OnelinerNode ast = OnelinerParser.parse(input);

    assertEquals("java.lang.OutOfMemoryError", ast.probe.probeSpec.classPattern);
    assertEquals("<init>", ast.probe.probeSpec.methodPattern);
    assertEquals(Location.ERROR, ast.probe.probeSpec.location);
  }

  @Test
  void testRegexPattern() {
    String input = "/com\\.myapp\\..*/::execute @entry { print }";
    OnelinerNode ast = OnelinerParser.parse(input);

    assertEquals("/com\\.myapp\\..*/", ast.probe.probeSpec.classPattern);
  }

  @Test
  void testMethodRegexPattern() {
    String input = "java.sql.Statement::/execute.*/ @entry { print }";
    OnelinerNode ast = OnelinerParser.parse(input);

    assertEquals("java.sql.Statement", ast.probe.probeSpec.classPattern);
    assertEquals("/execute.*/", ast.probe.probeSpec.methodPattern);
  }

  @Test
  void testPrintWithArguments() {
    String input = "javax.swing.*::* @entry { print method, args }";
    OnelinerNode ast = OnelinerParser.parse(input);

    PrintAction printAction = (PrintAction) ast.probe.actionBlock.actions.get(0);
    assertEquals(2, printAction.args.size());
    assertEquals("method", printAction.args.get(0));
    assertEquals("args", printAction.args.get(1));
  }

  @Test
  void testPrintAllIdentifiers() {
    String input = "Test::test @return { print method, args, duration, return, self, class }";
    OnelinerNode ast = OnelinerParser.parse(input);

    PrintAction printAction = (PrintAction) ast.probe.actionBlock.actions.get(0);
    assertEquals(6, printAction.args.size());
    assertEquals("method", printAction.args.get(0));
    assertEquals("args", printAction.args.get(1));
    assertEquals("duration", printAction.args.get(2));
    assertEquals("return", printAction.args.get(3));
    assertEquals("self", printAction.args.get(4));
    assertEquals("class", printAction.args.get(5));
  }

  @Test
  void testCountAction() {
    String input = "java.util.HashMap::get @entry { count }";
    OnelinerNode ast = OnelinerParser.parse(input);

    assertTrue(ast.probe.actionBlock.actions.get(0) instanceof CountAction);
  }

  @Test
  void testTimeAction() {
    String input = "java.sql.Statement::execute @return { time }";
    OnelinerNode ast = OnelinerParser.parse(input);

    assertTrue(ast.probe.actionBlock.actions.get(0) instanceof TimeAction);
  }

  @Test
  void testStackAction() {
    String input = "Test::test @entry { stack }";
    OnelinerNode ast = OnelinerParser.parse(input);

    StackAction stackAction = (StackAction) ast.probe.actionBlock.actions.get(0);
    assertNull(stackAction.depth);
  }

  @Test
  void testStackActionWithDepth() {
    String input = "Test::test @entry { stack(10) }";
    OnelinerNode ast = OnelinerParser.parse(input);

    StackAction stackAction = (StackAction) ast.probe.actionBlock.actions.get(0);
    assertEquals(10, stackAction.depth);
  }

  @Test
  void testMultipleActions() {
    String input = "Test::test @entry { print method, count, stack }";
    OnelinerNode ast = OnelinerParser.parse(input);

    assertEquals(3, ast.probe.actionBlock.actions.size());
    assertTrue(ast.probe.actionBlock.actions.get(0) instanceof PrintAction);
    assertTrue(ast.probe.actionBlock.actions.get(1) instanceof CountAction);
    assertTrue(ast.probe.actionBlock.actions.get(2) instanceof StackAction);
  }

  @Test
  void testDurationFilterGreaterThan() {
    String input = "java.sql.Statement::execute @return if duration>100ms { print }";
    OnelinerNode ast = OnelinerParser.parse(input);

    assertNotNull(ast.probe.filter);
    assertEquals(FilterType.DURATION, ast.probe.filter.type);
    assertEquals(Comparator.GT, ast.probe.filter.comparator);
    assertEquals(100, ast.probe.filter.value);
  }

  @Test
  void testDurationFilterGreaterThanOrEqual() {
    String input = "Test::test @return if duration>=50ms { print }";
    OnelinerNode ast = OnelinerParser.parse(input);

    assertEquals(Comparator.GTE, ast.probe.filter.comparator);
    assertEquals(50, ast.probe.filter.value);
  }

  @Test
  void testArgFilterStringEquals() {
    String input = "Test::test @entry if args[0]==\"test\" { print }";
    OnelinerNode ast = OnelinerParser.parse(input);

    ArgFilter argFilter = (ArgFilter) ast.probe.filter;
    assertEquals(FilterType.ARG_COMPARE, argFilter.type);
    assertEquals(0, argFilter.argIndex);
    assertEquals(Comparator.EQ, argFilter.comparator);
    assertEquals("test", argFilter.value);
  }

  @Test
  void testArgFilterNumberGreaterThan() {
    String input = "Test::test @entry if args[1]>100 { print }";
    OnelinerNode ast = OnelinerParser.parse(input);

    ArgFilter argFilter = (ArgFilter) ast.probe.filter;
    assertEquals(1, argFilter.argIndex);
    assertEquals(Comparator.GT, argFilter.comparator);
    assertEquals(100, argFilter.value);
  }

  @Test
  void testArgFilterNotEquals() {
    String input = "Test::test @entry if args[0]!=\"foo\" { print }";
    OnelinerNode ast = OnelinerParser.parse(input);

    ArgFilter argFilter = (ArgFilter) ast.probe.filter;
    assertEquals(Comparator.NEQ, argFilter.comparator);
  }

  @Test
  void testErrorMissingDoubleColon() {
    String input = "javax.swing.* setText @entry { print }";
    assertThrows(OnelinerException.class, () -> OnelinerParser.parse(input));
  }

  @Test
  void testErrorMissingAt() {
    String input = "javax.swing.*::setText entry { print }";
    assertThrows(OnelinerException.class, () -> OnelinerParser.parse(input));
  }

  @Test
  void testErrorMissingActionBlock() {
    String input = "javax.swing.*::setText @entry";
    assertThrows(OnelinerException.class, () -> OnelinerParser.parse(input));
  }

  @Test
  void testErrorMissingClosingBrace() {
    String input = "javax.swing.*::setText @entry { print";
    assertThrows(OnelinerException.class, () -> OnelinerParser.parse(input));
  }

  @Test
  void testErrorInvalidLocation() {
    String input = "javax.swing.*::setText @invalid { print }";
    assertThrows(OnelinerException.class, () -> OnelinerParser.parse(input));
  }

  @Test
  void testErrorInvalidAction() {
    String input = "javax.swing.*::setText @entry { invalid }";
    assertThrows(OnelinerException.class, () -> OnelinerParser.parse(input));
  }

  @Test
  void testErrorEmptyActionBlock() {
    String input = "javax.swing.*::setText @entry { }";
    assertThrows(OnelinerException.class, () -> OnelinerParser.parse(input));
  }
}
