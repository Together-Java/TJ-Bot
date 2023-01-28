package org.togetherjava.tjbot.features.moderation.temp;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestAction;

import org.togetherjava.tjbot.features.moderation.ModerationAction;

/**
 * Represents revocable moderation actions, such as temporary bans. Primarily used by
 * {@link TemporaryModerationRoutine} to identify and revoke such actions.
 */
interface RevocableModerationAction {
    /**
     * Classification of a revocation failure.
     */
    @SuppressWarnings("PublicInnerClass")
    enum FailureIdentification {
        /**
         * Acknowledges that the failure is known and has been handled. Hence, further error
         * handling should not be continued.
         */
        KNOWN,
        /**
         * The failure is unknown and has not been handled. Hence, further error handling should be
         * continued.
         */
        UNKNOWN
    }

    /**
     * The type to apply the temporary action, such as
     * {@link net.dv8tion.jda.api.audit.ActionType#BAN}.
     * 
     * @return the type to apply the temporary action
     */
    ModerationAction getApplyType();

    /**
     * The type to revoke the temporary action, such as
     * {@link net.dv8tion.jda.api.audit.ActionType#UNBAN}.
     * 
     * @return the type to revoke the temporary action
     */
    ModerationAction getRevokeType();

    /**
     * Revokes the temporary action against the given target.
     * 
     * @param guild the guild the user belongs to
     * @param target the target to revoke the action against
     * @param reason why the action is revoked
     * @return the unsubmitted revocation action
     */
    RestAction<Void> revokeAction(Guild guild, User target, String reason);

    /**
     * Handle a failure that might occur during revocation, i.e. execution of the action returned by
     * {@link #revokeAction(Guild, User, String)}.
     * 
     * @param failure the failure to handle
     * @param targetId the id of the user who is targeted by the revocation
     * @return a classification of the failure, decides whether the surrounding flow will continue
     *         to handle the error further or not
     */
    FailureIdentification handleRevokeFailure(Throwable failure, long targetId);
}
