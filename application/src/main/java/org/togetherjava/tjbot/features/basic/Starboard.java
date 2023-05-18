package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.StarboardConfig;
import org.togetherjava.tjbot.features.EventReceiver;

import java.util.Optional;

public class Starboard extends ListenerAdapter implements EventReceiver {

    private static final Logger logger = LoggerFactory.getLogger(Starboard.class);
    private final StarboardConfig config;

    public Starboard(Config config) {
        this.config = config.getStarboard();
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        String emojiName = event.getEmoji().asCustom().getName();
        Guild guild = event.getGuild();
        if (ignoreMessage(emojiName, guild, event.getGuildChannel())) {
            return;
        }
        Optional<TextChannel> starboardChannel = getStarboardChannel(guild);
        if (starboardChannel.isEmpty()) {
            logger.warn("There is no channel for the starboard in the guild with the name {}",
                    config.getChannelName());
            return;
        }
        event.retrieveMessage()
            .flatMap(
                    message -> starboardChannel.orElseThrow().sendMessageEmbeds(formEmbed(message)))
            .queue();
    }

    private boolean ignoreMessage(String emojiName, Guild guild, GuildChannel channel) {
        return !config.getEmojiNames().contains(emojiName)
                || !guild.getPublicRole().hasPermission(channel, Permission.VIEW_CHANNEL);
    }

    private Optional<TextChannel> getStarboardChannel(Guild guild) {
        return guild.getTextChannelsByName(config.getChannelName(), false).stream().findFirst();
    }

    private static MessageEmbed formEmbed(Message message) {
        User author = message.getAuthor();
        return new EmbedBuilder().setAuthor(author.getName(), null, author.getAvatarUrl())
            .setDescription(message.getContentDisplay())
            .build(); // TODO make footer with link and reacted emojis
    }
}
