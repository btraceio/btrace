/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.btrace.runtime;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Jaroslav Bachorik
 */
public class CycleDetector {
    public static class Node {
        private String id;
        private Set<Edge> incoming = new HashSet<Edge>();
        private Set<Edge> outgoing = new HashSet<Edge>();

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
            final Node other = (Node) obj;
            if ((this.id == null) ? (other.id != null) : !this.id.equals(other.id)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 11 * hash + (this.id != null ? this.id.hashCode() : 0);
            return hash;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Node{id='").append(id).append("'}");
            sb.append("\n");
            sb.append("incomming:\n");
            sb.append("=============================\n");
            for(Edge e : incoming) {
                sb.append(e.from.id).append("\n");
            }
            sb.append("=============================\n");
            sb.append("outgoing:\n");
            for(Edge e : outgoing) {
                sb.append(e.to.id).append("\n");
            }
            sb.append("=============================\n");

            return sb.toString();
        }
    }
    public static class Edge {
        private Node from;
        private Node to;

        public Edge(Node from, Node to) {
            this.from = from;
            this.to = to;
        }

        public void delete() {
            this.from.removeOutgoing(this);
            this.to.removeIncoming(this);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Edge other = (Edge) obj;
            if (this.from != other.from && (this.from == null || !this.from.equals(other.from))) {
                return false;
            }
            if (this.to != other.to && (this.to == null || !this.to.equals(other.to))) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 37 * hash + (this.from != null ? this.from.hashCode() : 0);
            hash = 37 * hash + (this.to != null ? this.to.hashCode() : 0);
            return hash;
        }
    }
    
    final private Set<Node> nodes = new HashSet<Node>();
    final private Set<Node> startingNodes = new HashSet<Node>();
    
    public void addEdge(String fromId, String toId) {
        Node fromNode = null;
        Node toNode = null;
        for(Node n : nodes) {
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
        for(Node orig : nodes) {
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
        Set<Node> checkingSet = new HashSet<Node>(looped);

        checkingSet.retainAll(startingNodes);
        if (!checkingSet.isEmpty()) {
            // a starting node is part of the loop
            return true;
        }

        Deque<Node> processingQueue = new ArrayDeque<Node>();
        for(Node n : startingNodes) {
            processingQueue.push(n);
            do {
                Node current = processingQueue.pop();
                if (looped.contains(current)) {
                    // there is a path leading from a starting node to the detected loop
                    return true;
                }
                for(Edge e : current.outgoing) {
                    processingQueue.push(e.to);
                }
            } while (!processingQueue.isEmpty());
        }
        return false;
    }



    private Set<Node> findCycles() {
        if (nodes.size() < 2) return Collections.EMPTY_SET;

        Map<String, Node> checkingNodes = new HashMap<String, Node>();
        for(Node n : nodes) {
            Node newN = checkingNodes.get(n.id);
            if (newN == null) {
                newN = new Node(n.id);
                checkingNodes.put(n.id, newN);
            }
            for(Edge e : n.incoming) {
                Node fromN = checkingNodes.get(e.from.id);
                if (fromN == null) {
                    fromN = new Node(e.from.id);
                    checkingNodes.put(e.from.id, fromN);
                }
                Edge ee = new Edge(fromN, newN);
                newN.addIncoming(ee);
                fromN.addOutgoing(ee);
            }
            for(Edge e : n.outgoing) {
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
        Set<Node> sortedNodes = new HashSet<Node>(checkingNodes.values());
        do {
            changesMade = false;

            Iterator<Node> iter = sortedNodes.iterator();
            while (iter.hasNext()) {
                Node n = iter.next();
                if ((n.incoming.isEmpty() && !startingNodes.contains(n)) ||
                     n.outgoing.isEmpty()) {
                    changesMade = true;
                    for(Edge e : new HashSet<Edge>(n.incoming)) {
                        e.delete();
                    }
                    for (Edge e : new HashSet<Edge>(n.outgoing)) {
                        e.delete();
                    }
                    iter.remove();
                }
            }
        } while (changesMade);
        return sortedNodes;
    }
}
