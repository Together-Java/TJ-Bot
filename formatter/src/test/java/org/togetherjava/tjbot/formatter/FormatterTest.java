package org.togetherjava.tjbot.formatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FormatterTest {
    private Formatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new Formatter();
    }

    private static Stream<Arguments> provideFormatTests() {
        return Stream.of(Arguments.of("basic", """
                public static
                void main ( String [ ]args){ System.out. println( "Hello World!"      );}""", """
                public static void main(String[] args) {
                  System.out.println("Hello World!");
                }"""), Arguments.of("basic with comment", """
                // Your First Program

                class HelloWorld { public static void main(String[] args) {
                        System.out.println(  "Hello, World!"    )    ;
                 }}""", """
                // Your First Program
                class HelloWorld {
                  public static void main(String[] args) {
                    System.out.println("Hello, World!");
                  }
                }"""), Arguments.of("compact",
                "List<String>input=new ArrayList<>();List<String> result=new ArrayList<>();for(String s:input){result.add(s.trim());}return result;",
                """
                        List<String> input = new ArrayList<>();
                        List<String> result = new ArrayList<>();
                        for (String s : input) {
                          result.add(s.trim());
                        }
                        return result;"""),
                Arguments.of("nested unclosed ifs", "if(){if(){if(){if(){", """
                        if () {
                          if () {
                            if () {
                              if () {"""),
                Arguments.of("while with too many closed braces", "while(if()e){}}", """
                        while (if () e) {
                        }
                        }"""), Arguments.of("else if", "if(1){}else if(2){}else{}", """
                        if (1) {
                        }
                        else if (2) {
                        }
                        else {
                        }"""),
                Arguments.of("multi-arg method", "foo(1,2,3,b);", "foo(1, 2, 3, b);"),
                Arguments.of("method declaration", "void foo(int a, int b, Object c){}", """
                        void foo(int a, int b, Object c) {
                        }"""), Arguments.of("index for loop", "for(int i=0;i<3;i++){}", """
                        for (int i = 0; i < 3; i++) {
                        }"""),
                Arguments.of("diamond operator", "List<String>list=new ArrayList < > ( ) ;",
                        "List<String> list = new ArrayList<>();"),
                Arguments.of("greater and less", "3<2&&2>3", "3 < 2 && 2 > 3"),
                Arguments.of("single line comment", """
                        // this is a comment
                        void foo() {}""", """
                        // this is a comment
                        void foo() {
                        }"""), Arguments.of("annotations", """
                        @MyAnnotation
                        void foo() {}""", """
                        @MyAnnotation
                        void foo() {
                        }"""), Arguments.of("lambdas", "()->{System.out.println(5);};", """
                        () -> {
                          System.out.println(5);
                        };"""),
                Arguments.of("lambda with params", "(s,e,d)->{System.out.println(5);};", """
                        (s, e, d) -> {
                          System.out.println(5);
                        };"""),
                Arguments.of("lambda single param", "s->{System.out.println(5);};", """
                        s -> {
                          System.out.println(5);
                        };"""),
                Arguments.of("lambda typed params",
                        "(String s,Object b)->{System.out.println(5);};", """
                                (String s, Object b) -> {
                                  System.out.println(5);
                                };"""),
                Arguments.of("ternary", "foo?bar:baz", "foo ? bar : baz"),
                Arguments.of("adjusts existing whitespaces", """
                        int   i3  = 4;
                        int   i_3   = 5;""", """
                        int i3 = 4;
                        int i_3 = 5;"""),
                Arguments.of("nested generics", "Map<String, Map<List<Person>, Integer>>",
                        "Map<String, Map<List<Person>, Integer>>"),
                Arguments.of("right shifts", "x >> 1, y >>> 1", "x >> 1, y >>> 1"),
                Arguments.of("minimized real code",
                        """
                                package org.togetherjava.tjbot.features.code;import com.google.common.collect.Range; \
                                import com.google.googlejavaformat.java.FormatterException; \
                                import com.google.googlejavaformat.java.JavaOutput;import com.google.googlejavaformat.java.Replacement; \
                                import com.google.googlejavaformat.java.SnippetFormatter;import net.dv8tion.jda.api.EmbedBuilder; \
                                import net.dv8tion.jda.api.entities.MessageEmbed;import org.togetherjava.tjbot.features.utils.CodeFence; \
                                import java.util.List;final class FormatCodeCommand implements CodeAction{private final SnippetFormatter formatter \
                                =new SnippetFormatter();@Override
                                public String getLabel(){return"Format";}@Override
                                public MessageEmbed apply(CodeFence codeFence){String formattedCode=formatCode(codeFence.code()); \
                                CodeFence formattedCodeFence=new CodeFence(codeFence.language(),formattedCode); \
                                return new EmbedBuilder().setTitle("Formatted code").setDescription(formattedCodeFence.toMarkdown()) \
                                .setColor(CodeMessageHandler.AMBIENT_COLOR).build();}
                                private String formatCode(String code){for(SnippetFormatter.SnippetKind snippetKind: \
                                SnippetFormatter.SnippetKind.values()){try{List<Replacement>replacements= \
                                formatter.format(snippetKind,code,List.of(Range.closedOpen(0,code.length())),2,false); \
                                return JavaOutput.applyReplacements(code,replacements);}catch(FormatterException e){}}
                                return"nope...";}}""",
                        """
                                package org.togetherjava.tjbot.features.code;

                                import com.google.common.collect.Range;
                                import com.google.googlejavaformat.java.FormatterException;
                                import com.google.googlejavaformat.java.JavaOutput;
                                import com.google.googlejavaformat.java.Replacement;
                                import com.google.googlejavaformat.java.SnippetFormatter;
                                import net.dv8tion.jda.api.EmbedBuilder;
                                import net.dv8tion.jda.api.entities.MessageEmbed;
                                import org.togetherjava.tjbot.features.utils.CodeFence;
                                import java.util.List;

                                final class FormatCodeCommand implements CodeAction {
                                  private final SnippetFormatter formatter = new SnippetFormatter();
                                  @Override
                                  public String getLabel() {
                                    return "Format";
                                  }
                                  @Override
                                  public MessageEmbed apply(CodeFence codeFence) {
                                    String formattedCode = formatCode(codeFence.code());
                                    CodeFence formattedCodeFence = new CodeFence(codeFence.language(), formattedCode);
                                    return new EmbedBuilder().setTitle("Formatted code").setDescription(formattedCodeFence.toMarkdown()).setColor(CodeMessageHandler.AMBIENT_COLOR).build();
                                  }
                                  private String formatCode(String code) {
                                    for (SnippetFormatter.SnippetKind snippetKind : SnippetFormatter.SnippetKind.values()) {
                                      try {
                                        List<Replacement> replacements = formatter.format(snippetKind, code, List.of(Range.closedOpen(0, code.length())), 2, false);
                                        return JavaOutput.applyReplacements(code, replacements);
                                      } catch (FormatterException e) {
                                      }
                                    }
                                    return "nope...";
                                  }
                                }"""),
                Arguments.of("real code with javadoc and generics",
                        """
                                package org.togetherjava.tjbot.formatter.tokenizer;
                                import java.util.Set;
                                                    /**
                                 * Single token of a code, for example an opening-brace.
                                 * <p>
                                 * As example, the code {@code int x = "foo";} is split into tokens:
                                * <ul>
                                      * <li>("int", INT)</li>
                                               * <li>("x", IDENTIFIER)</li>
                                * <li>("=", ASSIGN)</li>
                                  <li>("\\"foo\\"", STRING)</li>
                                                <li>(";", SEMICOLON)</li>
                                 </ul>
                                 *
                                 * @param content the actual text contained in the token, e.g., an identifier like {@code x}
                                 * @param type the type of the token, e.g., IDENTIFIER
                                           */
                                public record Token(String content, TokenType type) {
                                  private static final Set<TokenType> DEBUG_SHOW_CONTENT_TYPES = Set.of(TokenType.IDENTIFIER, TokenType.UNKNOWN, TokenType.STRING, TokenType.SINGLE_LINE_COMMENT, TokenType.MULTI_LINE_COMMENT);
                                  @Override
                                  public String toString() {
                                    // For some types it helps debugging to also show the content
                                    String contentText = DEBUG_SHOW_CONTENT_TYPES.contains(type) ?"(%s)".formatted(content) : "";
                                    return type.name() + contentText;
                                  }
                                }""",
                        """
                                package org.togetherjava.tjbot.formatter.tokenizer;

                                import java.util.Set;

                                /**
                                 * Single token of a code, for example an opening-brace.
                                 * <p>
                                 * As example, the code {@code int x = "foo";} is split into tokens:
                                 * <ul>
                                 * <li>("int", INT)</li>
                                 * <li>("x", IDENTIFIER)</li>
                                 * <li>("=", ASSIGN)</li>
                                 <li>("\\"foo\\"", STRING)</li>
                                 <li>(";", SEMICOLON)</li>
                                 </ul>
                                 *
                                 * @param content the actual text contained in the token, e.g., an identifier like {@code x}
                                 * @param type the type of the token, e.g., IDENTIFIER
                                 */
                                public record Token(String content, TokenType type) {
                                  private static final Set<TokenType> DEBUG_SHOW_CONTENT_TYPES = Set.of(TokenType.IDENTIFIER, TokenType.UNKNOWN, TokenType.STRING, TokenType.SINGLE_LINE_COMMENT, TokenType.MULTI_LINE_COMMENT);
                                  @Override
                                  public String toString() {
                                    // For some types it helps debugging to also show the content
                                    String contentText = DEBUG_SHOW_CONTENT_TYPES.contains(type) ? "(%s)".formatted(content) : "";
                                    return type.name() + contentText;
                                  }
                                }"""),
                Arguments.of("real code with annotations arguments",
                        """
                                @StreamListener()
                                    @SendTo(EnrichmentKStreamProcessor.OUTPUT)
                                    public KStream<String, X> process(
                                            @Input(KStreamProcessor.INPUT) KStream<String, Data> X,
                                            @Input(KStreamProcessor.INPUT_ENRICH_TABLE) KTable<String, Map<String, Object>> Y) {

                                        return X
                                                .filter((s, x1) -> checkProductClassFilter(x1))
                                                .map((k, v) -> new KeyValue<>(v.getId(), v))
                                                .leftJoin(Y,
                                                        (x, y) -> enrichData(x, y),
                                                        Joined.with(Serdes.String(), XDataJsonSerde,jdbcMapJsonSerde ));
                                    }""",
                        """
                                @StreamListener() @SendTo(EnrichmentKStreamProcessor.OUTPUT) public KStream<String, X> \
                                process(@Input(KStreamProcessor.INPUT) KStream<String, Data> X, \
                                @Input(KStreamProcessor.INPUT_ENRICH_TABLE) KTable<String, Map<String, Object>> Y) {
                                  return X.filter((s, x1) -> checkProductClassFilter(x1)).map((k, v) -> new KeyValue<>(v.getId(), v))\
                                .leftJoin(Y, (x, y) -> enrichData(x, y), Joined.with(Serdes.String(), XDataJsonSerde, jdbcMapJsonSerde));
                                }"""),
                Arguments.of("c++", """
                        using namespace std;

                        int main()
                        {   \s
                            int divisor, dividend, quotient, remainder;

                            cout << "Enter dividend: ";
                            cin >> dividend;

                            cout << "Enter divisor: ";
                            cin >> divisor;

                            quotient = dividend / divisor;
                            remainder = dividend % divisor;

                            cout << "Quotient = " << quotient << endl;
                            cout << "Remainder = " << remainder;

                            return 0;
                        }""", """
                        using namespace std;
                        int main() {
                          int divisor, dividend, quotient, remainder;
                          cout << "Enter dividend: ";
                          cin >> dividend;
                          cout << "Enter divisor: ";
                          cin >> divisor;
                          quotient = dividend / divisor;
                          remainder = dividend % divisor;
                          cout << "Quotient = " << quotient << endl;
                          cout << "Remainder = " << remainder;
                          return 0;
                        }"""), Arguments.of("kotlin", """
                        fun main(args: Array<String>) {

                            val first = 1.5f
                            val second = 2.0f

                            val product = first * second

                            println("The product is: $product")
                        }""",
                        """
                                fun main(args : Array<String> ) {
                                  val first = 1.5fval second = 2.0fval product = first * second println("The product is: $product") }"""));
    }

    @ParameterizedTest
    @MethodSource("provideFormatTests")
    void format(String testName, String code, String expectedFormattedCode) {
        String actualFormattedCode = formatter.format(code);

        assertEquals(expectedFormattedCode, actualFormattedCode, testName);
    }
}
