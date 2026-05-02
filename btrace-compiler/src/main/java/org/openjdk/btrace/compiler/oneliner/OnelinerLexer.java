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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tokenizer for BTrace oneliner language */
public class OnelinerLexer {

  /** Token types */
  public enum TokenType {
    // Literals
    IDENTIFIER,
    NUMBER,
    STRING,
    REGEX,

    // Keywords
    IF,
    PRINT,
    COUNT,
    STACK,
    TIME,
    ENTRY,
    RETURN,
    ERROR,

    // Special identifiers
    METHOD,
    ARGS,
    DURATION,
    RETURN_VAL,
    SELF,
    CLASS,

    // Symbols
    DOUBLE_COLON,
    AT,
    LBRACE,
    RBRACE,
    LPAREN,
    RPAREN,
    LBRACKET,
    RBRACKET,
    COMMA,
    EQ,
    GT,
    LT,
    GTE,
    LTE,
    NEQ,

    EOF
  }

  /** Token with type, value, and position */
  public static class Token {
    public final TokenType type;
    public final String value;
    public final int position;

    public Token(TokenType type, String value, int position) {
      this.type = type;
      this.value = value;
      this.position = position;
    }

    @Override
    public String toString() {
      return type + (value != null && !value.isEmpty() ? "(" + value + ")" : "") + "@" + position;
    }
  }

  private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

  static {
    KEYWORDS.put("if", TokenType.IF);
    KEYWORDS.put("print", TokenType.PRINT);
    KEYWORDS.put("count", TokenType.COUNT);
    KEYWORDS.put("stack", TokenType.STACK);
    KEYWORDS.put("time", TokenType.TIME);
    KEYWORDS.put("entry", TokenType.ENTRY);
    KEYWORDS.put("return", TokenType.RETURN_VAL);
    KEYWORDS.put("error", TokenType.ERROR);
    KEYWORDS.put("method", TokenType.METHOD);
    KEYWORDS.put("args", TokenType.ARGS);
    KEYWORDS.put("duration", TokenType.DURATION);
    KEYWORDS.put("self", TokenType.SELF);
    KEYWORDS.put("class", TokenType.CLASS);
  }

  private final String input;
  private int pos = 0;

  public OnelinerLexer(String input) {
    this.input = input != null ? input : "";
  }

  /** Tokenize the entire input */
  public List<Token> tokenize() {
    List<Token> tokens = new ArrayList<>();
    Token token;
    while ((token = nextToken()).type != TokenType.EOF) {
      tokens.add(token);
    }
    tokens.add(token); // add EOF
    return tokens;
  }

  /** Get the next token */
  public Token nextToken() {
    skipWhitespace();

    if (isEOF()) {
      return new Token(TokenType.EOF, "", pos);
    }

    int startPos = pos;
    char ch = peek();

    // Two-character operators
    if (ch == ':' && peek(1) == ':') {
      pos += 2;
      return new Token(TokenType.DOUBLE_COLON, "::", startPos);
    }
    if (ch == '=' && peek(1) == '=') {
      pos += 2;
      return new Token(TokenType.EQ, "==", startPos);
    }
    if (ch == '>' && peek(1) == '=') {
      pos += 2;
      return new Token(TokenType.GTE, ">=", startPos);
    }
    if (ch == '<' && peek(1) == '=') {
      pos += 2;
      return new Token(TokenType.LTE, "<=", startPos);
    }
    if (ch == '!' && peek(1) == '=') {
      pos += 2;
      return new Token(TokenType.NEQ, "!=", startPos);
    }

    // Single-character tokens
    switch (ch) {
      case '@':
        pos++;
        return new Token(TokenType.AT, "@", startPos);
      case '{':
        pos++;
        return new Token(TokenType.LBRACE, "{", startPos);
      case '}':
        pos++;
        return new Token(TokenType.RBRACE, "}", startPos);
      case '(':
        pos++;
        return new Token(TokenType.LPAREN, "(", startPos);
      case ')':
        pos++;
        return new Token(TokenType.RPAREN, ")", startPos);
      case '[':
        pos++;
        return new Token(TokenType.LBRACKET, "[", startPos);
      case ']':
        pos++;
        return new Token(TokenType.RBRACKET, "]", startPos);
      case ',':
        pos++;
        return new Token(TokenType.COMMA, ",", startPos);
      case '>':
        pos++;
        return new Token(TokenType.GT, ">", startPos);
      case '<':
        pos++;
        return new Token(TokenType.LT, "<", startPos);
    }

    // Regex pattern /pattern/
    if (ch == '/') {
      return scanRegex(startPos);
    }

    // String "value"
    if (ch == '"') {
      return scanString(startPos);
    }

    // Number
    if (Character.isDigit(ch)) {
      return scanNumber(startPos);
    }

    // Identifier or keyword
    if (isIdentifierStart(ch)) {
      return scanIdentifierOrKeyword(startPos);
    }

    throw new OnelinerException("Unexpected character: '" + ch + "'", input, pos);
  }

