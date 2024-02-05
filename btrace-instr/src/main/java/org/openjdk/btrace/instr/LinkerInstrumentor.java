package org.openjdk.btrace.instr;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class LinkerInstrumentor {
    private static class LinkerMethodVisitor extends MethodVisitor {
        private final Label tryStart = new Label();
        private final Label tryEnd = new Label();
        private final Label finallyStart = new Label();

        private int maxVarIndex;

        public LinkerMethodVisitor(MethodVisitor mv, int argStackSize) {
            super(Opcodes.ASM9, mv);
            this.maxVarIndex = argStackSize;
        }

        @Override
        public void visitCode() {
            mv.visitTryCatchBlock(tryStart, tryEnd, finallyStart, null);
            mv.visitLabel(tryStart);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/openjdk/btrace/runtime/LinkingFlag", "guardLinking", "()I", false);
            mv.visitInsn(Opcodes.POP); // discard the result
            super.visitCode();
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            super.visitVarInsn(opcode, varIndex);
            maxVarIndex = Math.max(maxVarIndex, varIndex);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.ARETURN) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/openjdk/btrace/runtime/LinkingFlag", "reset", "()V", false);
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            mv.visitLabel(tryEnd);
            mv.visitLabel(finallyStart);
            mv.visitVarInsn(Opcodes.ASTORE, maxVarIndex);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/openjdk/btrace/runtime/LinkingFlag", "reset", "()V", false);
            mv.visitVarInsn(Opcodes.ALOAD, maxVarIndex);
            mv.visitInsn(Opcodes.ATHROW);
            super.visitMaxs(0, maxVarIndex);
        }
    }

    private static class LinkerClassVisitor extends ClassVisitor {
        public LinkerClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals("linkCallSite") || name.equals("linkMethodHandleConstant")) {
                int argStackSize = Type.getArgumentsAndReturnSizes(descriptor) - 1;
                mv = new LinkerMethodVisitor(mv, argStackSize);
            }
            return mv;
        }
    }
    public static byte[] addGuard(byte[] classData) {
        ClassReader cr = new ClassReader(classData);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new LinkerClassVisitor(cw), ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }
}
