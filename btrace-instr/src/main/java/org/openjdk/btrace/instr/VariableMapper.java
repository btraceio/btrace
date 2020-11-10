package org.openjdk.btrace.instr;

import java.util.Arrays;

public class VariableMapper {
  private static final int UNMASK = 0x1FFFFFFF;
  private static final int DOUBLE_SLOT_FLAG = 0x20000000;
  private static final int REMAP_FLAG = 0x40000000;
  private static final int INVALID_MASK = 0xFFFFFFFF;

  private int argsSize;

  private int nextMappedVar = 0;
  private int[] mapping = new int[8];

  public VariableMapper(int argsSize) {
    this.argsSize = argsSize;
    nextMappedVar = argsSize;
  }

  VariableMapper(int argsSize, int nextMappedVar, int[] mapping) {
    this.argsSize = argsSize;
    this.nextMappedVar = nextMappedVar;
    this.mapping = Arrays.copyOf(mapping, mapping.length);
  }

  public static int unmask(int var) {
    return var & UNMASK;
  }

  public static boolean isInvalidMapping(int var) {
    return (var & INVALID_MASK) != 0;
  }

  public VariableMapper mirror() {
    return new VariableMapper(argsSize, nextMappedVar, mapping);
  }

  public void setMapping(int from, int to, int size) {
    int padding = size == 1 ? 0 : 1;
    if (mapping.length <= from + padding) {
      mapping = Arrays.copyOf(mapping, Math.max(mapping.length * 2, from + padding + 1));
    }
    mapping[from] = to | REMAP_FLAG;
    if (padding > 0) {
      mapping[from + padding] = Math.abs(to) + padding; // padding
    }
  }

  public int remap(int var, int size) {
    if ((var & REMAP_FLAG) != 0) {
      return unmask(var);
    }

    int offset = var - argsSize;
    if (offset < 0) {
      // self projection for method arguments
      return var;
    }
    if (offset >= mapping.length) {
      mapping = Arrays.copyOf(mapping, Math.max(mapping.length * 2, offset + 1));
    }
    int mappedVar = mapping[offset];

    boolean isRemapped = ((mappedVar & REMAP_FLAG) != 0);
    if (size == 2) {
      if ((mappedVar & DOUBLE_SLOT_FLAG) == 0) {
        // no double slot mapping; must re-map
        isRemapped = false;
      }
    }
    if (!isRemapped) {
      mappedVar = remapVar(newVarIdxInternal(size), size);
      setMapping(offset, mappedVar, size);
    }
    int unmasked = unmask(mappedVar);
    // adjust the mapping pointer if remapping with variable occupying 2 slots
    nextMappedVar = Math.max(unmasked + size, nextMappedVar);
    return unmasked;
  }

  public int map(int var) {
    // if the var number is the result of current mapping (REMAP_FLAG is set)
    if (((var & REMAP_FLAG) != 0)) {
      return unmask(var);
    }

    int offset = (var - argsSize);

    // only remap locals slots above method arguments
    if (offset >= 0) {
      if (mapping.length <= offset) {
        // catch out of bounds mapping
        return 0xFFFFFFFF;
      }
      int newVar = mapping[offset];
      return (newVar & REMAP_FLAG) != 0 ? unmask(newVar) : 0xFFFFFFFF;
    }
    // method argument slots are not remapped
    return var;
  }

  public int[] mappings() {
    int[] cleansed = new int[mapping.length];
    for (int i = 0; i < mapping.length; i++) {
      cleansed[i] = unmask(mapping[i]);
    }
    return cleansed;
  }

  private int newVarIdxInternal(int size) {
    int var = nextMappedVar;
    nextMappedVar += size;
    return var == 0 ? Integer.MIN_VALUE : var;
  }

  private int remapVar(int var, int size) {
    int mappedVar = var | REMAP_FLAG;
    if (size == 2) {
      mappedVar = mappedVar | DOUBLE_SLOT_FLAG;
    }
    return mappedVar;
  }

  public int newVarIdx(int size) {
    int var = newVarIdxInternal(size);
    return remapVar(var, size);
  }

  public int getNextMappedVar() {
    return nextMappedVar;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("nextVar: ").append(nextMappedVar).append(", mappings: [");
    for (int i = 0; i < mapping.length; i++) {
      if ((mapping[i] & REMAP_FLAG) != 0) {
        sb.append(unmask(mapping[i]));
      } else {
        sb.append(mapping[i]);
      }
      sb.append(",");
    }
    sb.append("],\n{").append(Arrays.toString(mapping)).append("}");
    return sb.toString();
  }
}
