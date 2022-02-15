package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.JDA;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.basic.PingCommand;
import org.togetherjava.tjbot.commands.basic.RoleSelectCommand;
import org.togetherjava.tjbot.commands.basic.VcActivityCommand;
import org.togetherjava.tjbot.commands.free.FreeCommand;
import org.togetherjava.tjbot.commands.mathcommands.TeXCommand;
import org.togetherjava.tjbot.commands.moderation.*;
import org.togetherjava.tjbot.commands.moderation.temp.TemporaryModerationRoutine;
import org.togetherjava.tjbot.commands.system.BotCore;
import org.togetherjava.tjbot.commands.tags.TagCommand;
import org.togetherjava.tjbot.commands.tags.TagManageCommand;
import org.togetherjava.tjbot.commands.tags.TagSystem;
import org.togetherjava.tjbot.commands.tags.TagsCommand;
import org.togetherjava.tjbot.commands.tophelper.TopHelpersCommand;
import org.togetherjava.tjbot.commands.tophelper.TopHelpersMessageListener;
import org.togetherjava.tjbot.commands.tophelper.TopHelpersPurgeMessagesRoutine;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.routines.ModAuditLogRoutine;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Utility class that offers all features that should be registered by the system, such as commands.
 * New features have to be added here, where {@link BotCore} will then pick it up from and register
 * it with the system.
 * <p>
 * To add a new slash command, extend the commands returned by
 * {@link #createFeatures(JDA, Database)}.
 */
public enum Features {
    ;

    /**
     * Creates all features that should be registered with this application.
     * <p>
     * Calling this method multiple times will result in multiple features being created, which
     * generally should be avoided.
     *
     * @param jda the JDA instance commands will be registered at
     * @param database the database of the application, which features can use to persist data
     * @return a collection of all features
     */
    public static @NotNull Collection<Feature> createFeatures(@NotNull JDA jda,
            @NotNull Database database) {
        TagSystem tagSystem = new TagSystem(database);
        ModerationActionsStore actionsStore = new ModerationActionsStore(database);

        // NOTE The system can add special system relevant commands also by itself,
        // hence this list may not necessarily represent the full list of all commands actually
        // available.
        Collection<Feature> features = new ArrayList<>();

        // Routines
        features.add(new ModAuditLogRoutine(database));
        features.add(new TemporaryModerationRoutine(jda, actionsStore));
        features.add(new TopHelpersPurgeMessagesRoutine(database));

        // Message receivers
        features.add(new TopHelpersMessageListener(database));

        // Event receivers
        features.add(new RejoinMuteListener(actionsStore));

        // Slash commands
        features.add(new PingCommand());
        features.add(new TeXCommand());
        features.add(new TagCommand(tagSystem));
        features.add(new TagManageCommand(tagSystem));
        features.add(new TagsCommand(tagSystem));
        features.add(new VcActivityCommand());
        features.add(new WarnCommand(actionsStore));
        features.add(new KickCommand(actionsStore));
        features.add(new BanCommand(actionsStore));
        features.add(new UnbanCommand(actionsStore));
        features.add(new AuditCommand(actionsStore));
        features.add(new MuteCommand(actionsStore));
        features.add(new UnmuteCommand(actionsStore));
        features.add(new RoleSelectCommand());
        features.add(new TopHelpersCommand(database));

        // Message context commands

        // User context commands

        // Mixtures
        features.add(new FreeCommand());

        return features;
    }
}
