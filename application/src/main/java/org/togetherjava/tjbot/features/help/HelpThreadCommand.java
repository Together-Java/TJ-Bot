package org.togetherjava.tjbot.features.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.togetherjava.tjbot.features.help.HelpThreadActivityUpdater.manuallyResetChannelActivityCache;

/**
 * Implements the {@code /help-thread} command, used to maintain certain aspects of help threads,
 * such as renaming or closing them.
 */
public final class HelpThreadCommand extends SlashCommandAdapter {
    private static final int COOLDOWN_DURATION_VALUE = 2;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;
    public static final String CHANGE_CATEGORY_SUBCOMMAND = "category";
    private static final String CHANGE_CATEGORY_OPTION = "category";
    public static final String CHANGE_TITLE_SUBCOMMAND = "title";
    private static final String CHANGE_TITLE_OPTION = "title";
    private static final String CLOSE_SUBCOMMAND = "close";
    private static final String RESET_ACTIVITY_SUBCOMMAND = "reset-activity";
    public static final String CHANGE_SUBCOMMAND_GROUP = "change";
    public static final String COMMAND_NAME = "help-thread";

    private final HelpSystemHelper helper;
    private final Map<String, Subcommand> nameToSubcommand;
    private final Map<Subcommand, Cache<Long, Instant>> subcommandToCooldownCache;
    private final Map<Subcommand, BiConsumer<SlashCommandInteractionEvent, ThreadChannel>> subcommandToEventHandler;

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param helper the helper to use
     */
    public HelpThreadCommand(Config config, HelpSystemHelper helper) {
        super(COMMAND_NAME, "Help thread specific commands", CommandVisibility.GUILD);

        OptionData categoryChoices =
                new OptionData(OptionType.STRING, CHANGE_CATEGORY_OPTION, "new category", true);
        config.getHelpSystem()
            .getCategories()
            .forEach(categoryText -> categoryChoices.addChoice(categoryText, categoryText));

        SubcommandData changeCategory =
                Subcommand.CHANGE_CATEGORY.toSubcommandData().addOptions(categoryChoices);

        OptionData changeTitleOption =
                new OptionData(OptionType.STRING, CHANGE_TITLE_OPTION, "new title", true)
                    .setMinLength(2);
        SubcommandData changeTitle =
                Subcommand.CHANGE_TITLE.toSubcommandData().addOptions(changeTitleOption);

        SubcommandGroupData changeCommands = new SubcommandGroupData(CHANGE_SUBCOMMAND_GROUP,
                "Change the details of this help thread")
            .addSubcommands(changeCategory, changeTitle);
        getData().addSubcommandGroups(changeCommands);

        getData().addSubcommands(Subcommand.CLOSE.toSubcommandData());
        getData().addSubcommands(Subcommand.RESET_ACTIVITY.toSubcommandData());

        this.helper = helper;

        Supplier<Cache<Long, Instant>> createCooldownCache = () -> Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
            .build();
        nameToSubcommand = streamSubcommands()
            .collect(Collectors.toMap(Subcommand::getCommandName, Function.identity()));
        subcommandToCooldownCache = new EnumMap<>(streamSubcommands()
            .filter(Subcommand::hasCooldown)
            .collect(Collectors.toMap(Function.identity(), any -> createCooldownCache.get())));
        subcommandToEventHandler = new EnumMap<>(Map.of(Subcommand.CHANGE_CATEGORY,
                this::changeCategory, Subcommand.CHANGE_TITLE, this::changeTitle, Subcommand.CLOSE,
                this::closeThread, Subcommand.RESET_ACTIVITY, this::resetActivity));
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        ThreadChannel helpThread = event.getChannel().asThreadChannel();

        Subcommand invokedSubcommand =
                Objects.requireNonNull(nameToSubcommand.get(event.getSubcommandName()));

        if (invokedSubcommand.hasCooldown()
                && isHelpThreadOnCooldown(invokedSubcommand, helpThread)) {
            sendCooldownMessage(event);
            return;
        }

        subcommandToEventHandler.get(invokedSubcommand).accept(event, helpThread);
    }

