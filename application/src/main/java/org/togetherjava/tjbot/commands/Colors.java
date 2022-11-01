package org.togetherjava.tjbot.commands;

import java.awt.Color;

/**
 * <a href="https://github.com/catppuccin/catppuccin#-palettes">Catppuccin Macchiato</a> color
 * palette for all features to use.
 */
public final class Colors {

    private Colors() {
        throw new IllegalStateException("Utility class");
    }

    // Catppuccin Macchiato colors
    public static final Color COLOR_ROSEWATER = new Color(0xf4dbd6);
    public static final Color COLOR_FLAMINGO = new Color(0xf0c6c6);
    public static final Color COLOR_PINK = new Color(0xf5bde6);
    public static final Color COLOR_MAUVE = new Color(0xc6a0f6);
    public static final Color COLOR_RED = new Color(0xed8796);
    public static final Color COLOR_MAROON = new Color(0xee99a0);
    public static final Color COLOR_PEACH = new Color(0xf5a97f);
    public static final Color COLOR_YELLOW = new Color(0xeed49f);
    public static final Color COLOR_GREEN = new Color(0xa6da95);
    public static final Color COLOR_TEAL = new Color(0x8bd5ca);
    public static final Color COLOR_SKY = new Color(0x91d7e3);
    public static final Color COLOR_SAPPHIRE = new Color(0x7dc4e4);
    public static final Color COLOR_BLUE = new Color(0x8aadf4);
    public static final Color COLOR_LAVENDER = new Color(0xb7bdf8);
    public static final Color COLOR_TEXT = new Color(0xcad3f5);
    public static final Color COLOR_SUBTEXT1 = new Color(0xb8c0e0);
    public static final Color COLOR_SUBTEXT0 = new Color(0xa5adcb);
    public static final Color COLOR_OVERLAY2 = new Color(0x939ab7);
    public static final Color COLOR_OVERLAY1 = new Color(0x8087a2);
    public static final Color COLOR_OVERLAY0 = new Color(0x6e738d);
    public static final Color COLOR_SURFACE2 = new Color(0x5b6078);
    public static final Color COLOR_SURFACE1 = new Color(0x494d64);
    public static final Color COLOR_SURFACE0 = new Color(0x363a4f);
    public static final Color COLOR_BASE = new Color(0x24273a);
    public static final Color COLOR_MANTLE = new Color(0x1e2030);
    public static final Color COLOR_CRUST = new Color(0x181926);

    // General colors
    public static final Color SUCCESS = COLOR_GREEN;
    public static final Color WARNING = COLOR_PEACH;
    public static final Color FAILURE = COLOR_MAROON;

    // Feature colors
    public static final Color MOD_AUDIT_LOG = COLOR_BLUE;

    public static final Color REMIND = COLOR_YELLOW;

    public static final Color WOLFRAM_ALPHA = COLOR_SKY;

    public static final Color TEX_BACKGROUND = COLOR_BASE;
    public static final Color TEX_FOREGROUND = COLOR_TEXT;

    public static final Color SCAM_BLOCKER = COLOR_LAVENDER;

    public static final Color MODERATION = COLOR_MAUVE;

    public static final Color TAG = COLOR_MAROON;

    public static final Color MEDIA_ONLY = COLOR_PEACH;

    public static final Color BLACKLISTED_ATTACHMENT = COLOR_PEACH;

    public static final Color MODMAIL = COLOR_MANTLE;

    public static final Color LOG_TRACE = COLOR_SKY;
    public static final Color LOG_DEBUG = COLOR_SAPPHIRE;
    public static final Color LOG_INFO = SUCCESS;
    public static final Color LOG_WARN = WARNING;
    public static final Color LOG_ERROR = FAILURE;
    public static final Color LOG_FATAL = COLOR_RED;

    public static final Color ROLE_SELECT = COLOR_TEAL;

    public static final Color HELP = COLOR_YELLOW;

}
