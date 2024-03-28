package plc.project;

import jdk.jfr.internal.tool.Main;

import java.io.PrintWriter;
import java.math.BigDecimal;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        Main intMain = new Main();


        // class header, including opening brace
        print("public class Main {");
        newline(0);
        newline(++indent);

        // the source's fields
        for (int i = 0; i < ast.getFields().size(); i++) {
            print(ast.getFields().get(i));
            newline(indent);
            if (i == ast.getFields().size() - 1)
                newline(indent);
        }

        // java's main method
        print("public static void main(String[] args) {");
        newline(++indent);
        print("System.exit(new Main().main());");
        newline(--indent);
        print("}");

        newline(0);
        newline(indent);

        // the source's methods
        for (int i = 0; i < ast.getMethods().size(); i++) {
            print(ast.getMethods().get(i));
            newline(0);
            if (i == ast.getMethods().size() - 1)
                newline(--indent);
        }

        print("}");


        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        print(ast.getTypeName(), " ");
        print(ast.getVariable().getName());

        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue());
        }

        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        print(ast.getFunction().getReturnType().getJvmName(), " ");
        print(ast.getFunction().getName(), "(");

        for (int i = 0; i < ast.getParameters().size(); i++) {
            print(ast.getParameterTypeNames().get(i), " ");
            print(ast.getParameters().get(i));
            if (i != ast.getParameters().size() - 1)
                print(", ");
        }
        print(") {");
        if (ast.getStatements().isEmpty())
            print("}");
        else {
            newline(++indent);
            for (int i = 0; i < ast.getStatements().size(); i++) {
                print(ast.getStatements().get(i));
                if (i != ast.getStatements().size() - 1)
                    newline(indent);
                else
                    newline(--indent);
            }
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        print(ast.getExpression(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());

        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        print(ast.getReceiver());
        print(" = ");
        print(ast.getValue());
        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        print("if (", ast.getCondition(), ") {");
        newline(++indent);

        for (int i = 0; i < ast.getThenStatements().size(); i++) {
            print(ast.getThenStatements().get(i));
            if (i == ast.getThenStatements().size() - 1)
                newline(--indent);
            else
                newline(indent);
        }
        print("}");

        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            newline(++indent);

            for (int i = 0; i < ast.getElseStatements().size(); i++) {
                print(ast.getElseStatements().get(i));
                if (i == ast.getThenStatements().size() - 1)
                    newline(--indent);
                else
                    newline(indent);
            }
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        print("for (");
        print(ast.getValue().getType(), " ");
        print(ast.getName(), " : ");
        print(ast.getValue(), ")");
        newline(0);
        print("{");

        newline(++indent);
        for (int i = 0; i < ast.getStatements().size(); i++) {
            print(ast.getStatements().get(i));
        }
        newline(indent);

        newline(--indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        print("while (", ast.getCondition(), ") {");

        if (!ast.getStatements().isEmpty()) {
            newline(++indent);
            for (int i = 0; i < ast.getStatements().size(); i++) {
                if (i != 0) {
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        print("return ");

        print(ast.getValue());

        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        if (ast.getLiteral() instanceof String || ast.getLiteral() instanceof Character) {
            print("\"");
            print(ast.getLiteral());
            print("\"");
        }
        else if (ast.getLiteral() instanceof BigDecimal) {
            print(new BigDecimal(((BigDecimal) ast.getLiteral()).toString()));
        }
        else
            print(ast.getLiteral());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        print("(");
        print(ast.getExpression());
        print(")");

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        print(ast.getLeft());
        if (ast.getOperator().equals("AND"))
            print(" && ");
        else if (ast.getOperator().equals("OR"))
            print(" || ");
        else
            print(" " + ast.getOperator() + " ");
        print(ast.getRight());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent()) {
            print(ast.getReceiver().get());
            print(".");
        }
        print(ast.getVariable().getJvmName());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        if (ast.getReceiver().isPresent()) {
            print(ast.getReceiver().get());
            print(".");
        }

        print(ast.getFunction().getJvmName());
        print("(");

        for (int i = 0; i < ast.getArguments().size(); i++) {
            print(ast.getArguments().get(i));
            if (i != ast.getArguments().size() - 1)
                print(", ");
        }
        print(")");

        return null;
    }

}
