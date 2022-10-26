package org.togetherjava.tjbot.commands.tophelper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TopHelperAntiCheatTest {

    Predicate<String> uncountedCharsMatchPredicate =
            TopHelpersMessageListener.UNCOUNTED_CHARS.asMatchPredicate();


    @ParameterizedTest
    @MethodSource("provideInvisibleNamedCharacters")
    @DisplayName("Does exclude invisible characters")
    void excludesInvisibleCharacters(char character, String name) {
        String characterString = String.valueOf(character);

        String errorMessage = "Character [%s] was unrightfully matched".formatted(name);

        assertTrue(uncountedCharsMatchPredicate.test(characterString), errorMessage);
    }


    @ParameterizedTest
    @MethodSource("provideVisibleCharacters")
    @DisplayName("Does count visible characters")
    void countsVisibleCharacters(char character) {
        String characterString = String.valueOf(character);

        String errorMessage = "Character [%c] was unrightfully matched".formatted(character);

        assertFalse(uncountedCharsMatchPredicate.test(characterString), errorMessage);
    }


    private static Stream<Arguments> provideInvisibleNamedCharacters() {
        return Stream.of( // Invisible characters
                Arguments.of('\u061C', "Arabic Letter Mark"),
                Arguments.of('\u0600', "Arabic Number Sign"),
                Arguments.of('\u180E', "Mongolian Vowel Separator"),
                Arguments.of('\u200B', "Zero Width Space"),
                Arguments.of('\u200C', "Zero Width Non-Joiner"),
                Arguments.of('\u200D', "Zero Width Joiner"),
                Arguments.of('\u200E', "Left-to-Right Mark"),
                Arguments.of('\u200F', "Right-to-Left Mark"));
    }


    private static List<Character> provideVisibleCharacters() {
        return List.of( // Visible characters
                'a', 'A', 'b', 'B', 'c', 'C', 'x', 'X', 'y', 'Y', 'z', 'Z', // Latin alphabet
                '1', '2', '9', '0', // Numbers
                '!', '"', '§', '%', '&', '/', '\\', '(', ')', '{', '}', '[', ']', '<', '>', // Other
                '.', ':', ',', ';', '?', '-', '_', '#', '=', '\'', '+', '*', '´', '`', '|', // Other
                'α', 'Α', 'β', 'Β', 'γ', 'Γ', 'χ', 'Χ', 'ψ', 'Ψ', 'ω', 'Ω', // Greek alphabet
                'ä', 'ö', 'ü', 'ß', // German
                'á', 'è', 'î', // French
                '天', '四', '永' // Chinese
        );
    }

}
