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
package io.btrace.core;

import java.util.HashMap;
import java.util.Map;

/** Simplified trie-based prefix map */
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
      refs.putIfAbsent(ch, n);
    }

    public void setValue(CharSequence val) {
      value = val;
    }
  }
}
