package org.togetherjava.tjbot.features.tophelper;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.Routine;

import java.time.Instant;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// TODO Javadoc everywhere
public final class TopHelpersAssignmentRoutine implements Routine {
    private static final Logger logger = LoggerFactory.getLogger(TopHelpersAssignmentRoutine.class);
    private static final int CHECK_AT_HOUR = 12;

    private final Config config;
    private final TopHelpersService service;
    private final Predicate<String> assignmentChannelNamePredicate;

    public TopHelpersAssignmentRoutine(Config config, TopHelpersService service) {
        this.config = config;
        this.service = service;
        assignmentChannelNamePredicate =
                Pattern.compile(config.getTopHelperAssignmentChannelPattern()).asMatchPredicate();
    }

    @Override
    public Schedule createSchedule() {
        return Schedule.atFixedHour(CHECK_AT_HOUR);
    }

    @Override
    public void runRoutine(JDA jda) {
        int dayOfMonth = Instant.now().atOffset(ZoneOffset.UTC).getDayOfMonth();
        if (dayOfMonth != 1) {
            return;
        }

        jda.getGuilds().forEach(this::startDialogFor);
    }

    public void startDialogFor(Guild guild) {
        Optional<TextChannel> assignmentChannel = guild.getTextChannelCache()
            .stream()
            .filter(channel -> assignmentChannelNamePredicate.test(channel.getName()))
            .findAny();

        assignmentChannel.ifPresentOrElse(this::startDialogIn, () -> logger.warn(
                "Unable to assign Top Helpers, did not find an assignment channel matching the configured pattern '{}' for guild '{}'",
                config.getTopHelperAssignmentChannelPattern(), guild.getName()));
    }

    private void startDialogIn(TextChannel channel) {
        Month previousMonth = Instant.now().atZone(ZoneOffset.UTC).minusMonths(1).getMonth();
        TopHelpersService.TimeRange timeRange =
                TopHelpersService.TimeRange.fromMonth(previousMonth);
        List<TopHelpersService.TopHelperResult> topHelpers = service.computeTopHelpersDescending(
                channel.getGuild().getIdLong(), timeRange.start(), timeRange.end());

        if (topHelpers.isEmpty()) {
            channel.sendMessage(
                    "Wanted to assign Top Helpers, but there seems to be no entries for that time range (%s)."
                        .formatted(timeRange.description()))
                .queue();
            return;
        }

        logger.error("TODO Implement me");
    }
}
