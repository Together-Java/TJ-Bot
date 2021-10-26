package org.togetherjava.tjbot.util;

import net.dv8tion.jda.api.entities.TextChannel;

import java.util.regex.Pattern;

/**
 * Miscellaneous utilities for JDA entities.
 */
public final class JdaUtils {
    private static final Pattern HELP_CHANNEL_PATTERN = Pattern.compile(".*\\Qhelp\\E.*");

    private JdaUtils() {}

    /**
     * Checks if a provided channel is a help channel.
     * 
     * @param textChannel provided channel.
     * @return true if the provided channel is a help channel, false otherwise.
     */
    public static boolean isAHelpChannel(TextChannel textChannel) {
        return HELP_CHANNEL_PATTERN.matcher(textChannel.getName()).matches();
    }
}
