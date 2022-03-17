package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.EventReceiver;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.FreeCommandConfig;

import java.awt.*;
import java.time.Instant;
import java.util.*;

// TODO (can SlashCommandVisibility be narrower than GUILD?)
// TODO monitor all channels when list is empty? monitor none?
// TODO (use other emojis? use images?)
// TODO add command to add/remove/status channels to monitor?
// TODO test if message is a reply and don't mark as busy if it is
// TODO add button query to confirm that message is new question not additional info for existing
// discussion before marking as busy
// TODO add scheduled tasks to check last message every predefined duration and mark as free if
// applicable

/**
 * Implementation of the free command. It is used to monitor a predefined list of channels and show
 * users which ones are available for use and which are not.
 * <p>
 * When a user posts a message in a channel that is being monitored that channel is automatically
 * marked as busy until they post {@code /free} to notify the bot and other users that the channel
 * is now available or after a preconfigured period of time has passed without any traffic.
 * <p>
 * If any user posts a message that directly 'replies' to an existing message, in a monitored
 * channel that is currently marked as free, the free status will remain.
 * <p>
 * If a user starts typing in a channel where 2 or more users have posted multiple messages each,
 * less than a configured time ago, they will receive an ephemeral message warning them that the
 * channel is currently in use and that they should post in a free channel if they are trying to ask
 * a question.
 * <p>
 * A summary of the current status of those channels is displayed in a predefined channel. This
 * channel may be one of the monitored channels however it is recommended that a different channel
 * is used.
 */
public final class FreeCommand extends SlashCommandAdapter implements EventReceiver {
    private static final Logger logger = LoggerFactory.getLogger(FreeCommand.class);

    private static final String STATUS_TITLE = "**__CHANNEL STATUS__**\n\n";
    private static final String COMMAND_NAME = "free";
    private static final Color MESSAGE_HIGHLIGHT_COLOR = Color.decode("#CCCC00");

    private final Config config;

    // Map to store channel ID's, use Guild.getChannels() to guarantee order for display
    private final ChannelMonitor channelMonitor;
    private final Map<Long, Long> channelIdToMessageIdForStatus;

    private volatile boolean isReady;


    /**
     * Creates an instance of FreeCommand.
     * <p>
     * This fetches configuration information from a json configuration file (see
     * {@link FreeCommandConfig}) for further details.
     * 
     * @param config the config to use for this
     */
    public FreeCommand(@NotNull Config config) {
        super(COMMAND_NAME, "Marks this channel as free for another user to ask a question",
                SlashCommandVisibility.GUILD);

        this.config = config;
        channelIdToMessageIdForStatus = new HashMap<>();
        channelMonitor = new ChannelMonitor();

        isReady = false;
    }

    /**
     * Reaction to the 'onReady' event. This method binds the configurables to the
     * {@link net.dv8tion.jda.api.JDA} instance. Including fetching the names of the channels this
     * command monitors.
     * <p>
     * It also updates the Status messages in their relevant channels, so that the message is
     * up-to-date.
     * <p>
     * This also registers a new listener on the {@link net.dv8tion.jda.api.JDA}, this should be
     * removed when the code base supports additional functionality
     *
     * @param event the event this method reacts to
     */
    public void onReady(@NotNull final ReadyEvent event) {
        final JDA jda = event.getJDA();

        initChannelsToMonitor();
        initStatusMessageChannels(jda);
        logger.debug("Config loaded:\n{}", channelMonitor);

        checkBusyStatusAllChannels(jda);

        channelMonitor.statusIds()
            .map(id -> requiresTextChannel(jda, id))
            .forEach(this::displayStatus);

        isReady = true;
    }

    /**
     * When triggered with {@code /free} this will mark a help channel as not busy (free for another
     * person to use).
     * <p>
     * If this is called on from a channel that was not configured for monitoring (see
     * {@link FreeCommandConfig}) the user will receive an ephemeral message stating such.
     * 
     * @param event the event that triggered this
     * @throws IllegalStateException if this method is called for a Global Slash Command
     */
    @Override
    public void onSlashCommand(@NotNull final SlashCommandEvent event) {
        logger.debug("/free used by {} on channel {}", event.getUser().getAsTag(),
                event.getChannel().getName());
        if (!handleShouldBeProcessed(event)) {
            return;
        }

        long id = event.getChannel().getIdLong();
        // do not need to test if key is present, shouldHandle(event) already does.
        if (!channelMonitor.isChannelBusy(id)) {
            FreeUtil.sendErrorMessage(event, UserStrings.ALREADY_FREE_ERROR.message());
            return;
        }
        // TODO check if /free called by original author, if not put message asking if he approves
        channelMonitor.setChannelFree(id);
        displayStatus(channelMonitor.getStatusChannelFor(requiresGuild(event)));
        event.reply(UserStrings.MARK_AS_FREE.message()).queue();
    }

