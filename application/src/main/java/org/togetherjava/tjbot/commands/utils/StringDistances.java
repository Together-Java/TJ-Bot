package org.togetherjava.tjbot.commands.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Utility class for computing string distances, for example the edit distance between two words.
 */
public enum StringDistances {
    ;

    /**
     * Computes the candidate that matches the given query string best.
     *
     * It is given that, if the candidates contain the query literally, the query will also be the
     * returned match. If the candidates do not contain the query literally, the best match will be
     * determined. The measures for this are unspecified.
     * 
     * @param query the query string to find a match for
     * @param candidates the set of candidates to select a match from
     * @param <S> the type of the candidates
     * @return the best matching candidate, or empty iff the candidates are empty
     */
    public static <S extends CharSequence> Optional<S> closestMatch(@NotNull CharSequence query,
            @NotNull Collection<S> candidates) {
        return candidates.stream()
            .min(Comparator.comparingInt(candidate -> editDistance(query, candidate)));
    }

    /**
     * Attempts to autocomplete the given prefix string by selecting the candidate that matches the
     * prefix best.
     *
     * It is given that, if the candidates contain the query literally, the query will also be the
     * returned match. If the candidates do not contain the query literally, the best match will be
     * determined. The measures for this are unspecified.
     * 
     * @param prefix the prefix string to find a match for
     * @param candidates the set of candidates to select a match from
     * @param <S> the type of the candidates
     * @return the best matching candidate, or empty iff the candidates are empty
     */
    public static <S extends CharSequence> Optional<S> autocomplete(@NotNull CharSequence prefix,
            @NotNull Collection<S> candidates) {
        return candidates.stream()
            .min(Comparator.comparingInt(candidate -> prefixEditDistance(prefix, candidate)));
    }

    /**
     * Distance to receive {@code destination} from {@code source} by editing.
     *
     * For example {@code editDistance("hello", "hallo")} is {@code 1}.
     *
     * @param source the source string to start with
     * @param destination the destination string to receive by editing the source
     * @return the edit distance
     */
    public static int editDistance(@NotNull CharSequence source,
            @NotNull CharSequence destination) {
        // Given by the value in the last row and column
        int[][] table = computeLevenshteinDistanceTable(source, destination);
        int rows = table.length;
        int columns = table[0].length;

        return table[rows - 1][columns - 1];
    }

    /**
     * Distance to receive a prefix of {@code destination} from {@code source} by editing that
     * minimizes the distance.
     *
     * For example {@code prefixEditDistance("foa", "foobar")} is {@code 1}.
     *
     * @param source the source string to start with
     * @param destination the destination string to receive a prefix of by editing the source
     * @return the prefix edit distance
     */
    public static int prefixEditDistance(@NotNull CharSequence source,
            @NotNull CharSequence destination) {
        // Given by the smallest value in the last row
        int[][] table = computeLevenshteinDistanceTable(source, destination);
        int lastRowIndex = table.length - 1;

        return Arrays.stream(table[lastRowIndex]).min().orElseThrow();
    }

    /**
     * Computes the Levenshtein distance table for the given strings. See
     * <a href="https://en.wikipedia.org/wiki/Levenshtein_distance">Levenshtein distance</a> for
     * details.
     *
     * An example for {@code "abc"} to {@code "abcdefg"} would be:
     * 
     * <pre>
     *   | 0 a b c d e f g
     * -------------------
     * 0 | 0 1 2 3 4 5 6 7
     * a | 1 0 1 2 3 4 5 6
     * b | 2 1 0 1 2 3 4 5
     * c | 3 2 1 0 1 2 3 4
     * </pre>
     * 
     * @param source the source string to start with
     * @param destination the destination string to receive by editing the source
     * @return the levenshtein distance table
     */
    private static int @NotNull [][] computeLevenshteinDistanceTable(@NotNull CharSequence source,
            @NotNull CharSequence destination) {
        int rows = source.length() + 1;
        int columns = destination.length() + 1;
        int[][] table = new int[rows][columns];

        // Initialize first row and column for distances from the empty word to the target word
        for (int y = 0; y < columns; y++) {
            table[0][y] = y;
        }
        for (int x = 0; x < rows; x++) {
            table[x][0] = x;
        }

        // Process row by row, selecting diagonal candidates
        for (int x = 1; x < rows; x++) {
            for (int y = 1; y < columns; y++) {
                // Take minimum of all candidates
                int upperCandidate = table[x - 1][y] + 1;
                int leftCandidate = table[x][y - 1] + 1;
                int diagonalCandidate = table[x - 1][y - 1];
                if (source.charAt(x - 1) != destination.charAt(y - 1)) {
                    diagonalCandidate++;
                }

                int bestCandidate = IntStream.of(upperCandidate, leftCandidate, diagonalCandidate)
                    .min()
                    .orElseThrow();
                table[x][y] = bestCandidate;
            }
        }

        return table;
    }
}
