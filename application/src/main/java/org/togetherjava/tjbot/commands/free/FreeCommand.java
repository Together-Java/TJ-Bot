package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
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
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.FreeCommandConfig;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// todo (can SlashCommandVisibility be
// narrower than GUILD?)
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
    private static final String FREE_COMMAND = "free";

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
            .forEach(this::drawStatus);

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
            drawStatus(getStatusChannelFor(event.getGuild()));
            event.reply("This channel is now free.").queue();
        } else {
            Util.sendErrorMessage(event, "This channel is already free, no changes made");
        }
    }

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
                    "Slash command used by {} in {}(channel: {}) when guild is not registed in Free Command",
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

    public String drawStatus(TextChannel channel) {
        final Guild guild = channel.getGuild();

        List<ChannelStatus> statusFor = guild.getChannels()
            .stream()
            .map(GuildChannel::getIdLong)
            .filter(channelsById::containsKey)
            .map(channelsById::get)
            .toList();

        // update name so that current channel name is used
        statusFor.forEach(channelStatus -> channelStatus
            .setName(guild.getGuildChannelById(channelStatus.getChannelID()).getName()));
        String message =
                statusFor.stream().map(ChannelStatus::toDiscord).collect(Collectors.joining("\n"));

        channel.getLatestMessageIdLong();
        channel.sendMessage(message).queue();

        return message;
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof GuildMessageReceivedEvent castEvent) {
            if (castEvent.isWebhookMessage() || castEvent.getAuthor().isBot()) {
                return;
            }
            ChannelStatus status = channelsById.get(castEvent.getChannel().getIdLong());
            if (status == null || status.isBusy()) {
                logger.debug("Channel status is currently busy, ignoring message received. {} ",
                        status);
                return;
            }
            status.busy(ChannelStatus.BUSY);
            drawStatus(getStatusChannelFor(castEvent.getGuild()));
            castEvent.getMessage().reply("The channel was free, please ask your question").queue();
        }
    }

    private TextChannel getStatusChannelFor(@NotNull final Guild guild) {
        // todo add error checking for invalid keys ??
        return guild.getTextChannelById(guildToStatusChannel.get(guild.getIdLong()));
    }

    private Optional<Message> getStatusMessageIn(TextChannel channel) {
        channel.getHistory().retrievePast(100).queue();

        return Optional.empty();
    }
}
