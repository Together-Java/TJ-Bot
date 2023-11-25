package org.togetherjava.tjbot.features.tags;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Utility class to hold util methods for tag feature.
 */
public final class TagUtil {
    private TagUtil() {}

    static TextChannel getBotsCommandChannel(JDA jda, Predicate<String> isBotsCommandChannel) {
        Optional<TextChannel> botsChannelOptional = jda.getTextChannels()
            .stream()
            .filter(channel -> isBotsCommandChannel.test(channel.getName()))
            .findFirst();

        return botsChannelOptional.orElseThrow(() -> new IllegalArgumentException(
                "Unable to get channel used for bots command, try fixing config"));
    }

    static boolean isBotsChannel(TextChannel textChannel, Predicate<String> isBotsCommandChannel) {
        return isBotsCommandChannel.test(textChannel.getName());
    }
}