    private boolean isHelpThreadOnCooldown(Subcommand subcommand, ThreadChannel helpThread) {
        Cache<? super Long, Instant> helpThreadIdToLastAction = requireCooldownCache(subcommand);
        return Optional.ofNullable(helpThreadIdToLastAction.getIfPresent(helpThread.getIdLong()))
            .map(lastAction -> lastAction.plus(COOLDOWN_DURATION_VALUE, COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }

    private Cache<? super Long, Instant> requireCooldownCache(Subcommand subcommand) {
        if (!subcommand.hasCooldown()) {
            throw new IllegalArgumentException(
                    "Must only be used with subcommands that do have cooldown, but " + subcommand
                            + " was given.");
        }

        return subcommandToCooldownCache.get(subcommand);
    }

    private void sendCooldownMessage(SlashCommandInteractionEvent event) {
        event
            .reply("Please wait a bit, this command can only be used once per %d %s.".formatted(
                    COOLDOWN_DURATION_VALUE,
                    COOLDOWN_DURATION_UNIT.toString().toLowerCase(Locale.US)))
            .setEphemeral(true)
            .queue();
    }

    private void changeCategory(SlashCommandInteractionEvent event, ThreadChannel helpThread) {
        String category = event.getOption(CHANGE_CATEGORY_OPTION).getAsString();

        event.deferReply().queue();
        refreshCooldownFor(Subcommand.CHANGE_CATEGORY, helpThread);

        helper.changeChannelCategory(helpThread, category)
            .flatMap(any -> sendCategoryChangedMessage(helpThread.getGuild(), event.getHook(),
                    helpThread, category))
            .queue();
    }

    private void refreshCooldownFor(Subcommand subcommand, ThreadChannel helpThread) {
        Cache<? super Long, Instant> helpThreadIdToLastAction = requireCooldownCache(subcommand);
        helpThreadIdToLastAction.put(helpThread.getIdLong(), Instant.now());
    }

    private RestAction<Message> sendCategoryChangedMessage(Guild guild, InteractionHook hook,
            ThreadChannel helpThread, String category) {
        String changedContent = "Changed the category to **%s**.".formatted(category);
        WebhookMessageEditAction<Message> action = hook.editOriginal(changedContent);

        Optional<Role> helperRole = helper.handleFindRoleForCategory(category, guild);
        if (helperRole.isEmpty()) {
            return action;
        }

        // We want to invite all members of a role, but without hard-pinging them. However,
        // manually inviting them is cumbersome and can hit rate limits.
        // Instead, we abuse the fact that a role-ping through an edit will not hard-ping users,
        // but still invite them to a thread.
        String headsUpPattern = "%s please have a look, thanks.";
        String headsUpWithoutRole = headsUpPattern.formatted("");
        String headsUpWithRole = headsUpPattern.formatted(helperRole.orElseThrow().getAsMention());
        return action.flatMap(any -> helpThread.sendMessage(headsUpWithoutRole)
            .flatMap(message -> message.editMessage(headsUpWithRole)));
    }

    private void changeTitle(SlashCommandInteractionEvent event, ThreadChannel helpThread) {
        String title = event.getOption(CHANGE_TITLE_OPTION).getAsString();

        refreshCooldownFor(Subcommand.CHANGE_TITLE, helpThread);

        helper.renameChannel(helpThread, title)
            .flatMap(any -> event.reply("Changed the title to **%s**.".formatted(title)))
            .queue();
    }

    private void closeThread(SlashCommandInteractionEvent event, ThreadChannel helpThread) {
        refreshCooldownFor(Subcommand.CLOSE, helpThread);

        MessageEmbed embed = new EmbedBuilder().setDescription("Closed the thread.")
            .setColor(HelpSystemHelper.AMBIENT_COLOR)
            .build();

        event.replyEmbeds(embed).flatMap(any -> helpThread.getManager().setArchived(true)).queue();
    }

    private void resetActivity(SlashCommandInteractionEvent event, ThreadChannel helpThread) {
        refreshCooldownFor(Subcommand.RESET_ACTIVITY, helpThread);

        helpThread.getHistory()
            .retrievePast(1)
            .map(List::getFirst)
            .queue(lastMessage -> manuallyResetChannelActivityCache.put(helpThread.getIdLong(),
                    lastMessage.getIdLong()));

        helper.changeChannelActivity(helpThread, HelpSystemHelper.ThreadActivity.LOW);
        event.reply("Activities have been reset.").queue();
    }

    private static Stream<Subcommand> streamSubcommands() {
        return Arrays.stream(Subcommand.values());
    }

    enum Subcommand {
        CHANGE_CATEGORY(CHANGE_CATEGORY_SUBCOMMAND, "Change the category of this help thread",
                Cooldown.YES),
        CHANGE_TITLE(CHANGE_TITLE_SUBCOMMAND, "Change the title of this help thread", Cooldown.YES),
        CLOSE(CLOSE_SUBCOMMAND, "Close this help thread", Cooldown.YES),
        RESET_ACTIVITY(RESET_ACTIVITY_SUBCOMMAND,
                "Resets the activity indicator, use if help is still needed, but the indicator shows otherwise",
                Cooldown.YES);

        private final String commandName;
        private final String description;
        private final Cooldown cooldown;

        Subcommand(String commandName, String description, Cooldown cooldown) {
            this.commandName = commandName;
            this.description = description;
            this.cooldown = cooldown;
        }

        String getCommandName() {
            return commandName;
        }

        String getDescription() {
            return description;
        }

        boolean hasCooldown() {
            return cooldown == Cooldown.YES;
        }

        SubcommandData toSubcommandData() {
            return new SubcommandData(commandName, description);
        }
    }

    enum Cooldown {
        YES,
        NO
    }
}
