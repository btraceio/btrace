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
package io.btrace.compiler;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;

class Printer {
  static int debugPrintIndentLevel = 0;
  ////////////
  // Output //
  ////////////
  private final PrintWriter writer;
  private final ArrayList<Boolean> enabledBits = new ArrayList<>();

  Printer() {
    writer = new PrintWriter(System.err);
  }

  Printer(Writer out) {
    writer = (out instanceof PrintWriter) ? (PrintWriter) out : new PrintWriter(out);
  }

  public static int getDebugPrintIndentLevel() {
    return debugPrintIndentLevel;
  }

  void println() {
    if (enabled()) {
      writer.println();
    }
  }

  boolean enabled() {
    return (enabledBits.isEmpty() || enabledBits.get(enabledBits.size() - 1));
  }

  void pushEnableBit(boolean enabled) {
    enabledBits.add(enabled);
    ++debugPrintIndentLevel;
    // debugPrint(false, "PUSH_ENABLED, NOW: " + enabled());
  }

  void print(String s) {
    if (enabled()) {
      writer.print(s);
      // System.out.print(s);//debug
    }
  }

  void flush() {
    if (enabled()) {
      writer.flush();
      // System.err.flush(); //debug
    }
  }

  void popEnableBit() {
    if (enabledBits.isEmpty()) {
      System.err.println("WARNING: mismatched #ifdef/endif pairs");
      return;
    }
    enabledBits.remove(enabledBits.size() - 1);
    --debugPrintIndentLevel;
    // debugPrint(false, "POP_ENABLED, NOW: " + enabled());
  }
}
