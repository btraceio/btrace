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
package io.btrace.extcli;

import io.btrace.registry.ExtensionRegistryClient;
import io.btrace.registry.RegistrySource;
import java.nio.file.Path;

final class RegistrySupport {
  private static final String REGISTRY_URL_PROPERTY = "btrace.extensions.registry";
  private static final String DEFAULT_REGISTRY_URL =
      "https://btraceio.github.io/btrace-extensions/registry/extensions.json";

  private RegistrySupport() {}

  static ExtensionRegistryClient client() {
    String url = System.getProperty(REGISTRY_URL_PROPERTY, DEFAULT_REGISTRY_URL);
    Path cache = cacheFile();
    return new ExtensionRegistryClient(RegistrySource.uri(url), cache);
  }

  private static Path cacheFile() {
    return Path.of(System.getProperty("user.home"), ".btrace", "cache", "extensions-registry.json");
  }
}
