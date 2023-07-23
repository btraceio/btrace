package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Label;

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
    instance.setMapping(0, 2 | VariableMapper.REMAP_FLAG, 1);
    instance.setMapping(1, VariableMapper.REMAP_FLAG | VariableMapper.DOUBLE_SLOT_FLAG, 2);

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
  void testRemapDoublesAndSingles() {
    assertEquals(0, instance.remap(0, 1));
    assertEquals(1, instance.remap(1, 2));
    assertEquals(3, instance.remap(3, 2));
    assertEquals(5, instance.remap(5, 1));
    assertEquals(6, instance.remap(6, 1));

    assertEquals(1, instance.remap(1, 1)); // change slot 1 to single-slot value
    assertEquals(2, instance.remap(2, 1)); // 1 slot value should fit easily

    assertEquals(7, instance.remap(5, 2)); // can't overwrite slot 6

    assertEquals(9, instance.remap(4, 1)); // can not write to the double-slot-extension
    assertEquals(3, instance.remap(3, 1)); // change slot 3 to single-slot value
    assertEquals(4, instance.remap(4, 1)); // and suddenly the slot 4 is free to grab
  }

  @Test
  void testScopedMappings() {
    Label l1 = new Label();
    Label l2 = new Label();
    instance.noteLabel(l1);
    instance.remap(0, 1); // 0
    instance.remap(1, 2); // 1
    // although the l1-scope is not finished yet we must be able to get the mapping
    assertEquals(0, instance.map(0, l1));

    instance.noteLabel(l2);
    instance.remap(0, 2); // 3

    assertEquals(0, instance.map(0, l1));
    assertEquals(3, instance.map(0, l2));
    assertEquals(3, instance.map(0));
  }

  @Test
  void testScopedMappingsWithNewVars() {
    Label l1 = new Label();
    Label l2 = new Label();
    instance.noteLabel(l1);
    instance.remap(0, 1); // 0
    instance.newVarIdx(1); // 1
    instance.remap(1, 2); // 2
  // although the l1-scope is not finished yet we must be able to get the mapping
    assertEquals(0, instance.map(0, l1));
    instance.noteLabel(l2);
    instance.remap(0, 2); // 4

    assertEquals(0, instance.map(0, l1));
    assertEquals(4, instance.map(0, l2));
    assertEquals(4, instance.map(0));
  }

  @Test
  void testBug() {
    Label l0 = new Label();
    Label l5 = new Label();
    Label l10 = new Label();
    Label l22 = new Label();
    Label l34 = new Label();
    Label l55 = new Label();
    Label l77 = new Label();
    Label l99 = new Label();
    Label l110 = new Label();
    VariableMapper vm = new VariableMapper(2);

    vm.noteLabel(l0);
    assertEquals(0, vm.remap(0, 1));
    vm.noteLabel(l5);
    assertEquals(2, vm.remap(2, 1));
    assertEquals(1, vm.remap(1, 1));
    vm.noteLabel(l10);
    assertEquals(3, vm.remap(3, 1));
    vm.noteLabel(l22);
    assertEquals(4, vm.remap(4, 1));
    vm.noteLabel(l34);
    assertEquals(5, vm.remap(5, 1));
    vm.noteLabel(l55);
    assertEquals(6, vm.remap(5, 2));
    vm.noteLabel(l77);
    assertEquals(8, vm.remap(4, 2));
    vm.noteLabel(l99);
    assertEquals(10, vm.remap(6, 1));
    vm.noteLabel(l110);
    assertEquals(11, vm.remap(6, 2));
  }
}
