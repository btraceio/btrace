/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.btrace.compiler;

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
  private final URLClassLoader cl;

  public CompilerClassWriter(String classPath, PrintWriter perr) {
    super(ClassWriter.COMPUTE_FRAMES);
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
    cl = new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
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
