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

package com.sun.btrace.runtime;

import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.util.LocalVariableHelperImpl;
import com.sun.btrace.util.templates.TemplateExpanderVisitor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;
import sun.misc.Unsafe;
import support.InstrumentorTestBase;

/**
 *
 * @author Jaroslav Bachorik
 */
public class ClinitInstrumentorTest extends InstrumentorTestBase {
    private ClassLoader dummyCl = null;
    @Before
    public void setup() {
        dummyCl = new URLClassLoader(new URL[0]);
    }

    /**
     * Tests the correctness of the "clinit" code injected
     * in order to allow proper matching for subclasses/implementations
     */
    @Test
    public void checkTransformedClinit() throws Exception {
        originalBC = loadTargetClass("DerivedClass");
        transform("onmethod/MatchDerived");

        // make sure the class is loadable
        Field f = AtomicLong.class.getDeclaredField("unsafe");
        f.setAccessible(true);
        Unsafe u = (Unsafe)f.get(null);

        Class clz = u.defineClass("resources.DerivedClass", transformedBC, 0, transformedBC.length, dummyCl, null);

        // check transformation
        checkTransformation("LDC \"btrace\"\nLDC Ltraces/onmethod/MatchDerived;.class\n" +
                            "INVOKESTATIC com/sun/btrace/BTraceRuntime.retransform (Ljava/lang/String;Ljava/lang/Class;)V\n" +
                            "MAXSTACK = 2");
    }

    @Override
    protected void transform(String traceName) throws IOException {
        final Trace btrace = loadTrace(traceName);
        ClassReader reader = new ClassReader(originalBC);
        ClassWriter writer = InstrumentUtils.newClassWriter();

        InstrumentUtils.accept(reader, new ClinitInjector(writer, "btrace", btrace.className));

        transformedBC = writer.toByteArray();

        System.out.println(asmify(transformedBC));

        ClassReader cr1 = new ClassReader(transformedBC);
        ClassWriter cw1 = InstrumentUtils.newClassWriter();

        InstrumentUtils.accept(reader, new ClassVisitor(Opcodes.ASM5, cw1) {

            @Override
            public MethodVisitor visitMethod(int i, String string, String string1, String string2, String[] strings) {
                MethodVisitor mv = super.visitMethod(i, string, string1, string2, strings);
                LocalVariableHelperImpl lvh = new LocalVariableHelperImpl(mv, i, string1);
                TemplateExpanderVisitor tev = new TemplateExpanderVisitor(lvh, btrace.className, string, string1);
                return tev;
            }
        });

        System.out.println(asmify(cw1.toByteArray()));
        System.err.println("==== " + traceName);
    }
}
