package org.openjdk.btrace.instr;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HandlerRepository {

    private static final Map<String, BTraceProbe> probeMap = new ConcurrentHashMap<>();

    public static void registerProbe(BTraceProbe probe) {
        probeMap.put(probe.getClassName(true), probe);
    }

    public static void unregisterProbe(BTraceProbe probe) {
        probeMap.remove(probe.getClassName(true));
    }

    public static byte[] getProbeHandler(String callerName, String probeName, final String handlerName, final String handlerDesc) {
        BTraceProbe probe = probeMap.get(probeName);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        String handlerClassName = callerName.replace('.', '/');
        ClassVisitor visitor = new CopyingVisitor(handlerClassName, true, writer) {
            @Override
            protected String getMethodName(String name) {
                int idx = name.lastIndexOf("$");
                if (idx > -1) {
                    return name.substring(idx + 1);
                }
                return name;
            }
        };

        probe.copyHandlers(visitor);
        byte[] data = writer.toByteArray();
        System.out.println("===> data size: " + data.length);

        try {
            System.out.println("===> dump: /tmp/" + handlerClassName.replace('/', '_') + ".class");
            Files.write(Paths.get("/tmp/" + handlerClassName.replace('/', '_') + ".class"), data, StandardOpenOption.CREATE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return data;
    }
}
