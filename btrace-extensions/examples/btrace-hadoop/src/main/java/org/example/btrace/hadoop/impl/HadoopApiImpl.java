package org.example.btrace.hadoop.impl;

import org.example.btrace.hadoop.api.HadoopApi;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.extension.util.ClassLoadingUtil;
import org.openjdk.btrace.extension.util.MethodHandleCache;

import java.lang.invoke.MethodHandle;
import java.net.URI;

/**
 * Example implementation demonstrating provided-style integration with Hadoop.
 */
public final class HadoopApiImpl extends Extension implements HadoopApi {
  private final MethodHandleCache mh = new MethodHandleCache();

  @Override
  public void onOpen(Object fileSystem, Object path) {
    handleFsOp(fileSystem, path, "open");
  }

  @Override
  public void onCreate(Object fileSystem, Object path) {
    handleFsOp(fileSystem, path, "create");
  }

  private void handleFsOp(Object fs, Object path, String op) {
    if (path == null) return;
    ClassLoadingUtil.withDefiningLoader(
        path,
        () -> {
          try {
            // Resolve Hadoop Path and FileSystem types reflectively
            Class<?> pathCls = ClassLoadingUtil.loadFromContext("org.apache.hadoop.fs.Path", path);
            // Use toString on Path via MH
            MethodHandle toStringMH = mh.findVirtual(pathCls, "toString", String.class);
            String p = (String) toStringMH.invoke(path);

            if (fs != null) {
              try {
                Class<?> fsCls = ClassLoadingUtil.loadFromContext("org.apache.hadoop.fs.FileSystem", fs);
                // getUri() -> URI
                MethodHandle getUri = mh.findVirtual(fsCls, "getUri", URI.class);
                Object uri = getUri.invoke(fs);
                // Example: println(op+" "+p+" on "+uri)
              } catch (Throwable ignored) {
                // Optional if FileSystem not present
              }
            }
          } catch (Throwable ignored) {
          }
          return null;
        });
  }
}
