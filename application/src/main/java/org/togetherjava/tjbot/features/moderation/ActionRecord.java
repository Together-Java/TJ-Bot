package org.togetherjava.tjbot.features.moderation;

import org.togetherjava.tjbot.db.generated.tables.records.ModerationActionsRecord;

import javax.annotation.Nullable;

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
public record ActionRecord(int caseId, Instant issuedAt, long guildId, long authorId, long targetId,
        ModerationAction actionType, @Nullable Instant actionExpiresAt, String reason) {

    /**
     * Creates the action record that corresponds to the given action entry from the database table.
     *
     * @param action the action to convert
     * @return the corresponding action record
     */
    static ActionRecord of(ModerationActionsRecord action) {
        return new ActionRecord(action.getCaseId(), action.getIssuedAt(), action.getGuildId(),
                action.getAuthorId(), action.getTargetId(),
                ModerationAction.valueOf(action.getActionType()), action.getActionExpiresAt(),
                action.getReason());
    }

    /**
     * Whether this action is still effective. That is, it is either a permanent action or temporary
     * but not expired yet.
     *
     * @return True when still effective, false otherwise
     */
    public boolean isEffective() {
        return actionExpiresAt == null || actionExpiresAt.isAfter(Instant.now());
    }
}
