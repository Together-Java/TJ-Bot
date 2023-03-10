package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.jetbrains.annotations.NotNull;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.StarboardConfig;
import org.togetherjava.tjbot.features.EventReceiver;

import java.awt.*;
import java.util.Optional;

public class Starboard extends ListenerAdapter implements EventReceiver {

    private final StarboardConfig config;

    public Starboard(Config config) {
        this.config = config.getStarboard();
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        String emojiName = event.getReaction().getEmoji().asCustom().getName();
        Guild guild = event.getGuild();
        GuildChannel channel = event.getGuildChannel();
        if (!config.getEmojiNames().contains(emojiName) || !guild.getPublicRole().hasPermission(channel, Permission.VIEW_CHANNEL)) {
            return;
        }
        Optional<TextChannel> starboardChannel = guild.getTextChannelsByName(config.getStarboardChannelName(), false).stream().findFirst();
        if (starboardChannel.isEmpty()) {
            //TODO log?
            return;
        }
        MessageEmbed embed = new EmbedBuilder().build(); //TODO build embed
        starboardChannel.get().sendMessageEmbeds(embed).queue();
    }
}
