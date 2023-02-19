package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class VariableMapperTest {
  private VariableMapper instance;

  @BeforeEach
  public void setup() {
    instance = new VariableMapper(0);
  }

  @Test
  public void remapMethodArguments() {
    int numArgs = 4;
    VariableMapper mapper = new VariableMapper(numArgs);

    assertEquals(0, mapper.remap(0, 1));
    assertEquals(1, mapper.remap(1, 2));
    assertEquals(3, mapper.remap(3, 1));
  }

  @Test
  public void remapOutOfOrder() {
    assertEquals(0, instance.remap(2, 1));
    assertEquals(1, instance.remap(0, 2));
    assertEquals(3, instance.remap(3, 1));
  }

  @Test
  public void remapSame() {
    int var = instance.remap(2, 1);
    assertEquals(var, instance.remap(2, 1));
  }

  @Test
  public void remapWithNewVar() {
    int xVar = instance.newVarIdx(2);
    int yVar = instance.newVarIdx(1);

    assertEquals(VariableMapper.unmask(xVar), instance.remap(xVar, 2));
    assertEquals(VariableMapper.unmask(yVar), instance.remap(yVar, 1));
    assertEquals(3, instance.remap(0, 1));
    assertEquals(4, instance.remap(1, 2));
  }

  @Test
  public void remapOverflow() {
    assertEquals(
        0,
        instance.remap(
            16, 1)); // default mapping array size is 8 so going for double should trigger overflow
    // handling
  }

  @Test
  public void testMapEmpty() {
    assertTrue(VariableMapper.isInvalidMapping(instance.map(0)));
  }

  @Test
  public void testMapMethodArgs() {
    int numArgs = 4;
    VariableMapper mapper = new VariableMapper(numArgs);

    assertEquals(0, mapper.map(0));
    assertEquals(1, mapper.map(1));
    assertEquals(3, mapper.map(3));
  }

  @Test
  public void testMappings() {
    instance.setMapping(0, 2, 1);
    instance.setMapping(1, 0, 2);

    assertEquals(2, instance.map(0));
    assertEquals(0, instance.map(1));
  }

  @Test
  public void testMapRemapped() {
    int varX = instance.newVarIdx(1);
    assertEquals(VariableMapper.unmask(varX), instance.map(varX));
  }

  @Test
  public void mapOverflow() {
    assertTrue(
        VariableMapper.isInvalidMapping(
            instance.map(
                16))); // default mapping array size is 8 so going for double should trigger
    // overflow handling
  }

  @Test
  void testOverrideMapping() {
    System.out.println("==> " + instance.remap(4, 1));
    System.out.println("===> " + instance.remap(5, 1));
    System.out.println("===> " + instance.remap(4, 2));

    System.out.println("===> " + instance.map(4));
    System.out.println("===> " + instance.map(5));
  }
}
