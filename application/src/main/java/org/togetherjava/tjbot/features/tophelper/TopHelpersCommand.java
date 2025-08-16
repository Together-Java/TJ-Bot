package org.togetherjava.tjbot.features.tophelper;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import javax.annotation.Nullable;

import java.time.Instant;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Command that displays the top helpers of a given time range.
 * <p>
 * Top helpers are measured by their message length in help channels, as set by
 * {@link TopHelpersMessageListener}.
 */
public final class TopHelpersCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TopHelpersCommand.class);
    private static final String COMMAND_NAME = "top-helpers";
    private static final String SUBCOMMAND_SHOW_NAME = "show";
    private static final String SUBCOMMAND_ASSIGN_NAME = "assign";
    private static final String MONTH_OPTION = "at-month";

    private final TopHelpersService service;
    private final TopHelpersAssignmentRoutine assignmentRoutine;

    /**
     * Creates a new instance.
     *
     * @param service the service that can compute top helpers
     * @param assignmentRoutine the routine that can assign top helpers automatically
     */
    public TopHelpersCommand(TopHelpersService service,
            TopHelpersAssignmentRoutine assignmentRoutine) {
        super(COMMAND_NAME, "Manages top helpers", CommandVisibility.GUILD);

        OptionData monthData = new OptionData(OptionType.STRING, MONTH_OPTION,
                "the month to compute for, by default the last month", false);
        Arrays.stream(Month.values())
            .forEach(month -> monthData.addChoice(
                    month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US), month.name()));

        getData().addSubcommands(
                new SubcommandData(SUBCOMMAND_SHOW_NAME,
                        "Lists top helpers for the last month, or a given month")
                    .addOptions(monthData),
                new SubcommandData(SUBCOMMAND_ASSIGN_NAME,
                        "Automatically assigns top helpers for the last month"));

        this.service = service;
        this.assignmentRoutine = assignmentRoutine;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        switch (event.getSubcommandName()) {
            case SUBCOMMAND_SHOW_NAME -> showTopHelpers(event);
            case SUBCOMMAND_ASSIGN_NAME -> assignTopHelpers(event);
            default -> throw new AssertionError(
                    "Unexpected subcommand '%s'".formatted(event.getSubcommandName()));
        }
    }

    private void showTopHelpers(SlashCommandInteractionEvent event) {
        OptionMapping atMonthData = event.getOption(MONTH_OPTION);

        TopHelpersService.TimeRange timeRange =
                TopHelpersService.TimeRange.ofMonth(computeMonth(atMonthData));
        List<TopHelpersService.TopHelperResult> topHelpers = service.computeTopHelpersDescending(
                event.getGuild().getIdLong(), timeRange.start(), timeRange.end());

        if (topHelpers.isEmpty()) {
            event
                .reply("No entries for the selected time range (%s)."
                    .formatted(timeRange.description()))
                .queue();
            return;
        }
        event.deferReply().queue();

        service.retrieveTopHelperMembers(topHelpers, event.getGuild())
            .onError(error -> handleError(error, event))
            .onSuccess(members -> handleTopHelpers(topHelpers, members, timeRange, event));
    }

    private void assignTopHelpers(SlashCommandInteractionEvent event) {
        event.reply("Automatic Top Helper assignment dialog has started")
            .setEphemeral(true)
            .queue();
        assignmentRoutine.startDialogFor(Objects.requireNonNull(event.getGuild()));
    }

    private static Month computeMonth(@Nullable OptionMapping atMonthData) {
        if (atMonthData != null) {
            return Month.valueOf(atMonthData.getAsString());
        }

        // Previous month
        return Instant.now().atZone(ZoneOffset.UTC).minusMonths(1).getMonth();
    }

    private static void handleError(Throwable error, IDeferrableCallback event) {
        logger.warn("Failed to compute top-helpers", error);
        event.getHook().editOriginal("Sorry, something went wrong.").queue();
    }

    private void handleTopHelpers(Collection<TopHelpersService.TopHelperResult> topHelpers,
            Collection<? extends Member> members, TopHelpersService.TimeRange timeRange,
            IDeferrableCallback event) {
        String message = """
                ```java
                // for %s
                %s
                ```""".formatted(timeRange.description(),
                service.asAsciiTable(topHelpers, members, true));
        event.getHook().editOriginal(message).queue();
    }
}
