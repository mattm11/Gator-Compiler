package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        for (int i = 0; i < ast.getFields().size(); i++)
            visit(ast.getFields().get(i));
        for (int i = 0; i < ast.getMethods().size(); i++)
            visit(ast.getMethods().get(i));
        Environment.Function func = scope.lookupFunction("main", 0);
        return Environment.create(func.invoke(null).getValue());
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        if (!ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), Environment.NIL);
        }
        else {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            try {
                scope = new Scope(scope);
                for (int i = 0; i < ast.getParameters().size(); i++) {
                    scope.defineVariable(ast.getParameters().get(i), Environment.create(args.get(i).getValue()));
                }
                for (int i = 0; i < ast.getStatements().size(); i++) {
                    try {
                        visit(ast.getStatements().get(i));
                    }
                    catch (Return e) {
                        return e.value;
                    }
                }
            }
            finally {
                scope.getParent();
            }
            return Environment.NIL;
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Declaration ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        }
        else {
            scope.defineVariable(ast.getName(), Environment.NIL);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Assignment ast) {
        if (ast.getReceiver() instanceof Ast.Expr.Access) {
            if (((Ast.Expr.Access) ast.getReceiver()).getReceiver().isPresent()) {
                Environment.PlcObject obj = visit(((Ast.Expr.Access) ast.getReceiver()).getReceiver().get());
                obj.setField(((Ast.Expr.Access) ast.getReceiver()).getName(), visit(ast.getValue()));
            }
            else {
                scope.lookupVariable(((Ast.Expr.Access) ast.getReceiver()).getName()).setValue(visit(ast.getValue()));
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.If ast) {
        requireType(Boolean.class, visit(ast.getCondition()));
        try {
            scope = new Scope(scope);
            System.out.println(visit(ast.getCondition()).getValue());
            if (visit(ast.getCondition()).getValue().equals(true)) {
                ast.getThenStatements().forEach(this::visit);
            }
            else {
                ast.getElseStatements().forEach(this::visit);
            }
        }
        finally {
            scope = scope.getParent();
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.For ast) {
            Iterable<Environment.PlcObject> list = requireType(Iterable.class, visit(ast.getValue()));
            for (Environment.PlcObject obj : list) {
                try {
                    scope = new Scope(scope);
                    scope.defineVariable(ast.getName(), obj);
                    ast.getStatements().forEach(this::visit);
                }
                finally {
                    scope = scope.getParent();
                }
            }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.While ast) {
        while (requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                for (Ast.Stmt stmt : ast.getStatements()) {
                    visit(stmt);
                }
                // could also do ast.getStatements().forEach(this::visit);
            }
            finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Literal ast) {
        if (ast.getLiteral() == null)
            return Environment.create(Environment.NIL.getValue());
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Binary ast) {
        if (ast.getOperator().equals("AND") || ast.getOperator().equals("OR")) {
            if (ast.getOperator().equals("AND")) {
                boolean left = requireType(Boolean.class, visit(ast.getLeft()));
                boolean right = requireType(Boolean.class, visit(ast.getRight()));
                return Environment.create(left && right);
            }
            else if (ast.getOperator().equals("OR")) {
                try {
                    boolean left = requireType(Boolean.class, visit(ast.getLeft()));
                    boolean right = requireType(Boolean.class, visit(ast.getRight()));
                    return Environment.create(left || right);
                }
                finally {
                    boolean left = requireType(Boolean.class, visit(ast.getLeft()));
                    return Environment.create(left);
                }
            }
        }
        else if (ast.getOperator().equals("<") || ast.getOperator().equals("<=") || ast.getOperator().equals(">") || ast.getOperator().equals(">=")) {
            Comparable cmp1 = requireType(Comparable.class, visit(ast.getLeft()));
            Comparable cmp2 = requireType(cmp1.getClass(), visit(ast.getRight()));
            if (ast.getOperator().equals("<")) {
                if (cmp1.compareTo(cmp2) < 0)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
            else if (ast.getOperator().equals("<=")) {
                if (cmp1.compareTo(cmp2) <= 0)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
            else if (ast.getOperator().equals(">")) {
                if (cmp1.compareTo(cmp2) > 0)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
            else if (ast.getOperator().equals(">=")) {
                if (cmp1.compareTo(cmp2) >= 0)
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
        }
        else if (ast.getOperator().equals("==") || ast.getOperator().equals("!=")) {
            Object obj1 = requireType(Object.class, visit(ast.getLeft()));
            Object obj2 = requireType(Object.class, visit(ast.getRight()));
            if (ast.getOperator().equals("==")) {
                if (obj1.equals(obj2))
                    return Environment.create(true);
                else
                    return Environment.create(false);
            }
            else if (ast.getOperator().equals("!=")) {
                if (obj1.equals(obj2))
                    return Environment.create(false);
                else
                    return Environment.create(true);
            }
        }
        else if (ast.getOperator().equals("+")) {
            Environment.PlcObject obj = visit(ast.getLeft());
            if (obj.getValue().getClass().equals(String.class)) {
                String left = requireType(String.class, visit(ast.getLeft()));
                String right = requireType(left.getClass(), visit(ast.getRight()));
                return Environment.create(left + right);
            }
            else if (obj.getValue().getClass().equals(BigInteger.class)) {
                BigInteger left = requireType(BigInteger.class, visit(ast.getLeft()));
                BigInteger right = requireType(left.getClass(), visit(ast.getRight()));
                return Environment.create(left.add(right));
            }
            else if (obj.getValue().getClass().equals(BigDecimal.class)) {
                BigDecimal left = requireType(BigDecimal.class, visit(ast.getLeft()));
                BigDecimal right = requireType(left.getClass(), visit(ast.getRight()));
                return Environment.create(left.add(right));
            }
            else {
                throw new RuntimeException();
            }
        }
        else if (ast.getOperator().equals("-") || ast.getOperator().equals("*")) {
            Environment.PlcObject obj = visit(ast.getLeft());
            if (obj.getValue().getClass().equals(BigInteger.class)) {
                BigInteger left = requireType(BigInteger.class, visit(ast.getLeft()));
                BigInteger right = requireType(left.getClass(), visit(ast.getRight()));
                if (ast.getOperator().equals("-"))
                    return Environment.create(left.subtract(right));
                else if (ast.getOperator().equals("*"))
                    return Environment.create(left.multiply(right));
            }
            else if (obj.getValue().getClass().equals(BigDecimal.class)) {
                BigDecimal left = requireType(BigDecimal.class, visit(ast.getLeft()));
                BigDecimal right = requireType(BigDecimal.class, visit(ast.getRight()));
                if (ast.getOperator().equals("-"))
                    return Environment.create(left.subtract(right));
                else if (ast.getOperator().equals("*"))
                    return Environment.create(left.multiply(right));
            }
            else {
                throw new RuntimeException();
            }
        }
        else if (ast.getOperator().equals("/")) {
            Environment.PlcObject obj = visit(ast.getLeft());
            if (obj.getValue().getClass().equals(BigInteger.class)) {
                BigInteger left = requireType(BigInteger.class, visit(ast.getLeft()));
                BigInteger right = requireType(left.getClass(), visit(ast.getRight()));
                if (right.equals(0))
                    throw new RuntimeException();
                return Environment.create(left.divide(right));
            }
            else if (obj.getValue().getClass().equals(BigDecimal.class)) {
                BigDecimal left = requireType(BigDecimal.class, visit(ast.getLeft()));
                BigDecimal right = requireType(BigDecimal.class, visit(ast.getRight()));
                if (right.equals(0))
                    throw new RuntimeException();
                MathContext mc = new MathContext(1, RoundingMode.HALF_EVEN);
                return Environment.create(left.divide(right, mc));
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent()) {
            Environment.PlcObject obj = visit(ast.getReceiver().get());
            return obj.getField(ast.getName()).getValue();
        }
        else {
            return scope.lookupVariable(ast.getName()).getValue();
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Function ast) {
        List<Environment.PlcObject> argList = new ArrayList<>();
        for (int i = 0; i < ast.getArguments().size(); i++) {
            argList.add(visit(ast.getArguments().get(i)));
        }
        if (ast.getReceiver().isPresent())  {
            Environment.PlcObject obj = visit(ast.getReceiver().get());
            return obj.callMethod(ast.getName(), argList);
        }
        else {
            Environment.PlcObject obj = new Environment.PlcObject(scope, ast.getName());
            Environment.Function func = scope.lookupFunction(ast.getName(), argList.size());
            return Environment.create(func.invoke(argList).getValue());
        }
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
