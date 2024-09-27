package org.togetherjava.tjbot.features.help;

import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.toMap;

public class HelpThreadStatsCommand extends SlashCommandAdapter {

    public static final String COMMAND_NAME = "help-thread-stats";
    public static final String DURATION_OPTION = "duration-option";
    public static final String DURATION_SUBCOMMAND = "duration";
    public static final String OPTIONAL_SUBCOMMAND_GROUP = "optional";
    private final Map<String, Subcommand> nameToSubcommand;

    public HelpThreadStatsCommand() {
        super(COMMAND_NAME, "Display Help Thread Statistics", CommandVisibility.GUILD);
        OptionData durationOption =
                new OptionData(OptionType.STRING, DURATION_OPTION, "optional duration", false)
                    .setMinLength(1);
        SubcommandData duration = Subcommand.DURATION.toSubcommandData().addOptions(durationOption);
        SubcommandGroupData optionalCommands =
                new SubcommandGroupData(OPTIONAL_SUBCOMMAND_GROUP, "optional commands")
                    .addSubcommands(duration);
        getData().addSubcommandGroups(optionalCommands);
        nameToSubcommand = streamSubcommands()
            .collect(Collectors.toMap(Subcommand::getCommandName, Function.identity()));
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        List<ForumChannel> forumChannels =
                Objects.requireNonNull(event.getGuild()).getForumChannels();
        Subcommand invokedSubcommand = nameToSubcommand.get(event.getSubcommandName());
        OffsetDateTime startDate = OffsetDateTime.MIN;
        if (Objects.nonNull(invokedSubcommand) && invokedSubcommand.equals(Subcommand.DURATION)
                && Objects.nonNull(event.getOption(DURATION_OPTION))) {
            startDate =
                    OffsetDateTime.now().minusDays(event.getOption(DURATION_OPTION).getAsLong());
        }
        ForumTag mostPopularTag = getMostPopularForumTag(forumChannels, startDate);
        Double averageNumberOfParticipants =
                getAverageNumberOfParticipantsPerThread(forumChannels, startDate);
        Integer totalNumberOfThreads =
                getThreadChannelsStream(forumChannels, startDate).toList().size();
        Long emptyThreads = getThreadsWithNoParticipants(forumChannels, startDate);
        Integer totalMessages = getTotalNumberOfMessages(forumChannels, startDate);
        Double averageNumberOfMessages = Double.valueOf(totalMessages) / totalNumberOfThreads;
        Double averageThreadLifecycle = getAverageThreadLifecycle(forumChannels, startDate);
        String statistics =
                "Most Popular Tag: %s%nAverage Number Of Participants: %.2f%nEmpty Threads: %s%nAverage Number Of Messages: %.2f%nAverage Thread Lifecycle: %.2f"
                    .formatted(mostPopularTag.getName(), averageNumberOfParticipants, emptyThreads,
                            averageNumberOfMessages, averageThreadLifecycle);
        event.reply(statistics).delay(2, TimeUnit.SECONDS).queue();
    }

    private ForumTag getMostPopularForumTag(List<ForumChannel> forumChannels,
            OffsetDateTime startDate) {
        Map<ForumTag, Integer> tagCount = getThreadChannelsStream(forumChannels, startDate)
            .flatMap((threadChannel -> threadChannel.getAppliedTags().stream()))
            .collect(toMap(Function.identity(), tag -> 1, Integer::sum));
        return Collections.max(tagCount.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    private Double getAverageNumberOfParticipantsPerThread(List<ForumChannel> forumChannels,
            OffsetDateTime startDate) {
        return getThreadChannelsStream(forumChannels, startDate)
            .collect(averagingDouble((ThreadChannel::getMemberCount)));
    }

    private Long getThreadsWithNoParticipants(List<ForumChannel> forumChannels,
            OffsetDateTime startDate) {
        return getThreadChannelsStream(forumChannels, startDate)
            .filter(threadChannel -> threadChannel.getMemberCount() > 1)
            .count();
    }

    private Integer getTotalNumberOfMessages(List<ForumChannel> forumChannels,
            OffsetDateTime startDate) {
        return getThreadChannelsStream(forumChannels, startDate)
            .mapToInt(ThreadChannel::getMessageCount)
            .sum();
    }

    private Double getAverageThreadLifecycle(List<ForumChannel> forumChannels,
            OffsetDateTime startDate) {
        return getThreadChannelsStream(forumChannels, startDate).filter(ThreadChannel::isArchived)
            .mapToDouble(threadChannel -> calculateDurationInDays(
                    threadChannel.getTimeArchiveInfoLastModified(), threadChannel.getTimeCreated()))
            .average()
            .orElse(0);
    }

    private Double calculateDurationInDays(OffsetDateTime t1, OffsetDateTime t2) {
        long time1 = t1.toEpochSecond();
        long time2 = t2.toEpochSecond();
        return (time1 - time2) / 86400.0;
    }

    private Stream<ThreadChannel> getThreadChannelsStream(List<ForumChannel> forumChannels,
            OffsetDateTime startDate) {
        return forumChannels.stream()
            .flatMap(forumChannel -> getAllThreadChannels(forumChannel).stream())
            .filter(threadChannel -> threadChannel.getTimeCreated().isAfter(startDate));
    }

    private Set<ThreadChannel> getAllThreadChannels(ForumChannel forumChannel) {
        Set<ThreadChannel> threadChannels = new HashSet<>(forumChannel.getThreadChannels());
        Optional<ThreadChannelPaginationAction> publicThreadChannels =
                Optional.of(forumChannel.retrieveArchivedPublicThreadChannels());
        publicThreadChannels.ifPresent(threads -> threads.forEach(threadChannels::add));
        return threadChannels;
    }

    private static Stream<Subcommand> streamSubcommands() {
        return Arrays.stream(Subcommand.values());
    }

    enum Subcommand {
        DURATION(DURATION_SUBCOMMAND, "Set the duration");

        private final String commandName;
        private final String description;

        Subcommand(String commandName, String description) {
            this.commandName = commandName;
            this.description = description;
        }

        String getCommandName() {
            return commandName;
        }

        SubcommandData toSubcommandData() {
            return new SubcommandData(commandName, description);
        }
    }
}
