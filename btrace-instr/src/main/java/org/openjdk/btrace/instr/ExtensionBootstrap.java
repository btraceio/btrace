package org.openjdk.btrace.instr;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Arrays;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.openjdk.btrace.core.extensions.ExtensionEntry;
import org.openjdk.btrace.core.extensions.ExtensionRepository;

/** The bootstrap handler for the extension dynamic invocations */
public final class ExtensionBootstrap {
  public static CallSite bootstrapInvoke(
      MethodHandles.Lookup caller,
      String name,
      MethodType type,
      String extName,
      String extClassName,
      String descriptor,
      int opcode)
      throws Exception {
    Class<?> rType = type.returnType();
    try {
      // Try resolving the extension containing the target class
      ExtensionEntry extension = ExtensionRepository.getInstance().getExtensionById(extName);
      if (extension != null) {
        ClassLoader extClassLoader =
            extension.getClassLoader(caller.lookupClass().getClassLoader());
        // Load the actual extension class using the extension classloader
        Class<?> extClass = extClassLoader.loadClass(extClassName.replace('/', '.'));
        if (extClass != null) {
          // The provided MethodType has erased argument and return types not to cause
          // class loading problems in the calling code when the extension is not enabled/available.
          // The actual types are transferred via 'descriptor' string.
          MethodHandle callHandle = null;
          MethodType origType = type;
          Type retType = Type.getReturnType(descriptor);
          Type[] args = Type.getArgumentTypes(descriptor);

          // Restore the erased parameter types
          for (int i = 0; i < type.parameterCount(); i++) {
            if (!type.parameterType(i).isPrimitive()) {
              type = type.changeParameterType(i, getTypeClass(args[i], extClassLoader));
            }
          }
          // Restore the erased return type
          if (retType.getSort() == Type.OBJECT) {
            Class<?> clz = getTypeClass(retType, extClassLoader);
            type = type.changeReturnType(clz);
          }

          // Find the actual method handle or field accessor
          switch (opcode) {
            case Opcodes.INVOKESTATIC:
              {
                callHandle =
                    MethodHandles.lookup().findStatic(extClass, name, type).asType(origType);
                break;
              }
            case Opcodes.INVOKEVIRTUAL:
              {
                callHandle =
                    MethodHandles.lookup()
                        .findVirtual(extClass, name, type.dropParameterTypes(0, 1));
                callHandle =
                    callHandle.asType(callHandle.type().changeParameterType(0, Object.class));
                break;
              }
            case Opcodes.GETFIELD:
              {
                Class<?> fldType = getTypeClass(retType, extClassLoader);
                callHandle = MethodHandles.lookup().findGetter(extClass, name, fldType);
                break;
              }
            case Opcodes.GETSTATIC:
              {
                Class<?> fldType = getTypeClass(retType, extClassLoader);
                callHandle = MethodHandles.lookup().findStaticGetter(extClass, name, fldType);
                break;
              }
            case Opcodes.PUTFIELD:
              {
                Class<?> fldType = getTypeClass(args[0], extClassLoader);
                callHandle = MethodHandles.lookup().findSetter(extClass, name, fldType);
                break;
              }
            case Opcodes.PUTSTATIC:
              {
                Class<?> fldType = getTypeClass(args[0], extClassLoader);
                callHandle = MethodHandles.lookup().findStaticSetter(extClass, name, fldType);
                break;
              }
          }
          if (callHandle != null) {
            return new ConstantCallSite(callHandle);
          }
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw t;
    }

    // No handle was resolved - use the no-op fallback instead
    return new ConstantCallSite(getNoopHandle(type, rType));
  }

  private static MethodHandle getNoopHandle(MethodType type, Class<?> rType)
      throws NoSuchMethodException, IllegalAccessException {
    MethodHandle noopHandle =
        MethodHandles.lookup()
            .findStatic(ExtensionBootstrap.class, "noop", MethodType.methodType(Void.class));
    // Discard any incoming arguments
    noopHandle = MethodHandles.dropArguments(noopHandle, 0, type.parameterArray());
    // Do return type transformation - the shared noop handler returns Void and 'filterReturnValue'
    // is used to transform it to the default value for each primitive and Object.
    if (rType.equals(void.class)) {
      noopHandle =
          MethodHandles.filterReturnValue(
              noopHandle,
              MethodHandles.lookup()
                  .findStatic(
                      ExtensionBootstrap.class,
                      "defaultVoid",
                      MethodType.methodType(void.class, Void.class)));
    } else if (rType.equals(byte.class)) {
      noopHandle =
          MethodHandles.filterReturnValue(
              noopHandle,
              MethodHandles.lookup()
                  .findStatic(
                      ExtensionBootstrap.class,
                      "defaultByte",
                      MethodType.methodType(byte.class, Void.class)));
    } else if (rType.equals(short.class)) {
      noopHandle =
          MethodHandles.filterReturnValue(
              noopHandle,
              MethodHandles.lookup()
                  .findStatic(
                      ExtensionBootstrap.class,
                      "defaultShort",
                      MethodType.methodType(short.class, Void.class)));
    } else if (rType.equals(int.class)) {
      noopHandle =
          MethodHandles.filterReturnValue(
              noopHandle,
              MethodHandles.lookup()
                  .findStatic(
                      ExtensionBootstrap.class,
                      "defaultInt",
                      MethodType.methodType(int.class, Void.class)));
    } else if (rType.equals(long.class)) {
      noopHandle =
          MethodHandles.filterReturnValue(
              noopHandle,
              MethodHandles.lookup()
                  .findStatic(
                      ExtensionBootstrap.class,
                      "defaultLong",
                      MethodType.methodType(long.class, Void.class)));
    } else if (rType.equals(float.class)) {
      noopHandle =
          MethodHandles.filterReturnValue(
              noopHandle,
              MethodHandles.lookup()
                  .findStatic(
                      ExtensionBootstrap.class,
                      "defaultFloat",
                      MethodType.methodType(float.class, Void.class)));
    } else if (rType.equals(double.class)) {
      noopHandle =
          MethodHandles.filterReturnValue(
              noopHandle,
              MethodHandles.lookup()
                  .findStatic(
                      ExtensionBootstrap.class,
                      "defaultDouble",
                      MethodType.methodType(double.class, Void.class)));
    } else if (rType.equals(boolean.class)) {
      noopHandle =
          MethodHandles.filterReturnValue(
              noopHandle,
              MethodHandles.lookup()
                  .findStatic(
                      ExtensionBootstrap.class,
                      "defaultBoolean",
                      MethodType.methodType(boolean.class, Void.class)));
    } else if (rType.equals(Object.class)) {
      noopHandle =
          MethodHandles.filterReturnValue(
              noopHandle,
              MethodHandles.lookup()
                  .findStatic(
                      ExtensionBootstrap.class,
                      "defaultObject",
                      MethodType.methodType(Object.class, Void.class)));
    } else {
      throw new IllegalStateException("Unsupported return type: " + rType);
    }
    return noopHandle;
  }

  public static Void noop() {
    return null;
  }

  private static void defaultVoid(Void v) {}

  private static byte defaultByte(Void v) {
    return (byte) 0;
  }

  private static short defaultShort(Void v) {
    return (short) 0;
  }

  private static int defaultInt(Void v) {
    return 0;
  }

  private static long defaultLong(Void v) {
    return 0L;
  }

  private static float defaultFloat(Void v) {
    return 0f;
  }

  private static double defaultDouble(Void v) {
    return 0d;
  }

  private static boolean defaultBoolean(Void v) {
    return false;
  }

  private static Object defaultObject(Void v) {
    return null;
  }

  private static Class<?> getTypeClass(Type type, ClassLoader classLoader) throws Exception {
    Class<?> clz = null;
    switch (type.getSort()) {
      case Type.BYTE:
        {
          clz = byte.class;
          break;
        }
      case Type.CHAR:
        {
          clz = char.class;
          break;
        }
      case Type.SHORT:
        {
          clz = short.class;
          break;
        }
      case Type.INT:
        {
          clz = int.class;
          break;
        }
      case Type.FLOAT:
        {
          clz = float.class;
          break;
        }
      case Type.LONG:
        {
          clz = long.class;
          break;
        }
      case Type.DOUBLE:
        {
          clz = double.class;
          break;
        }
      case Type.BOOLEAN:
        {
          clz = boolean.class;
          break;
        }
      case Type.OBJECT:
        {
          clz = classLoader.loadClass(type.getClassName());
          break;
        }
    }
    if (type.getSort() == Type.ARRAY && type.getDimensions() > 0) {
      int[] dims = new int[type.getDimensions()];
      Arrays.fill(dims, 0);
      clz = Array.newInstance(clz, dims).getClass();
    }
    return clz;
  }
}
