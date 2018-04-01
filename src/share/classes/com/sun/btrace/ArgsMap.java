/*
 * Copyright (c) 2018, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Copyright owner designates
 * this particular file as subject to the "Classpath" exception as provided
 * by the owner in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.sun.btrace;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple argument map wrapper allowing indexed access
 */
public final class ArgsMap implements Iterable<Map.Entry<String, String>>{
    private final LinkedHashMap<String, String> map;
    private final DebugSupport debug;

    public ArgsMap(Map<String, String> args, DebugSupport debug) {
        this.map = args != null ? new LinkedHashMap<>(args) : new LinkedHashMap<String, String>();
        this.debug = debug;
    }

    public ArgsMap(String[] argLine, DebugSupport debug) {
        this.map = new LinkedHashMap<>();
        if (argLine != null) {
                for (String arg : argLine) {
                String[] kv = arg.split("=");
                if (kv.length != 2) {
                    this.map.put(arg, "");
                } else {
                    this.map.put(kv[0], kv[1]);
                }
            }
        }
        this.debug = debug;
    }

    public ArgsMap() {
        this((Map<String, String>)null, new DebugSupport(SharedSettings.GLOBAL));
    }

    public ArgsMap(int initialCapacity, DebugSupport debug) {
        this.map = new LinkedHashMap<>(initialCapacity);
        this.debug = debug;
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

    public String template(String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return value;
        }

        StringBuilder sb = new StringBuilder();
        StringBuilder keySb = new StringBuilder();
        int state = 0;

        for (char c : value.toCharArray()) {
            switch (c) {
                case '$': {
                    if (state == 0) {
                        state = 1;
                    }
                    break;
                }
                case '{': {
                    if (state == 1) {
                        state = 2;
                        keySb.setLength(0);
                    }
                    break;
                }
                case '}': {
                    if (state == 2) {
                        String key = keySb.toString();
                        String val = get(key);
                        if (val != null) {
                            sb.append(val);
                        } else {
                            sb.append("${").append(key).append("}");
                        }
                        state = 0;
                    }
                    break;
                }
                default: {
                    switch (state) {
                        case 0: {
                            sb.append(c);
                            break;
                        }
                        case 2: {
                            keySb.append(c);
                            break;
                        }
                        default: {
                            // other states are invalid; ignore input
                        }
                    }
                }
            }
        }

        String expanded = sb.toString();
        if (debug.isDebug()) {
            debug.debug("Template: " + value + " -> " + expanded);
        }
        return expanded;
    }
}
