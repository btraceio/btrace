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
package io.btrace.compiler;

import java.io.File;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassWriter;

/**
 * @author Jaroslav Bachorik
 */
class CompilerClassWriter extends ClassWriter {
  private final ClassLoader cl;

  public CompilerClassWriter(String classPath, PrintWriter perr) {
    this(classPath, perr, null);
  }

  public CompilerClassWriter(String classPath, PrintWriter perr, ClassLoader maskedClassLoader) {
    super(ClassWriter.COMPUTE_FRAMES);
    if (maskedClassLoader != null) {
      // Use the provided MaskedClassLoader which can read .classdata files
      this.cl = maskedClassLoader;
    } else {
      // Fall back to URLClassLoader for standard .class files
      List<URL> urls = new ArrayList<>();
      if (classPath != null) {
        for (String e : classPath.split(File.pathSeparator)) {
          File f = new File(e);
          try {
            urls.add(f.toURI().toURL());
          } catch (MalformedURLException ex) {
            perr.printf("%s is not a valid classpath entry\n", e);
          }
        }
      }
      this.cl = new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
    }
  }

  @Override
  protected String getCommonSuperClass(String type1, String type2) {
    Class<?> c, d;
    try {
      c = cl.loadClass(type1.replace('/', '.'));
      d = cl.loadClass(type2.replace('/', '.'));
    } catch (Exception e) {
      throw new RuntimeException(e.toString());
    }
    if (c.isAssignableFrom(d)) {
      return type1;
    }
    if (d.isAssignableFrom(c)) {
      return type2;
    }
    if (c.isInterface() || d.isInterface()) {
      return "java/lang/Object";
    } else {
      do {
        c = c.getSuperclass();
      } while (!c.isAssignableFrom(d));
      return c.getName().replace('.', '/');
    }
  }
}
