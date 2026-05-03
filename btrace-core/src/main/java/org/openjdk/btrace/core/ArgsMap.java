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
package org.openjdk.btrace.core;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A simple argument map wrapper allowing indexed access */
public final class ArgsMap implements Iterable<Map.Entry<String, String>> {
  private final LinkedHashMap<String, String> map;

  public ArgsMap(Map<String, String> args) {
    map = args != null ? new LinkedHashMap<>(args) : new LinkedHashMap<>();
  }

  public ArgsMap(String[] argLine) {
    map = new LinkedHashMap<>();
    if (argLine != null) {
      for (String arg : argLine) {
        String[] kv = arg.split("=");
        if (kv.length != 2) {
          map.put(arg, "");
        } else {
          map.put(kv[0], kv[1]);
        }
      }
    }
  }

  public ArgsMap() {
    this((Map<String, String>) null);
  }

  public ArgsMap(int initialCapacity) {
    map = new LinkedHashMap<>(initialCapacity);
  }

  public static ArgsMap merge(ArgsMap... maps) {
    Map<String, String> propMap = new LinkedHashMap<>();
    for (ArgsMap map : maps) {
      propMap.putAll(map.map);
    }
    return new ArgsMap(propMap);
  }

  public String get(String key) {
    return map.get(key);
  }

  public String get(int idx) {
    if (idx >= 0 && idx < map.size()) {
      Iterator<Map.Entry<String, String>> argsIterator = map.entrySet().iterator();
      for (int i = 0; i < idx; i++) {
        argsIterator.next();
      }
      Map.Entry<String, String> e = argsIterator.next();
      return e.getValue() != null ? e.getKey() + "=" + e.getValue() : e.getKey();
    } else {
      return null;
    }
  }

  public void clear() {
    map.clear();
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public String put(String key, String value) {
    return map.put(key, value);
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return map.entrySet().iterator();
  }

  public boolean containsKey(String key) {
    return map.containsKey(key);
  }

  @Override
  public boolean equals(Object o) {
    return map.equals(o);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  @Override
  public String toString() {
    return "ArgsMap{" + "map=" + map + '}';
  }

  public String template(String value) {
    if (value == null) {
      return null;
    }
    if (value.isEmpty()) {
      return value;
    }

    Matcher matcher = PatternSingleton.INSTANCE.matcher(value);
    StringBuffer buffer = new StringBuffer(value.length());

    while (matcher.find()) {
      String val = get(matcher.group(1));
      matcher.appendReplacement(buffer, val != null ? val : "$0");
    }
    matcher.appendTail(buffer);

    return buffer.toString();
  }

  private static final class PatternSingleton {
    // lazy initialization trick
    // do not compile the pattern until it is actually requested
    private static final Pattern INSTANCE = Pattern.compile("\\$\\{(.*?)}");
  }
}
