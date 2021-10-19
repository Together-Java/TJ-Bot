package org.togetherjava.tjbot.commands.free;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// todo check if channel is valid before processing onSlashCommand (can SlashCommandVisibility be
// narrower than GUILD?)
// todo monitor all channels when list is empty? monitor none? (defaulting to all for testing)
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
    private final Map<Long, Long> guildToStatus;

    private boolean isRegistered;


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
        guildToStatus = config.stream()
            .map(FreeCommandConfig::getStatusChannel)
            // todo change Function.identity to JDA.getChannel.getGuild when viable.
            .collect(Collectors.toMap(Function.identity(), Function.identity()));
        channelsById = config.stream()
            .map(FreeCommandConfig::getMonitoredChannels)
            .flatMap(Collection::stream)
            .map(ChannelStatus::new)
            .collect(Collectors.toMap(ChannelStatus::getChannelID, Function.identity()));

        logger.debug("Config loaded: Display on {} and monitor {}", guildToStatus, channelsById);
        isRegistered = false;
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
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        logger.debug("/free used by {} on channel {}", event.getUser().getName(),
                event.getChannel().getName());
        if (!isRegistered) {
            event.getJDA().addEventListener(this);
        }
        long id = event.getChannel().getIdLong();
        ChannelStatus status = channelsById.get(id);
        if (status != null) {
            if (status.isBusy()) {
                status.busy(ChannelStatus.FREE);
                event.reply("This channel is now free.").queue();
            } else {
                Util.sendErrorMessage(event, "This channel is already free, no changes made");
            }
        } else {
            Util.sendErrorMessage(event,
                    "This channel is not being monitored for free/busy status");
        }
        drawStatus(event.getTextChannel());
    }

    public String drawStatus(TextChannel channel) {
        final Guild guild = channel.getGuild();

        List<GuildChannel> statusFor = guild.getChannels()
            .stream()
            .filter(channelInGuild -> channelsById.containsKey(channelInGuild.getIdLong()))
            .toList();

        String message = statusFor.stream()
            .map(GuildChannel::getIdLong)
            .map(channelsById::get)
            .map(channelStatus -> {
                // update name so that current name is used
                channelStatus
                    .setName(guild.getGuildChannelById(channelStatus.getChannelID()).getName());
                return channelStatus;
            })
            .map(ChannelStatus::toDiscord)
            .collect(Collectors.joining("\n"));

        channel.getLatestMessageIdLong();
        channel.sendMessage(message).queue();

        // Not implemented yet
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
                logger.debug("Channel status is currently busy, skipping message {}", status);
                return;
            }
            status.busy(ChannelStatus.BUSY);
            castEvent.getMessage().reply("The channel was free, please ask your question").queue();

        }
    }
}
