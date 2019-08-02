package org.openjdk.btrace.instr;

import java.util.Arrays;

public class VariableMapper {
    private static final int UNMASK = 0x1FFFFFFF;
    private static final int DOUBLE_SLOT_FLAG = 0x20000000;
    private static final int REMAP_FLAG = 0x40000000;

    private int argsSize;

    private int nextMappedVar = 0;
    private int[] mapping = new int[8];

    public VariableMapper(int argsSize) {
        this.argsSize = argsSize;
        nextMappedVar = argsSize;
    }

    private VariableMapper(int argsSize, int nextMappedVar, int[] mapping) {
        this.argsSize = argsSize;
        this.nextMappedVar = nextMappedVar;
        this.mapping = Arrays.copyOf(mapping, mapping.length);
    }

    public static int unmask(int var) {
        return var & UNMASK;
    }

    public void replaceWith(VariableMapper other) {
        argsSize = other.argsSize;
        nextMappedVar = other.nextMappedVar;
        mapping = Arrays.copyOf(other.mapping, other.mapping.length);
    }

    public VariableMapper mirror() {
        return new VariableMapper(argsSize, nextMappedVar, mapping);
    }

    public void setMapping(int from, int to, int size) {
        int padding = size == 1 ? 0 : 1;
        if (mapping.length <= from + padding) {
            mapping = Arrays.copyOf(mapping, Math.max(mapping.length * 2, from + padding + 1));
        }
        mapping[from] = to;
        if (padding > 0) {
            mapping[from + padding] = Math.abs(to) + padding; // padding
        }
    }

    public int remap(int var, int size) {
        if ((var & REMAP_FLAG) != 0) {
            return var & UNMASK;
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
        int unmasked = mappedVar & UNMASK;
        // adjust the mapping pointer if remapping with variable occupying 2 slots
        nextMappedVar = Math.max(unmasked + size, nextMappedVar);
        return unmasked;
    }

    public int map(int var) {
        if (((var & REMAP_FLAG) != 0)) {
            return var & UNMASK;
        }

        int offset = (var - argsSize);
        if (offset >= 0) {
            if (mapping.length <= offset) {
                return 0xFFFFFFFF;
            }
            return mapping[offset] & UNMASK;
        }
        return var;
    }

    public int[] mappings() {
        int[] cleansed = new int[mapping.length];
        for (int i = 0; i < mapping.length; i++) {
            cleansed[i] = mapping[i] & UNMASK;
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
}
