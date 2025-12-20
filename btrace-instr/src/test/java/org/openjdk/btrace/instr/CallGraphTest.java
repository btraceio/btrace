package org.openjdk.btrace.instr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    callGraph.addStarting("a");
    callGraph.addStarting("b");
    callGraph.addStarting("c");

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
    callGraph.addStarting("a");

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
    callGraph.addStarting("a");

    assertTrue(callGraph.hasCycle());
  }
}
