package org.togetherjava.formatter;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.togetherjava.formatter.tokenizer.Lexer;

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
                
                ```""", formatter.format("public static\nvoid main ( String [ ]args){ System.out. println( \"Hello World!\"      );}", lexer));
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

                ```""", formatter.format("List<String>input=new ArrayList<>();List<String> result=new ArrayList<>();for(String s:input){result.add(s.trim());}return result;", lexer));
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
}
