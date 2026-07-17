/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.BTraceRuntime;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class BTraceRuntimeAccessImplTest {
  @Test
  void escapeRestoresInstalledRuntimeAfterHandlerFailure() {
    BTraceRuntimeAccessImpl.RTWrapper wrapper = new BTraceRuntimeAccessImpl.RTWrapper();
    BTraceRuntime.Impl original = runtimeProxy();
    BTraceRuntime.Impl other = runtimeProxy();
    assertTrue(wrapper.set(original));

    assertNull(
        wrapper.escape(
            () -> {
              throw new IllegalStateException("handler failure");
            }));

    // set refuses to replace an installed runtime. This proves escape restored the exact prior
    // value rather than leaving the wrapper null after swallowing the handler exception.
    assertFalse(wrapper.set(other));
    assertTrue(wrapper.set(null));
    assertTrue(wrapper.set(other));
  }

  @Test
  void escapeRestoresNullRuntimeAfterHandlerFailure() {
    BTraceRuntimeAccessImpl.RTWrapper wrapper = new BTraceRuntimeAccessImpl.RTWrapper();

    assertNull(
        wrapper.escape(
            () -> {
              throw new IllegalStateException("handler failure");
            }));

    // A null previous value must be restored, so installing the first runtime remains allowed.
    assertTrue(wrapper.set(runtimeProxy()));
  }

  private static BTraceRuntime.Impl runtimeProxy() {
    return (BTraceRuntime.Impl)
        Proxy.newProxyInstance(
            BTraceRuntimeAccessImplTest.class.getClassLoader(),
            new Class<?>[] {BTraceRuntime.Impl.class},
            (proxy, method, args) -> {
              Class<?> type = method.getReturnType();
              if (type == Boolean.TYPE) return Boolean.FALSE;
              if (type == Integer.TYPE) return Integer.valueOf(0);
              if (type == Long.TYPE) return Long.valueOf(0L);
              return null;
            });
  }
}
