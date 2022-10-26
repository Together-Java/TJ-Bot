package org.togetherjava.tjbot.commands.tophelper;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;

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
    public static final Pattern UNCOUNTED_CHARS = Pattern.compile("\\p{C}");

    private final Database database;

    private final Predicate<String> isStagingChannelName;
    private final Predicate<String> isOverviewChannelName;

    /**
     * Creates a new listener to receive all message sent in help channels.
     *
     * @param database to store message meta-data in
     * @param config the config to use for this
     */
    public TopHelpersMessageListener(Database database, Config config) {
        super(Pattern.compile(".*"));

        this.database = database;

        isStagingChannelName = Pattern.compile(config.getHelpSystem().getStagingChannelPattern())
            .asMatchPredicate();
        isOverviewChannelName = Pattern.compile(config.getHelpSystem().getOverviewChannelPattern())
            .asMatchPredicate();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        if (!isHelpThread(event)) {
            return;
        }

        addMessageRecord(event);
    }

    private boolean isHelpThread(MessageReceivedEvent event) {
        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return false;
        }

        ThreadChannel thread = event.getChannel().asThreadChannel();
        String rootChannelName = thread.getParentChannel().getName();
        return isStagingChannelName.test(rootChannelName)
                || isOverviewChannelName.test(rootChannelName);
    }

    private void addMessageRecord(MessageReceivedEvent event) {
        String messageContent = event.getMessage().getContentRaw();
        long messageLength = UNCOUNTED_CHARS.matcher(messageContent).replaceAll("").length();

        database.write(context -> context.newRecord(HELP_CHANNEL_MESSAGES)
            .setMessageId(event.getMessage().getIdLong())
            .setGuildId(event.getGuild().getIdLong())
            .setChannelId(event.getChannel().getIdLong())
            .setAuthorId(event.getAuthor().getIdLong())
            .setSentAt(event.getMessage().getTimeCreated().toInstant())
            .setMessageLength(messageLength)
            .insert());
    }
}
