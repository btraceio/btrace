package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CallGraphTest {

  @Test
  void hasNoCycle() {
    CallGraph callGraph = new CallGraph();
    callGraph.addEdge("a", "b");
    callGraph.addEdge("a", "c");
    callGraph.addEdge("b", "d");
    callGraph.addEdge("c", "d");

    assertFalse(callGraph.hasCycle());
  }

  @Test
  void hasNoCycleWithIsolatedNode() {
    CallGraph callGraph = new CallGraph();
    callGraph.addStarting(new CallGraph.Node("a"));
    callGraph.addStarting(new CallGraph.Node("b"));
    callGraph.addStarting(new CallGraph.Node("c"));

    callGraph.addEdge("a", "d");
    callGraph.addEdge("b", "d");

    assertFalse(callGraph.hasCycle());
  }

  @Test
  void hasCycleFromRootNode() {
    CallGraph callGraph = new CallGraph();
    callGraph.addEdge("a", "b");
    callGraph.addEdge("a", "c");
    callGraph.addEdge("b", "d");
    callGraph.addEdge("c", "d");
    callGraph.addEdge("c", "b");
    callGraph.addEdge("d", "a");
    callGraph.addStarting(new CallGraph.Node("a"));

    assertTrue(callGraph.hasCycle());
  }

  @Test
  void hasCycle() {
    CallGraph callGraph = new CallGraph();
    callGraph.addEdge("a", "b");
    callGraph.addEdge("a", "c");
    callGraph.addEdge("b", "d");
    callGraph.addEdge("c", "d");
    callGraph.addEdge("c", "b");
    callGraph.addEdge("d", "b");
    callGraph.addStarting(new CallGraph.Node("a"));

    assertTrue(callGraph.hasCycle());
  }
}
