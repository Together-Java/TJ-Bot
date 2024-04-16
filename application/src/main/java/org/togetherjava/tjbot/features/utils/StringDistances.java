package org.togetherjava.tjbot.features.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Utility class for computing string distances, for example the edit distance between two words.
 */
public class StringDistances {
    /**
     * Matches that are further off than this are not considered as match anymore. The value is
     * between 0.0 (full match) and 1.0 (completely different).
     */
    private static final double OFF_BY_PERCENTAGE_THRESHOLD = 0.5;

    private StringDistances() {
        throw new UnsupportedOperationException("Utility class, construction not supported");
    }

    /**
     * Computes the candidate that matches the given query string best.
     * <p>
     * It is given that, if the candidates contain the query literally, the query will also be the
     * returned match. If the candidates do not contain the query literally, the best match will be
     * determined. The measures for this are unspecified.
     *
     * @param query the query string to find a match for
     * @param candidates the set of candidates to select a match from
     * @param <S> the type of the candidates
     * @return the best matching candidate, or empty iff the candidates are empty
     */
    public static <S extends CharSequence> Optional<S> closestMatch(CharSequence query,
            Collection<S> candidates) {
        return candidates.stream()
            .min(Comparator.comparingInt(candidate -> editDistance(query, candidate)));
    }

    /**
     * Attempts to autocomplete the given prefix string by selecting the candidate that matches the
     * prefix best.
     * <p>
     * It is given that, if the candidates contain the query literally, the query will also be the
     * returned match. If the candidates do not contain the query literally, the best match will be
     * determined. The measures for this are unspecified.
     *
     * @param prefix the prefix string to find a match for
     * @param candidates the set of candidates to select a match from
     * @param <S> the type of the candidates
     * @return the best matching candidate, or empty iff the candidates are empty
     */
    public static <S extends CharSequence> Optional<S> autocomplete(CharSequence prefix,
            Collection<S> candidates) {
        return candidates.stream()
            .min(Comparator.comparingInt(candidate -> prefixEditDistance(prefix, candidate)));
    }

    /**
     * Gives sorted suggestion to autocomplete a prefix string from the given options.
     *
     * @param prefix the prefix to give matches for
     * @param candidates all the possible matches
     * @param limit number of matches to generate at max
     * @return the matches closest to the given prefix, limited to the given limit
     */
    public static Collection<String> closeMatches(CharSequence prefix,
            Collection<String> candidates, int limit) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        Collection<MatchScore> scoredMatches = candidates.stream()
            .map(candidate -> new MatchScore(candidate, prefixEditDistance(prefix, candidate)))
            .toList();

        Queue<MatchScore> bestMatches = new PriorityQueue<>();
        bestMatches.addAll(scoredMatches);

        return Stream.generate(bestMatches::poll)
            .limit(Math.min(limit, bestMatches.size()))
            .takeWhile(matchScore -> isCloseEnough(matchScore, prefix))
            .map(MatchScore::candidate)
            .toList();
    }

    private static boolean isCloseEnough(MatchScore matchScore, CharSequence prefix) {
        if (prefix.isEmpty()) {
            return true;
        }

        return matchScore.score / prefix.length() <= OFF_BY_PERCENTAGE_THRESHOLD;
    }

    /**
     * Distance to receive {@code destination} from {@code source} by editing.
     * <p>
     * For example {@code editDistance("hello", "hallo")} is {@code 1}.
     *
     * @param source the source string to start with
     * @param destination the destination string to receive by editing the source
     * @return the edit distance
     */
    public static int editDistance(CharSequence source, CharSequence destination) {
        // Given by the value in the last row and column
        int[][] table = computeLevenshteinDistanceTable(source, destination);
        int rows = table.length;
        int columns = table[0].length;

        return table[rows - 1][columns - 1];
    }

    /**
     * Distance to receive a prefix of {@code destination} from {@code source} by editing that
     * minimizes the distance.
     * <p>
     * For example {@code prefixEditDistance("foa", "foobar")} is {@code 1}.
     *
     * @param source the source string to start with
     * @param destination the destination string to receive a prefix of by editing the source
     * @return the prefix edit distance
     */
    public static int prefixEditDistance(CharSequence source, CharSequence destination) {
        // Given by the smallest value in the last row
        int[][] table = computeLevenshteinDistanceTable(source, destination);
        int lastRowIndex = table.length - 1;

        return Arrays.stream(table[lastRowIndex]).min().orElseThrow();
    }

    /**
     * Computes the Levenshtein distance table for the given strings. See
     * <a href="https://en.wikipedia.org/wiki/Levenshtein_distance">Levenshtein distance</a> for
     * details.
     * <p>
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
    private static int[][] computeLevenshteinDistanceTable(CharSequence source,
            CharSequence destination) {
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

    private record MatchScore(String candidate, double score) implements Comparable<MatchScore> {
        @Override
        public int compareTo(MatchScore otherMatchScore) {
            int compareResult = Double.compare(this.score, otherMatchScore.score);

            if (compareResult == 0) {
                return this.candidate.compareTo(otherMatchScore.candidate);
            }

            return compareResult;
        }
    }
}
