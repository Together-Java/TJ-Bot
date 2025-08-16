package org.togetherjava.tjbot.features.tophelper;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.Routine;
import org.togetherjava.tjbot.features.UserInteractionType;
import org.togetherjava.tjbot.features.UserInteractor;
import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.features.componentids.ComponentIdInteractor;

import javax.annotation.Nullable;

import java.time.Instant;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// TODO Javadoc everywhere
public final class TopHelpersAssignmentRoutine implements Routine, UserInteractor {
    private static final Logger logger = LoggerFactory.getLogger(TopHelpersAssignmentRoutine.class);
    private static final int CHECK_AT_HOUR = 12;

    private final Config config;
    private final TopHelpersService service;
    private final Predicate<String> assignmentChannelNamePredicate;
    private final ComponentIdInteractor componentIdInteractor;

    public TopHelpersAssignmentRoutine(Config config, TopHelpersService service) {
        this.config = config;
        this.service = service;

        assignmentChannelNamePredicate =
                Pattern.compile(config.getTopHelperAssignmentChannelPattern()).asMatchPredicate();

        componentIdInteractor = new ComponentIdInteractor(getInteractionType(), getName());
    }

    @Override
    public String getName() {
        return "top-helper-assignment";
    }

    @Override
    public UserInteractionType getInteractionType() {
        return UserInteractionType.OTHER;
    }

    @Override
    public void acceptComponentIdGenerator(ComponentIdGenerator generator) {
        componentIdInteractor.acceptComponentIdGenerator(generator);
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
        Guild guild = channel.getGuild();

        Month previousMonth = Instant.now().atZone(ZoneOffset.UTC).minusMonths(1).getMonth();
        TopHelpersService.TimeRange timeRange =
                TopHelpersService.TimeRange.fromMonth(previousMonth);
        List<TopHelpersService.TopHelperResult> topHelpers = service
            .computeTopHelpersDescending(guild.getIdLong(), timeRange.start(), timeRange.end());

        if (topHelpers.isEmpty()) {
            channel.sendMessage(
                    "Wanted to assign Top Helpers, but there seems to be no entries for that time range (%s)."
                        .formatted(timeRange.description()))
                .queue();
            return;
        }

        service.retrieveTopHelperMembers(topHelpers, guild)
            .onError(error -> handleError(error, channel))
            .onSuccess(members -> sendSelectionMenu(topHelpers, members, timeRange, channel));
    }

    private static void handleError(Throwable error, TextChannel channel) {
        logger.warn("Failed to compute top-helpers for automatic assignment", error);
        channel.sendMessage("Wanted to assign Top Helpers, but something went wrong.").queue();
    }

    private void sendSelectionMenu(Collection<TopHelpersService.TopHelperResult> topHelpers,
            Collection<? extends Member> members, TopHelpersService.TimeRange timeRange,
            TextChannel channel) {
        String content = """
                Starting assignment of Top Helpers for %s:
                ```java
                %s
                ```""".formatted(timeRange.description(),
                service.asAsciiTable(topHelpers, members, false));

        StringSelectMenu.Builder menu =
                StringSelectMenu.create(componentIdInteractor.generateComponentId("foo"))
                    .setPlaceholder("Select the Top Helpers")
                    .setMinValues(1)
                    .setMaxValues(topHelpers.size());

        Map<Long, Member> userIdToMember =
                members.stream().collect(Collectors.toMap(Member::getIdLong, Function.identity()));
        topHelpers.stream()
            .map(topHelper -> topHelperToSelectOption(topHelper,
                    userIdToMember.get(topHelper.authorId())))
            .forEach(menu::addOptions);

        MessageCreateData message = new MessageCreateBuilder().setContent(content)
            .addActionRow(menu.build())
            .addActionRow(
                    Button.danger(componentIdInteractor.generateComponentId("cancel"), "Cancel"))
            .build();

        channel.sendMessage(message).queue();
    }

    private static SelectOption topHelperToSelectOption(TopHelpersService.TopHelperResult topHelper,
            @Nullable Member member) {
        String id = Long.toString(topHelper.authorId());

        String name = TopHelpersService.getUsernameDisplay(member);
        long messageLengths = topHelper.messageLengths().longValue();
        String label = messageLengths + " - " + name;

        return SelectOption.of(label, id);
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        // TODO Implement
        logger.error("TODO Button clicked: {}", args);
    }

    @Override
    public void onStringSelectSelection(StringSelectInteractionEvent event, List<String> args) {
        // TODO Implement
        logger.error("TODO Menu used: {}", args);
    }
}
