package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.commands.utils.MessageUtils;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.FreeCommandConfig;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

// todo (can SlashCommandVisibility be narrower than GUILD?)
// todo monitor all channels when list is empty? monitor none?
// todo (use other emojis? use images?)
// todo add command to add/remove/status channels to monitor
// todo test if message is a reply and don't mark as busy if it is
// todo add button query to confirm that message is new question not additional info for existing
// discussion before marking as busy
// todo add scheduled tasks to check last message every 15mins and mark as free if 1hr (2hrs?) has
// passed

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
public class FreeCommand extends SlashCommandAdapter implements EventListener {
    private static final Logger logger = LoggerFactory.getLogger(FreeCommand.class);
    private static final String STATUS_TITLE = "**__CHANNEL STATUS__**\n\n";
    private static final String FREE_COMMAND = "free";
    private static final Color FREE_COLOR = Color.decode("#CCCC00");

    // Map to store channel ID's, use Guild.getChannels() to guarantee order for display
    private final Map<Long, ChannelStatus> channelsById;
    private final Map<Long, Long> guildToStatusChannel;
    private final Map<Long, Long> channelToStatusMessage;

    private boolean isReady;


    /**
     * Creates an instance of FreeCommand.
     * <p>
     * This fetches configuration information from a json configuration file (see
     * {@link FreeCommandConfig}) for further details.
     */
    public FreeCommand() {
        super(FREE_COMMAND, "marks this channel as free for another user to ask a question",
                SlashCommandVisibility.GUILD);

        Collection<FreeCommandConfig> config = Config.getInstance().getFreeCommandConfig();
        guildToStatusChannel = new HashMap<>(); // JDA required to fetch guildID
        channelToStatusMessage = new HashMap<>(); // empty .... used to track status messages
        channelsById = config.stream()
            .map(FreeCommandConfig::getMonitoredChannels)
            .flatMap(Collection::stream)
            .map(ChannelStatus::new)
            .collect(Collectors.toMap(ChannelStatus::getChannelID, Function.identity()));

        isReady = false;
    }

