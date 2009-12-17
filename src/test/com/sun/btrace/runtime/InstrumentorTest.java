/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.runtime;

import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.objectweb.asm.util.TraceClassVisitor;

/**
 *
 * @author Jaroslav Bachorik
 */
public class InstrumentorTest {
    private static class Trace {
        final byte[] content;
        final List<OnMethod> onMethods;
        final String className;

        public Trace(byte[] content, List<OnMethod> onMethods, String className) {
            this.content = content;
            this.onMethods = onMethods;
            this.className = className;
        }
    }
    public InstrumentorTest() {
    }

    private byte[] originalBC;
    private byte[] transformedBC;

    @Before
    public void setUp() {

    }

    @After
    public void tearDown() {
        originalBC = null;
        transformedBC = null;
    }

    @Test
    public void matchDerivedClass() throws Exception {
        originalBC = loadTargetClass("DerivedClass");
        transform("onmethod/MatchDerived");

        checkTransformation("ALOAD 0\nALOAD 1\nALOAD 2\n" +
                            "INVOKESTATIC resources/DerivedClass.$btrace$traces$onmethod$MatchDerived$args (Lresources/AbstractClass;Ljava/lang/String;Ljava/util/Map;)V");
    }

    @Test
    public void methodEntryCheckcastBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/CheckcastBefore");

