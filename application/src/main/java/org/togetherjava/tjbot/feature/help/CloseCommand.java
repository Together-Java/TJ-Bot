package org.togetherjava.tjbot.feature.help;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.feature.SlashCommandAdapter;
import org.togetherjava.tjbot.feature.SlashCommandVisibility;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Implements the {@code /close} command to close question threads.
 * <p>
 * Can be used in active (non-archived) question threads. Will close, i.e. archive, the thread upon
 * use. Meant to be used once a question has been resolved.
 */
public final class CloseCommand extends SlashCommandAdapter {
    private static final int COOLDOWN_DURATION_VALUE = 30;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;

    private final HelpSystemHelper helper;
    private final Cache<Long, Instant> helpThreadIdToLastClose;

    /**
     * Creates a new instance.
     *
     * @param helper the helper to use
     */
    public CloseCommand(@NotNull HelpSystemHelper helper) {
        super("close", "Close this question thread", SlashCommandVisibility.GUILD);

        helpThreadIdToLastClose = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
            .build();

        this.helper = helper;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        if (!helper.handleIsHelpThread(event)) {
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
        helpThreadIdToLastClose.put(helpThread.getIdLong(), Instant.now());

        MessageEmbed embed = new EmbedBuilder().setDescription("Closed the thread.")
            .setColor(HelpSystemHelper.AMBIENT_COLOR)
            .build();

        event.replyEmbeds(embed).flatMap(any -> helpThread.getManager().setArchived(true)).queue();
    }

    private boolean isHelpThreadOnCooldown(@NotNull ThreadChannel helpThread) {
        return Optional.ofNullable(helpThreadIdToLastClose.getIfPresent(helpThread.getIdLong()))
            .map(lastCategoryChange -> lastCategoryChange.plus(COOLDOWN_DURATION_VALUE,
                    COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }
}
