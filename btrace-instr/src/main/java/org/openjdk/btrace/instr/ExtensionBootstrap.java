package org.openjdk.btrace.instr;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.openjdk.btrace.core.extensions.ExtensionEntry;
import org.openjdk.btrace.core.extensions.ExtensionRepository;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class ExtensionBootstrap {
    public static CallSite bootstrapInvoke(MethodHandles.Lookup caller, String name, MethodType type, String extName, String extClassName, String descriptor, int opcode) throws Exception {
        try {
            ExtensionEntry extension = ExtensionRepository.getInstance().getExtensionById(extName);
            if (extension != null) {
                ClassLoader extClassLoader = extension.getClassLoader(caller.lookupClass().getClassLoader());
                Class<?> extClass = extClassLoader.loadClass(extClassName.replace('/', '.'));
                if (extClass != null) {
                    MethodHandle callHandle = null;
                    MethodType origType = type;
                    Type retType = Type.getReturnType(descriptor);
                    Type[] args = Type.getArgumentTypes(descriptor);

                    for (int i = 0; i < type.parameterCount(); i++) {
                        if (!type.parameterType(i).isPrimitive()) {
                            type = type.changeParameterType(i, extClassLoader.loadClass(args[i].getClassName()));
                        }
                    }
                    if (retType.getSort() == Type.OBJECT) {
                        Class<?> clz = extClassLoader.loadClass(retType.getClassName());
                        type = type.changeReturnType(clz);
                    }
                    if (opcode == Opcodes.INVOKESTATIC) {
                        callHandle = MethodHandles.lookup().findStatic(extClass, name, type).asType(origType);
                    } else {
                        callHandle = MethodHandles.lookup().findVirtual(extClass, name, type.dropParameterTypes(0, 1));
                        callHandle = callHandle.asType(callHandle.type().changeParameterType(0, Object.class));
                    }
                    if (callHandle != null) {
                        return new ConstantCallSite(callHandle);
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // fallback to noop binding
        MethodHandle noopHandle =
                MethodHandles.lookup().findStatic(ExtensionBootstrap.class, "noop", MethodType.methodType(void.class));
        return new ConstantCallSite(MethodHandles.dropArguments(noopHandle, 0, type.parameterArray()));
    }

    public static void noop() {}
}