        checkTransformation("DUP\nASTORE 3\nALOAD 0\nLDC \"resources.OnMethodTest\"\nALOAD 3\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$CheckcastBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V");
    }

    @Test
    public void methodEntryCheckcastAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/CheckcastAfter");

        checkTransformation("DUP\nASTORE 3\nALOAD 0\nLDC \"casts\"\nALOAD 3\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$CheckcastAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V\n");
    }

    @Test
    public void methodEntryInstanceofBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/InstanceofBefore");

        checkTransformation("DUP\nASTORE 4\nALOAD 0\nLDC \"resources.OnMethodTest\"\nALOAD 4\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$InstanceofBefore$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V");
    }

    @Test
    public void methodEntryInstanceofAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/InstanceofAfter");

        checkTransformation("DUP\nASTORE 4\nALOAD 0\nLDC \"casts\"\nALOAD 4\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$InstanceofAfter$args (Ljava/lang/Object;Ljava/lang/String;Ljava/util/HashMap;)V\n");
    }

    @Test
    public void methodEntryCatch() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Catch");

        checkTransformation("DUP\nASTORE 2\nALOAD 0\nALOAD 2\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Catch$args (Ljava/lang/Object;Ljava/io/IOException;)V");
    }

    @Test
    public void methodEntryThrow() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Throw");

        checkTransformation("DUP\nASTORE 2\nALOAD 0\nLDC \"resources.OnMethodTest\"\nLDC \"exception\"\nALOAD 2\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Throw$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V");
    }

    @Test
    public void methodEntryError() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Error");

        checkTransformation("TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\nDUP\nASTORE 2\nALOAD 0\nLDC \"uncaught\"\nALOAD 2\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Error$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Throwable;)V");
    }

    @Test
    public void methodEntryLine() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Line");

        checkTransformation("LDC \"field\"\nLDC 64\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Line$args (Ljava/lang/Object;Ljava/lang/String;I)V\n" +
                            "ALOAD 0");
    }

    @Test
    public void methodEntryNewBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewBefore");

        checkTransformation("ALOAD 0\nLDC \"java.util.HashMap\"\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewBefore$args (Ljava/lang/Object;Ljava/lang/String;)V");
    }

    @Test
    public void methodEntryNewAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewAfter");

        checkTransformation("ASTORE 2\nALOAD 0\nALOAD 2\nLDC \"java.util.HashMap\"\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewAfter$args (Ljava/lang/Object;Ljava/util/Map;Ljava/lang/String;)V\n" +
                            "DUP");
    }

    @Test
    public void methodEntrySyncEntry() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/SyncEntry");

        checkTransformation("TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\nDUP\nDUP\nASTORE 3\nALOAD 0\nLDC \"sync\"\nALOAD 3\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$SyncEntry$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntrySyncExit() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/SyncExit");

        checkTransformation("TRYCATCHBLOCK L4 L5 L5 java/lang/Throwable\nDUP\nPOP\nL6\nLINENUMBER 90 L6\n" +
                            "DUP\nDUP\nASTORE 3\nALOAD 0\nLDC \"resources/OnMethodTest\"\nALOAD 3\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$SyncExit$args (Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V\n");
    }

    @Test
    public void methodEntryNewArrayIntBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayIntBefore");

        checkTransformation("ALOAD 0\nLDC \"int\"\nLDC 1\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayIntBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V");
    }

    @Test
    public void methodEntryNewArrayStringBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayStringBefore");

        checkTransformation("ALOAD 0\nLDC \"java.lang.String\"\nLDC 1\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayStringBefore$args (Ljava/lang/Object;Ljava/lang/String;I)V");
    }

    @Test
    public void methodEntryNewArrayIntAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayIntAfter");

        checkTransformation("DUP\nASTORE 2\nALOAD 0\nALOAD 2\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayIntAfter$args (Ljava/lang/Object;[I)V");
    }

    @Test
    public void methodEntryNewArrayStringAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NewArrayStringAfter");

        checkTransformation("DUP\nASTORE 4\nALOAD 0\nALOAD 4\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NewArrayStringAfter$args (Ljava/lang/Object;[Ljava/lang/String;)V");
    }

    @Test
    public void methodEntryArrayGetBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArrayGetBefore");

        checkTransformation("DUP2\nISTORE 5\nASTORE 6\nALOAD 0\nALOAD 6\nILOAD 5\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArrayGetBefore$args (Ljava/lang/Object;[II)V");
    }

    @Test
    public void methodEntryArrayGetAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArrayGetAfter");

        checkTransformation("DUP2\nISTORE 5\nASTORE 6\nDUP\nISTORE 8\nALOAD 0\nILOAD 8\nALOAD 6\nILOAD 5\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArrayGetAfter$args (Ljava/lang/Object;I[II)V");
    }

    @Test
    public void methodEntryArraySetBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArraySetBefore");

        checkTransformation("ISTORE 7\nDUP2\nISTORE 8\nASTORE 9\nILOAD 7\nALOAD 0\nALOAD 9\nILOAD 8\nILOAD 7\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArraySetBefore$args (Ljava/lang/Object;[III)V");
    }

    @Test
    public void methodEntryArraySetAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArraySetAfter");

        checkTransformation("ISTORE 7\nDUP2\nISTORE 8\nASTORE 9\nILOAD 7\nALOAD 0\nALOAD 9\nILOAD 8\nILOAD 7\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArraySetAfter$args (Ljava/lang/Object;[III)V");
    }

    @Test
    public void methodEntryFieldGetBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/FieldGetBefore");

        checkTransformation("DUP\nASTORE 2\nALOAD 0\nALOAD 2\nLDC \"field\"\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldGetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V");
    }

    @Test
    public void methodEntryFieldGetAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/FieldGetAfter");

        checkTransformation("DUP\nASTORE 2\nDUP\nISTORE 4\nALOAD 0\nALOAD 2\nLDC \"field\"\nILOAD 4\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldGetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V");
    }

    @Test
    public void methodEntryFieldSetBefore() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/FieldSetBefore");

        checkTransformation("ISTORE 2\nDUP\nASTORE 4\nILOAD 2\nALOAD 0\nALOAD 4\nLDC \"field\"\nILOAD 2\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldSetBefore$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V");
    }

    @Test
    public void methodEntryFieldSetAfter() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/FieldSetAfter");

        checkTransformation("ISTORE 2\nDUP\nASTORE 4\nILOAD 2\nALOAD 0\nALOAD 4\nLDC \"field\"\nILOAD 2\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$FieldSetAfter$args (Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;I)V");
    }

    @Test
    public void methodEntryArgsNoSelf() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsNoSelf");

        checkTransformation("ALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsNoSelf$argsNoSelf (Ljava/lang/String;J[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryNoArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NoArgs");

        checkTransformation("ALOAD 0\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgs$argsEmpty (Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntryArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/Args");
        checkTransformation("ALOAD 0\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$Args$args (Ljava/lang/Object;Ljava/lang/String;J[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryArgsReturn() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsReturn");
        checkTransformation("DUP2\nLSTORE 8\nALOAD 0\nLLOAD 8\nALOAD 1\nLLOAD 2\nALOAD 4\nALOAD 5\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsReturn$args (Ljava/lang/Object;JLjava/lang/String;J[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryArgsDuration() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration");
        checkTransformation("INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 8\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 12\n" +
                            "DUP2\nLSTORE 16\nALOAD 0\nLLOAD 16\nLLOAD 12\nLLOAD 8\nLSUB\nALOAD 1\n" +
                            "LLOAD 2\nALOAD 4\nALOAD 5\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
                            "MAXSTACK");
    }

    @Test
    // check for multiple timestamps
    public void methodEntryArgsDuration2() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/ArgsDuration2");
        checkTransformation("INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 10\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$time$stamp ()J\nLSTORE 16\n" +
                            "DUP2\nLSTORE 22\nALOAD 0\nLLOAD 22\nLLOAD 16\nLLOAD 10\nLSUB\nALOAD 1\n" +
                            "LLOAD 2\nALOAD 4\nALOAD 5\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2$args2 (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
                            "DUP2\nLSTORE 26\nALOAD 0\nLLOAD 26\nLLOAD 16\nLLOAD 10\nLSUB\nALOAD 1\n" +
                            "LLOAD 2\nALOAD 4\nALOAD 5\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$ArgsDuration2$args (Ljava/lang/Object;JJLjava/lang/String;J[Ljava/lang/String;[I)V\n" +
                            "MAXSTACK");
    }

    @Test
    public void methodEntryAnytypeArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/AnytypeArgs");
        checkTransformation("ALOAD 0\nICONST_4\nANEWARRAY java/lang/Object\nDUP\n" +
                     "ICONST_0\nALOAD 1\nAASTORE\nDUP\n" +
                     "ICONST_1\nLLOAD 2\nINVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\nAASTORE\nDUP\n" +
                     "ICONST_2\nALOAD 4\nAASTORE\nDUP\n" +
                     "ICONST_3\nALOAD 5\nAASTORE\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$AnytypeArgs$args (Ljava/lang/Object;[Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntryAnytypeArgsNoSelf() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/AnytypeArgsNoSelf");
        checkTransformation("ICONST_4\nANEWARRAY java/lang/Object\nDUP\nICONST_0\nALOAD 1\nAASTORE\n" +
                     "DUP\nICONST_1\nLLOAD 2\nINVOKESTATIC java/lang/Long.valueOf (J)Ljava/lang/Long;\nAASTORE\n" +
                     "DUP\nICONST_2\nALOAD 4\nAASTORE\nDUP\nICONST_3\nALOAD 5\nAASTORE\n" +
                     "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$AnytypeArgsNoSelf$argsNoSelf ([Ljava/lang/Object;)V");
    }

    @Test
    public void methodEntryStaticArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticArgs");

        checkTransformation("ALOAD 0\nLLOAD 1\nALOAD 3\nALOAD 4\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticArgs$args (Ljava/lang/String;J[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryStaticArgsReturn() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticArgsReturn");

        checkTransformation("DUP2\nLSTORE 7\nALOAD 0\nLLOAD 7\nLLOAD 1\nALOAD 3\nALOAD 4\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticArgsReturn$args (Ljava/lang/String;JJ[Ljava/lang/String;[I)V");
    }

    @Test
    public void methodEntryStaticArgsSelf() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticArgsSelf");

        assertTrue(diff().length() == 0);
    }

    @Test
    public void methodEntryStaticNoArgs() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticNoArgs");

        checkTransformation("INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticNoArgs$argsEmpty ()V");
    }

    @Test
    public void methodEntryStaticNoArgsSelf() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticNoArgsSelf");

        assertTrue(diff().length() == 0);
    }

    @Test
    public void methodCall() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCall");

        checkTransformation("LSTORE 6\nASTORE 9\nASTORE 11\nALOAD 0\nALOAD 9\nLLOAD 6\nALOAD 11\n" +
                            "LDC \"callTarget(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n" +
                            "LDC \"callTopLevel\"\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCall$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
                            "ALOAD 11\nALOAD 9\nLLOAD 6");
    }

    @Test
    public void methodCallStatic() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/MethodCallStatic");

        checkTransformation("LSTORE 6\nASTORE 9\nALOAD 0\nALOAD 9\nLLOAD 6\n" +
                            "LDC \"callTargetStatic(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n" +
                            "LDC \"callTopLevel\"\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$MethodCallStatic$args (Ljava/lang/Object;Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
                            "ALOAD 9\nLLOAD 6");
    }

    @Test
    public void staticMethodCall() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticMethodCall");

        checkTransformation("LSTORE 6\nASTORE 9\nASTORE 11\nALOAD 9\nLLOAD 6\nALOAD 11\n" +
                            "LDC \"callTarget(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n" +
                            "LDC \"callTopLevelStatic\"\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticMethodCall$args (Ljava/lang/String;JLjava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
                            "ALOAD 11\nALOAD 9\nLLOAD 6");
    }

    @Test
    public void staticMethodCallStatic() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/StaticMethodCallStatic");

        checkTransformation("LSTORE 6\nASTORE 9\nALOAD 9\nLLOAD 6\n" +
                            "LDC \"callTargetStatic(Ljava/lang/String;J)J\"\nLDC \"resources/OnMethodTest\"\n" +
                            "LDC \"callTopLevelStatic\"\n" +
                            "INVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$StaticMethodCallStatic$args (Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n" +
                            "ALOAD 9\nLLOAD 6");
    }

    @Test
    public void methodEntryNoArgsEntryReturn() throws Exception {
        originalBC = loadTargetClass("OnMethodTest");
        transform("onmethod/NoArgsEntryReturn");

        checkTransformation("ALOAD 0\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgsEntryReturn$argsEmptyEntry (Ljava/lang/Object;)V\n" +
                            "ALOAD 0\nINVOKESTATIC resources/OnMethodTest.$btrace$traces$onmethod$NoArgsEntryReturn$argsEmptyReturn (Ljava/lang/Object;)V");
    }
    
    private void checkTransformation(String expected) throws IOException {
        String diff = diff();
        System.err.println(diff);
        assertEquals(expected, diff.substring(0, diff.length() > expected.length() ? expected.length() : diff.length()));
    }

    private void transform(String traceName) throws IOException {
        Trace btrace = loadTrace(traceName);
        ClassReader reader = new ClassReader(originalBC);
        ClassWriter writer = InstrumentUtils.newClassWriter();

        InstrumentUtils.accept(reader, new Instrumentor(null,
                    btrace.className, btrace.content,
                    btrace.onMethods, writer));

        transformedBC = writer.toByteArray();
        System.err.println("==== " + traceName);
    }

    private String diff() throws IOException {
        StringWriter sw = new StringWriter();
        TraceClassVisitor acv = new TraceClassVisitor(new PrintWriter(sw));
        new org.objectweb.asm.ClassReader(originalBC).accept(acv, ClassReader.SKIP_FRAMES);
        String origCode = sw.toString();

        sw = new StringWriter();
        acv = new TraceClassVisitor(new PrintWriter(sw));
        new org.objectweb.asm.ClassReader(transformedBC).accept(acv, ClassReader.SKIP_FRAMES);
        String transCode = sw.toString();
        return diff(transCode, origCode);
    }

    private String diff(String modified, String orig) throws IOException {
        StringBuilder sb = new StringBuilder();

        String[] modArr = modified.split("\\n");
        String[] orgArr = orig.split("\\n");

        // number of lines of each file
        int M = modArr.length;
        int N = orgArr.length;

        // opt[i][j] = length of LCS of x[i..M] and y[j..N]
        int[][] opt = new int[M+1][N+1];

        // compute length of LCS and all subproblems via dynamic programming
        for (int i = M-1; i >= 0; i--) {
            for (int j = N-1; j >= 0; j--) {
                if (modArr[i].equals(orgArr[j]))
                    opt[i][j] = opt[i+1][j+1] + 1;
                else
                    opt[i][j] = Math.max(opt[i+1][j], opt[i][j+1]);
            }
        }

        // recover LCS itself and print out non-matching lines to standard output
        int i = 0, j = 0;
        while(i < M && j < N) {
            if (modArr[i].equals(orgArr[j])) {
                i++;
                j++;
            }
            else if (opt[i+1][j] >= opt[i][j+1]) sb.append(modArr[i++].trim()).append('\n');
            else j++; //                                sb.append("[").append(j).append("] ").append("> " + orgArr[j++]).append('\n');
        }

        // dump out one remainder of one string if the other is exhausted
        while(i < M || j < N) {
            if      (i == M) j++; //sb.append("[").append(j).append("] ").append("> " + modArr[j++]).append('\n');
            else if (j == N) sb.append(orgArr[i++].trim()).append('\n');
        }
        return sb.toString();
    }

    private Trace loadTrace(String name) throws IOException {
        byte[] btrace = loadFile("traces/" + name + ".class");
        ClassWriter writer = InstrumentUtils.newClassWriter();
        Verifier verifier = new Verifier(new Preprocessor(writer));
        InstrumentUtils.accept(new ClassReader(btrace), verifier);
        return new Trace(writer.toByteArray(), verifier.getOnMethods(), verifier.getClassName());
    }

    private byte[] loadTargetClass(String name) throws IOException {
        return loadFile("resources/" + name + ".class");
    }

    private byte[] loadFile(String path) throws IOException {
        InputStream is = ClassLoader.getSystemResourceAsStream(path);
        try {
            return loadFile(is);
        } finally {
            is.close();
        }
    }

    private byte[] loadFile(InputStream is) throws IOException {
        byte[] result = new byte[0];

        byte[] buffer = new byte[1024];

        int read = -1;
        while((read = is.read(buffer)) > 0) {
            byte[] newresult = new byte[result.length + read];
            System.arraycopy(result, 0, newresult, 0, result.length);
            System.arraycopy(buffer, 0, newresult, result.length, read);
            result = newresult;
        }
        return result;
    }

}