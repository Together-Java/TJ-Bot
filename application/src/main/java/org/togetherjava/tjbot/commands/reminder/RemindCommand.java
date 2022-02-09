package org.togetherjava.tjbot.commands.reminder;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.Reminders;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

// TODO Javadoc
public final class RemindCommand extends SlashCommandAdapter {
    private static final Color AMBIENT_COLOR = Color.decode("#F7F492");
    private static final String COMMAND_NAME = "remind";
    private static final String WHEN_OPTION = "when";
    private static final String CONTENT_OPTION = "content";

    private final Database database;

    /**
     * Creates an instance of the command.
     *
     * @param database to store and fetch the reminders from
     */
    public RemindCommand(@NotNull Database database) {
        super(COMMAND_NAME, "Reminds the user about something at a given time",
                SlashCommandVisibility.GUILD);

        getData()
            .addOption(OptionType.STRING, WHEN_OPTION, "when the reminder should be sent", true)
            .addOption(OptionType.STRING, CONTENT_OPTION, "the content of the reminder", true);

        this.database = database;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        String whenText = event.getOption(WHEN_OPTION).getAsString();
        String content = event.getOption(CONTENT_OPTION).getAsString();

        Instant remindAt = parseWhen(whenText);

        database.write(context -> context.newRecord(Reminders.REMINDERS)
            .setCreatedAt(Instant.now())
            .setGuildId(event.getGuild().getIdLong())
            .setChannelId(event.getChannel().getIdLong())
            .setAuthorId(event.getUser().getIdLong())
            .setRemindAt(remindAt)
            .setContent(content)
            .insert());
    }

    private static @NotNull Instant parseWhen(@NotNull String when) {
        // TODO parse "when" and remove stub
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }
}
