package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.CoolMessagesBoardConfig;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.awt.Color;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Manager for the cool messages board. It appends highly-voted text messages to a separate channel
 * where members of the guild can see a list of all of them.
 */
public final class CoolMessagesBoardManager extends MessageReceiverAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CoolMessagesBoardManager.class);
    private Emoji coolEmoji;
    private final Predicate<String> boardChannelNamePredicate;
    private final CoolMessagesBoardConfig config;

    public CoolMessagesBoardManager(Config config) {
        this.config = config.getCoolMessagesConfig();
        this.coolEmoji = Emoji.fromUnicode(this.config.reactionEmoji());

        boardChannelNamePredicate =
                Pattern.compile(this.config.boardChannelPattern()).asMatchPredicate();
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        final MessageReaction messageReaction = event.getReaction();
        int originalReactionsCount = messageReaction.hasCount() ? messageReaction.getCount() : 0;
        boolean isCoolEmoji = messageReaction.getEmoji().equals(coolEmoji);
        long guildId = event.getGuild().getIdLong();
        Optional<TextChannel> boardChannel = getBoardChannel(event.getJDA(), guildId);

        if (boardChannel.isEmpty()) {
            logger.warn(
                    "Could not find board channel with pattern '{}' in server with ID '{}'. Skipping reaction handling...",
                    this.config.boardChannelPattern(), guildId);
            return;
        }

        // If the bot has already reacted to this message, then this means that
        // the message has been quoted to the cool messages board, so skip it.
        if (hasBotReacted(event.getJDA(), messageReaction)) {
            return;
        }

        final int newReactionsCount = originalReactionsCount + 1;
        if (isCoolEmoji && newReactionsCount >= config.minimumReactions()) {
            event.retrieveMessage().queue(message -> {
                message.addReaction(coolEmoji).queue();

                insertCoolMessage(boardChannel.get(), message);
            }, e -> logger.warn("Tried to retrieve cool message but got: {}", e.getMessage()));
        }
    }

    /**
     * Gets the board text channel where the quotes go to, wrapped in an optional.
     *
     * @param jda the JDA
     * @param guildId the guild ID
     * @return the board text channel
     */
    private Optional<TextChannel> getBoardChannel(JDA jda, long guildId) {
        return jda.getGuildById(guildId)
            .getTextChannelCache()
            .stream()
            .filter(channel -> boardChannelNamePredicate.test(channel.getName()))
            .findAny();
    }

    /**
     * Inserts a message to the specified text channel
     */
    private static void insertCoolMessage(TextChannel boardChannel, Message message) {
        boardChannel.sendMessageEmbeds(Collections.singleton(createQuoteEmbed(message))).queue();
    }

    /**
     * Wraps a text message into a properly formatted quote message used for the board text channel.
     */
    private static MessageEmbed createQuoteEmbed(Message message) {
        final User author = message.getAuthor();
        EmbedBuilder embedBuilder = new EmbedBuilder();

        // If the message contains image(s), include the first one
        var firstImageAttachment = message.getAttachments()
            .stream()
            .parallel()
            .filter(Message.Attachment::isImage)
            .findAny()
            .orElse(null);

        if (firstImageAttachment != null) {
            embedBuilder.setThumbnail(firstImageAttachment.getUrl());
        }

        return embedBuilder.setDescription(message.getContentDisplay())
            .appendDescription("%n%n[Jump to Message](%s)".formatted(message.getJumpUrl()))
            .setColor(Color.orange)
            .setAuthor(author.getName(), null, author.getAvatarUrl())
            .setTimestamp(message.getTimeCreated())
            .build();
    }

    /**
     * Checks a {@link MessageReaction} to see if the bot has reacted to it.
     */
    private boolean hasBotReacted(JDA jda, MessageReaction messageReaction) {
        if (!coolEmoji.equals(messageReaction.getEmoji())) {
            return false;
        }

        return messageReaction.retrieveUsers()
            .parallelStream()
            .anyMatch(user -> jda.getSelfUser().getIdLong() == user.getIdLong());
    }
}
