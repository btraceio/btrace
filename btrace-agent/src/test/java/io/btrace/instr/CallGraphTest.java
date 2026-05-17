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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
