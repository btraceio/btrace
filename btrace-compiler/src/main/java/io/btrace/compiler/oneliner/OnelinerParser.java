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
import java.util.List;
import io.btrace.compiler.oneliner.OnelinerAST.*;
import io.btrace.compiler.oneliner.OnelinerLexer.Token;
import io.btrace.compiler.oneliner.OnelinerLexer.TokenType;

/** Parser for BTrace oneliner language */
public class OnelinerParser {

  private final String input;
  private final List<Token> tokens;
  private int current = 0;

  private OnelinerParser(String input, List<Token> tokens) {
    this.input = input;
    this.tokens = tokens;
  }

  /** Parse a oneliner expression */
  public static OnelinerNode parse(String input) {
    OnelinerLexer lexer = new OnelinerLexer(input);
    List<Token> tokens = lexer.tokenize();
    OnelinerParser parser = new OnelinerParser(input, tokens);
    return parser.parseOneliner();
  }

  /** oneliner = probe_spec [filter] action_block */
  private OnelinerNode parseOneliner() {
    ProbeSpec probeSpec = parseProbeSpec();
    Filter filter = null;
    if (check(TokenType.IF)) {
      filter = parseFilter();
    }
    ActionBlock actionBlock = parseActionBlock();

    if (!isAtEnd()) {
      error("Unexpected token after action block: " + peek().type);
    }

    ProbeClause probe = new ProbeClause(probeSpec, filter, actionBlock);
    return new OnelinerNode(probe);
  }

  /** probe_spec = class_pattern :: method_pattern @ location */
  private ProbeSpec parseProbeSpec() {
    String classPattern = parsePattern("class pattern");

    if (!match(TokenType.DOUBLE_COLON)) {
      error("Expected '::' after class pattern");
    }

    String methodPattern = parsePattern("method pattern");

    if (!match(TokenType.AT)) {
      error("Expected '@' before location");
    }

    Location location = parseLocation();

    return new ProbeSpec(classPattern, methodPattern, location);
  }

  /** Parse class or method pattern (supports wildcards * ? and regex /.../) */
  private String parsePattern(String patternName) {
    if (check(TokenType.REGEX)) {
      String regex = advance().value;
      return "/" + regex + "/";
    } else if (check(TokenType.IDENTIFIER)) {
      return advance().value;
    } else if (check(TokenType.LT)) {
      // Handle special method names like <init> and <clinit>
      advance(); // consume '<'
      if (!check(TokenType.IDENTIFIER)) {
        error("Expected 'init' or 'clinit' after '<'");
      }
      String methodName = advance().value;
      if (!check(TokenType.GT)) {
        error("Expected '>' after '" + methodName + "'");
      }
      advance(); // consume '>'
      return "<" + methodName + ">";
    } else {
      error("Expected " + patternName + " (identifier or regex)");
      return null;
    }
  }

  /** location = entry | return | error */
  private Location parseLocation() {
    if (match(TokenType.ENTRY)) {
      return Location.ENTRY;
    } else if (match(TokenType.RETURN_VAL)) {
      return Location.RETURN;
    } else if (match(TokenType.ERROR)) {
      return Location.ERROR;
    } else {
      error("Expected location: entry, return, or error");
      return null;
    }
  }

  /** filter = if predicate */
  private Filter parseFilter() {
    if (!match(TokenType.IF)) {
      error("Expected 'if' keyword");
    }

    return parsePredicate();
  }

  /** predicate = duration_predicate | arg_predicate */
  private Filter parsePredicate() {
    if (check(TokenType.DURATION)) {
      return parseDurationPredicate();
    } else if (check(TokenType.ARGS)) {
      return parseArgPredicate();
    } else {
      error("Expected 'duration' or 'args' in filter predicate");
      return null;
    }
  }

  /** duration_predicate = duration comparator number ms */
  private Filter parseDurationPredicate() {
    if (!match(TokenType.DURATION)) {
      error("Expected 'duration'");
    }

    Comparator comp = parseComparator();

    if (!check(TokenType.NUMBER)) {
      error("Expected number after comparator in duration filter");
    }
    int millis = Integer.parseInt(advance().value);

    if (!check(TokenType.IDENTIFIER) || !peek().value.equals("ms")) {
      error("Expected 'ms' after duration value");
    }
    advance(); // consume 'ms'

    return new Filter(FilterType.DURATION, comp, millis);
  }

