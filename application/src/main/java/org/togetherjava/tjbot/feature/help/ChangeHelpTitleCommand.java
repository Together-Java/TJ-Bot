package org.togetherjava.tjbot.feature.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.feature.SlashCommandAdapter;
import org.togetherjava.tjbot.feature.SlashCommandVisibility;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.togetherjava.tjbot.feature.help.HelpSystemHelper.TITLE_COMPACT_LENGTH_MAX;
import static org.togetherjava.tjbot.feature.help.HelpSystemHelper.TITLE_COMPACT_LENGTH_MIN;

/**
 * Implements the {@code /change-help-title} command, which is able to change the title of a help
 * thread.
 * <p>
 * This is to adjust a bad title in hindsight, for example if it was automatically created by
 * {@link ImplicitAskListener}.
 */
public final class ChangeHelpTitleCommand extends SlashCommandAdapter {
    private static final String TITLE_OPTION = "title";

    private static final int COOLDOWN_DURATION_VALUE = 30;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;

    private final HelpSystemHelper helper;
    private final Cache<Long, Instant> helpThreadIdToLastTitleChange;

    /**
     * Creates a new instance.
     *
     * @param helper the helper to use
     */
    public ChangeHelpTitleCommand(@NotNull HelpSystemHelper helper) {
        super("change-help-title", "changes the title of a help thread",
                SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.STRING, TITLE_OPTION, "short and to the point", true);

        helpThreadIdToLastTitleChange = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
            .build();

        this.helper = helper;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        String title = event.getOption(TITLE_OPTION).getAsString();

        if (!helper.handleIsHelpThread(event) || !handleIsValidTitle(title, event)) {
            return;
        }

        ThreadChannel helpThread = event.getThreadChannel();
        if (helpThread.isArchived()) {
            event.reply("This thread is already closed.").setEphemeral(true).queue();
            return;
        }

        if (isHelpThreadOnCooldown(helpThread)) {
            event
                .reply("Please wait a bit, this command can only be used once per %d %s.".formatted(
                        COOLDOWN_DURATION_VALUE,
                        COOLDOWN_DURATION_UNIT.toString().toLowerCase(Locale.US)))
                .setEphemeral(true)
                .queue();
            return;
        }
        helpThreadIdToLastTitleChange.put(helpThread.getIdLong(), Instant.now());

        helper.renameChannelToTitle(helpThread, title)
            .flatMap(any -> event.reply("Changed the title to **%s**.".formatted(title)))
            .queue();
    }

    private boolean isHelpThreadOnCooldown(@NotNull ThreadChannel helpThread) {
        return Optional
            .ofNullable(helpThreadIdToLastTitleChange.getIfPresent(helpThread.getIdLong()))
            .map(lastCategoryChange -> lastCategoryChange.plus(COOLDOWN_DURATION_VALUE,
                    COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }

    private boolean handleIsValidTitle(@NotNull CharSequence title, @NotNull IReplyCallback event) {
        if (HelpSystemHelper.isTitleValid(title)) {
            return true;
        }

        event.reply(
                "Sorry, but the title length (after removal of special characters) has to be between %d and %d."
                    .formatted(TITLE_COMPACT_LENGTH_MIN, TITLE_COMPACT_LENGTH_MAX))
            .setEphemeral(true)
            .queue();

        return false;
    }
}
