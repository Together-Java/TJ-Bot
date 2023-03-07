package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.StarboardConfig;
import org.togetherjava.tjbot.features.EventReceiver;

public class Starboard extends ListenerAdapter implements EventReceiver {

    private final StarboardConfig config;

    public Starboard(Config config) {
        this.config = config.getStarboard();
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        String emojiName = event.getReaction().getEmoji().asCustom().getName();
        MessageChannel channel = event.getChannel();
        // TODO

    }
}
