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
package io.btrace.qe.report;

import java.util.List;
import java.util.Map;

public final class JsonWriter {

  public String write(Object value) {
    StringBuilder sb = new StringBuilder();
    writeValue(value, sb, 0);
    return sb.toString();
  }

  private void writeValue(Object value, StringBuilder sb, int indent) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof String) {
      writeString((String) value, sb);
    } else if (value instanceof Number || value instanceof Boolean) {
      sb.append(value);
    } else if (value instanceof Map) {
      writeMap((Map<?, ?>) value, sb, indent);
    } else if (value instanceof List) {
      writeList((List<?>) value, sb, indent);
    } else {
      writeString(String.valueOf(value), sb);
    }
  }

  private void writeMap(Map<?, ?> map, StringBuilder sb, int indent) {
    if (map.isEmpty()) {
      sb.append("{}");
      return;
    }
    sb.append("{\n");
    int i = 0;
    for (Map.Entry<?, ?> e : map.entrySet()) {
      indent(sb, indent + 1);
      writeString(String.valueOf(e.getKey()), sb);
      sb.append(": ");
      writeValue(e.getValue(), sb, indent + 1);
      if (++i < map.size()) {
        sb.append(',');
      }
      sb.append('\n');
    }
    indent(sb, indent);
    sb.append('}');
  }

  private void writeList(List<?> list, StringBuilder sb, int indent) {
    if (list.isEmpty()) {
      sb.append("[]");
      return;
    }
    sb.append("[\n");
    for (int i = 0; i < list.size(); i++) {
      indent(sb, indent + 1);
      writeValue(list.get(i), sb, indent + 1);
      if (i < list.size() - 1) {
        sb.append(',');
      }
      sb.append('\n');
    }
    indent(sb, indent);
    sb.append(']');
  }

  private static void indent(StringBuilder sb, int level) {
    for (int i = 0; i < level; i++) {
      sb.append("  ");
    }
  }

  private static void writeString(String s, StringBuilder sb) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
  }
}
