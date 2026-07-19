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
package io.btrace.instr;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This class allows building an arbitrary graph caller-callee relationship
 *
 * @author Jaroslav Bachorik
 */
public final class CallGraph {
  private static final Pattern MID_SPLIT_PTN = Pattern.compile("::");
  private final Map<String, Node> nodes = new HashMap<>(); // O(1) lookup index
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
    // O(1) lookup instead of O(n)
    Node fromNode = nodes.computeIfAbsent(fromId, Node::new);
    Node toNode = nodes.computeIfAbsent(toId, Node::new);

    fromNode.addEdge(toNode);
  }

  public void addStarting(String methodId) {
    // O(1) lookup
    Node n = nodes.computeIfAbsent(methodId, Node::new);
    startingNodes.add(n);
  }

  public boolean hasCycle() {
    Set<Node> looped = findCycles();
    if (looped.isEmpty()) {
      return false;
    }

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
    // O(1) lookup instead of O(n)
    Node n = nodes.get(methodId);
    if (n != null) {
      for (Edge e : n.outgoing) {
        if (!closure.contains(e.to.id)) {
          closure.add(e.to.id);
          collectOutgoings(e.to.id, closure);
        }
      }
    }
  }

  private void collectIncomings(String methodId, Set<String> closure) {
    // O(1) lookup instead of O(n)
    Node n = nodes.get(methodId);
    if (n != null) {
      for (Edge e : n.incoming) {
        if (!closure.contains(e.from.id)) {
          closure.add(e.from.id);
          collectIncomings(e.from.id, closure);
        }
      }
    }
  }

  private Set<Node> findCycles() {
    if (nodes.isEmpty()) return Collections.emptySet();

    Map<String, Node> checkingNodes = new HashMap<>();
    for (Node n : nodes.values()) {
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

    Set<Node> sortedNodes = new HashSet<>(checkingNodes.values());
    // collect all terminal nodes
    Deque<Node> terminalNodes = new ArrayDeque<>();
    for (Node node : sortedNodes) {
      if ((node.incoming.isEmpty() && !startingNodes.contains(node)) || node.outgoing.isEmpty()) {
        terminalNodes.addLast(node);
      }
    }

    // remove each terminal node from the graph and if the removal creates more terminal nodes
    // add them all for processing
    while (!terminalNodes.isEmpty()) {
      Node n = terminalNodes.removeFirst();
      sortedNodes.remove(n);
      for (Edge e : new HashSet<>(n.incoming)) {
        e.delete();
        if (e.from.outgoing.isEmpty()) {
          terminalNodes.addLast(e.from);
        }
      }
      for (Edge e : new HashSet<>(n.outgoing)) {
        e.delete();
        if (e.to.incoming.isEmpty() && !startingNodes.contains(e.to)) {
          terminalNodes.addLast(e.to);
        }
      }
    }
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

    public void addEdge(Node to) {
      Edge edge = new Edge(this, to);
      this.addOutgoing(edge);
      to.addIncoming(edge);
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
      return Objects.equals(id, other.id);
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
      if (!Objects.equals(from, other.from)) {
        return false;
      }
      return Objects.equals(to, other.to);
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
