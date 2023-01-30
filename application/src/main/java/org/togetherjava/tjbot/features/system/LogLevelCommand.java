package org.togetherjava.tjbot.features.system;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import java.util.stream.Stream;

/**
 * Implements the '/set-log-level' command which can be used to change the log level used by the
 * bot, while it is running.
 * <p>
 * Example usage:
 *
 * <pre>
 * {@code
 * /set-log-level level: INFO
 * }
 * </pre>
 */
public final class LogLevelCommand extends SlashCommandAdapter {
    private static final String LOG_LEVEL_OPTION = "level";

    /**
     * Creates a new instance.
     */
    public LogLevelCommand() {
        super("set-log-level", "Changes the log level of the bot while it is running.",
                CommandVisibility.GUILD);

        OptionData option =
                new OptionData(OptionType.STRING, LOG_LEVEL_OPTION, "the log level to set", true);
        Stream.of(Level.values()).map(Level::name).forEach(level -> option.addChoice(level, level));

        getData().addOptions(option);
    }

    // Security warning about changing log configs. We only change the level, that is safe.
    @SuppressWarnings("squid:S4792")
    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String levelText = event.getOption(LOG_LEVEL_OPTION).getAsString();
        Level level = Level.getLevel(levelText);

        if (level == null) {
            event.reply("The selected log level '%s' is unknown.".formatted(levelText))
                .setEphemeral(true)
                .queue();
            return;
        }

        Configurator.setAllLevels(LogManager.getRootLogger().getName(), level);
        event.reply("Set the log level to '%s'.".formatted(levelText)).queue();
    }
}
