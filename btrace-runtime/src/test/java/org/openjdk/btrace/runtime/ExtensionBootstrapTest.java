package org.openjdk.btrace.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.openjdk.btrace.core.extensions.ExtensionEntry;
import org.openjdk.btrace.core.extensions.ExtensionRepository;
import org.openjdk.btrace.extensions.TestExtension;

class ExtensionBootstrapTest {
  private static final Map<Class<?>, Object> TYPE_VALUES = new HashMap<>();
  private static final String EXTENSION_TYPE_NAME = TestExtension.class.getName().replace('.', '/');

  static {
    TYPE_VALUES.put(String.class, null);
    TYPE_VALUES.put(byte.class, (byte) 0);
    TYPE_VALUES.put(short.class, (short) 0);
    TYPE_VALUES.put(int.class, (int) 0);
    TYPE_VALUES.put(long.class, (long) 0);
    TYPE_VALUES.put(float.class, 0f);
    TYPE_VALUES.put(double.class, 0d);
    TYPE_VALUES.put(boolean.class, false);
  }

  @BeforeAll
  static void setupAll() throws Throwable {
    ExtensionRepository repo = Mockito.mock(ExtensionRepository.class);
    ExtensionEntry entry = Mockito.mock(ExtensionEntry.class);
    Mockito.when(entry.getClassLoader(Mockito.any(ClassLoader.class)))
        .thenReturn(ExtensionBootstrapTest.class.getClassLoader());
    Mockito.when(repo.getExtensionById(Mockito.anyString())).thenReturn(entry);
    ExtensionBootstrap.repository = repo;

    TestExtension.clearInvocationMap();
  }

  @ParameterizedTest
  @ValueSource(strings = {"staticVoidMethod", "voidMethod"})
  void bootstrapInvokeExistingMethod(String methodName) throws Throwable {
    Method targetMethod = null;
    for (Method m : TestExtension.class.getMethods()) {
      if (m.getName().equals(methodName)) {
        targetMethod = m;
        break;
      }
    }
    assertNotNull(targetMethod);
    MethodType type = MethodHandles.lookup().unreflect(targetMethod).type();
    String descriptor = Type.getMethodDescriptor(targetMethod);
    int opcode =
        Modifier.isStatic(targetMethod.getModifiers())
            ? Opcodes.INVOKESTATIC
            : Opcodes.INVOKEVIRTUAL;
    CallSite site =
        ExtensionBootstrap.bootstrapInvoke(
            MethodHandles.lookup(),
            methodName,
            type,
            "test",
            EXTENSION_TYPE_NAME,
            descriptor,
            opcode);
    assertNotNull(site);

    MethodHandle handle = site.dynamicInvoker();
    if (!Modifier.isStatic(targetMethod.getModifiers())) {
      handle = handle.bindTo(new TestExtension());
    }
    handle.invokeExact("Hello", 1, new byte[] {0, 1});

    Assertions.assertEquals(1, TestExtension.getInvocations(methodName));
  }

  @Test
  void bootstrapInvokeMissingMethod() throws Throwable {
    int[] opcodes = new int[] {Opcodes.INVOKESTATIC, Opcodes.INVOKEVIRTUAL};

    for (Map.Entry<Class<?>, Object> entry : TYPE_VALUES.entrySet()) {
      for (int opcode : opcodes) {
        assertMissingMethod(opcode, entry.getKey(), entry.getValue());
      }
    }
  }

  void assertMissingMethod(int opcode, Class<?> retType, Object retValue) throws Throwable {
    String descriptor =
        Type.getMethodDescriptor(
            Type.getType(retType),
            Type.getType(String.class),
            Type.INT_TYPE,
            Type.getType(byte[].class));
    MethodType type =
        opcode == Opcodes.INVOKESTATIC
            ? MethodType.methodType(retType, String.class, int.class, byte[].class)
            : MethodType.methodType(retType, Object.class, String.class, int.class, byte[].class);
    CallSite site =
        ExtensionBootstrap.bootstrapInvoke(
            MethodHandles.lookup(),
            "invalid_name",
            type,
            "test",
            EXTENSION_TYPE_NAME,
            descriptor,
            opcode);
    assertNotNull(site);

    // sanity check that the generated noop handlers are ok to invoke
    MethodHandle handle = site.dynamicInvoker();
    if (opcode == Opcodes.INVOKEVIRTUAL) {
      handle = handle.bindTo(new TestExtension());
    }
    assertEquals(retValue, handle.invokeWithArguments("Hello", 1, new byte[] {0, 1}));
  }

  @Test
  void bootstrapInvokeMissingField() throws Throwable {
    int[] opcodes =
        new int[] {Opcodes.GETFIELD, Opcodes.GETSTATIC, Opcodes.PUTFIELD, Opcodes.PUTSTATIC};

    for (Map.Entry<Class<?>, Object> entry : TYPE_VALUES.entrySet()) {
      for (int opcode : opcodes) {
        assertMissingField(opcode, entry.getKey(), entry.getValue());
      }
    }
  }

  private static void assertMissingField(int opcode, Class<?> fldType, Object value)
      throws Throwable {
    MethodType type = null;
    String descriptor = null;
    switch (opcode) {
      case Opcodes.GETFIELD:
        {
          type = MethodType.methodType(fldType, Object.class);
          descriptor = Type.getMethodDescriptor(Type.getType(fldType));
          break;
        }
      case Opcodes.PUTFIELD:
        {
          type = MethodType.methodType(void.class, Object.class, fldType);
          descriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(fldType));
          break;
        }
      case Opcodes.GETSTATIC:
        {
          type = MethodType.methodType(fldType);
          descriptor = Type.getMethodDescriptor(Type.getType(fldType));
          break;
        }
      case Opcodes.PUTSTATIC:
        {
          type = MethodType.methodType(void.class, fldType);
          descriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(fldType));
          break;
        }
    }

    assertNotNull(type);
    CallSite site =
        ExtensionBootstrap.bootstrapInvoke(
            MethodHandles.lookup(),
            "invalid_field",
            type,
            "test",
            EXTENSION_TYPE_NAME,
            descriptor,
            opcode);
    assertNotNull(site);

    // sanity check that the generated noop handlers are ok to invoke
    MethodHandle handle = site.dynamicInvoker();
    if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) {
      handle = handle.bindTo(new TestExtension());
    }
    if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
      assertEquals(value, handle.invoke());
    }
    if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
      handle.invokeWithArguments(value);
    }
  }
}
