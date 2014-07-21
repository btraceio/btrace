/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.util.templates;

import com.sun.btrace.org.objectweb.asm.Handle;
import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.util.LocalVariableHelper;
import com.sun.btrace.util.templates.impl.MethodTimeStampExpander;
import java.util.ArrayList;
import java.util.Collection;

/**
 * An ASM visitor providing customized template expansion
 * @author Jaroslav Bachorik
 * @since 1.3
 */
public class TemplateExpanderVisitor extends MethodVisitor implements LocalVariableHelper {
    final private LocalVariableHelper lvs;

    final private Collection<TemplateExpander> expanders = new ArrayList<TemplateExpander>();

    public TemplateExpanderVisitor(LocalVariableHelper lvs,
                             String className, String methodName,
                             String desc) {
        super(Opcodes.ASM4, (MethodVisitor)lvs);
        this.lvs = lvs;

        this.expanders.add(new MethodTimeStampExpander(className, methodName, desc));
    }

    public int storeNewLocal(Type type) {
        return lvs.storeNewLocal(type);
    }

    @Override
    public void visitCode() {
        super.visitCode(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
        Template t = BTraceTemplates.getTemplate(owner, name, desc);

        if (expandTemplate(t) == TemplateExpander.Result.PASSED) {
            super.visitMethodInsn(opcode, owner, name, desc);
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isIface) {
        Template t = BTraceTemplates.getTemplate(owner, name, desc);

        if (expandTemplate(t) == TemplateExpander.Result.PASSED) {
            super.visitMethodInsn(opcode, owner, name, desc, isIface);
        }
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        expandTemplate(null);
        super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitMultiANewArrayInsn(String string, int i) {
        expandTemplate(null);
        super.visitMultiANewArrayInsn(string, i); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitLookupSwitchInsn(Label label, int[] ints, Label[] labels) {
        expandTemplate(null);
        super.visitLookupSwitchInsn(label, ints, labels); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitTableSwitchInsn(int i, int i1, Label label, Label... labels) {
        expandTemplate(null);
        super.visitTableSwitchInsn(i, i1, label, labels); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitIincInsn(int i, int i1) {
        expandTemplate(null);
        super.visitIincInsn(i, i1); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitLdcInsn(Object o) {
        expandTemplate(null);
        super.visitLdcInsn(o); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitJumpInsn(int i, Label label) {
        expandTemplate(null);
        super.visitJumpInsn(i, label); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitInvokeDynamicInsn(String string, String string1, Handle handle, Object... os) {
        expandTemplate(null);
        super.visitInvokeDynamicInsn(string, string1, handle, os); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitFieldInsn(int i, String string, String string1, String string2) {
        expandTemplate(null);
        super.visitFieldInsn(i, string, string1, string2); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitTypeInsn(int i, String string) {
        expandTemplate(null);
        super.visitTypeInsn(i, string); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitIntInsn(int i, int i1) {
        expandTemplate(null);
        super.visitIntInsn(i, i1); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitInsn(int i) {
        expandTemplate(null);
        super.visitInsn(i); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void visitEnd() {
        expandTemplate(null);
        super.visitEnd();
    }

    private boolean expanding = false;

    private TemplateExpander.Result expandTemplate(Template newTemplate) {
        if (expanding) return TemplateExpander.Result.PASSED;

        for(TemplateExpander exp : expanders) {
            TemplateExpander.Result r = exp.expand(this, newTemplate);
            if (r != TemplateExpander.Result.PASSED) {
                return r;
            }
        }
        return TemplateExpander.Result.PASSED;
    }

    public void expand(
        TemplateExpander.Consumer<TemplateExpanderVisitor> f)  {
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
