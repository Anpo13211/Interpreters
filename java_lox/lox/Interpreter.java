package java_lox.lox;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


//The class use to refer to a Lox value
class Interpreter implements Expr.Visitor<Object>,  
                             Stmt.Visitor<Void>    {
    final Environment globals = new Environment();
    private final Map<Expr, Integer> locals = new HashMap<>();
    private Environment environment = globals;
    private static Object uninitialized = new Object();

    Interpreter() {
        globals.define("clock", new LoxCallable() {
          @Override
          public int arity() { return 0; }
    
          @Override
          public Object call(Interpreter interpreter,
                             List<Object> arguments) {
            return (double)System.currentTimeMillis() / 1000.0;
          }
    
          @Override
          public String toString() { return "<native fn>"; }
        });
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement: statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    String interpret(Expr expression) {
        try {
            Object value = evaluate(expression);
            return stringify(value);
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
            return null;
        }
    }

    // convert the tree node to runtime value
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left;
        } else {
            if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances have fields.");
        }
        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitSelfExpr(Expr.Self expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;

            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            // Restore previous environment
            this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        environment.define(stmt.name.lexeme, null);
        Map<String, LoxFunction> classMethods = new HashMap<>();
        String fnName = stmt.name.lexeme;
        
        for (Stmt.Function method : stmt.classMethods) {
            LoxFunction function = new LoxFunction(fnName, method.function, environment, false);
            classMethods.put(method.name.lexeme, function);
        }

        LoxClass metaclass = new LoxClass(null, fnName + " metaclass", classMethods);

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method: stmt.methods) {
            LoxFunction function = new LoxFunction(fnName, method.function, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(metaclass, stmt.name.lexeme, methods);
        environment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        String fnName = stmt.name.lexeme;
        // capture the current environment when the function is declared
        environment.define(fnName, new LoxFunction(fnName, stmt.function, environment, false));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = uninitialized;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);
        
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        }  else {
            globals.assign(expr.name, value);
        }

        return value;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        // post-order traversal
        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }
        return null;
    }

    // 一つ一つ value をチェックしないといけないから遅い
    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
        return new LoxFunction(null, expr, environment, false);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        // Make a unique error function just for Lox
        throw new RuntimeError(operator, "Operand must be a numebr.");
    }

    private void checkNumberOperand(Token operator,
                                    Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private void checkOperands(Object left, Object right, Token operator) {
        if (left instanceof Double && right instanceof Double) return;
        if (left instanceof String && right instanceof String) return;
        throw new RuntimeError(operator, "Operands must be two numbers or two strings.");
    }

    // Follow Ruby's rule(false and nil are falsey, others are truthy)
    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean) object;
        return true;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
            
            case GREATER:
            case GREATER_EQUAL:
            case LESS:
            case LESS_EQUAL:
                checkOperands(left, right, expr.operator);
            if (left instanceof Double && right instanceof Double) {
                switch (expr.operator.type) {
                    case GREATER: return (double)left > (double)right;
                    case GREATER_EQUAL: return (double)left >= (double)right;
                    case LESS: return (double)left < (double)right;
                    case LESS_EQUAL: return (double)left <= (double)right;
                }
            } else {
                int comparison = ((String)left).compareTo((String)right);
                switch (expr.operator.type) {
                    case GREATER: return comparison > 0;
                    case GREATER_EQUAL: return comparison >= 0;
                    case LESS: return comparison < 0;
                    case LESS_EQUAL: return comparison <= 0;
                }
            }
            break;

            case MINUS:
                checkNumberOperand(expr.operator, left, right);    
                return (double)left - (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }
                if (left instanceof String && right instanceof String) {
                    return (String)left + (String)right;
                }
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperand(expr.operator, left, right);
                if ((double)right == 0) throw new RuntimeError(expr.operator, "Cannot devide by zero.");
                return (double)left / (double)right;
            case STAR:
                checkNumberOperand(expr.operator, left, right);
                return (double)left * (double)right;    
        }
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable)callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + 
                    function.arity() + " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
            Object result = ((LoxInstance) object).get(expr.name);
            if (result instanceof LoxFunction &&
                ((LoxFunction) result).isGetter()) {
                result = ((LoxFunction) result).call(this, null);
            }

            return result;
        }

        throw new RuntimeError(expr.name, "Only instances have properties.");
    }

    @Override
    public Object visitConditionalExpr(Expr.Conditional expr) {
        Object condition = evaluate(expr.condition);
        return isTruthy(condition) ? evaluate(expr.thenBranch) : evaluate(expr.elseBranch);
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        return object.toString();
    }
}
