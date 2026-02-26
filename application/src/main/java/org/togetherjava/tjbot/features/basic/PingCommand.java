package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.analytics.AnalyticsService;

/**
 * Implementation of an example command to illustrate how to respond to a user.
 * <p>
 * The implemented command is {@code /ping}, upon which the bot will respond with {@code Pong!}.
 */
public final class PingCommand extends SlashCommandAdapter {
    private final AnalyticsService analyticsService;

    /**
     * Creates an instance of the ping pong command.
     *
     * @param analyticsService the analytics service to track command usage
     */
    public PingCommand(AnalyticsService analyticsService) {
        super("ping", "Bot responds with 'Pong!'", CommandVisibility.GUILD);
        this.analyticsService = analyticsService;
    }

    /**
     * When triggered with {@code /ping}, the bot will respond with {@code Pong!}.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        try {
            event.reply("Pong!").queue();

            analyticsService.recordCommandSuccess(guild.getIdLong(), getName(),
                    event.getUser().getIdLong());

        } catch (Exception e) {
            analyticsService.recordCommandFailure(guild.getIdLong(), getName(),
                    event.getUser().getIdLong(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            throw e;
        }
    }
}
