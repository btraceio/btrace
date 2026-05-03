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
package io.btrace.instr;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import io.btrace.core.BTraceRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arbitrary class info type allowing access to supertype information also for not-already-loaded
 * classes.
 *
 * @author Jaroslav Bachorik
 */
public final class ClassInfo {
  private static final Logger log = LoggerFactory.getLogger(ClassInfo.class);

  private static final ClassLoader SYS_CL = ClassLoader.getSystemClassLoader();
  private static volatile MethodHandle BSTRP_CHECK_MTD;
  private final String cLoaderId;
  private final ClassName classId;

  // @ThreadSafe
  private final Collection<ClassInfo> supertypes = new ArrayList<>();
  private final ClassCache cache;
  private boolean isInterface = false;
  private boolean isAvailable = false;

  ClassInfo(ClassCache cache, Class<?> clz) {
    this.cache = cache;
    ClassLoader cl = clz.getClassLoader();
    cLoaderId = (cl != null ? cl.toString() : "<null>");
    classId = new ClassName(clz.getName());
    Class<?> supr = clz.getSuperclass();
    if (supr != null) {
      supertypes.add(cache.get(supr));
    }
    for (Class<?> itfc : clz.getInterfaces()) {
      if (itfc != null) {
        supertypes.add(cache.get(itfc));
      }
    }
    isInterface = clz.isInterface();
    isAvailable = true;
  }

  ClassInfo(ClassCache cache, ClassLoader cl, ClassName cName) {
    this.cache = cache;
    cLoaderId = (cl != null ? cl.toString() : "<null>");
    classId = cName;
    loadExternalClass(cl, cName);
  }

  private static ClassLoader inferClassLoader(ClassLoader initiating, ClassName className) {
    if (className == null) {
      return initiating;
    }

    String jClassName = className.getJavaClassName().toString();
    if (initiating == null || isBootstrap(jClassName)) {
      return null;
    } else {
      String rsrcName = className.getResourcePath();
      ClassLoader cl = initiating;
      ClassLoader prev = initiating;
      while (cl != null) {
        try {
          if (cl.getResource(rsrcName) == null) {
            return prev;
          }
        } catch (Throwable t) {
          // some containers can impose additional restrictions on loading resources and error on
          // unexpected state
          log.warn("Failed to get resource {}", rsrcName, t);
        }
        prev = cl;
        cl = cl.getParent();
      }
      return initiating;
    }
  }

  // package private only for testing purposes
  static boolean isBootstrap(String className) {
    return BTraceRuntime.isBootstrapClass(className);
  }

  /**
   * Retrieves supertypes (including interfaces)
   *
   * @param onlyDirect only immediate supertype and implemented interfaces
   * @return supertypes (including interfaces)
   */
  public Collection<ClassInfo> getSupertypes(boolean onlyDirect) {
    if (onlyDirect) {
      return supertypes;
    }
    Set<ClassInfo> supers = new LinkedHashSet<>(supertypes);
    for (ClassInfo ci : supertypes) {
      supers.addAll(ci.getSupertypes(onlyDirect));
    }
    return supers;
  }

  /**
   * Associated class loader string representation as returned by {@code cl.toString()} or {@code
   * "<null>"}
   *
   * @return associated class loader id
   */
  public String getLoaderId() {
    return cLoaderId;
  }

  /**
   * Class ID = internal class name
   *
   * @return internal class name
   */
  public String getClassName() {
    return classId.getInternalClassName().toString();
  }

  public String getJavaClassName() {
    return classId.getJavaClassName().toString();
  }

  public boolean isInterface() {
    return isInterface;
  }

  public boolean isAvailable() {
    return isAvailable;
  }

