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

import static org.togetherjava.tjbot.commands.help.HelpSystemHelper.TITLE_COMPACT_LENGTH_MAX;
import static org.togetherjava.tjbot.commands.help.HelpSystemHelper.TITLE_COMPACT_LENGTH_MIN;

/**
 * Implements the {@code /help-thread} command, which are contains special command for help threads
 * only
 */
public final class HelpThreadCommand extends SlashCommandAdapter {
    private static final int COOLDOWN_DURATION_VALUE = 30;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;
    private static final String CATEGORY = "category";
    private static final String TITLE = "title";
    private static final String CLOSE = "close";

    private final HelpSystemHelper helper;
    private final Cache<Long, Instant> helpThreadIdToLastCategoryChange = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
        .build();
    private final Cache<Long, Instant> helpThreadIdToLastTitleChange = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
        .build();
    private final Cache<Long, Instant> helpThreadIdToLastClose = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
        .build();

    /**
     * Creates a new instance.
     *
     * @param config the config to use
     * @param helper the helper to use
     */
    public HelpThreadCommand(Config config, HelpSystemHelper helper) {
        super("help-thread", "Help thread specific commands", SlashCommandVisibility.GUILD);

        OptionData category = new OptionData(OptionType.STRING, CATEGORY, "new category", true);
        config.getHelpSystem()
            .getCategories()
            .forEach(categoryText -> category.addChoice(categoryText, categoryText));

        getData().addSubcommandGroups(
                new SubcommandGroupData("change", "Change the details of this help thread")
                    .addSubcommands(
                            new SubcommandData(CATEGORY, "Change the category of this help thread")
                                .addOptions(category),
                            new SubcommandData(TITLE, "Change the title of this help thread")
                                .addOption(OptionType.STRING, "title", "new title", true)));

        getData().addSubcommands(new SubcommandData(CLOSE, "Close this help thread"));

        this.helper = helper;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        ThreadChannel helpThread = event.getThreadChannel();
        if (helpThread.isArchived()) {
            event.reply("This thread is already closed.").setEphemeral(true).queue();
            return;
        }

        boolean isOnCooldown = false;

        switch (event.getSubcommandName()) {
            case CATEGORY -> {
                if (isHelpThreadOnCooldown(helpThreadIdToLastCategoryChange, helpThread)) {
                    isOnCooldown = true;
                    break;
                }
                changeCategory(event, helpThread);
            }
            case TITLE -> {
                if (isHelpThreadOnCooldown(helpThreadIdToLastTitleChange, helpThread)) {
                    isOnCooldown = true;
                    break;
                }
                String title = event.getOption("title").getAsString();

                if (!HelpSystemHelper.isTitleValid(title)) {
                    event.reply(
                            "Sorry, but the title length (after removal of special characters) has to be between %d and %d."
                                .formatted(TITLE_COMPACT_LENGTH_MIN, TITLE_COMPACT_LENGTH_MAX))
                        .setEphemeral(true)
                        .queue();
                }

                changeTitle(event, helpThread);
            }
            case CLOSE -> {
                if (isHelpThreadOnCooldown(helpThreadIdToLastClose, helpThread)) {
                    isOnCooldown = true;
                    break;
                }
                close(event, helpThread);
            }
            default -> {
            }
        }

        if (isOnCooldown) {
            event
                .reply("Please wait a bit, this command can only be used once per %d %s.".formatted(
                        COOLDOWN_DURATION_VALUE,
                        COOLDOWN_DURATION_UNIT.toString().toLowerCase(Locale.US)))
                .setEphemeral(true)
                .queue();
        }
    }

    private boolean isHelpThreadOnCooldown(Cache<Long, Instant> helpThreadIdToLastAction,
            ThreadChannel helpThread) {
        return Optional.ofNullable(helpThreadIdToLastAction.getIfPresent(helpThread.getIdLong()))
            .map(lastAction -> lastAction.plus(COOLDOWN_DURATION_VALUE, COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }

    private void changeCategory(SlashCommandInteractionEvent event, ThreadChannel helpThread) {
        String category = event.getOption(CATEGORY).getAsString();

        helpThreadIdToLastCategoryChange.put(helpThread.getIdLong(), Instant.now());

        event.deferReply().queue();

        helper.renameChannelToCategory(helpThread, category)
            .flatMap(any -> sendCategoryChangedMessage(helpThread.getGuild(), event.getHook(),
                    helpThread, category))
            .queue();
    }

    private void changeTitle(SlashCommandInteractionEvent event, ThreadChannel helpThread) {
        String title = event.getOption(TITLE).getAsString();

        helpThreadIdToLastTitleChange.put(helpThread.getIdLong(), Instant.now());

        helper.renameChannelToTitle(helpThread, title)
            .flatMap(any -> event.reply("Changed the title to **%s**.".formatted(title)))
            .queue();
    }

    private void close(SlashCommandInteractionEvent event, ThreadChannel helpThread) {
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
