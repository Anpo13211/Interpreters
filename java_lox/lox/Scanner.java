package java_lox.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.text.html.HTMLEditorKit.Parser;

// TokenTypeクラスまたはインターフェースに定義されているすべての静的メンバーをインポート
import static java_lox.lox.TokenType.*;

class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0; // first character being scanned
    private int current = 0; // current character being scanned
    private int line = 1; // what source line current is on
    
    // raw source code
    Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        // add end of file(EOF) token to the end of the file.
        // not necessarily needed but cleaner
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private static final Map<String, TokenType> keywords;

    // keyword-map
    static {
        keywords = new HashMap<>();
        keywords.put("and",    AND);
        keywords.put("class",  CLASS);
        keywords.put("else",   ELSE);
        keywords.put("false",  FALSE);
        keywords.put("for",    FOR);
        keywords.put("fun",    FUN);
        keywords.put("if",     IF);
        keywords.put("nil",    NIL);
        keywords.put("or",     OR);
        keywords.put("print",  PRINT);
        keywords.put("return", RETURN);
        keywords.put("super",  SUPER);
        keywords.put("self",   SELF);
        keywords.put("true",   TRUE);
        keywords.put("var",    VAR);
        keywords.put("while",  WHILE);
    }

    private void scanToken() {
        char c = advance();
        switch(c) {
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;
            case '?': addToken(QUESTION); break;
            case ':': addToken(COLON); break;
            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else if (match('*')) {
                    skipBlockComment();
                    break;
                }else {
                    addToken(SLASH);
                }
                break;

            case '!':
                addToken(match('=') ? BANG_EQUAL: BANG);
                break;
            case '=':
                addToken(match('=')? EQUAL_EQUAL: EQUAL);
                break;
            case '<':
                addToken(match('=')? LESS_EQUAL: LESS);
                break;
            case '>':
                addToken(match('=')? GREATER_EQUAL: GREATER);
                break;

            case ' ':
            case '\r':
            case '\t':
                // ignore whitespace
                break;

            case '\n':
                line++;
                break;

            case '"': string(); break;

            default:
                if (isDigit(c)) {
                    number();
                } else if(isAlpha(c)) {
                    identifier();
                } else {
                    Lox.error(line, "Unexprected character.");
                }
                break;
        }
    }

    private void skipBlockComment() {
        int nesting = 1;
        while (nesting > 0) {
            if (peek() == '\0') {
                Lox.error(line, "Unterminated block comment.");
                return;
            }

            if (peek() == '/' && peekNext() == '*') {
                advance();
                advance();
                nesting++;
                continue;
            }

            if (peek() == '*' && peekNext() == '/') {
                advance();
                advance();
                nesting--;
                continue;
            }

            advance();
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) type = IDENTIFIER;
        addToken(type);
    }

    private void number() {
        while (isDigit(peek())) advance();

        if (peek() == '.' && isDigit(peekNext())) {
            advance();
            while (isDigit(peek())) advance();
        }

        // This interpreter uses java's Double type to represent numbers(you can inplement one by your own)
        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }
        advance();

        // Trim the surrounding quotes
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    // consume next character, return it
    private char advance() {
        return source.charAt(current++);
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    // addToken for literals
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }
}
