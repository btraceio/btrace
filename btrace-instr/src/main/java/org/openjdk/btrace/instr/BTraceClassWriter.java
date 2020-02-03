/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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
package org.openjdk.btrace.instr;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * A hacked version of <a href="http://asm.ow2.org/asm50/javadoc/user/org/objectweb/asm/ClassWriter.html">ClassWriter</a>
 * allowing to plug-in instrumentation providers and instrument class in single invocation. Also, it provides
 * a smart and lightweight common supertype resolution method for computing frames.
 *
 * @author Jaroslav Bachorik
 */
final class BTraceClassWriter extends ClassWriter {
    private final Deque<Instrumentor> instrumentors = new ArrayDeque<>();
    private final ClassLoader targetCL;
    private final BTraceClassReader cr;
    private final Collection<MethodNode> cushionMethods = new HashSet<>();

    BTraceClassWriter(ClassLoader cl, int flags) {
        super(flags);
        targetCL = cl != null ? cl : ClassLoader.getSystemClassLoader();
        cr = null;
    }

    BTraceClassWriter(ClassLoader cl, BTraceClassReader reader, int flags) {
        super(reader, flags);
        targetCL = cl != null ? cl : ClassLoader.getSystemClassLoader();
        cr = reader;
    }

    public void addInstrumentor(BTraceProbe bp) {
        addInstrumentor(bp, null);
    }

    public void addInstrumentor(BTraceProbe bp, ClassLoader cl) {
        if (cr != null && bp != null) {
            synchronized (instrumentors) {
                Instrumentor top = instrumentors.peekLast();
                ClassVisitor parent = top != null ? top : this;
                Instrumentor i = Instrumentor.create(cr, bp, parent, cl);
                if (i != null) {
                    instrumentors.add(i);
                }
            }
        }
    }

    public byte[] instrument() {
        boolean hit = false;
        synchronized (instrumentors) {
            if (instrumentors.isEmpty()) return null;

            ClassVisitor top = instrumentors.peekLast();
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM7, top != null ? top : this) {
                private String cname;

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    cname = name;
                    super.visit(version, access, name, signature, superName, interfaces);
                }

                @Override
                public void visitEnd() {
                    for (MethodNode m : cushionMethods) {
                        m.accept(this);
                    }
                    super.visitEnd();
                }
            };
            InstrumentUtils.accept(cr, cv);
            for (Instrumentor i : instrumentors) {
                hit |= i.hasMatch();
            }
        }
        return hit ? toByteArray() : null;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        // Using type closures resolved via the associate classloader
        LinkedHashSet<String> type1Closure = new LinkedHashSet<>();
        LinkedHashSet<String> type2Closure = new LinkedHashSet<>();
        InstrumentUtils.collectHierarchyClosure(targetCL, type1, type1Closure, true);
        InstrumentUtils.collectHierarchyClosure(targetCL, type2, type2Closure, true);
        // basically, do intersection
        type1Closure.retainAll(type2Closure);

        // if the intersection is not empty the first element is the closest common ancestor
        Iterator<String> iter = type1Closure.iterator();
        if (iter.hasNext()) {
            String common = iter.next();
            return common;
        }
        return Constants.OBJECT_INTERNAL;
    }

    /**
     * Add dummy cushion methods to account for an instrumented code in hot loop still running
     * even once the instrumentation was removed (code can not be hotswapped as long as the affected
     * method is on stack so it may happen that an instrumentation from a disconnected BTrace client
     * will be running for a long time)
     * @param pillowMethods the methods to create cushions for
     */
    public void addCushionMethods(Collection<MethodNode> pillowMethods) {
        cushionMethods.addAll(pillowMethods);
    }
}
