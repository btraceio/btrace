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
package org.openjdk.btrace.extension;

/**
 * Pluggable registry to validate whether a given service type is declared by any available BTrace
 * extension.
 *
 * <p>How it is used: - The agent wires a resolver at startup based on its ExtensionLoader; see
 * agent initialization. This lets bytecode-time validation (e.g., in BTraceProbeNode) query
 * declared services without loading any user classes. - This check is intentionally lightweight and
 * name-based. It complements the runtime reflection validation in the agent
 * (Client#validateDeclaredServices), which runs in the actual target JVM and covers classloader
 * identity, JPMS access, and linkage/loadability.
 *
 * <p>When unset, validation defaults to permissive to avoid blocking in environments where the
 * agent has not configured the resolver (e.g., tooling or tests that do not initialize extensions).
 */
public final class ServiceDeclarationRegistry {
  private ServiceDeclarationRegistry() {}

  public interface Resolver {
    boolean isDeclaredService(String fqcn);
  }

  private static volatile Resolver resolver;

  public static void setResolver(Resolver r) {
    resolver = r;
  }

  public static boolean isDeclaredService(String fqcn) {
    Resolver r = resolver;
    if (r == null) {
      return true; // permissive by default if not configured
    }
    try {
      return r.isDeclaredService(fqcn);
    } catch (Throwable t) {
      return true; // do not block if resolver fails
    }
  }
}
