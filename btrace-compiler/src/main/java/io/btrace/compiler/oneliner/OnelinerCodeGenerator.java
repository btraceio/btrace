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
package io.btrace.compiler.oneliner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.btrace.compiler.oneliner.OnelinerAST.*;

/** Generates BTrace Java source code from oneliner AST */
public class OnelinerCodeGenerator {

  private static final String INDENT = "  ";
  private static final String DEFAULT_CLASS_NAME = "BTraceOneliner";

  /**
   * Generate Java source code from a oneliner AST using the default class name.
   *
   * @param node The parsed oneliner AST
   * @return Generated BTrace Java source code
   */
  public static String generate(OnelinerNode node) {
    return generate(node, DEFAULT_CLASS_NAME);
  }

  /**
   * Generate Java source code from a oneliner AST with a custom class name.
   *
   * @param node The parsed oneliner AST
   * @param className The class name to use for the generated BTrace script
   * @return Generated BTrace Java source code
   */
  public static String generate(OnelinerNode node, String className) {
    if (className == null || className.isEmpty()) {
      className = DEFAULT_CLASS_NAME;
    }
    StringBuilder sb = new StringBuilder();

    // Package and imports
    sb.append("package io.btrace.generated;\n\n");
    sb.append("import java.util.concurrent.atomic.AtomicInteger;\n");
    sb.append("import io.btrace.core.BTraceUtils;\n");
    sb.append("import io.btrace.core.annotations.*;\n");
    sb.append("import io.btrace.core.types.AnyType;\n\n");

    // Class declaration
    sb.append("@BTrace\n");
    sb.append("public class ").append(className).append(" {\n\n");

    ProbeClause probe = node.probe;

    // Generate fields (for count action)
    boolean hasCountAction = hasCountAction(probe.actionBlock);
    if (hasCountAction) {
      sb.append(INDENT)
          .append(
              "private static final AtomicInteger counter = BTraceUtils.newAtomicInteger(0);\n\n");
    }

    // Generate probe method
    generateProbeMethod(sb, probe);

    // Generate @OnEvent method (for count action)
    if (hasCountAction) {
      sb.append("\n");
      sb.append(INDENT).append("@OnEvent\n");
      sb.append(INDENT).append("public static void onEvent() {\n");
      sb.append(INDENT).append(INDENT);
      sb.append("BTraceUtils.println(\"count: \" + BTraceUtils.get(counter));\n");
      sb.append(INDENT).append("}\n");
    }

    // Close class
    sb.append("}\n");

    return sb.toString();
  }

  private static void generateProbeMethod(StringBuilder sb, ProbeClause probe) {
    ProbeSpec spec = probe.probeSpec;

    // Generate @OnMethod annotation
    sb.append(INDENT).append("@OnMethod(");
    sb.append("clazz=\"").append(escapeJavaString(spec.classPattern)).append("\", ");
    sb.append("method=\"").append(escapeJavaString(spec.methodPattern)).append("\"");

    // Add location if not ENTRY
    if (spec.location != Location.ENTRY) {
      sb.append(",\n");
      sb.append(INDENT).append(INDENT).append(INDENT);
      sb.append("location=@Location(Kind.").append(spec.location.name()).append(")");
    }

    sb.append(")\n");

    // Generate method signature
    sb.append(INDENT).append("public static void probe(");

    List<String> params = collectParameters(probe);
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(params.get(i));
    }

    sb.append(") {\n");

    // Generate method body
    generateMethodBody(sb, probe);

