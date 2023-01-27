package org.togetherjava.tjbot.features.moderation;

import org.jooq.Condition;

import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.ModerationActions;
import org.togetherjava.tjbot.db.generated.tables.records.ModerationActionsRecord;

import javax.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Store for moderation actions, e.g. as banning users. Can be used to retrieve information about
 * past events, such as when a user has been banned the last time.
 * <p>
 * Actions have to be added to the store using
 * {@link #addAction(long, long, long, ModerationAction, Instant, String)} at the time they are
 * executed and can then be retrieved by methods such as
 * {@link #getActionsByTypeAscending(long, ModerationAction)} or {@link #findActionByCaseId(int)}.
 * <p>
 * Be aware that timestamps associated with actions, such as {@link ActionRecord#issuedAt()} are
 * slightly off the timestamps used by Discord.
 * <p>
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
    public ModerationActionsStore(Database database) {
        this.database = Objects.requireNonNull(database);
    }

    /**
     * Gets all already expired actions, measured by the current time, which have been written to
     * the store, chronologically ascending with the action issued the earliest first.
     * 
     * @return a list of all expired actions, chronologically ascending
     */
    public List<ActionRecord> getExpiredActionsAscending() {
        return getActionsAscendingWhere(
                ModerationActions.MODERATION_ACTIONS.ACTION_EXPIRES_AT.isNotNull()
                    .and(ModerationActions.MODERATION_ACTIONS.ACTION_EXPIRES_AT
                        .lessOrEqual(Instant.now())));
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
    public List<ActionRecord> getActionsByTypeAscending(long guildId, ModerationAction actionType) {
        Objects.requireNonNull(actionType);

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
    public List<ActionRecord> getActionsByTargetAscending(long guildId, long targetId) {
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
    public List<ActionRecord> getActionsByAuthorAscending(long guildId, long authorId) {
        return getActionsFromGuildAscending(guildId,
                ModerationActions.MODERATION_ACTIONS.AUTHOR_ID.eq(authorId));
    }

    /**
     * Gets the action of the given type that was issued the latest against the given target, if
     * present.
     * 
     * @param guildId the id of the guild, only actions that happened in the context of that guild
     *        will be retrieved
     * @param targetId the id of the target user to filter for
     * @param actionType the type of the action
     * @return the last action issued against the given user of the given type, if present
     */
    public Optional<ActionRecord> findLastActionAgainstTargetByType(long guildId, long targetId,
            ModerationAction actionType) {
        return database
            .read(context -> context.selectFrom(ModerationActions.MODERATION_ACTIONS)
                .where(ModerationActions.MODERATION_ACTIONS.GUILD_ID.eq(guildId)
                    .and(ModerationActions.MODERATION_ACTIONS.TARGET_ID.eq(targetId))
                    .and(ModerationActions.MODERATION_ACTIONS.ACTION_TYPE.eq(actionType.name())))
                .orderBy(ModerationActions.MODERATION_ACTIONS.ISSUED_AT.desc())
                .limit(1)
                .fetchOptional())
            .map(ActionRecord::of);
    }

    /**
     * Gets the action with the given case id from the store, if present.
     *
     * @param caseId the actions' case id to search for
     * @return the action with the given case id, if present
     */
    public Optional<ActionRecord> findActionByCaseId(int caseId) {
        return database
            .read(context -> context.selectFrom(ModerationActions.MODERATION_ACTIONS)
                .where(ModerationActions.MODERATION_ACTIONS.CASE_ID.eq(caseId))
                .fetchOptional())
            .map(ActionRecord::of);
    }

    /**
     * Adds the given action to the store. A unique case id will be associated to the action and
     * returned.
     * <p>
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
    public int addAction(long guildId, long authorId, long targetId, ModerationAction actionType,
            @Nullable Instant actionExpiresAt, String reason) {
        Objects.requireNonNull(actionType);
        Objects.requireNonNull(reason);

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

    private List<ActionRecord> getActionsFromGuildAscending(long guildId, Condition condition) {
        Objects.requireNonNull(condition);

        return getActionsAscendingWhere(
                ModerationActions.MODERATION_ACTIONS.GUILD_ID.eq(guildId).and(condition));
    }

    private List<ActionRecord> getActionsAscendingWhere(Condition condition) {
        Objects.requireNonNull(condition);

        return database.read(context -> context.selectFrom(ModerationActions.MODERATION_ACTIONS)
            .where(condition)
            .orderBy(ModerationActions.MODERATION_ACTIONS.ISSUED_AT.asc())
            .stream()
            .map(ActionRecord::of)
            .toList());
    }
}