  /** arg_predicate = args [ number ] comparator value */
  private Filter parseArgPredicate() {
    if (!match(TokenType.ARGS)) {
      error("Expected 'args'");
    }

    if (!match(TokenType.LBRACKET)) {
      error("Expected '[' after 'args'");
    }

    if (!check(TokenType.NUMBER)) {
      error("Expected number for argument index");
    }
    int argIndex = Integer.parseInt(advance().value);

    if (!match(TokenType.RBRACKET)) {
      error("Expected ']' after argument index");
    }

    Comparator comp = parseComparator();

    Object value;
    if (check(TokenType.STRING)) {
      value = advance().value;
    } else if (check(TokenType.NUMBER)) {
      value = Integer.parseInt(advance().value);
    } else if (check(TokenType.IDENTIFIER) && peek().value.equals("null")) {
      value = null;
      advance();
    } else {
      error("Expected string, number, or null in argument comparison");
      return null;
    }

    return new ArgFilter(argIndex, comp, value);
  }

  /** comparator = > | < | == | >= | <= | != */
  private Comparator parseComparator() {
    if (match(TokenType.GT)) {
      return Comparator.GT;
    } else if (match(TokenType.LT)) {
      return Comparator.LT;
    } else if (match(TokenType.EQ)) {
      return Comparator.EQ;
    } else if (match(TokenType.GTE)) {
      return Comparator.GTE;
    } else if (match(TokenType.LTE)) {
      return Comparator.LTE;
    } else if (match(TokenType.NEQ)) {
      return Comparator.NEQ;
    } else {
      error("Expected comparison operator: >, <, ==, >=, <=, !=");
      return null;
    }
  }

  /** action_block = { action [, action]* } */
  private ActionBlock parseActionBlock() {
    if (!match(TokenType.LBRACE)) {
      error("Expected '{' to start action block");
    }

    List<Action> actions = new ArrayList<>();
    actions.add(parseAction());

    while (match(TokenType.COMMA)) {
      actions.add(parseAction());
    }

    if (!match(TokenType.RBRACE)) {
      error("Expected '}' to end action block or ',' for another action");
    }

    return new ActionBlock(actions);
  }

  /** action = print_action | count_action | time_action | stack_action */
  private Action parseAction() {
    if (check(TokenType.PRINT)) {
      return parsePrintAction();
    } else if (check(TokenType.COUNT)) {
      return parseCountAction();
    } else if (check(TokenType.TIME)) {
      return parseTimeAction();
    } else if (check(TokenType.STACK)) {
      return parseStackAction();
    } else {
      error("Expected action: print, count, time, or stack");
      return null;
    }
  }

  /** print_action = print [identifier [, identifier]*] */
  private PrintAction parsePrintAction() {
    if (!match(TokenType.PRINT)) {
      error("Expected 'print'");
    }

    List<String> args = new ArrayList<>();

    // Check if there are any identifiers to print
    if (isPrintIdentifier(peek().type)) {
      args.add(parsePrintIdentifier());

      while (match(TokenType.COMMA) && isAtEnd() == false) {
        // Check if next token after comma is a print identifier or another action
        if (isPrintIdentifier(peek().type)) {
          args.add(parsePrintIdentifier());
        } else {
          // Put the comma back by decrementing current
          current--;
          break;
        }
      }
    }

    return new PrintAction(args);
  }

  private boolean isPrintIdentifier(TokenType type) {
    return type == TokenType.METHOD
        || type == TokenType.ARGS
        || type == TokenType.DURATION
        || type == TokenType.RETURN_VAL
        || type == TokenType.SELF
        || type == TokenType.CLASS;
  }

  private String parsePrintIdentifier() {
    if (match(TokenType.METHOD)) {
      return "method";
    } else if (match(TokenType.ARGS)) {
      return "args";
    } else if (match(TokenType.DURATION)) {
      return "duration";
    } else if (match(TokenType.RETURN_VAL)) {
      return "return";
    } else if (match(TokenType.SELF)) {
      return "self";
    } else if (match(TokenType.CLASS)) {
      return "class";
    } else {
      error("Expected print identifier: method, args, duration, return, self, or class");
      return null;
    }
  }

  /** count_action = count */
  private CountAction parseCountAction() {
    if (!match(TokenType.COUNT)) {
      error("Expected 'count'");
    }
    return new CountAction();
  }

  /** time_action = time */
  private TimeAction parseTimeAction() {
    if (!match(TokenType.TIME)) {
      error("Expected 'time'");
    }
    return new TimeAction();
  }

  /** stack_action = stack [(number)] */
  private StackAction parseStackAction() {
    if (!match(TokenType.STACK)) {
      error("Expected 'stack'");
    }

    Integer depth = null;
    if (match(TokenType.LPAREN)) {
      if (!check(TokenType.NUMBER)) {
        error("Expected number for stack depth");
      }
      depth = Integer.parseInt(advance().value);

      if (!match(TokenType.RPAREN)) {
        error("Expected ')' after stack depth");
      }
    }

    return new StackAction(depth);
  }

  // Helper methods

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) {
      return false;
    }
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd()) {
      current++;
    }
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == TokenType.EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private void error(String message) {
    int position = isAtEnd() ? input.length() : peek().position;
    throw new OnelinerException(message, input, position);
  }
}
