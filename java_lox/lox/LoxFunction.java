package java_lox.lox;

import java.util.List;

class LoxFunction implements LoxCallable{
    private final String name;
    private final Expr.Function declaration;
    private final Environment closure;
    private final boolean isInitializer;

    LoxFunction(String name, Expr.Function declaration, Environment closure, boolean isInitializer) {
        this.name = name;
        this.closure = closure;
        this.declaration = declaration;
        this.isInitializer = isInitializer;
    }

    LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("self", instance);
        return new LoxFunction(name, declaration, environment, isInitializer);
    }

    @Override
    public String toString() {
        if (name == null) return "<fn>";
        return "<fn " + name + ">";
    }

    @Override
    public int arity() {
        return declaration.parameters.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(closure);

        if (declaration.parameters != null) {
            for (int i = 0; i < declaration.parameters.size(); i++) {
                environment.define(declaration.parameters.get(i).lexeme, arguments.get(i));
            }
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch(Return returnValue) {
            if (isInitializer) return closure.getAt(0, "this");

            return returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, "self");
        return null;
    }
    
    public boolean isGetter() {
        return declaration.parameters == null;
    }
}
