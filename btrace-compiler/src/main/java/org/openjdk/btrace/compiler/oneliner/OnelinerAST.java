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
package org.openjdk.btrace.compiler.oneliner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AST node definitions for BTrace oneliner language. Represents the parsed structure of a oneliner
 * expression.
 */
public class OnelinerAST {

  /** Root node of the oneliner AST */
  public static class OnelinerNode {
    public final ProbeClause probe;

    public OnelinerNode(ProbeClause probe) {
      this.probe = Objects.requireNonNull(probe, "probe cannot be null");
    }
  }

  /** Probe clause: probe_spec [filter] action_block */
  public static class ProbeClause {
    public final ProbeSpec probeSpec;
    public final Filter filter; // nullable
    public final ActionBlock actionBlock;

    public ProbeClause(ProbeSpec probeSpec, Filter filter, ActionBlock actionBlock) {
      this.probeSpec = Objects.requireNonNull(probeSpec, "probeSpec cannot be null");
      this.filter = filter; // nullable
      this.actionBlock = Objects.requireNonNull(actionBlock, "actionBlock cannot be null");
    }
  }

  /** Probe specification: class::method @location */
  public static class ProbeSpec {
    public final String classPattern;
    public final String methodPattern;
    public final Location location;

    public ProbeSpec(String classPattern, String methodPattern, Location location) {
      this.classPattern = Objects.requireNonNull(classPattern, "classPattern cannot be null");
      this.methodPattern = Objects.requireNonNull(methodPattern, "methodPattern cannot be null");
      this.location = Objects.requireNonNull(location, "location cannot be null");
    }
  }

  /** Probe location */
  public enum Location {
    ENTRY,
    RETURN,
    ERROR
  }

  /** Filter: if predicate */
  public static class Filter {
    public final FilterType type;
    public final Comparator comparator;
    public final Object value; // Integer for duration ms, String/Integer for arg comparison

    public Filter(FilterType type, Comparator comparator, Object value) {
      this.type = Objects.requireNonNull(type, "type cannot be null");
      this.comparator = Objects.requireNonNull(comparator, "comparator cannot be null");
      this.value = Objects.requireNonNull(value, "value cannot be null");
    }
  }

  /** Filter type */
  public enum FilterType {
    DURATION, // duration>100ms
    ARG_COMPARE // args[0]=="value"
  }

  /** Comparison operator */
  public enum Comparator {
    GT,
    LT,
    EQ,
    GTE,
    LTE,
    NEQ
  }

  /** Action block: { action, action, ... } */
  public static class ActionBlock {
    public final List<Action> actions;

    public ActionBlock(List<Action> actions) {
      this.actions = new ArrayList<>(Objects.requireNonNull(actions, "actions cannot be null"));
      if (this.actions.isEmpty()) {
        throw new IllegalArgumentException("Action block cannot be empty");
      }
    }
  }

  /** Base interface for all actions */
  public interface Action {}

  /** Print action: print [identifier, ...] */
  public static class PrintAction implements Action {
    public final List<String> args; // nullable for bare "print"

    public PrintAction(List<String> args) {
      this.args = args != null ? new ArrayList<>(args) : new ArrayList<>();
    }
  }

  /** Count action: count */
  public static class CountAction implements Action {
    public CountAction() {}
  }

  /** Time action: time */
  public static class TimeAction implements Action {
    public TimeAction() {}
  }

  /** Stack action: stack [(depth)] */
  public static class StackAction implements Action {
    public final Integer depth; // nullable for default depth

    public StackAction(Integer depth) {
      if (depth != null && depth <= 0) {
        throw new IllegalArgumentException("Stack depth must be positive, got: " + depth);
      }
      this.depth = depth;
    }
  }

  /** For arg filters: args[index] */
  public static class ArgFilter extends Filter {
    public final int argIndex;

    public ArgFilter(int argIndex, Comparator comparator, Object value) {
      super(FilterType.ARG_COMPARE, comparator, value);
      if (argIndex < 0) {
        throw new IllegalArgumentException("Argument index must be non-negative, got: " + argIndex);
      }
      this.argIndex = argIndex;
    }
  }
}
