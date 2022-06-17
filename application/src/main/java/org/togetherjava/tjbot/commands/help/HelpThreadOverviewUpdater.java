package org.togetherjava.tjbot.commands.help;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.MessageReceiverAdapter;
import org.togetherjava.tjbot.commands.Routine;
import org.togetherjava.tjbot.config.Config;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provides and updates an overview of all active questions in an overview channel.
 *
 * The process runs on a schedule, but is also triggered whenever a new question has been asked in
 * the staging channel.
 *
 * Active questions are automatically picked up and grouped by categories.
 */
public final class HelpThreadOverviewUpdater extends MessageReceiverAdapter implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(HelpThreadOverviewUpdater.class);

    private static final String STATUS_TITLE = "Active questions";
    private static final int OVERVIEW_QUESTION_LIMIT = 150;

    private final HelpSystemHelper helper;
    private final List<String> allCategories;

    private static final ScheduledExecutorService UPDATE_SERVICE =
            Executors.newSingleThreadScheduledExecutor();

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param helper the helper to use
     */
    public HelpThreadOverviewUpdater(@NotNull Config config, @NotNull HelpSystemHelper helper) {
        super(Pattern.compile(config.getHelpSystem().getStagingChannelPattern()));

        allCategories = config.getHelpSystem().getCategories();
        this.helper = helper;
    }

    @Override
    public @NotNull Schedule createSchedule() {
        return new Schedule(ScheduleMode.FIXED_RATE, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void runRoutine(@NotNull JDA jda) {
        jda.getGuildCache().forEach(this::updateOverviewForGuild);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // Update whenever a thread was created
        Message message = event.getMessage();
        if (message.getType() != MessageType.THREAD_CREATED) {
            return;
        }

        // Cleanup the status messages
        message.delete().queue();

        // Thread creation can sometimes take a bit longer than the actual message, so that
        // "getThreadChannels()"
        // would not pick it up, hence we execute the update with some slight delay.
        UPDATE_SERVICE.schedule(() -> updateOverviewForGuild(event.getGuild()), 2,
                TimeUnit.SECONDS);
    }

    private void updateOverviewForGuild(@NotNull Guild guild) {
        Optional<TextChannel> maybeStagingChannel =
                handleRequireChannel(ChannelType.STAGING, guild);
        Optional<TextChannel> maybeOverviewChannel =
                handleRequireChannel(ChannelType.OVERVIEW, guild);

        if (maybeStagingChannel.isEmpty() || maybeOverviewChannel.isEmpty()) {
            return;
        }

        updateOverview(maybeStagingChannel.orElseThrow(), maybeOverviewChannel.orElseThrow());
    }

    private @NotNull Optional<TextChannel> handleRequireChannel(@NotNull ChannelType channelType,
            @NotNull Guild guild) {
        Predicate<String> isChannelName = switch (channelType) {
            case OVERVIEW -> helper::isOverviewChannelName;
            case STAGING -> helper::isStagingChannelName;
        };
        String channelPattern = switch (channelType) {
            case OVERVIEW -> helper.getOverviewChannelPattern();
            case STAGING -> helper.getStagingChannelPattern();
        };

        Optional<TextChannel> maybeChannel = guild.getTextChannelCache()
            .stream()
            .filter(channel -> isChannelName.test(channel.getName()))
            .findAny();

        if (maybeChannel.isEmpty()) {
            logger.warn(
                    "Unable to update help thread overview, did not find a {} channel matching the configured pattern '{}' for guild '{}'",
                    channelType, channelPattern, guild.getName());
            return Optional.empty();
        }

        return maybeChannel;
    }

    private void updateOverview(@NotNull IThreadContainer stagingChannel,
            @NotNull MessageChannel overviewChannel) {
        logger.debug("Updating overview of active questions");

        List<ThreadChannel> activeThreads = stagingChannel.getThreadChannels()
            .stream()
            .filter(Predicate.not(ThreadChannel::isArchived))
            .toList();

        logger.debug("Found {} active questions", activeThreads.size());

        MessageEmbed embed = new EmbedBuilder().setTitle(STATUS_TITLE)
            .setDescription(createDescription(activeThreads))
            .setColor(HelpSystemHelper.AMBIENT_COLOR)
            .build();

        getStatusMessage(overviewChannel).flatMap(maybeStatusMessage -> {
            logger.debug("Sending the updated question overview");
            if (maybeStatusMessage.isEmpty()) {
                return overviewChannel.sendMessageEmbeds(embed);
            }

            String statusMessageId = maybeStatusMessage.orElseThrow().getId();
            return overviewChannel.editMessageEmbedsById(statusMessageId, embed);
        }).queue();
    }

    private @NotNull String createDescription(@NotNull Collection<ThreadChannel> activeThreads) {
        if (activeThreads.isEmpty()) {
            return "Currently none.";
        }

        return activeThreads.stream()
            .sorted(Comparator.comparing(ThreadChannel::getTimeCreated).reversed())
            .limit(OVERVIEW_QUESTION_LIMIT)
            .collect(Collectors
                .groupingBy(thread -> helper.getCategoryOfChannel(thread).orElse("Uncategorized")))
            .entrySet()
            .stream()
            .map(CategoryWithThreads::ofEntry)
            .sorted(Comparator.comparingInt(categoryWithThreads -> {
                // Order based on config, unknown categories last
                int indexOfCategory = allCategories.indexOf(categoryWithThreads.category);
                if (indexOfCategory == -1) {
                    return Integer.MAX_VALUE;
                }
                return indexOfCategory;
            }))
            .map(CategoryWithThreads::toDiscordString)
            .collect(Collectors.joining("\n\n"));
    }

    private static @NotNull RestAction<Optional<Message>> getStatusMessage(
            @NotNull MessageChannel channel) {
        return channel.getHistory()
            .retrievePast(1)
            .map(messages -> messages.stream()
                .findFirst()
                .filter(HelpThreadOverviewUpdater::isStatusMessage));
    }

    private static boolean isStatusMessage(@NotNull Message message) {
        if (!message.getAuthor().equals(message.getJDA().getSelfUser())) {
            return false;
        }

        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.isEmpty()) {
            return false;
        }

        MessageEmbed embed = embeds.get(0);
        return STATUS_TITLE.equals(embed.getTitle());
    }

    private enum ChannelType {
        OVERVIEW,
        STAGING
    }

    private record CategoryWithThreads(@NotNull String category,
            @NotNull List<ThreadChannel> threads) {

        String toDiscordString() {
            String threadListText = threads.stream()
                .map(ThreadChannel::getAsMention)
                .collect(Collectors.joining("\n• ", "• ", ""));

            return "**%s**:%n%s".formatted(category, threadListText);
        }

        static @NotNull CategoryWithThreads ofEntry(
                Map.@NotNull Entry<String, ? extends List<ThreadChannel>> categoryAndThreads) {
            return new CategoryWithThreads(categoryAndThreads.getKey(),
                    categoryAndThreads.getValue());
        }
    }
}