    /**
     * Method to test event to see if it should be processed.
     * <p>
     * Will respond to users describing the problem if the event should not be processed.
     * <p>
     * This checks if the command system is ready to process events, if the event was triggered in a
     * monitored guild and in a monitored channel.
     *
     * @param event the event to test for validity.
     * @return true if the event should be processed false otherwise.
     */
    private boolean handleShouldBeProcessed(@NotNull final SlashCommandEvent event) {
        if (!isReady) {
            logger.debug(
                    "Slash command requested by {} in {}(channel: {}) before command is ready.",
                    event.getUser().getIdLong(), event.getGuild(), event.getChannel().getName());
            FreeUtil.sendErrorMessage(event, UserStrings.NOT_READY_ERROR.message());
            return false;
        }
        // checks if guild is null and throws IllegalStateException if it is
        Guild guild = requiresGuild(event);
        if (!channelMonitor.isMonitoringGuild(guild.getIdLong())) {
            logger.error(
                    "Slash command used by {} in {}(channel: {}) when guild is not configured for Free Command",
                    event.getUser().getIdLong(), guild, event.getChannel().getName());
            FreeUtil.sendErrorMessage(event,
                    UserStrings.NOT_CONFIGURED_ERROR.formatted(guild.getName()));
            return false;
        }
        if (!channelMonitor.isMonitoringChannel(event.getChannel().getIdLong())) {
            logger.debug("'/free called in un-configured channel {}({})", guild.getName(),
                    event.getChannel().getName());
            FreeUtil.sendErrorMessage(event, UserStrings.NOT_MONITORED_ERROR.message());
            return false;
        }

        return true;
    }

    /**
     * Displays the message that will be displayed for users.
     * <p>
     * This method detects if any messages have been posted in the channel below the status message.
     * If that is the case this will delete the existing status message and post another one so that
     * it's the last message in the channel.
     * <p>
     * If it cannot find an existing status message it will create a new one.
     * <p>
     * Otherwise it will edit the existing message.
     *
     * @param channel the text channel the status message will be posted in.
     */
    public void displayStatus(@NotNull TextChannel channel) {
        final Guild guild = channel.getGuild();

        String messageTxt = buildStatusMessage(guild);
        MessageEmbed embed = new EmbedBuilder().setTitle(STATUS_TITLE)
            .setDescription(messageTxt)
            .setFooter(channel.getJDA().getSelfUser().getName())
            .setTimestamp(Instant.now())
            .setColor(MESSAGE_HIGHLIGHT_COLOR)
            .build();

        getStatusMessageIn(channel).flatMap(this::deleteIfNotLatest)
            .ifPresentOrElse(message -> message.editMessageEmbeds(embed).queue(),
                    () -> channel.sendMessageEmbeds(embed)
                        .queue(message -> channelIdToMessageIdForStatus.put(channel.getIdLong(),
                                message.getIdLong())));
    }

    private @NotNull Optional<Message> deleteIfNotLatest(@NotNull Message message) {

        OptionalLong lastId = FreeUtil.getLastMessageId(message.getTextChannel());
        if (lastId.isPresent() && lastId.getAsLong() != message.getIdLong()) {
            message.delete().queue();
            return Optional.empty();
        }

        return Optional.of(message);
    }

    private void checkBusyStatusAllChannels(@NotNull JDA jda) {
        channelMonitor.guildIds()
            .map(id -> requiresGuild(jda, id))
            .forEach(channelMonitor::updateStatusFor);
    }

    private @NotNull Guild requiresGuild(@NotNull JDA jda, long id) {
        Guild guild = jda.getGuildById(id);
        if (guild == null) {
            throw new IllegalStateException(
                    "The guild with id '%d' has been deleted since free command system was configured."
                        .formatted(id));
        }
        return guild;
    }

