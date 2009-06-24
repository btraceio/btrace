/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.util;

import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;


/**
 *
 * @author Jaroslav Bachorik <jaroslav.bachorik@sun.com>
 */
public class TimeStampHelper {
    final public static String TIME_STAMP_NAME = "$btrace$time$stamp";

    public static void generateTimeStampGetter(ClassVisitor cv) {
        MethodVisitor timestamp = cv.visitMethod(ACC_STATIC + ACC_PRIVATE + ACC_FINAL, TIME_STAMP_NAME, "()J", null, new String[0]);
        timestamp.visitCode();
        timestamp.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J");
        timestamp.visitInsn(LRETURN);
        timestamp.visitMaxs(1, 0);
        timestamp.visitEnd();
    }

    public static void generateTimeStampAccess(MethodVisitor mv, String className) {
        mv.visitMethodInsn(INVOKESTATIC, className.replace(".", "/"), TIME_STAMP_NAME, "()J");
    }
}
