package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.JDA;

import org.togetherjava.tjbot.commands.basic.PingCommand;
import org.togetherjava.tjbot.commands.basic.RoleSelectCommand;
import org.togetherjava.tjbot.commands.basic.SuggestionsUpDownVoter;
import org.togetherjava.tjbot.commands.basic.VcActivityCommand;
import org.togetherjava.tjbot.commands.bookmarks.*;
import org.togetherjava.tjbot.commands.code.CodeMessageAutoDetection;
import org.togetherjava.tjbot.commands.code.CodeMessageHandler;
import org.togetherjava.tjbot.commands.code.CodeMessageManualDetection;
import org.togetherjava.tjbot.commands.filesharing.FileSharingMessageListener;
import org.togetherjava.tjbot.commands.help.*;
import org.togetherjava.tjbot.commands.mathcommands.TeXCommand;
import org.togetherjava.tjbot.commands.mathcommands.wolframalpha.WolframAlphaCommand;
import org.togetherjava.tjbot.commands.mediaonly.MediaOnlyChannelListener;
import org.togetherjava.tjbot.commands.moderation.*;
import org.togetherjava.tjbot.commands.moderation.ReportCommand;
import org.togetherjava.tjbot.commands.moderation.attachment.BlacklistedAttachmentListener;
import org.togetherjava.tjbot.commands.moderation.modmail.ModMailCommand;
import org.togetherjava.tjbot.commands.moderation.scam.ScamBlocker;
import org.togetherjava.tjbot.commands.moderation.scam.ScamHistoryPurgeRoutine;
import org.togetherjava.tjbot.commands.moderation.scam.ScamHistoryStore;
import org.togetherjava.tjbot.commands.moderation.temp.TemporaryModerationRoutine;
import org.togetherjava.tjbot.commands.reminder.RemindRoutine;
import org.togetherjava.tjbot.commands.reminder.ReminderCommand;
import org.togetherjava.tjbot.commands.system.BotCore;
import org.togetherjava.tjbot.commands.system.LogLevelCommand;
import org.togetherjava.tjbot.commands.tags.TagCommand;
import org.togetherjava.tjbot.commands.tags.TagManageCommand;
import org.togetherjava.tjbot.commands.tags.TagSystem;
import org.togetherjava.tjbot.commands.tags.TagsCommand;
import org.togetherjava.tjbot.commands.tophelper.TopHelpersCommand;
import org.togetherjava.tjbot.commands.tophelper.TopHelpersMessageListener;
import org.togetherjava.tjbot.commands.tophelper.TopHelpersPurgeMessagesRoutine;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.moderation.ModAuditLogWriter;
import org.togetherjava.tjbot.routines.ModAuditLogRoutine;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Utility class that offers all features that should be registered by the system, such as commands.
 * New features have to be added here, where {@link BotCore} will then pick it up from and register
 * it with the system.
 * <p>
 * To add a new slash command, extend the commands returned by
 * {@link #createFeatures(JDA, Database, Config)}.
 */
public class Features {
    private Features() {
        throw new UnsupportedOperationException("Utility class, construction not supported");
    }

    /**
     * Creates all features that should be registered with this application.
     * <p>
     * Calling this method multiple times will result in multiple features being created, which
     * generally should be avoided.
     *
     * @param jda the JDA instance commands will be registered at
     * @param database the database of the application, which features can use to persist data
     * @param config the configuration features should use
     * @return a collection of all features
     */
    public static Collection<Feature> createFeatures(JDA jda, Database database, Config config) {
        TagSystem tagSystem = new TagSystem(database);
        BookmarksSystem bookmarksSystem = new BookmarksSystem(config, database);
        ModerationActionsStore actionsStore = new ModerationActionsStore(database);
        ModAuditLogWriter modAuditLogWriter = new ModAuditLogWriter(config);
        ScamHistoryStore scamHistoryStore = new ScamHistoryStore(database);
        HelpSystemHelper helpSystemHelper = new HelpSystemHelper(jda, config, database);
        CodeMessageHandler codeMessageHandler = new CodeMessageHandler();

        // NOTE The system can add special system relevant commands also by itself,
        // hence this list may not necessarily represent the full list of all commands actually
        // available.
        Collection<Feature> features = new ArrayList<>();

        // Routines
        features.add(new ModAuditLogRoutine(database, config, modAuditLogWriter));
        features.add(new TemporaryModerationRoutine(jda, actionsStore, config));
        features.add(new TopHelpersPurgeMessagesRoutine(database));
        features.add(new RemindRoutine(database));
        features.add(new ScamHistoryPurgeRoutine(scamHistoryStore));
        features.add(new HelpThreadMetadataPurger(database));
        features.add(new HelpThreadActivityUpdater(helpSystemHelper));
        features
            .add(new AutoPruneHelperRoutine(config, helpSystemHelper, modAuditLogWriter, database));
        features.add(new HelpThreadAutoArchiver(helpSystemHelper));
        features.add(new LeftoverBookmarksCleanupRoutine(bookmarksSystem));

        // Message receivers
        features.add(new TopHelpersMessageListener(database, config));
        features.add(new SuggestionsUpDownVoter(config));
        features.add(new ScamBlocker(actionsStore, scamHistoryStore, config));
        features.add(new MediaOnlyChannelListener(config));
        features.add(new FileSharingMessageListener(config));
        features.add(new BlacklistedAttachmentListener(config, modAuditLogWriter));
        features.add(codeMessageHandler);
        features.add(new CodeMessageAutoDetection(config, codeMessageHandler));
        features.add(new CodeMessageManualDetection(codeMessageHandler));

        // Event receivers
        features.add(new RejoinModerationRoleListener(actionsStore, config));
        features.add(new OnGuildLeaveCloseThreadListener(database));
        features.add(new UserBannedDeleteRecentThreadsListener(database));
        features.add(new LeftoverBookmarksListener(bookmarksSystem));
        features.add(new HelpThreadCreatedListener(helpSystemHelper));

        // Message context commands

        // User context commands

        // Slash commands
        features.add(new LogLevelCommand());
        features.add(new PingCommand());
        features.add(new TeXCommand());
        features.add(new TagCommand(tagSystem));
        features.add(new TagManageCommand(tagSystem, modAuditLogWriter));
        features.add(new TagsCommand(tagSystem));
        features.add(new VcActivityCommand());
        features.add(new WarnCommand(actionsStore));
        features.add(new KickCommand(actionsStore));
        features.add(new BanCommand(actionsStore));
        features.add(new UnbanCommand(actionsStore));
        features.add(new AuditCommand(actionsStore));
        features.add(new MuteCommand(actionsStore, config));
        features.add(new UnmuteCommand(actionsStore, config));
        features.add(new TopHelpersCommand(database));
        features.add(new RoleSelectCommand());
        features.add(new NoteCommand(actionsStore));
        features.add(new ReminderCommand(database));
        features.add(new QuarantineCommand(actionsStore, config));
        features.add(new UnquarantineCommand(actionsStore, config));
        features.add(new WhoIsCommand());
        features.add(new WolframAlphaCommand(config));
        features.add(new ModMailCommand(jda, config));
        features.add(new HelpThreadCommand(config, helpSystemHelper));
        features.add(new ReportCommand(config));
        features.add(new BookmarksCommand(bookmarksSystem));

        // Mixtures

        return features;
    }
}
