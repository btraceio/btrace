package org.openjdk.btrace.instr;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class ProbeRenameVisitor extends ClassVisitor {
    private String oldClassName = null;
    private final String newClassName;

    ProbeRenameVisitor(ClassVisitor classVisitor, String newClassName) {
        super(Opcodes.ASM7, classVisitor);
        this.newClassName = newClassName.replace('.', '/');
    }

    static byte[] rename(String newClassName, byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cr.accept(new ProbeRenameVisitor(cw, newClassName), ClassReader.SKIP_DEBUG);
        return cw.toByteArray();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        oldClassName = name;
        super.visit(version, access, newClassName, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            @Override
            public void visitTypeInsn(int opcode, String type) {
                super.visitTypeInsn(opcode, type.equals(oldClassName) ? newClassName : type);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                super.visitFieldInsn(opcode, owner.equals(oldClassName) ? newClassName : owner, name, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                super.visitMethodInsn(opcode, owner.equals(oldClassName) ? newClassName : owner, name, descriptor, isInterface);
            }
        };
    }
}
