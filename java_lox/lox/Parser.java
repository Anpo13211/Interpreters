package java_lox.lox;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import static java_lox.lox.TokenType.*;

class Parser {
    private boolean allowExpression;
    private boolean foundExpression = false;
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Object parseRepl() {
        allowExpression = true;
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());

            if (foundExpression) {
                Stmt last = statements.get((statements.size() - 1));
                return ((Stmt.Expression) last).expression;
            }

            allowExpression = false;
        }

        return statements;
    }
    
    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    private Expr expression() {
        return assignment();
    }

    private Stmt declaration() {
        try {
            if (match(CLASS)) return classDeclaration();
            if (check(FUN) && checkNext(IDENTIFIER)) {
                consume(FUN, null);
                return function("function");
            }
            if (match(VAR)) return varDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");

        List<Stmt.Function> methods = new ArrayList<>();
        List<Stmt.Function> classMethods = new ArrayList<>();
        consume(LEFT_BRACE, "Expect '{' before class body.");

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            boolean isClassMethod = match(CLASS);
            (isClassMethod ? classMethods : methods).add(function("method"));
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.");
        return new Stmt.Class(name, methods, classMethods);
    }

    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(IF)) return ifStatement();
        if (match(WHILE)) return whileStatement();
        if (match(FOR)) return forStatement();

        return expressionStatement();
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        // initializer
        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        // condition
        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        // increment
        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        // increment incrment after body
        if (increment != null) {
            body = new Stmt.Block(
                Arrays.asList(body, new Stmt.Expression(increment))
            );
        }

        // evaluate the condition after increment
        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        // run initializer before the loop.
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    // else if をサポートできていない（修正必要）
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            if (check(IF)) {
                elseBranch = ifStatement();
            } else {
                elseBranch = statement();
            }
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }
        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expext ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();

        if (allowExpression && isAtEnd()) {
            foundExpression = true;
        } else {
            consume(SEMICOLON, "Expect ';' after expression.");
        }

        return new Stmt.Expression(expr);
    }

    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");

        // for getter
        List<Token> parameters = null;

        if (!kind.equals("method") || check(LEFT_PAREN)) {
            consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
            parameters = new ArrayList<>();
            if (!check(RIGHT_PAREN)) {
                do {
                    if (parameters.size() >= 255) {
                        error(peek(), "Can't have more than 255 parameters.");
                    }

                    parameters.add(consume(IDENTIFIER, "Expect parameter name."));
                } while (match(COMMA));
            }
            consume(RIGHT_PAREN, "Expect ')' after parameters.");
        }

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();

        Expr.Function func_body = new Expr.Function(parameters, body);
        return new Stmt.Function(name, func_body);
    }

    private Expr.Function functionBody(String kind) {
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 8) {
                    error(peek(), "Can't have more than 8 parameters.");
                }
                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");
        consume(LEFT_BRACE, "Expect '}' before " + kind + " body.");
        List<Stmt> body = block();
        return new Expr.Function(parameters, body);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd())  {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = conditional();

        while (match(AND)) {
            Token operator = previous();
            Expr right = conditional();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;
                // parse the left hand side as a normal expression
                return new Expr.Set(get.object, get.name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr conditional() {
        Expr expr = equality();
  
        if (match(QUESTION)) {
            Expr thenBranch = expression();
            consume(COLON,
                "Expect ':' after then branch of conditional expression.");
            Expr elseBranch = conditional();
            expr = new Expr.Conditional(expr, thenBranch, elseBranch);
        }  
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // addition an subtraction
    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // multiplication and division
    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return call();
    }

    // parse the arugment list
    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN,
                            "Expect ')' after arguments.");
        return new Expr.Call(callee, paren, arguments);
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER,
                        "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr primary() {
        if (match(FUN)) return functionBody("function");
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);
  
        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(SELF)) return new Expr.Self(previous());

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }
  
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
  
        // Error productions.
        if (match(BANG_EQUAL, EQUAL_EQUAL)) {
            error(previous(), "Missing left-hand operand.");
            equality();
            return null;
        }
  
        if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            error(previous(), "Missing left-hand operand.");
            comparison();
            return null;
        }
  
        if (match(PLUS)) {
            error(previous(), "Missing left-hand operand.");
            term();
            return null;
        }
  
        if (match(SLASH, STAR)) {
            error(previous(), "Missing left-hand operand.");
            factor();
            return null;
        }
  
        throw error(peek(), "Expect expression.");
    }

    private boolean match(TokenType... types) {
        for (TokenType type: types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }
 
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private boolean checkNext(TokenType tokenType) {
        if (isAtEnd()) return false;
        if (tokens.get(current + 1).type == EOF) return false;
        return tokens.get(current + 1).type == tokenType;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    // when an error occured, find the statement boundary and synchronize
    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS: case FOR: case FUN: case IF: case PRINT:
                case RETURN: case VAR: case WHILE:
                    return;
            }
        }
        advance();
    }
}
