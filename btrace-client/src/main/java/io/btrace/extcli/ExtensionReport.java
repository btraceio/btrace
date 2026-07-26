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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ExtensionReport {
  final boolean ok;
  final String message;
  final String id;
  final String version;
  final boolean privileged;
  final Set<String> services;
  final List<String> requiredPermNames;

  private ExtensionReport(
      boolean ok,
      String message,
      String id,
      String version,
      boolean privileged,
      Set<String> services,
      List<String> requiredPermNames) {
    this.ok = ok;
    this.message = message;
    this.id = id;
    this.version = version;
    this.privileged = privileged;
    this.services = services;
    this.requiredPermNames =
        requiredPermNames != null ? requiredPermNames : Collections.emptyList();
  }

  static ExtensionReport ok(
      String id,
      String version,
      boolean privileged,
      Set<String> services,
      List<String> requiredPermNames) {
    return new ExtensionReport(true, "", id, version, privileged, services, requiredPermNames);
  }

  static ExtensionReport error(String msg) {
    return new ExtensionReport(
        false, msg, "", "", false, Collections.emptySet(), Collections.emptyList());
  }

  public String toString() {
    if (!ok) return "ERROR: " + message;
    LinkedHashSet<String> permNames = new LinkedHashSet<>(requiredPermNames);
    String perms = permNames.stream().sorted().collect(Collectors.joining(","));
    return String.join(
        "\n",
        "Extension: " + id,
        "Version  : " + (version == null ? "" : version),
        "Privileged: " + privileged,
        "Required : [" + perms + "]",
        "Services : " + (services.isEmpty() ? "(none)" : String.join(",", services)));
  }

  public String toJson() {
    Map<String, Object> obj = new LinkedHashMap<>();
    obj.put("ok", ok);
    if (!ok) obj.put("error", message);
    obj.put("id", id);
    obj.put("version", version);
    obj.put("privileged", privileged);
    obj.put("services", new ArrayList<>(services));
    LinkedHashSet<String> pset = new LinkedHashSet<>(requiredPermNames);
    obj.put("requiredPermissions", new ArrayList<>(pset));
    return toJson(obj);
  }

  static String toJson(Object o) {
    if (o == null) return "null";
    if (o instanceof String)
      return '"' + ((String) o).replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    if (o instanceof Boolean || o instanceof Number) return String.valueOf(o);
    if (o instanceof Map) {
      StringBuilder sb = new StringBuilder();
      sb.append("{");
      boolean first = true;
      for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
        if (!first) sb.append(',');
        first = false;
        sb.append(toJson(String.valueOf(e.getKey()))).append(':').append(toJson(e.getValue()));
      }
      return sb.append('}').toString();
    }
    if (o instanceof Iterable) {
      StringBuilder sb = new StringBuilder();
      sb.append('[');
      boolean first = true;
      for (Object x : (Iterable<?>) o) {
        if (!first) sb.append(',');
        first = false;
        sb.append(toJson(x));
      }
      return sb.append(']').toString();
    }
    return toJson(String.valueOf(o));
  }
}
