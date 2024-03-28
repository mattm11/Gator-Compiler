package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have its own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        // field* method*
        List<Ast.Source.Field> fields = new ArrayList<>();
        List<Ast.Source.Method> methods = new ArrayList<>();

        while (peek("LET"))
            fields.add(parseField());
        while (peek("DEF"))
            methods.add(parseMethod());

        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {
        // 'LET' identifier ':' identifier ('=' expression)? ';'
        match("LET");

        if (!match(Token.Type.IDENTIFIER))
            throw new ParseException("Missing Identifier", tokens.get(-1).getIndex());
        String name = tokens.get(-1).getLiteral();

        if (!match(":"))
            throw new ParseException("Missing Colon", tokens.get(-1).getIndex());

        if (!match(Token.Type.IDENTIFIER))
            throw new ParseException("Missing Second Identifier", tokens.get(-1).getIndex());
        String type = tokens.get(-1).getLiteral();

        Optional<Ast.Expr> expr = Optional.empty();
        if (match("=")) {
            expr = Optional.of(parseExpression());
        }

        if (!match(";"))
            throw new ParseException("Missing Semicolon", tokens.get(-1).getIndex());

        return new Ast.Field(name, type, expr);

    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        // 'DEF' identifier '(' (identifier ':' identifier (',' identifier ':' identifier)*)? ')' (':' identifier)? 'DO' statement* 'END'
        List<String> identifiers = new ArrayList<>();
        List<String> typeNames = new ArrayList<>();
        List<Ast.Stmt> stmts = new ArrayList<>();
        match("DEF");

        if (!match(Token.Type.IDENTIFIER))
            throw new ParseException("Missing Identifier", tokens.get(-1).getIndex());
        String name = tokens.get(-1).getLiteral();

        if (!match("("))
            throw new ParseException("Missing Opening Parenthesis", tokens.get(-1).getIndex());

        if (match(Token.Type.IDENTIFIER)) {
            identifiers.add(tokens.get(-1).getLiteral());
            if (!match(":"))
                throw new ParseException("Missing Colon", tokens.get(-1).getIndex());
            if (!match(Token.Type.IDENTIFIER))
                throw new ParseException("Missing Type Identifier", tokens.get(-1).getIndex());
            typeNames.add(tokens.get(-1).getLiteral());
            if (match(",")) {
                while (match(",")) {
                    if (!match(Token.Type.IDENTIFIER))
                        throw new ParseException("Missing Identifier", tokens.get(-1).getIndex());
                    identifiers.add(tokens.get(-1).getLiteral());
                    if (!match(":"))
                        throw new ParseException("Missing Colon", tokens.get(-1).getIndex());
                    if (!match(Token.Type.IDENTIFIER))
                        throw new ParseException("Missing Type Identifier", tokens.get(-1).getIndex());
                    typeNames.add(tokens.get(-1).getLiteral());
                }
            }
        }

        if (!match(")"))
            throw new ParseException("Missing Closing Parenthesis", tokens.get(-1).getIndex());

        Optional<String> returnType = Optional.empty();
        if (match(":")) {
            if (!match(Token.Type.IDENTIFIER))
                throw new ParseException("Missing Type Identifier", tokens.get(-1).getIndex());
            returnType = Optional.of(tokens.get(-1).getLiteral());
        }

        if (!match("DO"))
            throw new ParseException("Missing DO", tokens.get(-1).getIndex());

        while (!peek("END"))
            stmts.add(parseStatement());

        return new Ast.Method(name, identifiers, typeNames, returnType, stmts);

    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Stmt parseStatement() throws ParseException {
        if (peek("LET")) {
            return parseDeclarationStatement();
        }
        else if (peek("IF")) {
            return parseIfStatement();
        }
        else if (peek("FOR")) {
            return parseForStatement();
        }
        else if (peek("WHILE")) {
            return parseWhileStatement();
        }
        else if (peek("RETURN")) {
            return parseReturnStatement();
        }
        else {
            Ast.Stmt.Expr expr1 = parseExpression();
            if (match("=")) {
                Ast.Stmt.Expr expr2 = parseExpression();
                if (!match(";"))
                    throw new ParseException("Missing Semicolon", tokens.get(-1).getIndex());
                return new Ast.Stmt.Assignment(expr1, expr2);
            }
            if (!match(";"))
                throw new ParseException("Missing Semicolon", tokens.get(-1).getIndex());

            return new Ast.Stmt.Expression(expr1);
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Stmt.Declaration parseDeclarationStatement() throws ParseException {
        // 'LET' identifier (':' identifier)? ('=' expression)? ';'
        match("LET");
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected Identifier.", tokens.get(-1).getIndex());
        }
        String name = tokens.get(-1).getLiteral();

        Optional<String> typeName = Optional.empty();
        if (match(":")) {
            if (!match(Token.Type.IDENTIFIER))
                throw new ParseException("Expected Token Identifier.", tokens.get(-1).getIndex());
            typeName = Optional.of(tokens.get(-1).getLiteral());
        }

        Optional<Ast.Expr> expr = Optional.empty();
        if (match("=")) {
            expr = Optional.of(parseExpression());
        }

        if (!match(";")) {
            throw new ParseException("Expected semicolon.", tokens.get(-1).getIndex());
        }

        return new Ast.Stmt.Declaration(name, typeName, expr);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Stmt.If parseIfStatement() throws ParseException {
        // 'IF' expression 'DO' statement* ('ELSE' statement*)? 'END'
        match("IF");
        Ast.Expr expr = parseExpression();
        if (!match("DO"))
            throw new ParseException("Missing DO String", tokens.get(-1).getIndex());

        List<Ast.Stmt> thenStmts = new ArrayList<>();
        while (!peek("ELSE") && !peek("END")) {
            thenStmts.add(parseStatement());
        }
        List<Ast.Stmt> elseStmts = new ArrayList<>();
        if (match("ELSE")) {
            while (!peek("END")) {
                elseStmts.add(parseStatement());
            }
        }

        if (!match("END"))
            throw new ParseException("Missing END", tokens.get(-1).getIndex());

        return new Ast.Stmt.If(expr, thenStmts, elseStmts);
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() throws ParseException {
        // 'FOR' identifier 'IN' expression 'DO' statement* 'END'
        match("FOR");
        if (!match(Token.Type.IDENTIFIER))
            throw new ParseException("Missing Identifier", tokens.get(-1).getIndex());
        String name = tokens.get(-1).getLiteral();

        if (!match("IN"))
            throw new ParseException("Missing IN", tokens.get(-1).getIndex());
        Ast.Stmt.Expr expr = parseExpression();

        if (!match("DO"))
            throw new ParseException("Missing DO", tokens.get(-1).getIndex());

        List<Ast.Stmt> stmts = new ArrayList<>();
        while (!peek("END"))
            stmts.add(parseStatement());

        if (!match("END"))
            throw new ParseException("Missing END", tokens.get(-1).getIndex());

        return new Ast.Stmt.For(name, expr, stmts);

    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Stmt.While parseWhileStatement() throws ParseException {
        // 'WHILE' expression 'DO' statement* 'END'
        match("WHILE");
        Ast.Stmt.Expr expr = parseExpression();

        if (!match("DO"))
            throw new ParseException("Missing DO", tokens.get(-1).getIndex());

        List<Ast.Stmt> stmts = new ArrayList<>();
        while (!peek("END")) {
            stmts.add(parseStatement());
        }

        if (!match("END"))
            throw new ParseException("Missing END", tokens.get(-1).getIndex());

        return new Ast.Stmt.While(expr, stmts);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Stmt.Return parseReturnStatement() throws ParseException {
        match("RETURN");

        Ast.Stmt.Expr expr = parseExpression();

        if (!match(";"))
            throw new ParseException("Missing Semicolon", tokens.get(-1).getIndex());

        return new Ast.Stmt.Return(expr);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expr parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expr parseLogicalExpression() throws ParseException {
        Ast.Expr left = parseEqualityExpression();
        String operator;
        if (match("AND")) {
            operator = "AND";
            return new Ast.Expr.Binary(operator, left, parseEqualityExpression());
        }
        else if (match("OR")){
            operator = "OR";
            return new Ast.Expr.Binary(operator, left, parseEqualityExpression());
        }

        return left;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() throws ParseException {
        Ast.Expr left = parseAdditiveExpression();
        String operator;
        if (match("<=")) {
            operator = "<=";
            return new Ast.Expr.Binary(operator, left, parseAdditiveExpression());
        }
        else if (match(">=")) {
            operator = ">=";
            return new Ast.Expr.Binary(operator, left, parseAdditiveExpression());
        }
        else if (match("==")) {
            operator = "==";
            return new Ast.Expr.Binary(operator, left, parseAdditiveExpression());
        }
        else if (match("!=")) {
            operator = "!=";
            return new Ast.Expr.Binary(operator, left, parseAdditiveExpression());
        }
        else if (match("<")) {
            operator = "<";
            return new Ast.Expr.Binary(operator, left, parseAdditiveExpression());
        }
        else if (match(">")) {
            operator = ">";
            return new Ast.Expr.Binary(operator, left, parseAdditiveExpression());
        }

        return left;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expr parseAdditiveExpression() throws ParseException {
        Ast.Expr left = parseMultiplicativeExpression();
        String operator;
        if (match("+")) {
            operator = "+";
            return new Ast.Expr.Binary(operator, left, parseMultiplicativeExpression());
        }
        else if (match("-")) {
            operator = "-";
            return new Ast.Expr.Binary(operator, left, parseMultiplicativeExpression());
        }

        return left;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() throws ParseException {
        Ast.Expr left = parseSecondaryExpression();
        String operator;
        if (match("*")) {
            operator = "*";
            return new Ast.Expr.Binary(operator, left, parseSecondaryExpression());
        }
        else if (match("/")) {
            operator = "/";
            return new Ast.Expr.Binary(operator, left, parseSecondaryExpression());
        }

        return left;

    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expr parseSecondaryExpression() throws ParseException {
        Ast.Expr expr = parsePrimaryExpression();
        List<Ast.Expr> exprList = new ArrayList<>();
        while (match(".")) {
            if (!match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Identifier missing", tokens.get(-1).getIndex());
            }
            else {
                String name = tokens.get(-1).getLiteral();
                if (match("(")) {
                    boolean hasMore;
                    if (!match(")"))
                        hasMore = true;
                    else
                        return new Ast.Expr.Function(Optional.of(expr), name, exprList);
                    while (hasMore) {
                        exprList.add(parseExpression());
                        if (match(",")) {
                            if (match(")"))
                                throw new ParseException("Trailing Comma", tokens.get(-1).getIndex());
                            exprList.add(parseExpression());
                        }
                        else {
                            hasMore = false;
                            if (!match(")"))
                                throw new ParseException("Missing Closing Parenthesis", tokens.get(-1).getIndex());
                            return new Ast.Expr.Function(Optional.of(expr), name, exprList);
                        }
                    }
                }
                else {
                    return new Ast.Expr.Access(Optional.of(expr), name);
                }
                return new Ast.Expr.Access(Optional.empty(), name);
            }
        }
        return expr;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expr parsePrimaryExpression() throws ParseException {
        if (match("NIL")) {
            return new Ast.Expr.Literal(null);
        }
        else if (match("TRUE")) {
            return new Ast.Expr.Literal(true);
        }
        else if (match("FALSE")) {
            return new Ast.Expr.Literal(false);
        }
        else if (match(Token.Type.INTEGER)) {
            String x = tokens.get(-1).getLiteral();
            return new Ast.Expr.Literal(new BigInteger(x));
        }
        else if (match(Token.Type.DECIMAL)) {
            String x = tokens.get(-1).getLiteral();
            return new Ast.Expr.Literal(new BigDecimal(x));
        }
        else if (match(Token.Type.CHARACTER)) {
            String x = tokens.get(-1).getLiteral();
            x = x.replace("\'", "");
            if (x.contains("\\b"))
                x = x.replace("\\b", "\b");
            if (x.contains("\\n"))
                x = x.replace("\\n", "\n");
            if (x.contains("\\r"))
                x = x.replace("\\r", "\r");
            if (x.contains("\\t"))
                x = x.replace("\\t", "\t");
            if (x.contains("\\\'"))
                x = x.replace("\\\'", "\'");
            if (x.contains("\\\""))
                x = x.replace("\\\"", "\"");
            if (x.contains("\\\\"))
                x = x.replace("\\\\", "\\");
            char y = x.charAt(0);
            return new Ast.Expr.Literal(y);
        }
        else if (match(Token.Type.STRING)) {
            String x = tokens.get(-1).getLiteral();
            x = x.substring(1, x.length() - 1);
            if (x.contains("\\b"))
                x = x.replace("\\b", "\b");
            if (x.contains("\\n"))
                x = x.replace("\\n", "\n");
            if (x.contains("\\r"))
                x = x.replace("\\r", "\r");
            if (x.contains("\\t"))
                x = x.replace("\\t", "\t");
            if (x.contains("\\\'"))
                x = x.replace("\\\'", "\'");
            if (x.contains("\\\""))
                x = x.replace("\\\"", "\"");
            if (x.contains("\\\\"))
                x = x.replace("\\\\", "\\");
            return new Ast.Expr.Literal(x);
        }
        else if (match("(")) {
            Ast.Expr expr = parseExpression();
            if (!match(")"))
                throw new ParseException("Missing Closing Parenthesis", tokens.get(-1).getIndex());
            return new Ast.Expr.Group(expr);
        }
        else if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            List<Ast.Expr> exprList = new ArrayList<>();
            if (match("(")) {
                boolean hasMore;
                if (!match(")"))
                    hasMore = true;
                else
                    return new Ast.Expr.Function(Optional.empty(), name, exprList);
                while (hasMore) {
                    exprList.add(parseExpression());
                    while (match(",")) {
                        exprList.add(parseExpression());
                    }
                    hasMore = false;
                    if (!match(")"))
                        throw new ParseException("Missing Closing Parenthesis", tokens.get(-1).getIndex());
                    return new Ast.Expr.Function(Optional.empty(), name, exprList);
                }
            }
            return new Ast.Expr.Access(Optional.empty(), name);
        }
        else {
            throw new ParseException("Unexpected Primary Expression", tokens.get(-1).getIndex());
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            } else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            } else {
                throw new AssertionError("Invalid pattern objectL " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);

        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
