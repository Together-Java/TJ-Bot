package org.togetherjava.tjbot.commands.moderation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.ModerationActions;
import org.togetherjava.tjbot.db.generated.tables.records.ModerationActionsRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// FIXME Javadoc on the whole class
final class ModerationActionsStore {
    private final Database database;

    ModerationActionsStore(@NotNull Database database) {
        this.database = database;
    }

    public @NotNull List<ActionRecord> getActionsByTypeAscending(long guildId,
            @NotNull ModerationUtils.Action actionType) {
        return database.read(context -> context.selectFrom(ModerationActions.MODERATION_ACTIONS)
            .where(ModerationActions.MODERATION_ACTIONS.GUILD_ID.eq(guildId)
                .and(ModerationActions.MODERATION_ACTIONS.ACTION_TYPE.eq(actionType.name())))
            .orderBy(ModerationActions.MODERATION_ACTIONS.ISSUED_AT.asc())
            .stream()
            .map(ActionRecord::of)
            .toList());
    }

    public @NotNull List<ActionRecord> getActionsByTargetAscending(long guildId, long targetId) {
        return database.read(context -> context.selectFrom(ModerationActions.MODERATION_ACTIONS)
            .where(ModerationActions.MODERATION_ACTIONS.GUILD_ID.eq(guildId)
                .and(ModerationActions.MODERATION_ACTIONS.TARGET_ID.eq(targetId)))
            .orderBy(ModerationActions.MODERATION_ACTIONS.ISSUED_AT.asc())
            .stream()
            .map(ActionRecord::of)
            .toList());
    }

    public @NotNull List<ActionRecord> getActionsByAuthorAscending(long guildId, long authorId) {
        return database.read(context -> context.selectFrom(ModerationActions.MODERATION_ACTIONS)
            .where(ModerationActions.MODERATION_ACTIONS.GUILD_ID.eq(guildId)
                .and(ModerationActions.MODERATION_ACTIONS.AUTHOR_ID.eq(authorId)))
            .orderBy(ModerationActions.MODERATION_ACTIONS.ISSUED_AT.asc())
            .stream()
            .map(ActionRecord::of)
            .toList());
    }

    public @NotNull Optional<ActionRecord> findActionByCaseId(int caseId) {
        return Optional
            .of(database.read(context -> context.selectFrom(ModerationActions.MODERATION_ACTIONS)
                .where(ModerationActions.MODERATION_ACTIONS.CASE_ID.eq(caseId))
                .fetchOne()))
            .map(ActionRecord::of);
    }

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

    record ActionRecord(int caseId, @NotNull Instant issuedAt, long guildId, long authorId,
            long targetId, @NotNull ModerationUtils.Action actionType,
            @Nullable Instant actionExpiresAt, @NotNull String reason) {
        private static ActionRecord of(@NotNull ModerationActionsRecord action) {
            return new ActionRecord(action.getCaseId(), action.getIssuedAt(), action.getGuildId(),
                    action.getAuthorId(), action.getTargetId(),
                    ModerationUtils.Action.valueOf(action.getActionType()),
                    action.getActionExpiresAt(), action.getReason());
        }
    }
}
