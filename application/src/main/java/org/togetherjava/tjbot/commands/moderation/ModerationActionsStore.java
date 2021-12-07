package org.togetherjava.tjbot.commands.moderation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.Condition;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.ModerationActions;
import org.togetherjava.tjbot.db.generated.tables.records.ModerationActionsRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Store for moderation actions, e.g. as banning users. Can be used to retrieve information about
 * past events, such as when a user has been banned the last time.
 *
 * Actions have to be added to the store using
 * {@link #addAction(long, long, long, ModerationUtils.Action, Instant, String)} at the time they
 * are executed and can then be retrieved by methods such as
 * {@link #getActionsByTypeAscending(long, ModerationUtils.Action)} or
 * {@link #findActionByCaseId(int)}.
 *
 * Be aware that timestamps associated with actions, such as {@link ActionRecord#issuedAt()} are
 * slightly off the timestamps used by Discord.
 *
 * The store persists the actions and is thread safe.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class ModerationActionsStore {
    private final Database database;

    /**
     * Creates a new instance which writes and retrieves actions from a given database.
     * 
     * @param database the database to write and retrieve actions from
     */
    public ModerationActionsStore(@NotNull Database database) {
        this.database = database;
    }

    /**
     * Gets all actions of a given type that have been written to the store, chronologically
     * ascending with the earliest action first.
     * 
     * @param guildId the id of the guild, only actions that happened in the context of that guild
     *        will be retrieved
     * @param actionType the type of action to filter for
     * @return a list of all actions with the given type, chronologically ascending
     */
    public @NotNull List<ActionRecord> getActionsByTypeAscending(long guildId,
            @NotNull ModerationUtils.Action actionType) {
        return getActionsFromGuildAscending(guildId,
                ModerationActions.MODERATION_ACTIONS.ACTION_TYPE.eq(actionType.name()));
    }

    /**
     * Gets all actions executed against a given target that have been written to the store,
     * chronologically ascending with the earliest action first.
     * 
     * @param guildId the id of the guild, only actions that happened in the context of that guild
     *        will be retrieved
     * @param targetId the id of the target user to filter for
     * @return a list of all actions executed against the target, chronologically ascending
     */
    public @NotNull List<ActionRecord> getActionsByTargetAscending(long guildId, long targetId) {
        return getActionsFromGuildAscending(guildId,
                ModerationActions.MODERATION_ACTIONS.TARGET_ID.eq(targetId));
    }

    /**
     * Gets all actions executed by a given author that have been written to the store,
     * chronologically ascending with the earliest action first.
     * 
     * @param guildId the id of the guild, only actions that happened in the context of that guild
     *        will be retrieved
     * @param authorId the id of the author user to filter for
     * @return a list of all actions executed by the author, chronologically ascending
     */
    public @NotNull List<ActionRecord> getActionsByAuthorAscending(long guildId, long authorId) {
        return getActionsFromGuildAscending(guildId,
                ModerationActions.MODERATION_ACTIONS.AUTHOR_ID.eq(authorId));
    }

    /**
     * Gets the action with the given case id from the store, if present.
     * 
     * @param caseId the actions' case id to search for
     * @return the action with the given case id, if present
     */
    public @NotNull Optional<ActionRecord> findActionByCaseId(int caseId) {
        return Optional
            .of(database.read(context -> context.selectFrom(ModerationActions.MODERATION_ACTIONS)
                .where(ModerationActions.MODERATION_ACTIONS.CASE_ID.eq(caseId))
                .fetchOne()))
            .map(ActionRecord::of);
    }

    /**
     * Adds the given action to the store. A unique case id will be associated to the action and
     * returned.
     *
     * It is assumed that the action is issued at the point in time this method is called. It is not
     * possible to assign a different timestamp, especially not an earlier point in time.
     * Consequently, this causes the timestamps to be slightly off from the timestamps recorded by
     * Discord itself.
     *
     * @param guildId the id of the guild in which context this action happened
     * @param authorId the id of the user who issued the action
     * @param targetId the id of the user who was the target of the action
     * @param actionType the type of the action
     * @param actionExpiresAt the instant at which this action expires, for temporary actions;
     *        otherwise {@code null}
     * @param reason the reason why this action was executed
     * @return the unique case id associated with the action
     */
    @SuppressWarnings("MethodWithTooManyParameters")
    public int addAction(long guildId, long authorId, long targetId,
            @NotNull ModerationUtils.Action actionType, @Nullable Instant actionExpiresAt,
            @NotNull String reason) {
        return database.writeAndProvide(context -> {
            ModerationActionsRecord actionRecord =
                    context.newRecord(ModerationActions.MODERATION_ACTIONS)
                        .setIssuedAt(Instant.now())
                        .setGuildId(guildId)
                        .setAuthorId(authorId)
                        .setTargetId(targetId)
                        .setActionType(actionType.name())
                        .setActionExpiresAt(actionExpiresAt)
                        .setReason(reason);
            actionRecord.insert();
            return actionRecord.getCaseId();
        });
    }

    private @NotNull List<ActionRecord> getActionsFromGuildAscending(long guildId,
            @NotNull Condition condition) {
        return database.read(context -> context.selectFrom(ModerationActions.MODERATION_ACTIONS)
            .where(ModerationActions.MODERATION_ACTIONS.GUILD_ID.eq(guildId).and(condition))
            .orderBy(ModerationActions.MODERATION_ACTIONS.ISSUED_AT.asc())
            .stream()
            .map(ActionRecord::of)
            .toList());
    }
}
