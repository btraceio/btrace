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
package io.btrace.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles MCP JSON-RPC protocol over stdio. Reads JSON-RPC messages from stdin and writes responses
 * to stdout. All logging goes to stderr.
 */
final class McpProtocol {
  private static final Logger log = LoggerFactory.getLogger(McpProtocol.class);
  private final BufferedReader reader;
  private final OutputStream out;

  McpProtocol(InputStream in, OutputStream out) {
    this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    this.out = out;
  }

  /** Reads a single JSON-RPC message from stdin. Returns null on EOF. */
  Map<String, Object> readMessage() throws IOException {
    String line = reader.readLine();
    if (line == null) {
      return null;
    }
    line = line.trim();
    if (line.isEmpty()) {
      return null;
    }
    log.debug("Received: {}", line);
    return parseJson(line);
  }

  /** Writes a JSON-RPC response to stdout (one line, newline terminated). */
  synchronized void writeMessage(Map<String, Object> message) throws IOException {
    String json = toJson(message);
    log.debug("Sending: {}", json);
    out.write(json.getBytes(StandardCharsets.UTF_8));
    out.write('\n');
    out.flush();
  }

  /** Sends a JSON-RPC success response. */
  void sendResult(Object id, Object result) throws IOException {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("result", result);
    writeMessage(response);
  }

  /** Sends a JSON-RPC error response. */
  void sendError(Object id, int code, String message) throws IOException {
    sendError(id, code, message, null);
  }

  /** Sends a JSON-RPC error response with optional data. */
  void sendError(Object id, int code, String message, Object data) throws IOException {
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("code", code);
    error.put("message", message);
    if (data != null) {
      error.put("data", data);
    }
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("error", error);
    writeMessage(response);
  }

  // --- Minimal JSON parser (handles objects, arrays, strings, numbers, booleans, null) ---

  @SuppressWarnings("unchecked")
  static Map<String, Object> parseJson(String json) {
    Object result = new JsonParser(json.trim()).parseValue();
    if (result instanceof Map) {
      return (Map<String, Object>) result;
    }
    throw new IllegalArgumentException("Expected JSON object, got: " + json);
  }

  private static final class JsonParser {
    private final String src;
    private int pos;

    JsonParser(String src) {
      this.src = src;
      this.pos = 0;
    }

    Object parseValue() {
      skipWhitespace();
      if (pos >= src.length()) {
        throw new IllegalArgumentException("Unexpected end of JSON");
      }
      char c = src.charAt(pos);
      if (c == '{') {
        return parseObject();
      }
      if (c == '[') {
        return parseArray();
      }
      if (c == '"') {
        return parseString();
      }
      if (c == 't' || c == 'f') {
        return parseBoolean();
      }
      if (c == 'n') {
        return parseNull();
      }
      return parseNumber();
    }

    Map<String, Object> parseObject() {
      expect('{');
      Map<String, Object> map = new LinkedHashMap<>();
      skipWhitespace();
      if (pos < src.length() && src.charAt(pos) == '}') {
        pos++;
        return map;
      }
      while (true) {
        skipWhitespace();
        String key = parseString();
        skipWhitespace();
        expect(':');
        Object value = parseValue();
        map.put(key, value);
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ',') {
          pos++;
        } else {
          break;
        }
      }
      expect('}');
      return map;
    }

