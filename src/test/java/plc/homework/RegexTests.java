package plc.homework;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Contains JUnit tests for {@link Regex}. A framework of the test structure 
 * is provided, you will fill in the remaining pieces.
 *
 * To run tests, either click the run icon on the left margin, which can be used
 * to run all tests or only a specific test. You should make sure your tests are
 * run through IntelliJ (File > Settings > Build, Execution, Deployment > Build
 * Tools > Gradle > Run tests using <em>IntelliJ IDEA</em>). This ensures the
 * name and inputs for the tests are displayed correctly in the run window.
 */
public class RegexTests {

    /**
     * This is a parameterized test for the {@link Regex#EMAIL} regex. The
     * {@link ParameterizedTest} annotation defines this method as a
     * parameterized test, and {@link MethodSource} tells JUnit to look for the
     * static method {@link #testEmailRegex()}.
     *
     * For personal preference, I include a test name as the first parameter
     * which describes what that test should be testing - this is visible in
     * IntelliJ when running the tests (see above note if not working).
     */
    @ParameterizedTest
    @MethodSource
    public void testEmailRegex(String test, String input, boolean success) {
        test(input, Regex.EMAIL, success);
    }

    /**
     * This is the factory method providing test cases for the parameterized
     * test above - note that it is static, takes no arguments, and has the same
     * name as the test. The {@link Arguments} object contains the arguments for
     * each test to be passed to the function above.
     */
    public static Stream<Arguments> testEmailRegex() {
        return Stream.of(
                Arguments.of("Alphanumeric", "thelegend27@gmail.com", true),
                Arguments.of("UF Domain", "otherdomain@ufl.edu", true),
                Arguments.of("Dash Username", "someuser@gmail.com", true),
                Arguments.of("Period Username", "matt.user@gmail.com", true),
                Arguments.of("Underscore Username", "matt_user@gmail.com", true),
                Arguments.of("Short Username", "m@gmail.com", true),
                Arguments.of("Long Username", "reallyreallylonguser@gmail.com", true),
                Arguments.of("Capitalization Username", "Hey@gmail.com", true),
                Arguments.of("Two letter Domain", "someusername@gmail.io", true),
                Arguments.of("No Website Characters", "username@.com", true),
                Arguments.of("Missing Domain Dot", "missingdot@gmailcom", false),
                Arguments.of("Symbols", "symbols#$%@gmail.com", false),
                Arguments.of("No Username", "@gmail.com", false),
                Arguments.of("Missing @ Character", "usernamegmail.com", false),
                Arguments.of("Short Domain", "username@gmail.o", false),
                Arguments.of("Long Domain", "username@gmail.nets", false),
                Arguments.of("Invalid Domain", "username@gmail.A7!", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testEvenStringsRegex(String test, String input, boolean success) {
        test(input, Regex.EVEN_STRINGS, success);
    }

    public static Stream<Arguments> testEvenStringsRegex() {
        return Stream.of(
                //what has ten letters and starts with gas?
                Arguments.of("10 Characters", "automobile", true),
                Arguments.of("12 Characters", "twelveletter", true),
                Arguments.of("14 Characters", "i<3pancakes10!", true),
                Arguments.of("16 Characters", "16characterslol!", true),
                Arguments.of("18 Characters", "wow18characters123", true),
                Arguments.of("20 Characters", "ThisIsALotOfLetters.", true),
                Arguments.of("Spaces", "auto mobiles", true),
                Arguments.of("0 Characters", "", false),
                Arguments.of("6 Characters", "6chars", false),
                Arguments.of("7 Characters", "7chars!", false),
                Arguments.of("13 Characters", "i<3pancakes9!", false),
                Arguments.of("22 Characters", "Its hard to count this", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testIntegerListRegex(String test, String input, boolean success) {
        test(input, Regex.INTEGER_LIST, success);
    }

    public static Stream<Arguments> testIntegerListRegex() {
        return Stream.of(
                Arguments.of("No Element", "[]", true),
                Arguments.of("Single Element", "[1]", true),
                Arguments.of("Multiple Elements", "[1,2,3]", true),
                Arguments.of("Multiple with Spaces", "[1, 2, 3]", true),
                Arguments.of("Some Followed by Spaces", "[1, 2,3]", true),
                Arguments.of("Trailing Zeros", "[10, 11]", true),
                Arguments.of("Missing Brackets", "1,2,3", false),
                Arguments.of("Missing Commas", "[1 2 3]", false),
                Arguments.of("Ending Comma", "[1,2,]", false),
                Arguments.of("Multiple Commas", "[1,,2]", false),
                Arguments.of("Number followed by Space", "[1 ,2,3]", false),
                Arguments.of("Number lower than 1", "[0,1,2]", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testNumberRegex(String test, String input, boolean success) {
        test(input, Regex.NUMBER, success);
    }

    public static Stream<Arguments> testNumberRegex() {
        return Stream.of(
                Arguments.of("One Number", "1", true),
                Arguments.of("Decimal Number", "1.0", true),
                Arguments.of("Positive Symbol Number", "+1", true),
                Arguments.of("Positive Symbol Number leading zero", "+0.1", true),
                Arguments.of("Negative Symbol Number", "-1", true),
                Arguments.of("Negative Symbol Number leading zero", "-0.1", true),
                Arguments.of("Leading Zeros", "001", true),
                Arguments.of("Large Number", "123456", true),
                Arguments.of("Large Decimal Number", "12345.67890", true),
                Arguments.of("Ending Period", "1.", false),
                Arguments.of("Starting Period", ".1", false),
                Arguments.of("Starting Positive Period", "+.1", false),
                Arguments.of("Starting Negative Period", "-.1", false),
                Arguments.of("Both Symbols", "+-1", false),
                Arguments.of("Multiple Periods", "1..0", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testStringRegex(String test, String input, boolean success) {
        test(input, Regex.STRING, success);
    }

    public static Stream<Arguments> testStringRegex() {
        return Stream.of(
                Arguments.of("Empty String", "\"\"", true),
                Arguments.of("Hello World String", "\"Hello, World!\"", true),
                Arguments.of("Backslash bnrt", "\"1\\t2\"", true),
                Arguments.of("Backslash single quotes", "\"It\\'s\"", true),
                Arguments.of("Backslash double quotes", "\"What does \\\"NCAA\\\" stand for?\"", true),
                Arguments.of("Backslash Backslash", "\"\\\\ is a backslash while / is a front slash\"", true),
                Arguments.of("No Closing Quotes", "\"unterminated", false),
                Arguments.of("Invalid Escape", "\"invalid\\escape\"", false),
                Arguments.of("No Opening Quotes", "Wow!\"", false),
                Arguments.of("Uneven Quotes", "\"\"\"", false),
                Arguments.of("String Inproper String Closing", "\"Hello\".", false)
        );
    }

    /**
     * Asserts that the input matches the given pattern. This method doesn't do
     * much now, but you will see this concept in future assignments.
     */
    private static void test(String input, Pattern pattern, boolean success) {
        Assertions.assertEquals(success, pattern.matcher(input).matches());
    }

}
