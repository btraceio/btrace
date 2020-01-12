/*
 * Copyright (c) 2017, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package org.openjdk.btrace.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Simplified trie-based prefix map
 */
public class PrefixMap {
    private final Node root = new Node();

    public void add(CharSequence val) {
        Node n = root;
        for (int i = 0; i < val.length(); i++) {
            char ch = val.charAt(i);
            Node child = n.getReferencedNode(ch);
            if (child == null) {
                child = new Node();
                n.addReferencedNode(ch, child);
            }
            n = child;
        }
        n.setValue(val);
    }

    public boolean contains(CharSequence val) {
        Node n = root;
        for (int i = 0; i < val.length(); i++) {
            char ch = val.charAt(i);
            Node child = n.getReferencedNode(ch);
            if (child == null) {
                return false;
            }
            if (child.value != null) {
                return true;
            }
            n = child;
        }
        return false;
    }

    private static final class Node {
        private final Map<Character, Node> refs = new HashMap<>();
        private CharSequence value;

        public Node() {
            value = null;
        }

        public Node getReferencedNode(char ch) {
            return refs.get(ch);
        }

        public void addReferencedNode(char ch, Node n) {
            if (!refs.containsKey(ch)) {
                refs.put(ch, n);
            }
        }

        public void setValue(CharSequence val) {
            value = val;
        }

    }
}
