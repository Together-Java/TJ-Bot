package org.togetherjava.tjbot.commands.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageUpdateAction;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.togetherjava.tjbot.commands.help.HelpSystemHelper.TITLE_COMPACT_LENGTH_MAX;
import static org.togetherjava.tjbot.commands.help.HelpSystemHelper.TITLE_COMPACT_LENGTH_MIN;

/**
 * Implements the {@code /help-thread} command, which are contains special command for help threads
 * only
 */
public final class HelpThreadCommand extends SlashCommandAdapter {
    private static final int COOLDOWN_DURATION_VALUE = 30;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;
    private static final String CATEGORY_SUBCOMMAND = "category";
    private static final String TITLE_SUBCOMMAND = "title";
    private static final String CLOSE_SUBCOMMAND = "close";

    private static final Supplier<Cache<Long, Instant>> newCaffeine = () -> Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
        .build();

    private final HelpSystemHelper helper;
    private final Cache<Long, Instant> helpThreadIdToLastCategoryChange = newCaffeine.get();
    private final Cache<Long, Instant> helpThreadIdToLastTitleChange = newCaffeine.get();
    private final Cache<Long, Instant> helpThreadIdToLastClose = newCaffeine.get();

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param helper the helper to use
     */
    public HelpThreadCommand(Config config, HelpSystemHelper helper) {
        super("help-thread", "Help thread specific commands", SlashCommandVisibility.GUILD);

        OptionData category =
                new OptionData(OptionType.STRING, CATEGORY_SUBCOMMAND, "new category", true);
        config.getHelpSystem()
            .getCategories()
            .forEach(categoryText -> category.addChoice(categoryText, categoryText));

        getData().addSubcommandGroups(new SubcommandGroupData("change",
                "Change the details of this help thread").addSubcommands(
                        new SubcommandData(CATEGORY_SUBCOMMAND,
                                "Change the category of this help thread").addOptions(category),
                        new SubcommandData(TITLE_SUBCOMMAND, "Change the title of this help thread")
                            .addOption(OptionType.STRING, TITLE_SUBCOMMAND, "new title", true)));

        getData().addSubcommands(new SubcommandData(CLOSE_SUBCOMMAND, "Close this help thread"));

        this.helper = helper;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        ThreadChannel helpThread = event.getThreadChannel();

        switch (event.getSubcommandName()) {
            case CATEGORY_SUBCOMMAND -> changeCategory(event, helpThread);
            case TITLE_SUBCOMMAND -> changeTitle(event, helpThread);
            case CLOSE_SUBCOMMAND -> close(event, helpThread);
            default -> {
                // This can never be the case
            }
        }
    }

    private boolean isHelpThreadOnCooldown(Cache<Long, Instant> helpThreadIdToLastAction,
            ThreadChannel helpThread) {
        return Optional.ofNullable(helpThreadIdToLastAction.getIfPresent(helpThread.getIdLong()))
            .map(lastAction -> lastAction.plus(COOLDOWN_DURATION_VALUE, COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
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
        if (isHelpThreadOnCooldown(helpThreadIdToLastCategoryChange, helpThread)) {
            sendCooldownMessage(event);
            return;
        }

        String category = event.getOption(CATEGORY_SUBCOMMAND).getAsString();

        helpThreadIdToLastCategoryChange.put(helpThread.getIdLong(), Instant.now());

        event.deferReply().queue();

        helper.renameChannelToCategory(helpThread, category)
            .flatMap(any -> sendCategoryChangedMessage(helpThread.getGuild(), event.getHook(),
                    helpThread, category))
            .queue();
    }

    private void changeTitle(SlashCommandInteractionEvent event, ThreadChannel helpThread) {
        if (isHelpThreadOnCooldown(helpThreadIdToLastTitleChange, helpThread)) {
            sendCooldownMessage(event);
            return;
        }

        String title = event.getOption(TITLE_SUBCOMMAND).getAsString();

        if (!HelpSystemHelper.isTitleValid(title)) {
            event.reply(
                    "Sorry, but the title length (after removal of special characters) has to be between %d and %d."
                        .formatted(TITLE_COMPACT_LENGTH_MIN, TITLE_COMPACT_LENGTH_MAX))
                .setEphemeral(true)
                .queue();
            return;
        }

        helpThreadIdToLastTitleChange.put(helpThread.getIdLong(), Instant.now());

        helper.renameChannelToTitle(helpThread, title)
            .flatMap(any -> event.reply("Changed the title to **%s**.".formatted(title)))
            .queue();
    }

    private void close(SlashCommandInteractionEvent event, ThreadChannel helpThread) {
        if (isHelpThreadOnCooldown(helpThreadIdToLastClose, helpThread)) {
            sendCooldownMessage(event);
            return;
        }

        helpThreadIdToLastClose.put(helpThread.getIdLong(), Instant.now());

        MessageEmbed embed = new EmbedBuilder().setDescription("Closed the thread.")
            .setColor(HelpSystemHelper.AMBIENT_COLOR)
            .build();

        event.replyEmbeds(embed).flatMap(any -> helpThread.getManager().setArchived(true)).queue();
    }

    private RestAction<Message> sendCategoryChangedMessage(Guild guild, InteractionHook hook,
            ThreadChannel helpThread, String category) {
        String changedContent = "Changed the category to **%s**.".formatted(category);
        WebhookMessageUpdateAction<Message> action = hook.editOriginal(changedContent);

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
}
