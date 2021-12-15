package org.togetherjava.tjbot.commands.moderation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.togetherjava.tjbot.db.generated.tables.records.ModerationActionsRecord;

import java.time.Instant;

/**
 * Record for actions as maintained by {@link ModerationActionsStore}. Each action has a unique
 * caseId.
 *
 * @param caseId the unique case id associated with this action
 * @param issuedAt the instant at which this action was issued
 * @param guildId the id of the guild in which context this action happened
 * @param authorId the id of the user who issued the action
 * @param targetId the id of the user who was the target of the action
 * @param actionType the type of the action
 * @param actionExpiresAt the instant at which this action expires, for temporary actions; otherwise
 *        {@code null}
 * @param reason the reason why this action was executed
 */
public record ActionRecord(int caseId, @NotNull Instant issuedAt, long guildId, long authorId,
        long targetId, @NotNull ModerationAction actionType, @Nullable Instant actionExpiresAt,
        @NotNull String reason) {

    /**
     * Creates the action record that corresponds to the given action entry from the database table.
     * 
     * @param action the action to convert
     * @return the corresponding action record
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static @NotNull ActionRecord of(@NotNull ModerationActionsRecord action) {
        return new ActionRecord(action.getCaseId(), action.getIssuedAt(), action.getGuildId(),
                action.getAuthorId(), action.getTargetId(),
                ModerationAction.valueOf(action.getActionType()), action.getActionExpiresAt(),
                action.getReason());
    }
}
