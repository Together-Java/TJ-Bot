package org.togetherjava.tjbot.formatter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.togetherjava.tjbot.formatter.tokenizer.Lexer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FormatterTest {
    Lexer lexer;
    Formatter formatter;

    @BeforeAll
    void init() {
        lexer = new Lexer();
        formatter = new Formatter();
    }

    @Test
    @DisplayName("Format Test #1")
    void formatTest1() {
        assertEquals("""
                ```java
                public static void main(String[] args) {
                    System.out.println("Hello World!");
                }
                ```""", formatter.format(
                "public static\nvoid main ( String [ ]args){ System.out. println( \"Hello World!\"      );}",
                lexer));
    }

    @Test
    @DisplayName("Format Test #2")
    void formatTest2() {
        assertEquals("""
                ```java
                List<String> input = new ArrayList<>();
                List<String> result = new ArrayList<>();
                for (String s : input) {
                    result.add(s.trim());
                }
                return result;
                ```""", formatter.format(
                "List<String>input=new ArrayList<>();List<String> result=new ArrayList<>();for(String s:input){result.add(s.trim());}return result;",
                lexer));
    }

    @Test
    @DisplayName("Format Test #3")
    void formatTest3() {
        assertEquals("""
                ```java
                if () {
                    if () {
                        if () {
                            if () {
                ```""", formatter.format("if(){if(){if(){if(){", lexer));
    }

    @Test
    @DisplayName("Format Test #4")
    void formatTest4() {
        assertEquals("""
                ```java
                while (if () e) {
                }
                }
                ```""", formatter.format("while(if()e){}}", lexer));
    }

    @Test
    @DisplayName("Format Test #5")
    void formatTest5() {
        assertEquals("""
                ```java
                if (1) {
                }
                else if (2) {
                }
                else {
                }
                ```""", formatter.format("if(1){}else if(2){}else{}", lexer));
    }

    @Test
    @DisplayName("Multi-argument method invocation renders correctly")
    void testMultiArgMethodInvocation() {
        assertEquals("""
                ```java
                foo(1, 2, 3, b);
                ```""", formatter.format("foo(1,2,3,b);", lexer));
    }

    @Test
    @DisplayName("Method declaration renders correctly")
    void testMethodDeclaration() {
        assertEquals("""
                ```java
                void foo(int a, int b, Object c) {
                }
                ```""", formatter.format("void foo(int a, int b, Object c){}", lexer));
    }

    @Test
    @DisplayName("For int loop working correctly")
    void testForInt() {
        assertEquals("""
                ```java
                for (int i = 0; i < 3; i++) {
                }
                ```""", formatter.format("for(int i=0;i<3;i++){}", lexer));
    }

    @Test
    @DisplayName("Diamond operator rendering correctly")
    void testDiamondOperator() {
        assertEquals("""
                ```java
                List<String> list = new ArrayList<>();
                ```""", formatter.format("List<String>list=new ArrayList < > ( ) ;", lexer));
    }

    @Test
    @DisplayName("BIGGER and SMALLER rendering correctly")
    void testBiggerAndSmaller() {
        assertEquals("""
                ```java
                3 < 2 && 2 > 3
                ```""", formatter.format("3<2&&2>3", lexer));
    }

    @Test
    @DisplayName("Comment renders correctly")
    void testComment() {
        assertEquals("""
                ```java
                // this is a comment
                void foo() {
                }
                ```""", formatter.format("// this is a comment\nvoid foo() {}", lexer));
    }

    @Test
    @DisplayName("Annotation renders correctly")
    void testAnnotation() {
        assertEquals("""
                ```java
                @MyAnnotation
                void foo() {
                }
                ```""", formatter.format("@MyAnnotation\nvoid foo() {}", lexer));
    }

    @Test
    @DisplayName("Lambda renders correctly")
    void testLambda() {
        assertEquals("""
                ```java
                () -> {
                    System.out.println("Hello World");
                };
                ```""", formatter.format("()->{System.out.println(\"Hello World\");};", lexer));
    }

    @Test
    @DisplayName("Lambda with parameters renders correctly")
    void testLambdaParams() {
        assertEquals("""
                ```java
                (s, e, d) -> {
                    System.out.println("Hello World");
                };
                ```""",
                formatter.format("(s,e,d)->{System.out.println(\"Hello World\");};", lexer));
    }

    @Test
    @DisplayName("Lambda with parenthesis parameter renders correctly")
    void testLambdaNoParenParams() {
        assertEquals("""
                ```java
                s -> {
                    System.out.println("Hello World");
                };
                ```""", formatter.format("s->{System.out.println(\"Hello World\");};", lexer));
    }

    @Test
    @DisplayName("Lambda with typed parameters renders correctly")
    void testLambdaTypeParams() {
        assertEquals("""
                ```java
                (String s, Object b) -> {
                    System.out.println("Hello World");
                };
                ```""", formatter
            .format("(String s,Object b)->{System.out.println(\"Hello World\");};", lexer));
    }
}