    List<Object> parseArray() {
      expect('[');
      List<Object> list = new ArrayList<>();
      skipWhitespace();
      if (pos < src.length() && src.charAt(pos) == ']') {
        pos++;
        return list;
      }
      while (true) {
        list.add(parseValue());
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == ',') {
          pos++;
        } else {
          break;
        }
      }
      expect(']');
      return list;
    }

    String parseString() {
      expect('"');
      StringBuilder sb = new StringBuilder();
      while (pos < src.length()) {
        char c = src.charAt(pos++);
        if (c == '"') {
          return sb.toString();
        }
        if (c == '\\') {
          if (pos >= src.length()) {
            break;
          }
          char esc = src.charAt(pos++);
          switch (esc) {
            case '"':
            case '\\':
            case '/':
              sb.append(esc);
              break;
            case 'b':
              sb.append('\b');
              break;
            case 'f':
              sb.append('\f');
              break;
            case 'n':
              sb.append('\n');
              break;
            case 'r':
              sb.append('\r');
              break;
            case 't':
              sb.append('\t');
              break;
            case 'u':
              if (pos + 4 <= src.length()) {
                String hex = src.substring(pos, pos + 4);
                sb.append((char) Integer.parseInt(hex, 16));
                pos += 4;
              }
              break;
            default:
              sb.append(esc);
          }
        } else {
          sb.append(c);
        }
      }
      throw new IllegalArgumentException("Unterminated string");
    }

    Object parseNumber() {
      int start = pos;
      if (pos < src.length() && src.charAt(pos) == '-') {
        pos++;
      }
      while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
        pos++;
      }
      boolean isFloat = false;
      if (pos < src.length() && src.charAt(pos) == '.') {
        isFloat = true;
        pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
          pos++;
        }
      }
      if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
        isFloat = true;
        pos++;
        if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
          pos++;
        }
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
          pos++;
        }
      }
      String numStr = src.substring(start, pos);
      if (isFloat) {
        try {
          return Double.parseDouble(numStr);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Invalid number at " + start + ": " + numStr, e);
        }
      }
      long val;
      try {
        val = Long.parseLong(numStr);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid number at " + start + ": " + numStr, e);
      }
      if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
        return (int) val;
      }
      return val;
    }

    Object parseBoolean() {
      if (src.startsWith("true", pos)) {
        pos += 4;
        return Boolean.TRUE;
      }
      if (src.startsWith("false", pos)) {
        pos += 5;
        return Boolean.FALSE;
      }
      throw new IllegalArgumentException("Invalid boolean at " + pos);
    }

    Object parseNull() {
      if (src.startsWith("null", pos)) {
        pos += 4;
        return null;
      }
      throw new IllegalArgumentException("Invalid null at " + pos);
    }

    void skipWhitespace() {
      while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
        pos++;
      }
    }

    void expect(char c) {
      skipWhitespace();
      if (pos >= src.length() || src.charAt(pos) != c) {
        throw new IllegalArgumentException(
            "Expected '"
                + c
                + "' at "
                + pos
                + " but got: "
                + (pos < src.length() ? src.charAt(pos) : "EOF"));
      }
      pos++;
    }
  }

  // --- Minimal JSON serializer ---

  @SuppressWarnings("unchecked")
  static String toJson(Object obj) {
    if (obj == null) {
      return "null";
    }
    if (obj instanceof String) {
      return escapeJsonString((String) obj);
    }
    if (obj instanceof Number || obj instanceof Boolean) {
      return obj.toString();
    }
    if (obj instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) obj;
      StringBuilder sb = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        if (!first) {
          sb.append(",");
        }
        sb.append(escapeJsonString(entry.getKey()));
        sb.append(":");
        sb.append(toJson(entry.getValue()));
        first = false;
      }
      sb.append("}");
      return sb.toString();
    }
    if (obj instanceof List) {
      List<Object> list = (List<Object>) obj;
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
          sb.append(",");
        }
        sb.append(toJson(list.get(i)));
      }
      sb.append("]");
      return sb.toString();
    }
    if (obj instanceof Object[]) {
      Object[] arr = (Object[]) obj;
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < arr.length; i++) {
        if (i > 0) {
          sb.append(",");
        }
        sb.append(toJson(arr[i]));
      }
      sb.append("]");
      return sb.toString();
    }
    return escapeJsonString(obj.toString());
  }

  private static String escapeJsonString(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
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
    sb.append("\"");
    return sb.toString();
  }

  /** Helper to build a tool content result (text). */
  static Map<String, Object> toolResult(String text, boolean isError) {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("type", "text");
    content.put("text", text);

    List<Object> contentList = new ArrayList<>();
    contentList.add(content);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", contentList);
    result.put("isError", isError);
    return result;
  }
}
