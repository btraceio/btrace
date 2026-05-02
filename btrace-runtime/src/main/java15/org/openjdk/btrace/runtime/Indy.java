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


package org.openjdk.btrace.runtime;

import org.openjdk.btrace.core.HandlerRepository;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Invoke-dynamic linking support class */
public final class Indy {
  // Indy must reside in bootstrap but the HandlerRepository implementation is in agent area.
  // This field will be dynamically set from the actual HandlerRepository implementation class
  // static initializer.
  public static volatile HandlerRepository repository = null;

  public static CallSite bootstrap(
      MethodHandles.Lookup caller, String name, MethodType type, String probeClassName)
      throws Exception {
    assert repository != null;
    MethodHandle mh;
    try {
      byte[] classData =
              repository.getProbeHandler(
                      caller.lookupClass().getName(), probeClassName, name, type.toMethodDescriptorString());

      caller =
              caller.defineHiddenClass(classData, false, MethodHandles.Lookup.ClassOption.NESTMATE);
      mh = caller.findStatic(caller.lookupClass(), name.substring(name.lastIndexOf("$") + 1), type);
    } catch (Throwable t) {
      // if unable to properly link just ignore the instrumentation
      MethodHandle noopHandle =
              MethodHandles.lookup().findStatic(Indy.class, "noop", MethodType.methodType(void.class));
      mh = MethodHandles.dropArguments(noopHandle, 0, type.parameterArray());
    }

    return new ConstantCallSite(mh);
  }

  public static void noop() {}
}
