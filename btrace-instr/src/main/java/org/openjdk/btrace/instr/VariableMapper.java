package org.openjdk.btrace.instr;

import org.objectweb.asm.Label;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class VariableMapper {
  private static class CopyOnWriteArray {
    private final int[] src;
    private int[] copy = null;
    private final int upperLimit;
    CopyOnWriteArray(int[] src, int upperLimit) {
      this.src = src;
      this.upperLimit = upperLimit;
    }

    void force() {
      copy = copy == null ? Arrays.copyOf(src, upperLimit) : copy;
    }

    int length() {
      return copy == null ? upperLimit : copy.length;
    }

    void set(int pos, int value) {
      if (copy == null) {
        copy = Arrays.copyOf(src, Math.max(upperLimit, pos * 2));
      } else if (pos >= copy.length) {
        copy = Arrays.copyOf(copy, copy.length * 2);
      }
      copy[pos] = value;
    }

    int get(int pos) {
      int[] target = copy != null ? copy : src;
      if (pos >= target.length) {
        throw new InstrumentationException(
            String.format("Invalid variable index: %d (valid range: 0-%d)", pos, target.length - 1));
      }
      return target[pos];
    }
  }

  private static final int UNMASK = 0x1FFFFFFF;
  static final int DOUBLE_SLOT_FLAG = 0x20000000;
  static final int DOUBLE_SLOT_FLAG_2 = 0x40000000;
  static final int REMAP_FLAG = 0x80000000;
  private static final int INVALID_MASK = 0xFFFFFFFF;

  private final int argsSize;

  private int nextMappedVar = 0;
  private int[] mapping;

  private static final Label FIRST_LABEL = new Label();

  private Label currentLabel = FIRST_LABEL;
  private final Map<Label, CopyOnWriteArray> labelMappings = new HashMap<>(16);

  public VariableMapper(int argsSize) {
    this.argsSize = argsSize;
    nextMappedVar = argsSize;
    // Estimate: args + 8 locals + 16 instrumentation vars
    this.mapping = new int[argsSize + 8 + 16];
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

  /**
   * Creates a new scope for all the subsequent mappings.<br>
   * Label snapshots capture the full mapping array capacity to handle variables
   * that are declared but not yet remapped when the label is visited.
   * @param label the scope label
   */
  public void noteLabel(Label label) {
    labelMappings.put(currentLabel, new CopyOnWriteArray(mapping, mapping.length));
    currentLabel = label;
  }

  void setMapping(int from, int to, int size) {
    assert((to & REMAP_FLAG) != 0);

    int padding = size == 1 ? 0 : 1;
    if (mapping.length <= from + padding) {
      mapping = Arrays.copyOf(mapping, Math.max(mapping.length * 2, from + padding + 1));
    }
    mapping[from] = to;
    if (padding > 0) {
      assert(((to & DOUBLE_SLOT_FLAG) != 0));
      mapping[from + padding] = unmask(to) | REMAP_FLAG | DOUBLE_SLOT_FLAG_2; // padding
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
    // materialize all copy-on-writes before we modify the backing array
    labelMappings.forEach((k, v) -> v.force());
    // ok, can continue with modifications
    if (offset >= mapping.length) {
      mapping = Arrays.copyOf(mapping, Math.max(mapping.length * 2, offset + 1));
    }
    int mappedVar = mapping[offset];
    int unmasked = unmask(mappedVar);

    boolean isRemapped = ((mappedVar & REMAP_FLAG) != 0);
    if (size == 2) {
      if ((mappedVar & DOUBLE_SLOT_FLAG) == 0) {
        // no double slot mapping over an int slot;
        // must re-map unless the int slot is the last used one or there is a free double-ext slot
        isRemapped = false;
      }
    } else {
      // size == 1
      if ((mappedVar & DOUBLE_SLOT_FLAG_2) != 0) {
        // no mapping over a previously 2-slot value
        isRemapped = false;
      } else if ((mappedVar & DOUBLE_SLOT_FLAG) != 0) {
        // the previously second part of the double slot is free to reuse
        mapping[offset + 1] = (unmasked + 1) | REMAP_FLAG;
      }
    }
    if (!isRemapped) {
      mappedVar = remapVar(newVarIdxInternal(size), size);
      setMapping(offset, mappedVar, size);
    }

    unmasked = unmask(mappedVar);
    // adjust the mapping pointer if remapping with variable occupying 2 slots
    nextMappedVar = Math.max(unmasked + size, nextMappedVar);
    return unmasked;
  }

  public int map(int var) {
    return map(var, mapping);
  }

  public int map(int var, Label label) {
    return map(var, labelMappings.getOrDefault(label, new CopyOnWriteArray(mapping, mapping.length)));
  }

  private int map(int var, CopyOnWriteArray currentMapping) {
    // if the var number is the result of current mapping (REMAP_FLAG is set)
    if (((var & REMAP_FLAG) != 0)) {
      return unmask(var);
    }

    int offset = (var - argsSize);

    // only remap locals slots above method arguments
    if (offset >= 0) {
      // If the snapshot is too small, fall back to the live mapping.
      // This handles variables that were declared but not yet remapped when the snapshot was taken.
      if (currentMapping.length() <= offset) {
        // Check if variable exists in current live mapping
        if (offset < mapping.length) {
          int liveVar = mapping[offset];
          if ((liveVar & REMAP_FLAG) != 0) {
            return unmask(liveVar);
          }
        }
        throw new InstrumentationException(
            String.format(
                "Variable mapping out of bounds: var=%d, offset=%d, mappingSize=%d, argsSize=%d",
                var, offset, currentMapping.length(), argsSize));
      }
      int newVar = currentMapping.get(offset);
      if ((newVar & REMAP_FLAG) == 0) {
        throw new InstrumentationException(
            String.format(
                "Variable not remapped: var=%d, offset=%d, mappedValue=0x%08X, argsSize=%d",
                var, offset, newVar, argsSize));
      }
      return unmask(newVar);
    }
    // method argument slots are not remapped
    return var;
  }

  private int map(int var, int[] currentMapping) {
    // if the var number is the result of current mapping (REMAP_FLAG is set)
    if (((var & REMAP_FLAG) != 0)) {
      return unmask(var);
    }

    int offset = (var - argsSize);

    // only remap locals slots above method arguments
    if (offset >= 0) {
      if (currentMapping.length <= offset) {
        throw new InstrumentationException(
            String.format(
                "Variable mapping out of bounds: var=%d, offset=%d, mappingSize=%d, argsSize=%d",
                var, offset, currentMapping.length, argsSize));
      }
      int newVar = currentMapping[offset];
      if ((newVar & REMAP_FLAG) == 0) {
        throw new InstrumentationException(
            String.format(
                "Variable not remapped: var=%d, offset=%d, mappedValue=0x%08X, argsSize=%d",
                var, offset, newVar, argsSize));
      }
      return unmask(newVar);
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
    for (int j : mapping) {
      if ((j & REMAP_FLAG) != 0) {
        sb.append(unmask(j));
      } else {
        sb.append(j);
      }
      sb.append(",");
    }
    sb.append("],\n{").append(Arrays.toString(mapping)).append("}");
    return sb.toString();
  }
}
