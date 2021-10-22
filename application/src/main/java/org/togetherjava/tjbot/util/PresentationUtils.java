package org.togetherjava.tjbot.util;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Utility class for representation of data in various formats
 */
public final class PresentationUtils {
    private PresentationUtils() {}

    /**
     * Flattens a dataFrame to String representation of a table.
     * 
     * @param dataFrame dataframe represented as List<List<String>> where List<String> represents a
     *        single row
     * @param headers column headers for the table
     * @param horizontalAligns column alignment for the table
     * @return String representation of the dataFrame in tabular form
     */
    public static String dataFrameToAsciiTable(List<List<String>> dataFrame, String[] headers,
            HorizontalAlign[] horizontalAligns) {
        Objects.requireNonNull(dataFrame, "DataFrame cannot be null");
        Objects.requireNonNull(headers, "Headers cannot be null");
        Objects.requireNonNull(horizontalAligns, "HorizontalAligns cannot be null");
        Character[] characters = AsciiTable.BASIC_ASCII_NO_DATA_SEPARATORS_NO_OUTSIDE_BORDER;
        List<ColumnData<List<String>>> columnData = IntStream.range(0, headers.length)
            .mapToObj(i -> new Column().header(headers[i])
                .dataAlign(horizontalAligns[i])
                .<List<String>>with(row -> row.get(i)))
            .toList();
        return AsciiTable.getTable(characters, dataFrame, columnData);
    }
}
