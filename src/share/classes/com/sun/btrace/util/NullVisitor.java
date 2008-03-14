/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.btrace.util;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

/**
 * A visitor that does nothing on visitXXX calls.
 * Implements all visitor interfaces of ASM library.
 *
 * @author A. Sundararajan
 */
public class NullVisitor implements AnnotationVisitor,
        ClassVisitor, FieldVisitor, MethodVisitor {

    // AnnotationVisitor methods
    public void visit(String name, Object value) {
    }

    public AnnotationVisitor visitAnnotation(String name, String desc) {
        return this;
    }

    public AnnotationVisitor visitArray(String name) {
        return this;
    }

    public void visitEnd() {
    }
     
    public void visitEnum(String name, String desc, String value) {
    }

    // ClassVisitor methods
    public void visit(int version, int access, String name, 
            String signature, String superName, String[] interfaces) {
    }
     
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return this;
    }    

    public void visitAttribute(Attribute attr) {
    }
   
    // already implemented
    // public void visitEnd()    

    public FieldVisitor visitField(int access, String name, String desc, 
            String signature, Object value) {
        return this;
    }
     
    public void visitInnerClass(String name, String outerName,
           String innerName, int access) {
    }

    public MethodVisitor visitMethod(int access, String name, String desc, 
           String signature, String[] exceptions) {
        return this;
    }

    public void visitOuterClass(String owner, String name, String desc) {
    }

    public void visitSource(String source, String debug) {
    }

    // FieldVisitor methods

    // already implemented
    // public AnnotationVisitor visitAnnotation(String desc, boolean visible)

    // already implemented
    // public void visitAttribute(Attribute attr)

    // already implemented
    // public void visitEnd() 

    // MethodVisitor methods

    // already implemented
    // public AnnotationVisitor visitAnnotation(String desc, boolean visible)

    public AnnotationVisitor visitAnnotationDefault() {
        return this;
    }
     
    // already implemented
    // public void visitAttribute(Attribute attr)
 
    public void visitCode() {
    }
     
    // already implemented
    // public void visitEnd()

    public void visitFieldInsn(int opcode, String owner, 
            String name, String desc) {
    }

    public void visitFrame(int type, int nLocal, Object[] local, 
            int nStack, Object[] stack) {
    }

    public void visitIincInsn(int var, int increment) {
    }

    public void visitInsn(int opcode) {
    }
 
    public void visitIntInsn(int opcode, int operand) {
    }

    public void visitJumpInsn(int opcode, Label label) {
    }

    public void visitLabel(Label label) {
    }

    public void visitLdcInsn(Object cst) {
    }

    public void visitLineNumber(int line, Label start) {
    }

    public void visitLocalVariable(String name, String desc, 
            String signature, Label start, Label end, int index) {
    }

    public void visitLookupSwitchInsn(Label dflt, 
            int[] keys, Label[] labels) {
    }

    public void visitMaxs(int maxStack, int maxLocals) {
    }

    public void visitMethodInsn(int opcode, String owner, 
            String name, String desc) {
    }

    public void visitMultiANewArrayInsn(String desc, int dims) {
    }

    public AnnotationVisitor visitParameterAnnotation(int parameter, 
            String desc, boolean visible) {
        return this;
    }

    public void visitTableSwitchInsn(int min, int max, 
            Label dflt, Label[] labels) {
    }
     
    public void visitTryCatchBlock(Label start, Label end,
            Label handler, String type) {
    }

    public void visitTypeInsn(int opcode, String desc) {
    }

    public void visitVarInsn(int opcode, int var) {
    }   
}