  private Token scanRegex(int startPos) {
    pos++; // skip initial '/'
    StringBuilder sb = new StringBuilder();
    while (!isEOF() && peek() != '/') {
      if (peek() == '\\' && peek(1) == '/') {
        sb.append('/');
        pos += 2;
      } else {
        sb.append(peek());
        pos++;
      }
    }
    if (isEOF()) {
      throw new OnelinerException(
          "Unterminated regex pattern, expected closing '/'", input, startPos);
    }
    pos++; // skip closing '/'
    return new Token(TokenType.REGEX, sb.toString(), startPos);
  }

  private Token scanString(int startPos) {
    pos++; // skip initial '"'
    StringBuilder sb = new StringBuilder();
    while (!isEOF() && peek() != '"') {
      if (peek() == '\\') {
        pos++;
        if (isEOF()) {
          throw new OnelinerException(
              "Unterminated string, expected closing '\"'", input, startPos);
        }
        char escaped = peek();
        switch (escaped) {
          case 'n':
            sb.append('\n');
            break;
          case 't':
            sb.append('\t');
            break;
          case 'r':
            sb.append('\r');
            break;
          case '\\':
            sb.append('\\');
            break;
          case '"':
            sb.append('"');
            break;
          default:
            sb.append(escaped);
        }
        pos++;
      } else {
        sb.append(peek());
        pos++;
      }
    }
    if (isEOF()) {
      throw new OnelinerException("Unterminated string, expected closing '\"'", input, startPos);
    }
    pos++; // skip closing '"'
    return new Token(TokenType.STRING, sb.toString(), startPos);
  }

  private Token scanNumber(int startPos) {
    StringBuilder sb = new StringBuilder();
    while (!isEOF() && Character.isDigit(peek())) {
      sb.append(peek());
      pos++;
    }
    return new Token(TokenType.NUMBER, sb.toString(), startPos);
  }

  private Token scanIdentifierOrKeyword(int startPos) {
    StringBuilder sb = new StringBuilder();
    while (!isEOF() && isIdentifierPart(peek())) {
      sb.append(peek());
      pos++;
    }
    String value = sb.toString();
    TokenType type = KEYWORDS.getOrDefault(value, TokenType.IDENTIFIER);
    return new Token(type, value, startPos);
  }

  private void skipWhitespace() {
    while (!isEOF() && Character.isWhitespace(peek())) {
      pos++;
    }
  }

  private boolean isEOF() {
    return pos >= input.length();
  }

  private char peek() {
    return peek(0);
  }

  private char peek(int offset) {
    int index = pos + offset;
    return index < input.length() ? input.charAt(index) : '\0';
  }

  private boolean isIdentifierStart(char ch) {
    return Character.isLetter(ch) || ch == '_' || ch == '*' || ch == '?';
  }

  private boolean isIdentifierPart(char ch) {
    return Character.isLetterOrDigit(ch)
        || ch == '_'
        || ch == '.'
        || ch == '*'
        || ch == '?'
        || ch == '$';
  }
}
