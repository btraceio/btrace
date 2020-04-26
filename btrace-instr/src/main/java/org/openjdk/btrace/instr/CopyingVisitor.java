package org.openjdk.btrace.instr;


import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.objectweb.asm.Opcodes.ASM7;

class CopyingVisitor extends ClassVisitor {
    private final boolean renameParent;
    private final String targetClassName;

    private String origClassName;

    public CopyingVisitor(String targetClassName, boolean renameParent, ClassVisitor parent) {
        super(Opcodes.ASM8, parent);
        this.targetClassName = targetClassName;
        this.renameParent = renameParent;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (renameParent) {
            super.visit(version, access, targetClassName, signature, superName, interfaces);
        }
        origClassName = name;
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String desc, String sig, String[] exceptions) {
        return new MethodVisitor(ASM7, super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, getMethodName(name), desc, sig, exceptions)) {
            @Override
            public void visitMethodInsn(
                    int opcode, String owner, String name, String desc, boolean itfc) {
                if (owner.equals(origClassName)) {
                    owner = targetClassName;
                    name = getActionMethodName(name);
                }
                super.visitMethodInsn(opcode, owner, name, desc, itfc);
            }
        };
    };

    protected String getActionMethodName(String name) {
        return name;
    }

    protected String getMethodName(String name) {
        return name;
    }
}