    // Close method
    sb.append(INDENT).append("}\n");
  }

  private static List<String> collectParameters(ProbeClause probe) {
    List<String> params = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    // Collect parameters from filter
    if (probe.filter != null) {
      addFilterParameters(params, seen, probe.filter, probe.probeSpec.location);
    }

    // Collect parameters from actions
    for (Action action : probe.actionBlock.actions) {
      if (action instanceof PrintAction) {
        addPrintParameters(params, seen, (PrintAction) action, probe.probeSpec.location);
      } else if (action instanceof TimeAction) {
        addParameter(params, seen, "@Duration long duration");
      }
    }

    return params;
  }

  private static void addFilterParameters(
      List<String> params, Set<String> seen, Filter filter, Location location) {
    if (filter.type == FilterType.DURATION) {
      addParameter(params, seen, "@Duration long duration");
    } else if (filter.type == FilterType.ARG_COMPARE) {
      addParameter(params, seen, "AnyType[] args");
    }
  }

  private static void addPrintParameters(
      List<String> params, Set<String> seen, PrintAction action, Location location) {
    for (String arg : action.args) {
      switch (arg) {
        case "method":
          addParameter(params, seen, "@ProbeMethodName String method");
          break;
        case "class":
          addParameter(params, seen, "@ProbeClassName String className");
          break;
        case "args":
          addParameter(params, seen, "AnyType[] args");
          break;
        case "duration":
          addParameter(params, seen, "@Duration long duration");
          break;
        case "return":
          addParameter(params, seen, "@Return AnyType returnValue");
          break;
        case "self":
          addParameter(params, seen, "@Self Object self");
          break;
      }
    }
  }

  private static void addParameter(List<String> params, Set<String> seen, String param) {
    if (!seen.contains(param)) {
      params.add(param);
      seen.add(param);
    }
  }

  private static void generateMethodBody(StringBuilder sb, ProbeClause probe) {
    String indent = INDENT + INDENT;

    // Generate filter if-statement
    if (probe.filter != null) {
      sb.append(indent).append("if (");
      generateFilterCondition(sb, probe.filter);
      sb.append(") {\n");
      indent = INDENT + INDENT + INDENT;
    }

    // Generate actions
    for (Action action : probe.actionBlock.actions) {
      if (action instanceof PrintAction) {
        generatePrintAction(sb, indent, (PrintAction) action);
      } else if (action instanceof CountAction) {
        sb.append(indent).append("BTraceUtils.incrementAndGet(counter);\n");
      } else if (action instanceof TimeAction) {
        generateTimeAction(sb, indent);
      } else if (action instanceof StackAction) {
        generateStackAction(sb, indent, (StackAction) action);
      }
    }

    // Close filter if-statement
    if (probe.filter != null) {
      sb.append(INDENT).append(INDENT).append("}\n");
    }
  }

  private static void generateFilterCondition(StringBuilder sb, Filter filter) {
    if (filter.type == FilterType.DURATION) {
      sb.append("duration ");
      sb.append(comparatorToJava(filter.comparator));
      sb.append(" ");
      int millis = (Integer) filter.value;
      long nanos = millis * 1_000_000L;
      sb.append(nanos).append("L");
    } else if (filter.type == FilterType.ARG_COMPARE) {
      ArgFilter argFilter = (ArgFilter) filter;
      sb.append("args.length > ").append(argFilter.argIndex);
      sb.append(" && ");

      if (argFilter.value == null) {
        sb.append("args[").append(argFilter.argIndex).append("] ");
        sb.append(comparatorToJava(argFilter.comparator));
        sb.append(" null");
      } else if (argFilter.value instanceof String) {
        if (argFilter.comparator == Comparator.EQ) {
          sb.append("BTraceUtils.compare(\"")
              .append(escapeJavaString((String) argFilter.value))
              .append("\", ");
          sb.append("BTraceUtils.str(args[").append(argFilter.argIndex).append("]))");
        } else if (argFilter.comparator == Comparator.NEQ) {
          sb.append("!BTraceUtils.compare(\"")
              .append(escapeJavaString((String) argFilter.value))
              .append("\", ");
          sb.append("BTraceUtils.str(args[").append(argFilter.argIndex).append("]))");
        } else {
          throw new IllegalArgumentException(
              "String comparison only supports == and !=, got: " + argFilter.comparator);
        }
      } else if (argFilter.value instanceof Integer) {
        sb.append("((Number)args[").append(argFilter.argIndex).append("]).longValue() ");
        sb.append(comparatorToJava(argFilter.comparator));
        sb.append(" ").append(argFilter.value).append("L");
      }
    }
  }

  private static String comparatorToJava(Comparator comp) {
    switch (comp) {
      case GT:
        return ">";
      case LT:
        return "<";
      case EQ:
        return "==";
      case GTE:
        return ">=";
      case LTE:
        return "<=";
      case NEQ:
        return "!=";
      default:
        throw new IllegalArgumentException("Unknown comparator: " + comp);
    }
  }

  private static void generatePrintAction(StringBuilder sb, String indent, PrintAction action) {
    if (action.args.isEmpty()) {
      // Bare "print" with no arguments
      sb.append(indent).append("BTraceUtils.println(\"trace\");\n");
    } else {
      sb.append(indent).append("BTraceUtils.println(");

      for (int i = 0; i < action.args.size(); i++) {
        if (i > 0) {
          sb.append(" + \" \" + ");
        }
        String arg = action.args.get(i);
        switch (arg) {
          case "method":
            sb.append("method");
            break;
          case "class":
            sb.append("className");
            break;
          case "args":
            sb.append("BTraceUtils.str(args)");
            break;
          case "duration":
            sb.append("duration");
            break;
          case "return":
            sb.append("BTraceUtils.str(returnValue)");
            break;
          case "self":
            sb.append("BTraceUtils.str(self)");
            break;
        }
      }

      sb.append(");\n");
    }
  }

  private static void generateTimeAction(StringBuilder sb, String indent) {
    sb.append(indent)
        .append("BTraceUtils.println(\"execution time: \" + (duration / 1000000) + \" ms\");\n");
  }

  private static void generateStackAction(StringBuilder sb, String indent, StackAction action) {
    sb.append(indent).append("BTraceUtils.jstack(");
    if (action.depth != null) {
      sb.append(action.depth);
    }
    sb.append(");\n");
  }

  private static boolean hasCountAction(ActionBlock actionBlock) {
    for (Action action : actionBlock.actions) {
      if (action instanceof CountAction) {
        return true;
      }
    }
    return false;
  }

  private static String escapeJavaString(String str) {
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
  }
}
