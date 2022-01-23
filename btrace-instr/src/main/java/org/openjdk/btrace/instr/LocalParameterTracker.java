package org.openjdk.btrace.instr;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.objectweb.asm.Type;

final class LocalParameterTracker {
  private static final class Key {
    final ClassLoader cl;
    final String className;
    final String methodName;
    final String paramName;

    public Key(ClassLoader cl, String className, String methodName, String paramName) {
      this.cl = cl;
      this.className = className;
      this.methodName = methodName;
      this.paramName = paramName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Key key = (Key) o;
      return Objects.equals(cl, key.cl)
          && className.equals(key.className)
          && methodName.equals(key.methodName)
          && paramName.equals(key.paramName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(cl, className, methodName, paramName);
    }
  }

  private final ClassLoader cl;
  private final Map<Key, Integer> paramMap = new HashMap<>();

  LocalParameterTracker(ClassLoader cl) {
    this.cl = cl;
  }

  public int getParameter(String clzName, String methodName, String paramName, Assembler asm) {
    int idx = -1;
    Key key = new Key(cl, clzName, methodName, paramName);
    if (!paramMap.containsKey(key)) {
      idx =
          asm.newInstance(Type.getType(Constants.VALUE_HOLDER_DESC))
              .dup()
              .invokeSpecial(Constants.VALUE_HOLDER_INTERNAL, "<init>", "()V")
              .storeAsNew();
      paramMap.put(key, idx);
    } else {
      idx = paramMap.get(key);
    }
    return idx;
  }
}