    private @NotNull Guild requiresGuild(SlashCommandEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            throw new IllegalStateException(
                    "A global slash command '%s' somehow got routed to the free system which requires a guild"
                        .formatted(event.getCommandString()));
        }
        return guild;
    }

    /**
     * Method for creating the message that shows the channel statuses for the specified guild.
     * <p>
     * This method dynamically builds the status message as per the current values on the guild,
     * including the channel categories. This method will detect any changes made on the guild and
     * represent those changes in the status message.
     *
     * @param guild the guild that the message is required for.
     * @return the message to display showing the channel statuses. Includes Discord specific
     *         formatting, trying to display elsewhere may have unpredictable results.
     * @throws IllegalArgumentException if the guild passed in is not configured in the free command
     *         system, see {@link ChannelMonitor#addChannelForStatus(TextChannel)}.
     */
    public @NotNull String buildStatusMessage(@NotNull Guild guild) {
        if (!channelMonitor.isMonitoringGuild(guild.getIdLong())) {
            throw new IllegalArgumentException(
                    "The guild '%s(%s)' is not configured in the free command system"
                        .formatted(guild.getName(), guild.getIdLong()));
        }

        return channelMonitor.statusMessage(guild);
    }

    /**
     * Method for responding to 'onGuildMessageReceived' this will need to be replaced by a more
     * appropriate method when the bot has more functionality.
     * <p>
     * Marks channels as busy when a user posts a message in a monitored channel that is currently
     * free.
     *
     * @param event the generic event that includes the 'onGuildMessageReceived'.
     */
    @SuppressWarnings("squid:S2583") // False-positive about the if-else-instanceof, sonar thinks
                                     // the second case is unreachable; but it passes without
                                     // pattern-matching. Probably a bug in SonarLint with Java 17.
    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof ReadyEvent readyEvent) {
            onReady(readyEvent);
        } else if (event instanceof MessageReceivedEvent messageEvent) {
            if (messageEvent.isFromGuild()) {
                return;
            }

            if (messageEvent.isWebhookMessage() || messageEvent.getAuthor().isBot()) {
                return;
            }
            if (!channelMonitor.isMonitoringChannel(messageEvent.getChannel().getIdLong())) {
                logger.debug(
                        "Channel is not being monitored, ignoring message received in {} from {}",
                        messageEvent.getChannel().getName(), messageEvent.getAuthor());
                return;
            }
            if (channelMonitor.isChannelBusy(messageEvent.getChannel().getIdLong())) {
                logger.debug(
                        "Channel status is currently busy, ignoring message received in {} from {}",
                        messageEvent.getChannel().getName(), messageEvent.getAuthor());
                return;
            }
            channelMonitor.setChannelBusy(messageEvent.getChannel().getIdLong(),
                    messageEvent.getAuthor().getIdLong());
            displayStatus(channelMonitor.getStatusChannelFor(messageEvent.getGuild()));
            messageEvent.getMessage().reply(UserStrings.NEW_QUESTION.message()).queue();
        }
    }

    private @NotNull Optional<Message> getStatusMessageIn(@NotNull TextChannel channel) {
        if (!channelIdToMessageIdForStatus.containsKey(channel.getIdLong())) {
            return findExistingStatusMessage(channel);
        }
        return Optional.ofNullable(channelIdToMessageIdForStatus.get(channel.getIdLong()))
            .map(channel::retrieveMessageById)
            .map(RestAction::complete);
    }

    private @NotNull Optional<Message> findExistingStatusMessage(@NotNull TextChannel channel) {
        // will only run when bot starts, afterwards its stored in a map

        Optional<Message> statusMessage = FreeUtil
            .getChannelHistory(channel, FreeCommandConfig.MESSAGE_RETRIEVE_LIMIT)
            .flatMap(history -> history.stream()
                .filter(message -> !message.getEmbeds().isEmpty())
                .filter(message -> message.getAuthor().equals(channel.getJDA().getSelfUser()))
                // TODO the equals is not working, i believe its because there is no getTitleRaw()
                // .filter(message -> STATUS_TITLE.equals(message.getEmbeds().get(0).getTitle()))
                .findFirst());

        channelIdToMessageIdForStatus.put(channel.getIdLong(),
                statusMessage.map(Message::getIdLong).orElse(null));
        return statusMessage;
    }

    private void initChannelsToMonitor() {
        config.getFreeCommandConfig()
            .stream()
            .map(FreeCommandConfig::getMonitoredChannels)
            .flatMap(Collection::stream)
            .forEach(channelMonitor::addChannelToMonitor);
    }

    private void initStatusMessageChannels(@NotNull final JDA jda) {
        config.getFreeCommandConfig()
            .stream()
            .map(FreeCommandConfig::getStatusChannel)
            // throws IllegalStateException if the id's don't match TextChannels
            .map(id -> requiresTextChannel(jda, id))
            .forEach(channelMonitor::addChannelForStatus);
    }

    private @NotNull TextChannel requiresTextChannel(@NotNull JDA jda, long id) {
        TextChannel channel = jda.getTextChannelById(id);
        if (channel == null) {
            throw new IllegalStateException(
                    "The id '%d' supplied in the config file, is not a valid id for a TextChannel"
                        .formatted(id));
        }
        return channel;
    }
}
