package plc.project;

import sun.swing.AccumulativeRunnable;

import javax.lang.model.type.NullType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        // lookupFunction throws runtimeException if it doesn't exit
        Environment.Function main = scope.lookupFunction("main", 0);
        // If main does not have an int return
        if (!main.getReturnType().equals(Environment.Type.INTEGER))
            throw new RuntimeException("Main needs to return an Integer type.");

        // visit fields followed by methods
        ast.getFields().forEach(this::visit);
        ast.getMethods().forEach(this::visit);

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        // if value is present, visit value
        if (ast.getValue().isPresent())
            visit(ast.getValue().get());

        // define and set variable in current scope
        Environment.Variable var = scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getName()), Environment.NIL);
        ast.setVariable(var);

        // check that value is of the same type as the name of ast
        if (ast.getValue().get().getType() != Environment.getType(ast.getName()))
            throw new RuntimeException("Value is not assignable to the field.");

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        // create list of parameters and populate it
        List<Environment.Type> typeList = new ArrayList<>();
        for (int i = 0; i < ast.getParameters().size(); i++)
            typeList.add(i, Environment.getType(ast.getParameters().get(i)));

        // if return type name is present, store it in variable
        Environment.Type type;
        if (ast.getReturnTypeName().isPresent()) {
            type = Environment.getType(ast.getReturnTypeName().get());
        }
        // if not, define and set function in current scope with null return type
        else
            type = Environment.Type.NIL;
        Environment.Function func = scope.defineFunction(ast.getName(), ast.getName(), typeList, type, args -> Environment.NIL);
        ast.setFunction(func);

        // define variable for each parameter
        try {
            scope = new Scope(scope);
            for (int i = 0; i < ast.getParameters().size(); i++) {
                scope.defineVariable(ast.getParameters().get(i), ast.getParameters().get(i), Environment.getType(ast.getParameters().get(i)), Environment.NIL);
            }
            // snapshot of method before return function, visit statements, and then restore snapshot
            // maybe better method?
            Ast.Method meth = method;
            method = ast;
            ast.getStatements().forEach(this::visit);
            method = meth;
        }
        finally {
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        // if expression is not of type function, return runtime exception
        if (!(ast.getExpression() instanceof Ast.Expr.Function))
            throw new RuntimeException("Expression is not of Ast.Expr.Function type.");

        visit(ast.getExpression());

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        // 'LET' identifier (':' identifier)? ('=' expression)? ';

        // Optional<String> optTypeName = ast.getTypeName();
        // Optional<Ast.Expr> optValue = ast.getValue();

        // if (!optTypeName.isPresent() && !optValue.isPresent()) {
        // }


        // if (optTypeName.isPresent()) {
        //    type = Environment.getType(optTypeName.get());

            // Object obj = optTypeName.get();
            // String typeName = null;
            // if (obj instanceof String) {
            //     typeName = (String) obj;
            // }
            // type = Environment.getType(typeName);
        // }

        // if ast type name and value are not present, throw exception
        if (!ast.getTypeName().isPresent() && !ast.getValue().isPresent()) {
            throw new RuntimeException("Declaration must have type or value to infer type.");
        }
        Environment.Type type = null;

        // if type is present, store in variable
        if (ast.getTypeName().isPresent()) {
            type = Environment.getType(ast.getTypeName().get());
        }

        // if value is present, visit value and store if type is present
        if (ast.getValue().isPresent()) {

            visit(ast.getValue().get());

            // if (!ast.getTypeName().isPresent())
            if (type == null) {
                type = ast.getValue().get().getType();
            }

            requireAssignable(type, ast.getValue().get().getType());
        }

        // define and set variable
        ast.setVariable(scope.defineVariable(ast.getName(), ast.getName(), type, Environment.NIL));

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        // visit value and receiver
        visit(ast.getValue());
        visit(ast.getReceiver());

        // if receiver is not of type access, return exception
        if (!(ast.getReceiver() instanceof Ast.Expr.Access)) {
            throw new RuntimeException("Receiver is not an access expression.");
        }
        requireAssignable(ast.getValue().getType(), ast.getReceiver().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        visit(ast.getCondition());

        if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN))
            throw new RuntimeException("Condition is not of type boolean.");

        if (ast.getThenStatements().size() == 0)
            throw new RuntimeException("thenStatements list is empty.");

        // visit each statement in their own scope
        for (int i = 0; i < ast.getThenStatements().size(); i++) {
            scope = new Scope(scope);
            visit(ast.getThenStatements().get(i));
            scope = scope.getParent();
        }

        // same thing except with else statements
        for (int i = 0; i < ast.getElseStatements().size(); i++) {
            scope = new Scope(scope);
            visit(ast.getElseStatements().get(i));
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        if (!ast.getValue().getType().equals(Environment.Type.INTEGER_ITERABLE))
            throw new RuntimeException("Value is not of type IntegerIterable.");

        if (ast.getStatements().size() == 0)
            throw new RuntimeException("The statements list is empty.");

        // visit statements in new scope and define a variable for the ast
        scope = new Scope(scope);
        ast.getStatements().forEach(this::visit);
        scope.defineVariable(ast.getName(), ast.getName(), Environment.Type.INTEGER, Environment.NIL);

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        // visit condition and require that is it boolean type
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        // new scope, visit stmts
        try {
            scope = new Scope(scope);
            for (Ast.Stmt stmt : ast.getStatements()) {
                visit(stmt);
            }
        }
        finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        // require that value is assignable to return type of method it is in
        requireAssignable(ast.getValue().getType(), Environment.getType(method.getReturnTypeName().get()));

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        // make sure if BigInteger, it is within max and min values
        if (ast.getLiteral() instanceof BigInteger) {
            if (((BigInteger) ast.getLiteral()).longValue() > Integer.MAX_VALUE || ((BigInteger) ast.getLiteral()).longValue() < Integer.MIN_VALUE)
                throw new RuntimeException("Out of Java int range.");
            ast.setType(Environment.Type.INTEGER);
        }
        // same as integer except for double values
        else if (ast.getLiteral() instanceof BigDecimal) {
            if (((BigDecimal) ast.getLiteral()).doubleValue() > Double.MAX_VALUE || ((BigDecimal) ast.getLiteral()).doubleValue() < Double.MIN_VALUE)
                throw new RuntimeException("Out of Java decimal range.");
            ast.setType(Environment.Type.DECIMAL);
        }
        else if (ast.getLiteral() instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (ast.getLiteral() instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        }
        else if (ast.getLiteral() instanceof String) {
            ast.setType(Environment.Type.STRING);
        }
        else {
            ast.setType(Environment.Type.NIL);
        }


        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        if (!ast.getExpression().equals(Ast.Expr.Binary.class))
            throw new RuntimeException("The expression is not a binary expression.");
        ast.setType(ast.getExpression().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        visit(ast.getLeft());
        visit(ast.getRight());

        if (ast.getOperator().equals("AND") || ast.getOperator().equals("OR")) {
            requireAssignable(ast.getLeft().getType(), ast.getRight().getType());
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (ast.getOperator().equals("<") || ast.getOperator().equals("<=") || ast.getOperator().equals(">") || ast.getOperator().equals(">=") || ast.getOperator().equals("==") || ast.getOperator().equals("!=")) {
            requireAssignable(Environment.Type.COMPARABLE, ast.getRight().getType());
            requireAssignable(Environment.Type.COMPARABLE, ast.getLeft().getType());
            requireAssignable(ast.getLeft().getType(), ast.getRight().getType());
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (ast.getOperator().equals("+")) {
            if (ast.getLeft().getType().equals(Environment.Type.STRING) || ast.getRight().getType().equals(Environment.Type.STRING)) {
                ast.setType(Environment.Type.STRING);
            }
            else if (ast.getLeft().getType().equals(Environment.Type.INTEGER) || ast.getLeft().getType().equals(Environment.Type.DECIMAL)) {
                requireAssignable(ast.getLeft().getType(), ast.getRight().getType());
                Environment.Type type = ast.getLeft().getType();
                ast.setType(type);
            }
            else {
                throw new RuntimeException("Incorrect use of '+' operator.");
            }
        }
        else if (ast.getOperator().equals("-") || ast.getOperator().equals("*") || ast.getOperator().equals("/")) {
            if (ast.getLeft().getType().equals(Environment.Type.INTEGER) || ast.getLeft().getType().equals(Environment.Type.DECIMAL)) {
                requireAssignable(ast.getLeft().getType(), ast.getRight().getType());
                Environment.Type type = ast.getLeft().getType();
                ast.setType(type);
            }
            else {
                throw new RuntimeException("Incorrect us of '-', '*', or '/' operator.");
            }
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        // if receiver present, visit and set variable as variable in the scope of the receiver
        if (ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            ast.setVariable(ast.getReceiver().get().getType().getScope().lookupVariable(ast.getName()));
            return null;
        }

        ast.setVariable(scope.lookupVariable(ast.getName()));

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        Environment.Function func;
        int argSize = ast.getArguments().size();

        // if receiver present, visit and store in function with an extra argument
        if (ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            func = ast.getReceiver().get().getType().getScope().lookupFunction(ast.getName(), ast.getArguments().size() + 1);
            argSize++;
        }
        // otherwise store as function located in current scope
        else {
            func = scope.lookupFunction(ast.getName(), ast.getArguments().size());
        }

        if (argSize != func.getParameterTypes().size())
            throw new RuntimeException("Arguments size does not equal parameter size.");

        argSize -= ast.getArguments().size();

        // visit arguments and require that parameter types are assignable to argument types
        for (int i = 0; i < ast.getArguments().size(); i++) {
            visit(ast.getArguments().get(i));
            requireAssignable(func.getParameterTypes().get(i + argSize), ast.getArguments().get(i).getType());
        }

        ast.setFunction(func);

        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (target.equals(Environment.Type.COMPARABLE) && (type.equals(Environment.Type.INTEGER) || type.equals(Environment.Type.DECIMAL) || type.equals(Environment.Type.CHARACTER) || type.equals(Environment.Type.STRING)))
            return;
        else if (target.equals(Environment.Type.ANY))
            return;
        if (!target.equals(type))
            throw new RuntimeException("Target type does not match the type being used or assigned.");
    }

}
