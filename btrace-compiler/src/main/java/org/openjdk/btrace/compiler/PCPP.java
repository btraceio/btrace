/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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
package org.openjdk.btrace.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A minimal pseudo-C-preprocessor derived from PCPP of the GlueGen project.
 *
 * @author Kenneth B. Russell (original author)
 * @author A. Sundararajan (changes documented below)
 *     <p>Changes:
 *     <p>* Changed the package name. * Formatted with NetBeans. * Removed preservation of #define
 *     directives. commented #defines emission. * Commented out printing of line directives. * Print
 *     space char in output only for word tokens. This way multicharacter operators such as ==, !=
 *     etc. are property printed.
 */
public class PCPP {

  private static final boolean disableDebugPrint = true;
  private final Printer printer;
  /**
   * Map containing the results of #define statements. We must evaluate certain very simple
   * definitions. Macros and multi-f defines (which typically contain either macro definitions or
   * expressions) are currently not handled.
   */
  private final Map<String, String> defineMap = new HashMap<>();

  private final Set<String> nonConstantDefines = new HashSet<>();
  /** List containing the #include paths as Strings */
  private final List<String> includePaths;

  private ParseState state;

  public PCPP(List<String> includePaths) {
    this.includePaths = includePaths;
    printer = new Printer();
  }

  public PCPP(List<String> includePaths, Writer out) {
    this.includePaths = includePaths;
    printer = new Printer(out);
  }

