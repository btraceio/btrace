package com.sun.btrace.shared;

import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.org.objectweb.asm.tree.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MethodSerializer {
    private static final class LabelRepository {
        private final Map<LabelNode, Integer> labelMap = new HashMap<>();
        private final AtomicInteger counter = new AtomicInteger();

        public int getLabelIdx(LabelNode ln) {
            Integer i = labelMap.get(ln);
            if (i == null) {
                i = counter.getAndIncrement();
                labelMap.put(ln, i);
            }
            return i;
        }
    }

    public static byte[] serialize(MethodNode mn) throws IOException {
        LabelRepository lr = new LabelRepository();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeUTF(mn.name);
        dos.writeInt(mn.access);
        dos.writeUTF(mn.desc);
        writeParameters((List<ParameterNode>)mn.parameters, dos);
        writeLocalVariables((List<LocalVariableNode>)mn.localVariables, lr, dos);

        writeBody(mn.instructions, lr, dos);

        return bos.toByteArray();
    }

    private static void writeParameters(List<ParameterNode> nodes, DataOutputStream dos) throws IOException {
        if (nodes == null) {
            dos.writeInt(0);
            return;
        }
        dos.writeInt(nodes.size());
        for (ParameterNode pm : nodes) {
            dos.writeUTF(pm.name);
            dos.writeInt(pm.access);
        }
    }

    private static void writeLocalVariables(List<LocalVariableNode> nodes, LabelRepository lr, DataOutputStream dos) throws IOException {
        if (nodes == null) {
            dos.writeInt(0);
            return;
        }
        dos.writeInt(nodes.size());
        for (LocalVariableNode lvn : nodes) {
            dos.writeUTF(lvn.name);
            dos.writeUTF(lvn.desc);
            dos.writeUTF(lvn.signature);
            dos.writeInt(lvn.index);
            writeLabel(lvn.start, lr, dos);
            writeLabel(lvn.end, lr, dos);
        }
    }

    private static void writeBody(InsnList il, LabelRepository lr, DataOutputStream dos) throws IOException {
        for (AbstractInsnNode n = il.getFirst(); n != null; n = n.getNext()) {
            dos.writeInt(n.getType());
            dos.writeInt(n.getOpcode());
            switch (n.getType()) {
                case AbstractInsnNode.FIELD_INSN: {
                    writeField((FieldInsnNode)n, dos);
                    break;
                }
                case AbstractInsnNode.FRAME: {
                    writeFrame((FrameNode)n, lr, dos);
                    break;
                }
                case AbstractInsnNode.IINC_INSN: {
                    IincInsnNode iin = (IincInsnNode)n;
                    dos.writeInt(iin.var);
                    dos.writeInt(iin.incr);
                    break;
                }
                case AbstractInsnNode.INSN: {
                    // just opcode
                    break;
                }
                case AbstractInsnNode.INT_INSN: {
                    IntInsnNode iin = (IntInsnNode)n;
                    dos.writeInt(iin.operand);
                    break;
                }
                case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
                    // ignore for now
                    break;
                }
                case AbstractInsnNode.LABEL: {
                    writeLabel((LabelNode)n, lr, dos);
                    break;
                }
                case AbstractInsnNode.JUMP_INSN: {
                    JumpInsnNode jin = (JumpInsnNode)n;
                    writeLabel(jin.label, lr, dos);
                    break;
                }
                case AbstractInsnNode.LDC_INSN: {
                    writeLdc((LdcInsnNode)n, dos);
                    break;
                }
                case AbstractInsnNode.LOOKUPSWITCH_INSN: {
                    writeLookupSwitch((LookupSwitchInsnNode)n, lr, dos);
                    break;
                }
                case AbstractInsnNode.METHOD_INSN: {
                    writeMethodInv((MethodInsnNode)n, dos);
                    break;
                }
                case AbstractInsnNode.MULTIANEWARRAY_INSN: {
                    writeMultiANewArray((MultiANewArrayInsnNode)n, dos);
                    break;
                }
                case AbstractInsnNode.TABLESWITCH_INSN: {
                    writeTableSwitch((TableSwitchInsnNode)n, lr, dos);
                    break;
                }
                case AbstractInsnNode.TYPE_INSN: {
                    writeTypeInsn((TypeInsnNode)n, dos);
                    break;
                }
                case AbstractInsnNode.VAR_INSN: {
                    writeVar((VarInsnNode)n, dos);
                    break;
                }
            }
        }
    }

    private static void writeLabel(LabelNode ln, LabelRepository lr, DataOutputStream dos) throws IOException {
        dos.writeInt(lr.getLabelIdx(ln));
    }

    private static void writeFrame(FrameNode fn, LabelRepository lr, DataOutputStream dos) throws IOException {
        for (Object o : fn.local) {
            if (o instanceof LabelNode) {
                dos.writeShort(1);
                writeLabel((LabelNode)o, lr, dos);
            } else {
                if (o instanceof String) {
                    dos.writeShort(2);
                    dos.writeUTF((String)o);
                } else if (o instanceof Integer) {
                    dos.writeShort(3);
                    dos.writeInt((Integer)o);
                } else {
                    dos.writeShort(0);
                }
            }
        }
        for (Object o : fn.stack) {
            if (o instanceof LabelNode) {
                dos.writeShort(1);
                writeLabel((LabelNode)o, lr, dos);
            } else {
                if (o instanceof String) {
                    dos.writeShort(2);
                    dos.writeUTF((String)o);
                } else if (o instanceof Integer) {
                    dos.writeShort(3);
                    dos.writeInt((Integer)o);
                } else {
                    dos.writeShort(0);
                }
            }
        }
    }

    private static void writeField(FieldInsnNode fin, DataOutputStream dos) throws IOException {
        dos.writeUTF(fin.name);
        dos.writeUTF(fin.owner);
        dos.writeUTF(fin.desc);
    }

    private static void writeLdc(LdcInsnNode lin, DataOutputStream dos) throws IOException {
        Object o = lin.cst;
        if (o instanceof Integer) {
            dos.writeShort(1);
            dos.writeInt((Integer)o);
        } else if (o instanceof Float) {
            dos.writeShort(2);
            dos.writeFloat((Float)o);
        } else if (o instanceof Long) {
            dos.writeShort(3);
            dos.writeLong((Long)o);
        } else if (o instanceof Double) {
            dos.writeShort(4);
            dos.writeDouble((Double)o);
        } else if (o instanceof String) {
            dos.writeShort(5);
            dos.writeUTF((String)o);
        } else if (o instanceof Type) {
            dos.writeShort(6);
            dos.writeUTF(((Type)o).getDescriptor());
        } else {
            dos.writeShort(0);
        }
    }

    private static void writeLookupSwitch(LookupSwitchInsnNode lsin, LabelRepository lr, DataOutputStream dos) throws IOException {
        writeLabel(lsin.dflt, lr, dos);
        dos.writeInt(lsin.keys.size());
        for (Integer key : (List<Integer>)lsin.keys) {
            dos.writeInt(key);
        }
        dos.writeInt(lsin.labels.size());
        for (LabelNode ln : (List<LabelNode>)lsin.labels) {
            writeLabel(ln, lr, dos);
        }
    }

    private static void writeMethodInv(MethodInsnNode min, DataOutputStream dos) throws IOException {
        dos.writeUTF(min.owner);
        dos.writeUTF(min.name);
        dos.writeUTF(min.desc);
        dos.writeBoolean(min.itf);
    }

    private static void writeMultiANewArray(MultiANewArrayInsnNode n, DataOutputStream dos) throws IOException {
        dos.writeInt(n.dims);
        dos.writeUTF(n.desc);
    }

    private static void writeTableSwitch(TableSwitchInsnNode n, LabelRepository lr, DataOutputStream dos) throws IOException {
        dos.writeInt(n.min);
        dos.writeInt(n.max);
        writeLabel(n.dflt, lr, dos);
        dos.writeInt(n.labels.size());
        for (LabelNode ln : (List<LabelNode>)n.labels) {
            writeLabel(ln, lr, dos);
        }
    }

    private static void writeTypeInsn(TypeInsnNode n, DataOutputStream dos) throws IOException {
        dos.writeUTF(n.desc);
    }

    private static void writeVar(VarInsnNode n, DataOutputStream dos) throws IOException {
        dos.writeInt(n.var);
    }
}
