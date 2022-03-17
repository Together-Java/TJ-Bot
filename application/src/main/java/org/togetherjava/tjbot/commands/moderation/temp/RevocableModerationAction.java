package org.togetherjava.tjbot.commands.moderation.temp;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.moderation.ModerationAction;

/**
 * Represents revocable moderation actions, such as temporary bans. Primarily used by
 * {@link TemporaryModerationRoutine} to identify and revoke such actions.
 */
interface RevocableModerationAction {
    /**
     * The type to apply the temporary action, such as
     * {@link net.dv8tion.jda.api.audit.ActionType#BAN}.
     * 
     * @return the type to apply the temporary action
     */
    @NotNull
    ModerationAction getApplyType();

    /**
     * The type to revoke the temporary action, such as
     * {@link net.dv8tion.jda.api.audit.ActionType#UNBAN}.
     * 
     * @return the type to revoke the temporary action
     */
    @NotNull
    ModerationAction getRevokeType();

    /**
     * Revokes the temporary action against the given target.
     * 
     * @param guild the guild the user belongs to
     * @param target the target to revoke the action against
     * @param reason why the action is revoked
     * @return the unsubmitted revocation action
     */
    @NotNull
    RestAction<Void> revokeAction(@NotNull Guild guild, @NotNull User target,
            @NotNull String reason);

    /**
     * Handle a failure that might occur during revocation, i.e. execution of the action returned by
     * {@link #revokeAction(Guild, User, String)}.
     * 
     * @param errorResponse the error that occurred
     * @param targetId the id of the user who is targeted by the revocation
     * @return whether it should log an unknown failure message or not
     */
    boolean handleRevokeFailure(@NotNull ErrorResponse errorResponse, long targetId);
}
