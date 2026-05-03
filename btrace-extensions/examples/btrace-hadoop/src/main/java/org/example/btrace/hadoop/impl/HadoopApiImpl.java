/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.example.btrace.hadoop.impl;

import io.btrace.core.extensions.Extension;
import io.btrace.extension.util.ClassLoadingUtil;
import io.btrace.extension.util.MethodHandleCache;
import java.lang.invoke.MethodHandle;
import java.net.URI;
import org.example.btrace.hadoop.api.HadoopApi;

/** Example implementation demonstrating provided-style integration with Hadoop. */
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
                Class<?> fsCls =
                    ClassLoadingUtil.loadFromContext("org.apache.hadoop.fs.FileSystem", fs);
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
