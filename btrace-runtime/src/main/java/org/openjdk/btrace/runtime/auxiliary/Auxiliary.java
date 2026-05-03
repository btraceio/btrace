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
package io.btrace.runtime.auxiliary;

import java.lang.invoke.MethodHandles;

public final class Auxiliary {
  private Auxiliary() {}

  /**
   * Returns a {@link MethodHandles.Lookup} anchored on this class.
   *
   * <p>Probes on JDK 15+ are installed via {@code MethodHandles.Lookup.defineHiddenClass} into
   * {@code Auxiliary}'s runtime package and require a lookup with full privilege access. Obtaining
   * the lookup here (rather than via {@code privateLookupIn} from a caller in a different module)
   * keeps the MODULE bit and avoids {@code IllegalAccessException} when the caller's class loader
   * is distinct from this class's — e.g. a masked-jar deployment where the agent sits on {@code
   * MaskedClassLoader} and {@code Auxiliary} sits on the bootstrap loader.
   *
   * <p>Public because the primary caller lives in the sibling package {@code
   * io.btrace.runtime}.
   */
  public static MethodHandles.Lookup lookup() {
    return MethodHandles.lookup();
  }
}
