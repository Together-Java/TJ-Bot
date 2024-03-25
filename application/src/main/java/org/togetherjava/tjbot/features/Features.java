package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.JDA;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.FeatureBlacklist;
import org.togetherjava.tjbot.config.FeatureBlacklistConfig;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.basic.MemberCountDisplayRoutine;
import org.togetherjava.tjbot.features.basic.PingCommand;
import org.togetherjava.tjbot.features.basic.RoleSelectCommand;
import org.togetherjava.tjbot.features.basic.SlashCommandEducator;
import org.togetherjava.tjbot.features.basic.SuggestionsUpDownVoter;
import org.togetherjava.tjbot.features.bookmarks.BookmarksCommand;
import org.togetherjava.tjbot.features.bookmarks.BookmarksSystem;
import org.togetherjava.tjbot.features.bookmarks.LeftoverBookmarksCleanupRoutine;
import org.togetherjava.tjbot.features.bookmarks.LeftoverBookmarksListener;
import org.togetherjava.tjbot.features.chatgpt.ChatGptCommand;
import org.togetherjava.tjbot.features.chatgpt.ChatGptService;
import org.togetherjava.tjbot.features.code.CodeMessageAutoDetection;
import org.togetherjava.tjbot.features.code.CodeMessageHandler;
import org.togetherjava.tjbot.features.code.CodeMessageManualDetection;
import org.togetherjava.tjbot.features.filesharing.FileSharingMessageListener;
import org.togetherjava.tjbot.features.github.GitHubCommand;
import org.togetherjava.tjbot.features.github.GitHubReference;
import org.togetherjava.tjbot.features.help.*;
import org.togetherjava.tjbot.features.jshell.JShellCommand;
import org.togetherjava.tjbot.features.jshell.JShellEval;
import org.togetherjava.tjbot.features.mathcommands.TeXCommand;
import org.togetherjava.tjbot.features.mathcommands.wolframalpha.WolframAlphaCommand;
import org.togetherjava.tjbot.features.mediaonly.MediaOnlyChannelListener;
import org.togetherjava.tjbot.features.moderation.*;
import org.togetherjava.tjbot.features.moderation.attachment.BlacklistedAttachmentListener;
import org.togetherjava.tjbot.features.moderation.audit.AuditCommand;
import org.togetherjava.tjbot.features.moderation.audit.ModAuditLogRoutine;
import org.togetherjava.tjbot.features.moderation.audit.ModAuditLogWriter;
import org.togetherjava.tjbot.features.moderation.modmail.ModMailCommand;
import org.togetherjava.tjbot.features.moderation.scam.ScamBlocker;
import org.togetherjava.tjbot.features.moderation.scam.ScamHistoryPurgeRoutine;
import org.togetherjava.tjbot.features.moderation.scam.ScamHistoryStore;
import org.togetherjava.tjbot.features.moderation.temp.TemporaryModerationRoutine;
import org.togetherjava.tjbot.features.reminder.RemindRoutine;
import org.togetherjava.tjbot.features.reminder.ReminderCommand;
import org.togetherjava.tjbot.features.system.BotCore;
import org.togetherjava.tjbot.features.system.LogLevelCommand;
import org.togetherjava.tjbot.features.tags.TagCommand;
import org.togetherjava.tjbot.features.tags.TagManageCommand;
import org.togetherjava.tjbot.features.tags.TagSystem;
import org.togetherjava.tjbot.features.tags.TagsCommand;
import org.togetherjava.tjbot.features.tophelper.TopHelpersCommand;
import org.togetherjava.tjbot.features.tophelper.TopHelpersMessageListener;
import org.togetherjava.tjbot.features.tophelper.TopHelpersPurgeMessagesRoutine;

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
        FeatureBlacklistConfig blacklistConfig = config.getFeatureBlacklistConfig();
        JShellEval jshellEval = new JShellEval(config.getJshell(), config.getGitHubApiKey());

        TagSystem tagSystem = new TagSystem(database);
        BookmarksSystem bookmarksSystem = new BookmarksSystem(config, database);
        ModerationActionsStore actionsStore = new ModerationActionsStore(database);
        ModAuditLogWriter modAuditLogWriter = new ModAuditLogWriter(config);
        ScamHistoryStore scamHistoryStore = new ScamHistoryStore(database);
        GitHubReference githubReference = new GitHubReference(config);
        CodeMessageHandler codeMessageHandler =
                new CodeMessageHandler(blacklistConfig.special(), jshellEval);
        ChatGptService chatGptService = new ChatGptService(config);
        HelpSystemHelper helpSystemHelper = new HelpSystemHelper(config, database, chatGptService);

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
        features.add(new HelpThreadAutoArchiver(helpSystemHelper));
        features.add(new LeftoverBookmarksCleanupRoutine(bookmarksSystem));
        features.add(new MemberCountDisplayRoutine(config));

        // Message receivers
        features.add(new TopHelpersMessageListener(database, config));
        features.add(new SuggestionsUpDownVoter(config));
        features.add(new ScamBlocker(actionsStore, scamHistoryStore, config));
        features.add(new MediaOnlyChannelListener(config));
        features.add(new FileSharingMessageListener(config));
        features.add(new BlacklistedAttachmentListener(config, modAuditLogWriter));
        features.add(githubReference);
        features.add(codeMessageHandler);
        features.add(new CodeMessageAutoDetection(config, codeMessageHandler));
        features.add(new CodeMessageManualDetection(codeMessageHandler));
        features.add(new SlashCommandEducator());
        features.add(new PinnedNotificationRemover(config));

        // Event receivers
        features.add(new RejoinModerationRoleListener(actionsStore, config));
        features.add(new GuildLeaveCloseThreadListener(config));
        features.add(new LeftoverBookmarksListener(bookmarksSystem));
        features.add(new HelpThreadCreatedListener(helpSystemHelper));

        // Message context commands
        features.add(new TransferQuestionCommand(config));

        // User context commands

        // Slash commands
        features.add(new LogLevelCommand());
        features.add(new PingCommand());
        features.add(new TeXCommand());
        features.add(new TagCommand(tagSystem));
        features.add(new TagManageCommand(tagSystem, modAuditLogWriter));
        features.add(new TagsCommand(tagSystem));
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
        features.add(new GitHubCommand(githubReference));
        features.add(new ModMailCommand(jda, config));
        features.add(new HelpThreadCommand(config, helpSystemHelper));
        features.add(new ReportCommand(config));
        features.add(new BookmarksCommand(bookmarksSystem));
        features.add(new ChatGptCommand(chatGptService, helpSystemHelper));
        features.add(new JShellCommand(jshellEval));

        FeatureBlacklist<Class<?>> blacklist = blacklistConfig.normal();
        return blacklist.filterStream(features.stream(), Object::getClass).toList();
    }
}
