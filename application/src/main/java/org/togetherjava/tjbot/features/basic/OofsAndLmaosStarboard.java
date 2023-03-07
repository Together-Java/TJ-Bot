package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.OofsAndLmaosConfig;
import org.togetherjava.tjbot.features.EventReceiver;

public class OofsAndLmaosStarboard extends ListenerAdapter implements EventReceiver {

    private final OofsAndLmaosConfig config;

    public OofsAndLmaosStarboard(Config config) {
        this.config = config.getOofsAndLmaos();
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        String emojiName = event.getReaction().getEmoji().asCustom().getName();
        MessageChannel channel = event.getChannel();
        // TODO

    }
}
