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
package io.btrace.extension;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal failure registry for extensions discovered/loaded via manifest-based loader.
 *
 * <p>Manifest and extension properties are the single source of truth. Extension discovery,
 * permission checks, and instantiation are handled by the manifest-based bridge/loader. This class
 * only exposes a stable map of failure reasons for diagnostics and UI.
 */
public final class ExtensionRegistry {
  private ExtensionRegistry() {}

  private static final ConcurrentHashMap<String, String> failedExtensions =
      new ConcurrentHashMap<>();

  public static Map<String, String> getFailedExtensions() {
    return Collections.unmodifiableMap(failedExtensions);
  }

  public static void registerFailedExtension(String idOrName, String reason) {
    failedExtensions.put(idOrName, reason);
  }
}