    /**
     * Reaction to the 'onReady' event. This method binds the configurables to the
     * {@link net.dv8tion.jda.api.JDA} instance. Including fetching the names of the channels this
     * command monitors (as per the JDA)
     * <p>
     * It also updates the Status messages in their relevant channels, so that the message is up to
     * date.
     * <p>
     * This also registers a new listener on the {@link net.dv8tion.jda.api.JDA}, this should be
     * removed when the code base supports additional functionality
     *
     * @param event the event this method reacts to
     */
    @Override
    public void onReady(@NotNull final ReadyEvent event) {
        // todo remove this when onGuildMessageRecieved has another access point
        event.getJDA().addEventListener(this);

        Collection<FreeCommandConfig> config = Config.getInstance().getFreeCommandConfig();
        config.stream()
            .map(FreeCommandConfig::getStatusChannel)
            .distinct() // not necessary? validates user input, since input is from file
            // should this test if already present first?
            .forEach(id -> guildToStatusChannel
                .put(event.getJDA().getGuildChannelById(id).getGuild().getIdLong(), id));

        logger.debug("Config loaded:\nDisplay statuses on {}\nand monitor channels {}",
                guildToStatusChannel, channelsById);

        // not collecting to map because onReady is not run during construction
        guildToStatusChannel.values()
            .stream()
            .map(event.getJDA()::getTextChannelById)
            .map(this::getStatusMessageIn)
            .flatMap(Optional::stream)
            .forEach(message -> channelToStatusMessage.put(message.getChannel().getIdLong(),
                    message.getIdLong()));


        guildToStatusChannel.values()
            .stream()
            .map(event.getJDA()::getTextChannelById)
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
     */
    @Override
    public void onSlashCommand(@NotNull final SlashCommandEvent event) {
        logger.debug("/free used by {} on channel {}", event.getUser().getName(),
                event.getChannel().getName());
        if (!shouldHandle(event))
            return;

        long id = event.getChannel().getIdLong();
        // do not need to test if key is present, shouldHandle(event) already does.
        ChannelStatus status = channelsById.get(id);
        if (status.isBusy()) {
            status.busy(ChannelStatus.FREE);
            displayStatus(getStatusChannelFor(event.getGuild()));
            event.reply("This channel is now free.").queue();
        } else {
            Util.sendErrorMessage(event, "This channel is already free, no changes made");
        }
    }

    /**
     * Private method to test event to see if it should be processed.
     *
     * Will respond to users describing the problem if the event should not be processed.
     *
     * @param event the event to test for validity.
     * @return true if the event should be processed false otherwise.
     */
    private boolean shouldHandle(@NotNull final SlashCommandEvent event) {
        if (!isReady) {
            logger.debug(
                    "Slash command requested by {} in {}(channel: {}) before command is ready.",
                    event.getUser().getIdLong(), event.getGuild(), event.getChannel().getName());
            Util.sendErrorMessage(event, "Command not ready please try again in a minute");
            return false;
        }
        if (!guildToStatusChannel.containsKey(event.getGuild().getIdLong())) {
            logger.error(
                    "Slash command used by {} in {}(channel: {}) when guild is not registered in Free Command",
                    event.getUser().getIdLong(), event.getGuild(), event.getChannel().getName());
            Util.sendErrorMessage(event,
                    "This guild (%s) is not configured to use the '/free' command, please add entries in the config, restart the bot and try again."
                        .formatted(event.getGuild().getName()));
            return false;
        }
        if (!channelsById.containsKey(event.getChannel().getIdLong())) {
            logger.debug("'/free called in unregistered channel {}({})", event.getGuild().getName(),
                    event.getChannel().getName());
            Util.sendErrorMessage(event,
                    "This channel is not being monitored for free/busy status");
            return false;
        }

        return true;
    }

    /**
     * Builds the message that will be displayed for users.
     * <p>
     * This method dynamically builds the status message as per the current values on the guild,
     * including the channel categories. This method will detect any changes made on the guild and
     * represent those changes in the status message.
     *
     * @param channel the text channel the status message will be posted in.
     */
    public void displayStatus(@NotNull TextChannel channel) {
        final Guild guild = channel.getGuild();

        String messageTxt = buildStatusMessage(guild);
        MessageEmbed embed = MessageUtils.generateEmbed(STATUS_TITLE, messageTxt,
                channel.getJDA().getSelfUser(), FREE_COLOR);

        long latestMessageId = channel.getLatestMessageIdLong();
        Optional<Message> statusMessage = getStatusMessageIn(channel);
        channel.sendMessage("Message selected is: " + statusMessage).queue();
        if (statusMessage.isPresent()) {
            Message message = statusMessage.get();
            if (message.getIdLong() != latestMessageId) {
                message.delete();
            } else {
                message.editMessageEmbeds(embed);
            }
        }

        channel.sendMessageEmbeds(embed).queue();
    }

    public String buildStatusMessage(@NotNull Guild guild) {
        if (!guildToStatusChannel.containsKey(guild.getIdLong())) {
            throw new IllegalArgumentException(
                    "The guild '%s(%s)' is not registered in the free command system"
                        .formatted(guild.getName(), guild.getIdLong()));
        }

        List<ChannelStatus> statusFor = guild.getChannels()
            .stream()
            .map(GuildChannel::getIdLong)
            .filter(channelsById::containsKey)
            .map(channelsById::get)
            .toList();

        // update name so that current channel name is used
        statusFor.forEach(channelStatus -> channelStatus
            .setName(guild.getGuildChannelById(channelStatus.getChannelID()).getAsMention()));

        // dynamically separate channels by channel categories
        StringBuilder sb = new StringBuilder();
        String categoryName = "";
        for (ChannelStatus status : statusFor) {
            Category category = guild.getGuildChannelById(status.getChannelID()).getParent();
            if (category != null && !category.getName().equals(categoryName)) {
                categoryName = category.getName();
                sb.append("\n__").append(categoryName).append("__\n");
            }
            sb.append(status.toDiscord()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Method for responding to 'onGuildMessageRecieved' this will need to be replaced by a more
     * appropriate method when the bot has more functionality.
     * <p>
     * Marks channels as busy when a user posts a message in a monitored channel that is currently free.
     *
     * @param event the generic event that includes the 'onGuildMessageReceived'.
     */
    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof GuildMessageReceivedEvent guildEvent) {
            if (guildEvent.isWebhookMessage() || guildEvent.getAuthor().isBot()) {
                return;
            }
            ChannelStatus status = channelsById.get(guildEvent.getChannel().getIdLong());
            if (status == null || status.isBusy()) {
                logger.debug("Channel status is currently busy, ignoring message received. {} ",
                        status);
                return;
            }
            status.busy(ChannelStatus.BUSY);
            displayStatus(getStatusChannelFor(guildEvent.getGuild()));
            guildEvent.getMessage().reply("The channel was free, please ask your question").queue();
        }
    }

    private TextChannel getStatusChannelFor(@NotNull final Guild guild) {
        // todo add error checking for invalid keys ??
        return guild.getTextChannelById(guildToStatusChannel.get(guild.getIdLong()));
    }

    private Optional<Message> getStatusMessageIn(@NotNull TextChannel channel) {
        if (channelToStatusMessage.containsKey(channel.getIdLong())) {
            Long id = channelToStatusMessage.get(channel.getIdLong());
            if (id == null) {
                return Optional.empty();
            }
            Message message = channel.getHistory().getMessageById(id);
            if (message == null) {
                return Optional.empty();
            }
            return Optional.of(message);
        }
        return findExistingStatusMessage(channel);
    }

    private Optional<Message> findExistingStatusMessage(@NotNull TextChannel channel) {
        // will only run when bots starts, afterwards its stored in a map
        Optional<Message> result = channel.getHistory()
            .retrievePast(100)
            .map(history -> history.stream()
                .filter(message -> !message.getEmbeds().isEmpty())
                .filter(message -> message.getAuthor().equals(channel.getJDA().getSelfUser()))
                .filter(message -> message.getEmbeds().get(0).getTitle().equals(STATUS_TITLE))
                .findFirst())
            .complete();

        Message mapping = result.orElse(null);
        channelToStatusMessage.put(channel.getIdLong(),
                mapping == null ? null : mapping.getIdLong());
        return result;
    }
}
