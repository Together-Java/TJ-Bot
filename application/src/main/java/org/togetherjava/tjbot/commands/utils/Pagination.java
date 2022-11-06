package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Utility for pagination.
 */
public class Pagination {
    private static final Emoji PREVIOUS_BUTTON_EMOJI = Emoji.fromUnicode("⬅");
    private static final Emoji NEXT_BUTTON_EMOJI = Emoji.fromUnicode("➡");

    private Pagination() {
        throw new UnsupportedOperationException("Utility class, construction not supported");
    }

    /**
     * Gets entries for the page.
     *
     * @param list list of all the values
     * @param pageNumber page number to return the entries for
     * @param entriesPerPage max number of entries in one page
     * @return list of entries for the page number given
     */
    public static <T> List<T> getPageEntries(List<T> list, int pageNumber, int entriesPerPage) {
        int start = (pageNumber - 1) * entriesPerPage;
        int end = Math.min(start + entriesPerPage, list.size());

        return list.subList(start, end);
    }

    /**
     * Creates the buttons for navigation between pages.
     *
     * @param componentIdGenerator id generator for buttons
     * @param currentPage current page number
     * @param totalPages total number of pages
     * @param extraIds extra ids (default is the page to show)
     * @return the list of buttons
     */
    public static List<Button> createPageTurnButtons(
            Function<String[], String> componentIdGenerator, int currentPage, int totalPages,
            String... extraIds) {
        IntFunction<String[]> getIds = pageToShow -> {
            String[] ids = new String[extraIds.length + 1];
            ids[0] = String.valueOf(pageToShow);
            System.arraycopy(extraIds, 0, ids, 1, extraIds.length);

            return ids;
        };

        Button previousButton = Button.primary(
                componentIdGenerator.apply(getIds.apply(currentPage - 1)), PREVIOUS_BUTTON_EMOJI);
        if (currentPage <= 1) {
            previousButton = previousButton.asDisabled();
        }

        Button nextButton = Button
            .primary(componentIdGenerator.apply(getIds.apply(currentPage + 1)), NEXT_BUTTON_EMOJI);
        if (currentPage >= totalPages) {
            nextButton = nextButton.asDisabled();
        }

        return List.of(previousButton, nextButton);
    }

    /**
     * Calculates the total number of pages needed to show the list of values.
     *
     * @param list list of all the values
     * @param entriesPerPage entries to show on each page
     * @return total number of pages
     */
    public static <T> int calculateTotalPage(List<T> list, int entriesPerPage) {
        return Math.ceilDiv(list.size(), entriesPerPage);
    }

    /**
     * If the number is less than min value it'll return the min value, if the number is greater
     * than max value it'll return the max value else it'll return the value.
     *
     * @param min minimum value (inclusive)
     * @param value the value to verify
     * @param max maximum value (inclusive)
     * @return a number between min and max inclusive
     */
    public static int clamp(int min, int value, int max) {
        return Math.min(Math.max(min, value), max);
    }
}
