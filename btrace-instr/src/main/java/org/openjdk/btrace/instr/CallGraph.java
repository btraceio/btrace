/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.btrace.instr;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This class allows building an arbitrary graph caller-callee relationship
 *
 * @author Jaroslav Bachorik
 */
public final class CallGraph {
  private static final Pattern MID_SPLIT_PTN = Pattern.compile("\\:\\:");
  private final Set<Node> nodes = new HashSet<>();
  private final Set<Node> startingNodes = new HashSet<>();

  public static String methodId(String name, String desc) {
    return name + "::" + desc;
  }

  public static String[] method(String methodId) {
    if (methodId.contains("::")) {
      return MID_SPLIT_PTN.split(methodId);
    }
    return new String[0];
  }

  public void addEdge(String fromId, String toId) {
    Node fromNode = null;
    Node toNode = null;
    for (Node n : nodes) {
      if (n.id.equals(fromId)) {
        fromNode = n;
      }
      if (n.id.equals(toId)) {
        toNode = n;
      }
      if (fromNode != null && toNode != null) break;
    }
    if (fromNode == null) {
      fromNode = new Node(fromId);
      nodes.add(fromNode);
    }
    if (toNode == null) {
      toNode = new Node(toId);
      nodes.add(toNode);
    }
    Edge e = new Edge(fromNode, toNode);
    fromNode.addOutgoing(e);
    toNode.addIncoming(e);
  }

  public void addStarting(Node n) {
    for (Node orig : nodes) {
      if (orig.equals(n)) {
        startingNodes.add(orig);
        return;
      }
    }
    startingNodes.add(n);
    nodes.add(n);
  }

  public boolean hasCycle() {
    Set<Node> looped = findCycles();
    Set<Node> checkingSet = new HashSet<>(looped);

    checkingSet.retainAll(startingNodes);
    if (!checkingSet.isEmpty()) {
      // a starting node is part of the loop
      return true;
    }

    Deque<Node> processingQueue = new ArrayDeque<>();
    for (Node n : startingNodes) {
      processingQueue.push(n);
      do {
        Node current = processingQueue.pop();
        if (looped.contains(current)) {
          // there is a path leading from a starting node to the detected loop
          return true;
        }
        for (Edge e : current.outgoing) {
          processingQueue.push(e.to);
        }
      } while (!processingQueue.isEmpty());
    }
    return false;
  }

  void callees(String name, String desc, Set<String> closure) {
    collectOutgoings(methodId(name, desc), closure);
  }

  void callers(String name, String desc, Set<String> closure) {
    collectIncomings(methodId(name, desc), closure);
  }

  private void collectOutgoings(String methodId, Set<String> closure) {
    for (Node n : nodes) {
      if (n.id.equals(methodId)) {
        for (Edge e : n.outgoing) {
          String id = e.to.id;
          if (!closure.contains(id)) {
            closure.add(id);
            collectOutgoings(id, closure);
          }
        }
      }
    }
  }

  private void collectIncomings(String methodId, Set<String> closure) {
    for (Node n : nodes) {
      if (n.id.equals(methodId)) {
        for (Edge e : n.incoming) {
          String id = e.from.id;
          if (!closure.contains(id)) {
            closure.add(id);
            collectIncomings(id, closure);
          }
        }
      }
    }
  }

  private Set<Node> findCycles() {
    if (nodes.size() < 2) return Collections.EMPTY_SET;

    Map<String, Node> checkingNodes = new HashMap<>();
    for (Node n : nodes) {
      Node newN = checkingNodes.get(n.id);
      if (newN == null) {
        newN = new Node(n.id);
        checkingNodes.put(n.id, newN);
      }
      for (Edge e : n.incoming) {
        Node fromN = checkingNodes.get(e.from.id);
        if (fromN == null) {
          fromN = new Node(e.from.id);
          checkingNodes.put(e.from.id, fromN);
        }
        Edge ee = new Edge(fromN, newN);
        newN.addIncoming(ee);
        fromN.addOutgoing(ee);
      }
      for (Edge e : n.outgoing) {
        Node toN = checkingNodes.get(e.to.id);
        if (toN == null) {
          toN = new Node(e.to.id);
          checkingNodes.put(e.to.id, toN);
        }
        Edge ee = new Edge(newN, toN);
        newN.addOutgoing(ee);
        toN.addIncoming(ee);
      }
    }

    boolean changesMade = false;
    Set<Node> sortedNodes = new HashSet<>(checkingNodes.values());
    do {
      changesMade = false;

      Iterator<Node> iter = sortedNodes.iterator();
      while (iter.hasNext()) {
        Node n = iter.next();
        if ((n.incoming.isEmpty() && !startingNodes.contains(n)) || n.outgoing.isEmpty()) {
          changesMade = true;
          for (Edge e : new HashSet<>(n.incoming)) {
            e.delete();
          }
          for (Edge e : new HashSet<>(n.outgoing)) {
            e.delete();
          }
          iter.remove();
        }
      }
    } while (changesMade);
    return sortedNodes;
  }

  public static class Node {
    private final String id;
    private final Set<Edge> incoming = new HashSet<>();
    private final Set<Edge> outgoing = new HashSet<>();

    public Node(String id) {
      this.id = id;
    }

    public void addIncoming(Edge e) {
      incoming.add(e);
    }

    public void addOutgoing(Edge e) {
      outgoing.add(e);
    }

    public void removeIncoming(Edge e) {
      incoming.remove(e);
    }

    public void removeOutgoing(Edge e) {
      outgoing.remove(e);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Node other = (Node) obj;
      return (id == null) ? (other.id == null) : id.equals(other.id);
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 11 * hash + (id != null ? id.hashCode() : 0);
      return hash;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("Node{id='").append(id).append("'}");
      sb.append("\n");
      sb.append("incomming:\n");
      sb.append("=============================\n");
      for (Edge e : incoming) {
        sb.append(e.from.id).append("\n");
      }
      sb.append("=============================\n");
      sb.append("outgoing:\n");
      for (Edge e : outgoing) {
        sb.append(e.to.id).append("\n");
      }
      sb.append("=============================\n");

      return sb.toString();
    }
  }

  public static class Edge {
    private final Node from;
    private final Node to;

    public Edge(Node from, Node to) {
      this.from = from;
      this.to = to;
    }

    public void delete() {
      from.removeOutgoing(this);
      to.removeIncoming(this);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Edge other = (Edge) obj;
      if (from != other.from && (from == null || !from.equals(other.from))) {
        return false;
      }
      return !(to != other.to && (to == null || !to.equals(other.to)));
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 37 * hash + (from != null ? from.hashCode() : 0);
      hash = 37 * hash + (to != null ? to.hashCode() : 0);
      return hash;
    }
  }
}
