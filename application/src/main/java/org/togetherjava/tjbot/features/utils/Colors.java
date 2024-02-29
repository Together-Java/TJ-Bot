package org.togetherjava.tjbot.features.utils;

import java.awt.Color;

/**
 * Provides the color of different things.
 */
public class Colors {
    private Colors() {
        throw new UnsupportedOperationException();
    }

    public static final Color ERROR_COLOR = new Color(255, 99, 71);
    public static final Color SUCCESS_COLOR = new Color(118, 255, 0);
    public static final Color WARNING_COLOR = new Color(255, 255, 0);
    public static final Color PARTIAL_SUCCESS_COLOR = new Color(255, 140, 71);

}