  // not thread safe - must be called only from the constructor
  private void loadExternalClass(ClassLoader cl, ClassName className) {
    String resourcePath = className.getResourcePath();

    try {
      InputStream typeIs =
          cl == null
              ? SYS_CL.getResourceAsStream(resourcePath)
              : cl.getResourceAsStream(resourcePath);
      if (typeIs != null) {
        try {
          BTraceClassReader cr = new BTraceClassReader(cl, typeIs);

          isInterface = cr.isInterface();
          String[] info = cr.readClassSupers();
          String superName = info[0];
          if (superName != null) {
            ClassName superClassName = new ClassName(superName);
            supertypes.add(cache.get(inferClassLoader(cl, superClassName), superClassName));
          }
          if (info.length > 1) {
            for (int i = 1; i < info.length; i++) {
              String ifc = info[i];
              if (ifc != null) {
                ClassName ifcClassName = new ClassName(ifc);
                supertypes.add(cache.get(inferClassLoader(cl, ifcClassName), ifcClassName));
              }
            }
          }
          isAvailable = true;
        } catch (IllegalArgumentException | IOException e) {
          log.warn("Unable to load class: {}", className, e);
        }
      }
    } catch (Throwable t) {
      // some containers can impose additional restrictions on classloaders throwing exceptions when
      // not in expected state
      log.warn("Failed to load class {}", className, t);
    }
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 37 * hash + Objects.hashCode(cLoaderId);
    hash = 37 * hash + Objects.hashCode(classId);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ClassInfo other = (ClassInfo) obj;
    if (!Objects.equals(cLoaderId, other.cLoaderId)) {
      return false;
    }
    return Objects.equals(classId, other.classId);
  }

  @Override
  public String toString() {
    return "ClassInfo{"
        + "cLoaderId="
        + cLoaderId
        + ", classId="
        + classId
        + ", supertypes="
        + supertypes
        + '}';
  }

  private abstract static class BaseClassName implements CharSequence {
    protected final CharSequence wrapped;
    private String str = null;

    protected BaseClassName(CharSequence wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public int length() {
      return wrapped.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      if (str == null) {
        char[] val = new char[wrapped.length()];
        for (int i = 0; i < wrapped.length(); i++) {
          val[i] = charAt(i);
        }
        str = new String(val);
      }
      return str;
    }
  }

  private static final class JavaClassName extends BaseClassName {
    public JavaClassName(CharSequence wrapped) {
      super(wrapped);
    }

    @Override
    public char charAt(int index) {
      char c = wrapped.charAt(index);
      return (c == '/' ? '.' : c);
    }
  }

  private static final class InternalClassName extends BaseClassName {
    public InternalClassName(CharSequence wrapped) {
      super(wrapped);
    }

    @Override
    public char charAt(int index) {
      char c = wrapped.charAt(index);
      return (c == '.' ? '/' : c);
    }
  }

  static final class ClassName {
    private final CharSequence cName;
    private final JavaClassName jcName;
    private final InternalClassName icName;
    private String rsrcName = null;

    public ClassName(CharSequence cName) {
      this.cName = cName;
      jcName = new JavaClassName(cName);
      icName = new InternalClassName(cName);
    }

    public CharSequence getJavaClassName() {
      return jcName;
    }

    public CharSequence getInternalClassName() {
      return icName;
    }

    public String getResourcePath() {
      if (rsrcName == null) {
        rsrcName = icName + ".class";
      }
      return rsrcName;
    }

    @Override
    public String toString() {
      return String.valueOf(cName);
    }

    @Override
    public int hashCode() {
      int h = 7;
      int len = cName.length();
      for (int i = 0; i < len; i++) {
        char c = cName.charAt(i);
        h = 31 * h + (c == '.' ? '/' : c);
      }

      return h;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      ClassName other = (ClassName) obj;
      if (cName.length() != other.cName.length()) {
        return false;
      }
      for (int i = 0; i < cName.length(); i++) {
        char c1 = cName.charAt(i);
        char c2 = other.cName.charAt(i);
        switch (c1) {
          case '.':
          case '/':
            {
              if (c2 != '.' && c2 != '/') {
                return false;
              }
              break;
            }
          default:
            {
              if (c1 != c2) {
                return false;
              }
            }
        }
      }
      return true;
    }
  }
}
