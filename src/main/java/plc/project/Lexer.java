package plc.project;

import java.util.List;
import java.util.ArrayList;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the character which is
 * invalid or missing.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are
 * helpers you need to use, they will make the implementation a lot easier.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        List<Token> tokens = new ArrayList<Token>();
        // As long as peek finds any character
        while (peek(".")) {
            Token token = new Token(null, null, 0);
            // Skip any whitespace
            if (peek("[ \b\n\r\t]")) {
                match("[ \b\n\r\t]");
                chars.skip();
            }
            else {
                // Begin lexxing
                token = lexToken();
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        Token token = new Token(null, null, chars.index);
        if (peek("[A-Za-z_][A-Za-z0-9_-]*"))
            token = lexIdentifier();
        else if (peek("([+-]?)|[0-9]+(.[0-9]+)?"))
            token = lexNumber();
        else if (peek("[\']"))
            token = lexCharacter();
        else if (peek("[\"]"))
            token = lexString();
        else if (peek("\\\\[bnrt'\"\\\\]"))
            lexEscape();
        else if (peek("([<>!=]=)|."))
            token = lexOperator();
        else
            throw new ParseException(chars.input, chars.index);
        return token;
    }

    public Token lexIdentifier() {
        match("[A-Za-z_]");
        while (match("[A-Za-z0-9_-]"));
        return chars.emit(Token.Type.IDENTIFIER);
    }

    public Token lexNumber() {
        // When there is only a plus or minus sign
        if (peek("[+-]")) {
            match("[+-]");
            if (!chars.has(chars.index) || peek(" ")) {
                return chars.emit(Token.Type.OPERATOR);
            }
        }
        // Match sign and integer
        match("[+-]?");
        while (match("[0-9]"));
        // Match in the case of a decimal
        if (peek("[.]")) {
            match("[.]");
            if (!peek("[0-9]"))
                throw new ParseException(chars.input, chars.index);
            while (match("[0-9]"));
            return chars.emit(Token.Type.DECIMAL);
        }
        return chars.emit(Token.Type.INTEGER);
    }

    public Token lexCharacter() {
        match("\'");
        // Occurrence of backslash
        if (peek("\\\\", "[bnrt\'\"]", "\'"))
            match("\\\\", "[bnrt\'\"]", "\'");
        // No occurrence of backslash
        else if (peek("[^\']", "\'"))
            match("[^\']", "\'");
        else
            throw new ParseException(chars.input, chars.index);
        return chars.emit(Token.Type.CHARACTER);
    }

    public Token lexString() {
        match("\"");
        // If it is an empty string
        if (peek("\"")) {
            match("\"");
            return chars.emit(Token.Type.STRING);
        }
        // Not an empty string
        else {
            while (peek("[^\"]")) {
                // If there is a backslash, make sure it is valid
                if (peek("\\\\")) {
                    if (!peek("\\\\", "[bnrt\'\"\\\\]"))
                        throw new ParseException(chars.input, chars.index);
                    else
                        match("\\\\", "[bnrt\'\"\\\\]");
                }
                // Match normal string conditions
                match("[^\"]");
            }
        }
        if (!peek("\""))
            throw new ParseException(chars.input, chars.index);
        match("\"");
        return chars.emit(Token.Type.STRING);
    }

    public void lexEscape() {
        throw new UnsupportedOperationException();
    }

    public Token lexOperator() {
        // Special cases for comparators
        if (peek("[<>!=]", "="))
            match("[<>!=]", "=");
        else
            // Anything else left (only operators and special symbols
            match("[^ \b\n\r\t]");
        return chars.emit(Token.Type.OPERATOR);
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                chars.advance();
            }
        }
        return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}