  @SuppressWarnings("DefaultCharset")
  public static void main(String[] args) {
    try {
      Reader reader = null;
      String filename = null;

      if (args.length == 0) {
        usage();
      }

      List<String> includePaths = new ArrayList<>();
      for (int i = 0; i < args.length; i++) {
        if (i < args.length - 1) {
          String arg = args[i];
          if (arg.startsWith("-I")) {
            String[] paths = arg.substring(2).split(System.getProperty("path.separator"));
            includePaths.addAll(Arrays.asList(paths));
          } else {
            usage();
          }
        } else {
          String arg = args[i];
          if (arg.equals("-")) {
            reader = new InputStreamReader(System.in);
            filename = "standard input";
          } else {
            if (arg.startsWith("-")) {
              usage();
            }
            filename = arg;
            reader = new BufferedReader(new FileReader(filename));
          }
        }
      }

      new PCPP(includePaths).run(reader, filename);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  // ----------------------------------------------------------------------
  // Internals only below this point
  //
  private static void usage() {
    System.out.println("Usage: java PCPP [filename | -]");
    System.out.println("Minimal pseudo-C-preprocessor.");
    System.out.println("Output goes to standard output. Standard input can be used as input");
    System.out.println("by passing '-' as the argument.");
    System.exit(1);
  }

  public void run(Reader reader, String filename) throws IOException {
    StreamTokenizer tok = null;
    BufferedReader bufReader = null;
    if (reader instanceof BufferedReader) {
      bufReader = (BufferedReader) reader;
    } else {
      bufReader = new BufferedReader(reader);
    }
    tok = new StreamTokenizer(new ConcatenatingReader(bufReader));
    tok.resetSyntax();
    tok.wordChars('a', 'z');
    tok.wordChars('A', 'Z');
    tok.wordChars('0', '9');
    tok.wordChars('_', '_');
    tok.wordChars('.', '.');
    tok.wordChars(128 + 32, 255);
    tok.whitespaceChars(0, ' ');
    tok.quoteChar('"');
    tok.quoteChar('\'');
    tok.eolIsSignificant(true);
    tok.slashSlashComments(true);
    tok.slashStarComments(true);
    ParseState curState = new ParseState(tok, filename);
    ParseState oldState = state;
    state = curState;
    lineDirective();
    parse();
    state = oldState;
    if (state != null) {
      lineDirective();
    }
  }

  public String findFile(String filename) {
    String sep = File.separator;
    for (String inclPath : includePaths) {
      String fullPath = inclPath + sep + filename;
      File file = new File(fullPath);
      if (file.exists()) {
        return fullPath;
      }
    }
    return null;
  }

  // Accessors
  private void pushBackToken() {
    state.tok().pushBack();
  }

  /** Equivalent to nextToken(false) */
  private int nextToken() throws IOException {
    return nextToken(false);
  }

  private int nextToken(boolean returnEOLs) throws IOException {
    int lineno = lineNumber();
    // Check to see whether the previous call to nextToken() left an
    // EOL on the stream
    if (curToken() == StreamTokenizer.TT_EOL) {
      state.setStartOfLine(true);
    } else if (!state.startOfFile()) {
      state.setStartOfLine(false);
    }
    state.setStartOfFile(false);
    int val = state.tok().nextToken();
    if (!returnEOLs) {
      if (val == StreamTokenizer.TT_EOL) {
        do {
          // Consume and return next token, setting state appropriately
          val = state.tok().nextToken();
          state.setStartOfLine(true);
          printer.println();
        } while (val == StreamTokenizer.TT_EOL);
      }
    }
    if (lineNumber() > lineno + 1) {
      // This is a little noisier than it needs to be, but does handle
      // the case of multi-line comments properly
      lineDirective();
    }
    return val;
  }

  /** Reads the next token and throws an IOException if it is not the specified token character. */
  private void nextRequiredToken(int requiredToken) throws IOException {
    int nextTok = nextToken();
    if (nextTok != requiredToken) {
      String msg = "Expected token '" + requiredToken + "' but got ";
      switch (nextTok) {
        case StreamTokenizer.TT_EOF:
          msg += "<EOF>";
          break;
        case StreamTokenizer.TT_EOL:
          msg += "<EOL>";
          break;
        default:
          msg += "'" + curTokenAsString() + "'";
          break;
      }
      msg += " at file " + filename() + ", line " + lineNumber();
      throw new IOException(msg);
    }
  }

  private int curToken() {
    return state.tok().ttype;
  }

  private String curTokenAsString() {
    int t = curToken();
    if (t == StreamTokenizer.TT_WORD) {
      return curWord();
    }
    if (t == StreamTokenizer.TT_EOL) {
      throw new RuntimeException("Should not be converting EOL characters to strings");
    }
    char c = (char) t;
    if (c == '"' || c == '\'') {
      return c + state.tok().sval + c;
    }
    return String.valueOf(c);
  }

  private String nextWord() throws IOException {
    int val = nextToken();
    if (val != StreamTokenizer.TT_WORD) {
      throw new RuntimeException("Expected word at file " + filename() + ", line " + lineNumber());
    }
    return curWord();
  }

  private String curWord() {
    return state.tok().sval;
  }

  private boolean startOfLine() {
    return state.startOfLine();
  }

  private String filename() {
    return state.filename();
  }

  private int lineNumber() {
    return state.lineNumber();
  }

  /////////////
  // Parsing //
  /////////////
  private void parse() throws IOException {
    int tok = 0;
    while ((tok = nextToken()) != StreamTokenizer.TT_EOF) {
      // A '#' at the beginning of a line is a preprocessor directive
      if (startOfLine() && (tok == '#')) {
        preprocessorDirective();
      } else {
        // Output white space plus current token, handling #defines
        // (though not properly -- only handling #defines to constants and the empty string)

        // !!HACK!! - print space only for word tokens. This way multicharacter
        // operators such as ==, != etc. are property printed.
        if (tok == StreamTokenizer.TT_WORD) {
          printer.print(" ");
        }
        String s = curTokenAsString();
        String newS = defineMap.get(s);
        if (newS == null) {
          newS = s;
        }
        printer.print(newS);
      }
    }
    printer.flush();
  }

  private void preprocessorDirective() throws IOException {
    String w = nextWord();
    boolean shouldPrint = true;
    switch (w) {
      case "define":
        handleDefine();
        shouldPrint = false;
        break;
      case "undef":
        handleUndefine();
        shouldPrint = false;
        break;
      case "if":
      case "elif":
        handleIf(w.equals("if"));
        shouldPrint = false;
        break;
      case "ifdef":
      case "ifndef":
        handleIfdef(w.equals("ifdef"));
        shouldPrint = false;
        break;
      case "else":
        handleElse();
        shouldPrint = false;
        break;
      case "endif":
        handleEndif();
        shouldPrint = false;
        break;
      case "include":
        handleInclude();
        shouldPrint = false;
        break;
        // Unknown preprocessor directive (#pragma?) -- ignore
      default:
        break;
    }
    if (shouldPrint) {
      printer.print("# ");
      printToken();
    }
  }

  ////////////////////////////////////
  // Handling of #define directives //
  ////////////////////////////////////
  private void handleUndefine() throws IOException {
    // Next token is the name of the #undef
    String name = nextWord();

    debugPrint(true, "#undef " + name);

    // there shouldn't be any extra symbols after the name, but just in case...
    List<String> values = new ArrayList<>();
    while (nextToken(true) != StreamTokenizer.TT_EOL) {
      values.add(curTokenAsString());
    }

    if (printer.enabled()) {
      String oldDef = defineMap.remove(name);
      if (oldDef == null) {
        System.err.println(
            "WARNING: ignoring redundant \"#undef "
                + name
                + "\", at \""
                + filename()
                + "\" line "
                + lineNumber()
                + ": \""
                + name
                + "\" was not previously defined");
      } else {
        // System.err.println("UNDEFINED: '" + name + "'  (line " + lineNumber() + " file " +
        // filename() + ")");
      }
      nonConstantDefines.remove(name);
    } else {
      System.err.println(
          "FAILED TO UNDEFINE: '"
              + name
              + "'  (line "
              + lineNumber()
              + " file "
              + filename()
              + ")");
    }
  }

  private void handleDefine() throws IOException {
    // Next token is the name of the #define
    String name = nextWord();
    // System.err.println("IN HANDLE_DEFINE: '" + name + "'  (line " + lineNumber() + " file " +
    // filename() + ")");
    // (Note that this is not actually proper handling for multi-line #defines)
    List<String> values = new ArrayList<>();
    while (nextToken(true) != StreamTokenizer.TT_EOL) {
      values.add(curTokenAsString());
    }
    // if we're not within an active block of code (like inside an "#ifdef
    // FOO" where FOO isn't defined), then don't actually alter the definition
    // map.
    debugPrint(true, "#define " + name);
    if (printer.enabled()) {
      boolean emitDefine = true;

      // Handle #definitions to nothing or to a constant value
      int sz = values.size();
      if (sz == 0) {
        // definition to nothing, like "#define FOO"
        String oldDef = defineMap.put(name, "");
        if (oldDef != null) {
          System.err.println("WARNING: \"" + name + "\" redefined from \"" + oldDef + "\" to \"\"");
        }
        // We don't want to emit the define, because it would serve no purpose
        // and cause GlueGen errors (confuse the GnuCParser)
        emitDefine = false;
        // System.out.println("//---DEFINED: " + name + "to \"\"");
      } else if (sz == 1) {
        // See whether the value is a constant
        String value = values.get(0);
        if (isConstant(value)) {
          // Value is numeric constant like "#define FOO 5".
          // Put it in the #define map
          String oldDef = defineMap.put(name, value);
          if (oldDef != null) {
            System.err.println(
                "WARNING: \"" + name + "\" redefined from \"" + oldDef + "\" to \"" + value + "\"");
          }
          // System.out.println("//---DEFINED: " + name + " to \"" + value + "\"");
        } else {
          // Value is a symbolic constant like "#define FOO BAR".
          // Try to look up the symbol's value
          String newValue = resolveDefine(value, true);
          if (newValue != null) {
            // Set the value to the value of the symbol.
            //
            // TO DO: Is this correct? Why not output the symbol unchanged?
            // I think that it's a good thing to see that some symbols are
            // defined in terms of others. -chris
            values.set(0, newValue);
          } else {
            // Still perform textual replacement
            defineMap.put(name, value);
            nonConstantDefines.add(name);
            emitDefine = false;
          }
        }
      } else {
        // Non-constant define; try to do reasonable textual substitution anyway
        // (FIXME: should identify some of these, like (-1), as constants)
        emitDefine = false;
        StringBuilder val = new StringBuilder();
        for (int i = 0; i < sz; i++) {
          if (i != 0) {
            val.append(" ");
          }
          val.append(resolveDefine(values.get(i), false));
        }
        if (defineMap.get(name) != null) {
          // This is probably something the user should investigate.
          throw new RuntimeException(
              "Cannot redefine symbol \""
                  + name
                  + " from \""
                  + defineMap.get(name)
                  + "\" to non-constant "
                  + " definition \""
                  + val
                  + "\"");
        }
        defineMap.put(name, val.toString());
        nonConstantDefines.add(name);
      }

      if (emitDefine) {
        // commenting out the #define in output
        printer.print("// ");
        // Print name and value
        printer.print("# define ");
        printer.print(name);
        for (String line : values) {
          printer.print(" ");
          printer.print(line);
        }
        printer.println();
      }
    } // end if (enabled())

    // System.err.println("OUT HANDLE_DEFINE: " + name);
  }

  private boolean isConstant(String s) {
    if (s.startsWith("0x") || s.startsWith("0X")) {
      return checkHex(s);
    } else {
      return checkDecimal(s);
    }
  }

  private boolean checkHex(String s) {
    for (int i = 2; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
        return false;
      }
    }
    return true;
  }

  private boolean checkDecimal(String s) {
    try {
      Float.valueOf(s);
    } catch (NumberFormatException e) {
      // not parsable as a number
      return false;
    }
    return true;
  }

  private String resolveDefine(String word, boolean returnNullIfNotFound) {
    String lastWord = defineMap.get(word);
    if (lastWord == null) {
      if (returnNullIfNotFound) {
        return null;
      }
      return word;
    }
    String nextWord = null;
    do {
      nextWord = defineMap.get(lastWord);
      if (nextWord != null) {
        lastWord = nextWord;
      }
    } while (nextWord != null);
    return lastWord;
  }

  /**
   * @param isIfdef if true, we're processing #ifdef; if false, we're processing #ifndef.
   */
  private void handleIfdef(boolean isIfdef) throws IOException {
    // Next token is the name of the #ifdef
    String symbolName = nextWord();
    debugPrint(true, (isIfdef ? "#ifdef " : "#ifndef ") + symbolName);
    boolean symbolIsDefined = defineMap.get(symbolName) != null;
    // debugPrint(true, "HANDLE_IFDEF: ifdef(" + symbolName + ") = " + symbolIsDefined );
    printer.pushEnableBit(printer.enabled() && symbolIsDefined == isIfdef);
  }

  ////////////////////////////////////////////////
  // Handling of #if/#ifdef/ifndef/endif directives //
  ////////////////////////////////////////////////

  /** Handles #else directives */
  private void handleElse() {
    boolean enabledStatusBeforeElse = printer.enabled();
    printer.popEnableBit();
    printer.pushEnableBit(printer.enabled() && !enabledStatusBeforeElse);
    debugPrint(true, "#else ");
  }

  private void handleEndif() {
    boolean enabledBeforePopping = printer.enabled();
    printer.popEnableBit();

    // print the endif if we were enabled prior to popEnableBit() (sending
    // false to debugPrint means "print regardless of current enabled() state).
    debugPrint(!enabledBeforePopping, "#endif/end-else");
  }

  /**
   * @param isIf if true, we're processing #if; if false, we're processing #elif.
   */
  private void handleIf(boolean isIf) throws IOException {
    // System.out.println("IN HANDLE_" + (isIf ? "IF" : "ELIF") + " file \"" + filename() + " line "
    // + lineNumber());
    debugPrint(true, (isIf ? "#if" : "#elif"));
    boolean defineEvaluatedToTrue = handleIfRecursive(true);
    if (!isIf) {
      printer.popEnableBit();
    }
    printer.pushEnableBit(printer.enabled() && defineEvaluatedToTrue == isIf);
    // System.out.println("OUT HANDLE_" + (isIf ? "IF" : "ELIF") +" (evaluated to " +
    // defineEvaluatedToTrue + ")");
  }

  /**
   * This method is called recursively to process nested sub-expressions such as:
   *
   * <pre>
   *   #if !defined(OPENSTEP) && !(defined(NeXT) || !defined(NeXT_PDO))
   * </pre>
   *
   * @param greedy if true, continue evaluating sub-expressions until EOL is reached. If false,
   *     return as soon as the first sub-expression is processed.
   * @return the value of the sub-expression or (if greedy==true) series of sub-expressions.
   */
  private boolean handleIfRecursive(boolean greedy) throws IOException {
    // System.out.println("IN HANDLE_IF_RECURSIVE (" + ++tmp + ", greedy = " + greedy + ")");
    // System.out.flush();

    // ifValue keeps track of the current value of the potentially nested
    // "defined()" expressions as we process them.
    boolean ifValue = true;
    int openParens = 0;
    int tok;
    do {
      tok = nextToken(true);
      // System.out.println("-- READ: [" + (tok == StreamTokenizer.TT_EOL ? "<EOL>"
      // :curTokenAsString()) + "]");
      switch (tok) {
        case '(':
          ++openParens;
          // System.out.println("OPEN PARENS = " + openParens);
          ifValue = ifValue && handleIfRecursive(true);
          break;
        case ')':
          --openParens;
          // System.out.println("OPEN PARENS = " + openParens);
          break;
        case '!':
          {
            // System.out.println("HANDLE_IF_RECURSIVE HANDLING !");
            boolean rhs = handleIfRecursive(false);
            ifValue = !rhs;
            // System.out.println("HANDLE_IF_RECURSIVE HANDLED OUT !, RHS = " + rhs);
          }
          break;
        case '&':
          {
            nextRequiredToken('&');
            // System.out.println("HANDLE_IF_RECURSIVE HANDLING &&, LHS = " + ifValue);
            boolean rhs = handleIfRecursive(true);
            // System.out.println("HANDLE_IF_RECURSIVE HANDLED &&, RHS = " + rhs);
            ifValue = ifValue && rhs;
          }
          break;
        case '|':
          {
            nextRequiredToken('|');
            // System.out.println("HANDLE_IF_RECURSIVE HANDLING ||, LHS = " + ifValue);
            boolean rhs = handleIfRecursive(true);
            // System.out.println("HANDLE_IF_RECURSIVE HANDLED ||, RHS = " + rhs);
            ifValue = ifValue || rhs;
          }
          break;
        case '>':
        case '<':
        case '=':
          {
            // NOTE: we don't handle expressions like this properly
            boolean rhs = handleIfRecursive(true);
            ifValue = false;
          }
          break;
        case StreamTokenizer.TT_WORD:
          {
            String word = curTokenAsString();
            if (word.equals("defined")) {
              // Handle things like #if defined(SOMESYMBOL)
              nextRequiredToken('(');
              String symbol = nextWord();
              boolean isDefined = defineMap.get(symbol) != null;
              // System.out.println("HANDLE_IF_RECURSIVE HANDLING defined(" + symbol + ") = " +
              // isDefined);
              ifValue = ifValue && isDefined;
              nextRequiredToken(')');
            } else {
              // Handle things like #if SOME_SYMBOL.
              String symbolValue = defineMap.get(word);

              // See if the statement is "true"; i.e., a non-zero expression
              if (symbolValue != null) {
                // The statement is true if the symbol is defined and is a constant expression
                return (!nonConstantDefines.contains(word));
              } else {
                // The statement is true if the symbol evaluates to a non-zero value
                //
                // NOTE: This doesn't yet handle evaluable expressions like "#if
                // SOME_SYMBOL > 5" or "#if SOME_SYMBOL == 0", both of which are
                // valid syntax. It only handles numeric symbols like "#if 1"

                try {
                  // see if it's in decimal form
                  return Double.parseDouble(word) != 0;
                } catch (NumberFormatException nfe1) {
                  try {
                    // ok, it's not a valid decimal value, try hex/octal value
                    return Long.parseLong(word) != 0;
                  } catch (NumberFormatException nfe2) {
                    try {
                      // ok, it's not a valid hex/octal value, try boolean
                      return Boolean.parseBoolean(word);
                    } catch (NumberFormatException nfe3) {
                      // give up; the symbol isn't a numeric or boolean value
                      return false;
                    }
                  }
                }
              }
            }
          } // end case TT_WORD
          break;
        case StreamTokenizer.TT_EOL:
          // System.out.println("HANDLE_IF_RECURSIVE HIT <EOL>!");
          pushBackToken(); // so caller hits EOL as well if we're recursing
          break;
        case StreamTokenizer.TT_EOF:
          throw new RuntimeException(
              "Unexpected end of file while parsing "
                  + "#if statement at file "
                  + filename()
                  + ", line "
                  + lineNumber());

        default:
          throw new RuntimeException(
              "Unexpected token ("
                  + curTokenAsString()
                  + ") while parsing "
                  + "#if statement at file "
                  + filename()
                  + ", line "
                  + lineNumber());
      }
      // System.out.println("END OF WHILE: greedy = " + greedy + " parens = " +openParens + " not
      // EOL = " + (tok != StreamTokenizer.TT_EOL) + " --> " + ((greedy && openParens >= 0) && tok
      // != StreamTokenizer.TT_EOL));
    } while ((greedy && openParens >= 0) && tok != StreamTokenizer.TT_EOL);
    // System.out.println("OUT HANDLE_IF_RECURSIVE (" + tmp-- + ", returning " + ifValue + ")");
    // System.out.flush();
    return ifValue;
  }

  // static int tmp = -1;

  /////////////////////////////////////
  // Handling of #include directives //
  /////////////////////////////////////
  @SuppressWarnings("DefaultCharset")
  private void handleInclude() throws IOException {
    // Two kinds of #includes: one with quoted string for argument,
    // one with angle brackets surrounding argument
    int t = nextToken();
    String filename = null;
    if (t == '"') {
      filename = curWord();
    } else if (t == '<') {
      // Components of path name are coming in as separate tokens;
      // concatenate them
      StringBuilder buf = new StringBuilder();
      while ((t = nextToken()) != '>' && (t != StreamTokenizer.TT_EOF)) {
        buf.append(curTokenAsString());
      }
      if (t == StreamTokenizer.TT_EOF) {
        System.err.println("WARNING: unexpected EOF while processing #include directive");
      }
      filename = buf.toString();
    }
    // if we're not within an active block of code (like inside an "#ifdef
    // FOO" where FOO isn't defined), then don't actually process the
    // #included file.
    debugPrint(true, "#include [" + filename + "]");
    if (printer.enabled()) {
      // Look up file in known #include path
      String fullname = findFile(filename);
      // System.out.println("ACTIVE BLOCK, LOADING " + filename);
      if (fullname == null) {
        System.err.println("WARNING: unable to find #include file \"" + filename + "\"");
        return;
      }
      // Process this file in-line
      Reader reader = new BufferedReader(new FileReader(fullname));
      run(reader, fullname);
    } else {
      // System.out.println("INACTIVE BLOCK, SKIPPING " + filename);
    }
  }

  private void debugPrint(boolean onlyPrintIfEnabled, String msg) {
    if (disableDebugPrint) {
      return;
    }

    if (!onlyPrintIfEnabled || printer.enabled()) {
      for (int i = Printer.debugPrintIndentLevel; --i > 0; ) {
        System.out.print("  ");
      }
      System.out.println(msg + "  (line " + lineNumber() + " file " + filename() + ")");
    }
  }

  private void printToken() {
    printer.print(curTokenAsString());
  }

  private void lineDirective() {
    /*
     * Originally this code was emitting line directives. We don't need those.
     *
     * print("# " + lineNumber() + " \"" + filename() + "\"");
     * println();
     */
  }

  // State
  static class ParseState {

    private final StreamTokenizer tok;
    private final String filename;
    // We do not generate #line directives
    // private int lineNumber;
    private boolean startOfLine;
    private boolean startOfFile;

    ParseState(StreamTokenizer tok, String filename) {
      this.tok = tok;
      this.filename = filename;
      // We do not generate #line directives
      // lineNumber = 1;
      startOfLine = true;
      startOfFile = true;
    }

    StreamTokenizer tok() {
      return tok;
    }

    String filename() {
      return filename;
    }

    int lineNumber() {
      return tok.lineno();
    }

    boolean startOfLine() {
      return startOfLine;
    }

    void setStartOfLine(boolean val) {
      startOfLine = val;
    }

    boolean startOfFile() {
      return startOfFile;
    }

    void setStartOfFile(boolean val) {
      startOfFile = val;
    }
  }
}
