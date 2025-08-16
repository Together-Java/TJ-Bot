package org.togetherjava.tjbot.features.tophelper;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.TopHelpersConfig;
import org.togetherjava.tjbot.features.Routine;
import org.togetherjava.tjbot.features.UserInteractionType;
import org.togetherjava.tjbot.features.UserInteractor;
import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.features.componentids.ComponentIdInteractor;

import javax.annotation.Nullable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO Javadoc everywhere
public final class TopHelpersAssignmentRoutine implements Routine, UserInteractor {
    private static final Logger logger = LoggerFactory.getLogger(TopHelpersAssignmentRoutine.class);
    private static final int CHECK_AT_HOUR = 12;
    private static final String CANCEL_BUTTON_NAME = "cancel";
    private static final String YES_MESSAGE_BUTTON_NAME = "yes-message";
    private static final String NO_MESSAGE_BUTTON_NAME = "no-message";

    private final TopHelpersConfig config;
    private final TopHelpersService service;
    private final Predicate<String> roleNamePredicate;
    private final Predicate<String> assignmentChannelNamePredicate;
    private final Predicate<String> announcementChannelNamePredicate;
    private final ComponentIdInteractor componentIdInteractor;

    public TopHelpersAssignmentRoutine(Config config, TopHelpersService service) {
        this.config = config.getTopHelpers();
        this.service = service;

        roleNamePredicate = Pattern.compile(this.config.getRolePattern()).asMatchPredicate();
        assignmentChannelNamePredicate =
                Pattern.compile(this.config.getAssignmentChannelPattern()).asMatchPredicate();
        announcementChannelNamePredicate =
                Pattern.compile(this.config.getAnnouncementChannelPattern()).asMatchPredicate();

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
                config.getAssignmentChannelPattern(), guild.getName()));
    }

    private void startDialogIn(TextChannel channel) {
        Guild guild = channel.getGuild();

        TopHelpersService.TimeRange timeRange = TopHelpersService.TimeRange.ofPreviousMonth();
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
            .onSuccess(members -> sendSelectionMenu(topHelpers, members, timeRange, channel))
            .onError(error -> {
                logger.warn("Failed to compute top-helpers for automatic assignment", error);
                channel.sendMessage("Wanted to assign Top Helpers, but something went wrong.")
                    .queue();
            });
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
                StringSelectMenu.create(componentIdInteractor.generateComponentId())
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
            .addActionRow(Button
                .danger(componentIdInteractor.generateComponentId(CANCEL_BUTTON_NAME), "Cancel"))
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
        event.deferEdit().queue();
        String name = args.getFirst();

        switch (name) {
            case CANCEL_BUTTON_NAME -> endFlow(event, "cancelled");
            case NO_MESSAGE_BUTTON_NAME -> endFlow(event, "not posting an announcement");
            case YES_MESSAGE_BUTTON_NAME -> prepareAnnouncement(event, args);
            default -> throw new AssertionError("Unknown button name: " + name);
        }
    }

    @Override
    public void onStringSelectSelection(StringSelectInteractionEvent event, List<String> args) {
        Guild guild = Objects.requireNonNull(event.getGuild());
        Set<Long> selectedTopHelperIds = event.getSelectedOptions()
            .stream()
            .map(SelectOption::getValue)
            .map(Long::parseLong)
            .collect(Collectors.toSet());

        Optional<Role> topHelperRole = guild.getRoles()
            .stream()
            .filter(role -> roleNamePredicate.test(role.getName()))
            .findAny();
        if (topHelperRole.isEmpty()) {
            logger.warn(
                    "Unable to assign Top Helpers, did not find a role matching the configured pattern '{}' for guild '{}'",
                    config.getRolePattern(), guild.getName());
            event.reply("Wanted to assign Top Helpers, but something went wrong.").queue();
            return;
        }

        event.deferEdit().queue();
        guild.findMembersWithRoles(List.of(topHelperRole.orElseThrow()))
            .onSuccess(currentTopHelpers -> manageTopHelperRole(currentTopHelpers,
                    selectedTopHelperIds, event, topHelperRole.orElseThrow()))
            .onError(error -> {
                logger.warn("Failed to find existing top-helpers for automatic assignment", error);
                event.getHook()
                    .editOriginal(event.getMessage()
                            + "\n❌ Sorry, something went wrong trying to find existing top-helpers.")
                    .setComponents()
                    .queue();
            });
    }

    private void manageTopHelperRole(Collection<? extends Member> currentTopHelpers,
            Set<Long> selectedTopHelperIds, StringSelectInteractionEvent event,
            Role topHelperRole) {
        Guild guild = Objects.requireNonNull(event.getGuild());
        Set<Long> currentTopHelperIds =
                currentTopHelpers.stream().map(Member::getIdLong).collect(Collectors.toSet());

        Set<Long> usersToRemoveRoleFrom = new HashSet<>(currentTopHelperIds);
        usersToRemoveRoleFrom.removeAll(selectedTopHelperIds);

        Set<Long> usersToAddRoleTo = new HashSet<>(selectedTopHelperIds);
        usersToAddRoleTo.removeAll(currentTopHelperIds);

        for (long userToRemoveRoleFrom : usersToRemoveRoleFrom) {
            guild.removeRoleFromMember(UserSnowflake.fromId(userToRemoveRoleFrom), topHelperRole)
                .queue();
        }
        for (long userToAddRoleTo : usersToAddRoleTo) {
            guild.addRoleToMember(UserSnowflake.fromId(userToAddRoleTo), topHelperRole).queue();
        }

        reportRoleManageSuccess(event);
    }

    private void reportRoleManageSuccess(StringSelectInteractionEvent event) {
        String topHelperList = event.getSelectedOptions()
            .stream()
            .map(SelectOption::getLabel)
            .map(label -> "* " + label)
            .collect(Collectors.joining("\n"));

        Stream<String> topHelperIds =
                event.getSelectedOptions().stream().map(SelectOption::getValue);
        String[] successButtonArgs = Stream.concat(Stream.of(YES_MESSAGE_BUTTON_NAME), topHelperIds)
            .toArray(String[]::new);

        String content = event.getMessage().getContentRaw() + "\nSelected Top Helpers:\n"
                + topHelperList + "\nShould I send a generic announcement?";
        event.getHook()
            .editOriginal(content)
            .setActionRow(
                    Button.success(componentIdInteractor.generateComponentId(successButtonArgs),
                            "Yes"),
                    Button.danger(componentIdInteractor.generateComponentId(NO_MESSAGE_BUTTON_NAME),
                            "No"))
            .queue();
    }

    private void prepareAnnouncement(ButtonInteractionEvent event, List<String> args) {
        List<Long> topHelperIds = args.stream().skip(1).map(Long::parseLong).toList();

        event.getGuild()
            .retrieveMembersByIds(topHelperIds)
            .onSuccess(topHelpers -> postAnnouncement(event, topHelpers))
            .onError(error -> {
                logger.warn("Failed to retrieve top-helper data for automatic assignment", error);
                event.getHook()
                    .editOriginal(event.getMessage()
                            + "\n❌ Sorry, something went wrong trying to retrieve top-helper data.")
                    .setComponents()
                    .queue();
            });
    }

    private void postAnnouncement(ButtonInteractionEvent event, List<? extends Member> topHelpers) {
        Guild guild = Objects.requireNonNull(event.getGuild());
        Optional<TextChannel> announcementChannel = guild.getTextChannelCache()
            .stream()
            .filter(channel -> announcementChannelNamePredicate.test(channel.getName()))
            .findAny();

        if (announcementChannel.isEmpty()) {
            logger.warn(
                    "Unable to send a Top Helper announcement, did not find an announcement channel matching the configured pattern '{}' for guild '{}'",
                    config.getAnnouncementChannelPattern(), guild.getName());
            event.getHook()
                .editOriginal(event.getMessage()
                        + "\n❌ Sorry, something went wrong trying to post the announcement.")
                .setComponents()
                .queue();
            return;
        }

        Collections.shuffle(topHelpers); // for fairness
        String topHelperList = topHelpers.stream()
            .map(Member::getAsMention)
            .map(mention -> "* " + mention)
            .collect(Collectors.joining("\n"));
        TopHelpersService.TimeRange timeRange = TopHelpersService.TimeRange.ofPreviousMonth();
        String announcement = "Thanks to the Top Helpers of %s 🎉%n%s"
            .formatted(timeRange.description(), topHelperList);

        announcementChannel.orElseThrow().sendMessage(announcement).queue();

        endFlow(event, "posted an announcement");
    }

    private void endFlow(ComponentInteraction event, String message) {
        String content = event.getMessage().getContentRaw() + "\n✅ Okay, " + message
                + ". See you next time 👋";
        event.getHook().editOriginal(content).setComponents().queue();
    }
}
