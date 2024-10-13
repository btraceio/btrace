package org.openjdk.btrace.instr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.HandlerRepository;
import org.openjdk.btrace.core.SharedSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HandlerRepositoryImpl {
  private static final Logger log = LoggerFactory.getLogger(HandlerRepositoryImpl.class);

  private static final Map<String, BTraceProbe> probeMap = new ConcurrentHashMap<>();

  static {
    try {
      Class<?> indyClz = Class.forName("org.openjdk.btrace.runtime.Indy");
      HandlerRepository hook = HandlerRepositoryImpl::getProbeHandler;
      indyClz.getField("repository").set(null, hook);
    } catch (UnsupportedClassVersionError ignored) {
      // expected for pre Java 15 runtimes
    } catch (Throwable t) {
      log.warn("Unable to initialize BTrace Indy support", t);
    }
  }

  public static void registerProbe(BTraceProbe probe) {
    probeMap.put(probe.getClassName(true), probe);
  }

  public static void unregisterProbe(BTraceProbe probe) {
    probeMap.remove(probe.getClassName(true));
  }

  public static byte[] getProbeHandler(
      String callerName, String probeName, String handlerName, String handlerDesc) {
    DebugSupport debugSupport = new DebugSupport(SharedSettings.GLOBAL);
    BTraceProbe probe = probeMap.get(probeName);
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

    String handlerClassName = callerName.replace('.', '/');
    ClassVisitor visitor =
        new CopyingVisitor(handlerClassName, true, writer) {
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

    if (debugSupport.isDumpClasses()) {
      try {
        String handlerPath =
            debugSupport.getDumpClassDir() + "/" + handlerClassName.replace('/', '_') + ".class";
        log.debug("BTrace INDY handler dumped: {}", handlerPath);
        Files.write(Paths.get(handlerPath), data, StandardOpenOption.CREATE);
      } catch (Throwable e) {
        log.debug("Failed to dump BTrace INDY handler", e);
      }
    }

    return data;
  }
}
