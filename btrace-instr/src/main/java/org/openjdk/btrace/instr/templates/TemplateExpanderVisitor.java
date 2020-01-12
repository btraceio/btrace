/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.btrace.instr.templates;

import org.openjdk.btrace.core.MethodID;
import org.openjdk.btrace.instr.Assembler;
import org.openjdk.btrace.instr.BTraceMethodVisitor;
import org.openjdk.btrace.instr.MethodInstrumentorHelper;
import org.openjdk.btrace.instr.templates.impl.MethodTrackingExpander;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

import java.util.ArrayList;
import java.util.Collection;

/**
 * An ASM visitor providing customized template expansion
 *
 * @author Jaroslav Bachorik
 * @since 1.3
 */
public class TemplateExpanderVisitor extends BTraceMethodVisitor {
    private final Collection<TemplateExpander> expanders = new ArrayList<>();
    private final String className, methodName, desc;
    private final Assembler asm;
    private TemplateExpander.Result lastResult = TemplateExpander.Result.IGNORED;
    private boolean expanding = false;

    public TemplateExpanderVisitor(MethodVisitor mv, MethodInstrumentorHelper mHelper, String className, String methodName, String desc) {
        super(mv, mHelper);

        expanders.add(new MethodTrackingExpander(MethodID.getMethodId(className, methodName, desc), mHelper));
        this.className = className;
        this.methodName = methodName;
        this.desc = desc;
        asm = new Assembler(mv, mHelper);
    }

    public Assembler asm() {
        return asm;
    }

    @Override
    public void visitCode() {
        super.visitCode();
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isIface) {
        Template t = BTraceTemplates.getTemplate(owner, name, desc);

        if (expandTemplate(t) == TemplateExpander.Result.IGNORED) {
            super.visitMethodInsn(opcode, owner, name, desc, isIface);
        }
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitVarInsn(opcode, var);
        }
    }

    @Override
    public void visitMultiANewArrayInsn(String string, int i) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitMultiANewArrayInsn(string, i);
        }
    }

    @Override
    public void visitLookupSwitchInsn(Label label, int[] ints, Label[] labels) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitLookupSwitchInsn(label, ints, labels);
        }
    }

    @Override
    public void visitTableSwitchInsn(int i, int i1, Label label, Label... labels) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitTableSwitchInsn(i, i1, label, labels);
        }
    }

    @Override
    public void visitIincInsn(int i, int i1) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitIincInsn(i, i1);
        }
    }

    @Override
    public void visitLdcInsn(Object o) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitLdcInsn(o);
        }
    }

    @Override
    public void visitJumpInsn(int i, Label label) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitJumpInsn(i, label);
        }
    }

    @Override
    public void visitInvokeDynamicInsn(String string, String string1, Handle handle, Object... os) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitInvokeDynamicInsn(string, string1, handle, os);
        }
    }

    @Override
    public void visitFieldInsn(int i, String string, String string1, String string2) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitFieldInsn(i, string, string1, string2);
        }
    }

    @Override
    public void visitTypeInsn(int i, String string) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitTypeInsn(i, string);
        }
    }

    @Override
    public void visitIntInsn(int i, int i1) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitIntInsn(i, i1);
        }
    }

    @Override
    public void visitInsn(int i) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            switch (i) {
                case Opcodes.RETURN:
                case Opcodes.LRETURN:
                case Opcodes.DRETURN:
                case Opcodes.FRETURN:
                case Opcodes.ARETURN:
                case Opcodes.ATHROW:
                    resetState();
                    break;
            }
            super.visitInsn(i);
        }
    }

    @Override
    public void visitLineNumber(int i, Label label) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitLineNumber(i, label);
        }
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int i, TypePath tp, Label[] labels, Label[] labels1, int[] ints, String string, boolean bln) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            return super.visitLocalVariableAnnotation(i, tp, labels, labels1, ints, string, bln);
        }
        return null;
    }

    @Override
    public void visitLocalVariable(String string, String string1, String string2, Label label, Label label1, int i) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitLocalVariable(string, string1, string2, label, label1, i);
        }
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int i, TypePath tp, String string, boolean bln) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            return super.visitTryCatchAnnotation(i, tp, string, bln);
        }
        return null;
    }

    @Override
    public void visitTryCatchBlock(Label label, Label label1, Label label2, String string) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitTryCatchBlock(label, label1, label2, string);
        }
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int i, TypePath tp, String string, boolean bln) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            return super.visitInsnAnnotation(i, tp, string, bln);
        }
        return null;
    }

    @Override
    public void visitLabel(Label label) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitLabel(label);
        }
    }

    @Override
    public void visitAttribute(Attribute atrbt) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitAttribute(atrbt);
        }
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int i, String string, boolean bln) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            return super.visitParameterAnnotation(i, string, bln);
        }
        return null;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int i, TypePath tp, String string, boolean bln) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            return super.visitTypeAnnotation(i, tp, string, bln);
        }
        return null;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String string, boolean bln) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            return super.visitAnnotation(string, bln);
        }
        return null;
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            return super.visitAnnotationDefault();
        }
        return null;
    }

    @Override
    public void visitParameter(String string, int i) {
        if (expandTemplate(null) == TemplateExpander.Result.IGNORED) {
            super.visitParameter(string, i);
        }
    }

    @Override
    public void visitEnd() {
        expandTemplate(null);
        super.visitEnd();
    }

    @Override
    public void visitMaxs(int stack, int locals) {
        expandTemplate(null);
        super.visitMaxs(stack, locals);
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDesc() {
        return desc;
    }

    private TemplateExpander.Result expandTemplate(Template newTemplate) {
        if (expanding) {
            return TemplateExpander.Result.IGNORED;
        }
        if (newTemplate == null && lastResult == TemplateExpander.Result.IGNORED) {
            return TemplateExpander.Result.IGNORED;
        }

        for (TemplateExpander exp : expanders) {
            TemplateExpander.Result r = exp.expand(this, newTemplate);
            if (r != TemplateExpander.Result.IGNORED) {
                lastResult = r;
                return r;
            }
        }
        lastResult = TemplateExpander.Result.IGNORED;
        return TemplateExpander.Result.IGNORED;
    }

    private void resetState() {
        for (TemplateExpander exp : expanders) {
            exp.resetState();
        }
    }

    public void expand(
            TemplateExpander.Consumer<TemplateExpanderVisitor> f) {
        try {
            if (!expanding) {
                expanding = true;
                f.consume(this);
            }
        } finally {
            expanding = false;
        }
    }
}
