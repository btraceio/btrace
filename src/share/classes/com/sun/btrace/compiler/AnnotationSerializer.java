package com.sun.btrace.compiler;

import com.sun.btrace.org.objectweb.asm.tree.AnnotationNode;

public class AnnotationSerializer {
    public static void serialize(AnnotationNode an, StringBuilder sb) {
        sb.append("{type:").append(an.desc).append(',');
    }
}
