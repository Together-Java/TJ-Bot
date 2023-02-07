package org.togetherjava.tjbot.features.tophelper;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

/**
 * Listener that receives all sent help messages and puts them into the database for
 * {@link TopHelpersCommand} to pick them up.
 */
public final class TopHelpersMessageListener extends MessageReceiverAdapter {
    /**
     * Matches invisible control characters and unused code points
     *
     * @see <a href="https://www.regular-expressions.info/unicode.html#category">Unicode
     *      Categories</a>
     */
    private static final Pattern INVALID_CHARACTERS = Pattern.compile("\\p{C}");

    private final Database database;

    private final Predicate<String> isHelpForumName;

    /**
     * Creates a new listener to receive all message sent in help channels.
     *
     * @param database to store message meta-data in
     * @param config the config to use for this
     */
    public TopHelpersMessageListener(Database database, Config config) {
        this.database = database;

        isHelpForumName =
                Pattern.compile(config.getHelpSystem().getHelpForumPattern()).asMatchPredicate();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (shouldIgnoreMessage(event)) {
            return;
        }

        addMessageRecord(event);
    }

    private void addMessageRecord(MessageReceivedEvent event) {
        long messageLength = countValidCharacters(event.getMessage().getContentRaw());

        database.write(context -> context.newRecord(HELP_CHANNEL_MESSAGES)
            .setMessageId(event.getMessage().getIdLong())
            .setGuildId(event.getGuild().getIdLong())
            .setChannelId(event.getChannel().getIdLong())
            .setAuthorId(event.getAuthor().getIdLong())
            .setSentAt(event.getMessage().getTimeCreated().toInstant())
            .setMessageLength(messageLength)
            .insert());
    }

    boolean shouldIgnoreMessage(MessageReceivedEvent event) {
        return event.getAuthor().isBot() || event.isWebhookMessage()
                || !isHelpThread(event.getChannel()) || isSentByOp(event);
    }

    boolean isHelpThread(MessageChannelUnion channel) {
        if (channel.getType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return false;
        }

        ThreadChannel thread = channel.asThreadChannel();
        String rootChannelName = thread.getParentChannel().getName();
        return isHelpForumName.test(rootChannelName);
    }

    private boolean isSentByOp(MessageReceivedEvent event) {
        return event.getChannel().asThreadChannel().getOwnerId().equals(event.getAuthor().getId());
    }

    static long countValidCharacters(String messageContent) {
        return INVALID_CHARACTERS.matcher(messageContent).replaceAll("").length();
    }

}
