package java_lox.lox;

enum TokenType {
    // 一つ一つが Token のタイプである。（Lexeme を対応づけるため）

    // Single-character tokens
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR, QUESTION, COLON, 

    // One or two character tokens
    BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, TERNARY, 

    // Literals
    IDENTIFIER, STRING, NUMBER,

    // Keywords
    AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR, ELSE_IF,
    PRINT, RETURN, SUPER, SELF, TRUE, VAR, WHILE,

    EOF
}
