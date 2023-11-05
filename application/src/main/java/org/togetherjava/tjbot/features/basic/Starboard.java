package org.togetherjava.tjbot.features.basic;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.EventReceiver;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.togetherjava.tjbot.db.generated.tables.StarboardMessages.STARBOARD_MESSAGES;

public class Starboard extends ListenerAdapter implements EventReceiver {

    private static final Logger logger = LoggerFactory.getLogger(Starboard.class);
    private final StarboardConfig config;
    private final Database database;

    private final Cache<Long, Object> messageCache;

    public Starboard(Config config, Database database) {
        this.config = config.getStarboard();
        this.database = database;
        this.messageCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(24, TimeUnit.HOURS) // TODO make these constants
            .build();
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        String emojiName = event.getEmoji().getName();
        Guild guild = event.getGuild();
        long messageId = event.getMessageIdLong();
        if (shouldIgnoreMessage(emojiName, guild, event.getGuildChannel(), messageId)) {
            return;
        }
        Optional<TextChannel> starboardChannel = getStarboardChannel(guild);
        if (starboardChannel.isEmpty()) {
            logger.warn("There is no channel for the starboard in the guild with the name {}",
                    config.channelPattern());
            return;
        }
        database.write(context -> context.newRecord(STARBOARD_MESSAGES).setMessageId(messageId));
        messageCache.put(messageId, new Object());
        event.retrieveMessage()
            .flatMap(
                    message -> starboardChannel.orElseThrow().sendMessageEmbeds(formEmbed(message)))
            .queue();
    }

    private boolean shouldIgnoreMessage(String emojiName, Guild guild, GuildChannel channel,
            long messageId) {
        return !config.emojiNames().contains(emojiName)
                || !guild.getPublicRole().hasPermission(channel, Permission.VIEW_CHANNEL)
                || messageCache.getIfPresent(messageId) != null
                || database
                    .read(context -> context.fetchExists(context.selectFrom(STARBOARD_MESSAGES)
                        .where(STARBOARD_MESSAGES.MESSAGE_ID.eq(messageId))));
    }

    private Optional<TextChannel> getStarboardChannel(Guild guild) {
        return guild.getTextChannels()
            .stream()
            .filter(channel -> config.channelPattern().matcher(channel.getName()).find())
            .findFirst();
    }

    private static MessageEmbed formEmbed(Message message) {
        User author = message.getAuthor();
        return new EmbedBuilder().setAuthor(author.getName(), null, author.getAvatarUrl())
            .setDescription(message.getContentDisplay())
            .appendDescription(" [Link](%s)".formatted(message.getJumpUrl()))
            .build();
    }
}
