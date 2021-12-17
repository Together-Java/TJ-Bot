package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.JDA;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.basic.PingCommand;
import org.togetherjava.tjbot.commands.basic.VcActivityCommand;
import org.togetherjava.tjbot.commands.free.FreeCommand;
import org.togetherjava.tjbot.commands.mathcommands.TeXCommand;
import org.togetherjava.tjbot.commands.moderation.*;
import org.togetherjava.tjbot.commands.modmail.ModmailCommand;
import org.togetherjava.tjbot.commands.tags.TagCommand;
import org.togetherjava.tjbot.commands.tags.TagManageCommand;
import org.togetherjava.tjbot.commands.tags.TagSystem;
import org.togetherjava.tjbot.commands.tags.TagsCommand;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.routines.ModAuditLogRoutine;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Utility class that offers all commands that should be registered by the system. New commands have
 * to be added here, where {@link org.togetherjava.tjbot.commands.system.CommandSystem} will then
 * pick it up from and register it with the system.
 * <p>
 * To add a new slash command, extend the commands returned by
 * {@link #createSlashCommands(JDA, Database)}.
 */
public enum Commands {
    ;

    /**
     * Creates all slash commands that should be registered with this application.
     * <p>
     * Calling this method multiple times will result in multiple commands being created, which
     * generally should be avoided.
     *
     * @param jda the JDA instance commands will be registered at
     * @param database the database of the application, which commands can use to persist data
     * @return a collection of all slash commands
     */
    public static @NotNull Collection<SlashCommand> createSlashCommands(@NotNull JDA jda,
            @NotNull Database database) {
        TagSystem tagSystem = new TagSystem(database);
        ModerationActionsStore actionsStore = new ModerationActionsStore(database);

        // TODO This should be moved into some proper command system instead (see GH issue #235
        // which adds support for routines)
        new ModAuditLogRoutine(jda, database).start();

        // TODO This should be moved into some proper command system instead (see GH issue #236
        // which adds support for listeners)
        jda.addEventListener(new RejoinMuteListener(actionsStore));

        // NOTE The command system can add special system relevant commands also by itself,
        // hence this list may not necessarily represent the full list of all commands actually
        // available.
        Collection<SlashCommand> commands = new ArrayList<>();

        commands.add(new PingCommand());
        commands.add(new TeXCommand());
        commands.add(new TagCommand(tagSystem));
        commands.add(new TagManageCommand(tagSystem));
        commands.add(new TagsCommand(tagSystem));
        commands.add(new VcActivityCommand());
        commands.add(new KickCommand(actionsStore));
        commands.add(new BanCommand(actionsStore));
        commands.add(new UnbanCommand(actionsStore));
        commands.add(new FreeCommand());
        commands.add(new AuditCommand(actionsStore));
        commands.add(new MuteCommand(actionsStore));
        commands.add(new UnmuteCommand(actionsStore));

        ModmailCommand modmailCommand = new ModmailCommand();
        commands.add(modmailCommand);
        commands.add(modmailCommand.new ReloadModmailCommand());

        return commands;
    }
}
