package org.togetherjava.tjbot.commands.moderation.temp;

import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Role based moderation actions that can be revoked, for example a {@link TemporaryMuteAction} or a
 * {@link TemporaryBanAction}, which are applied implicitly purely by the presence of a role.
 */
abstract class RevocableRoleBasedAction implements RevocableModerationAction {
    private static final Logger logger = LoggerFactory.getLogger(RevocableRoleBasedAction.class);

    private final String actionName;

    /**
     * Creates a new role based action.
     *
     * @param actionName the action name to be used in logging in case of a failure, e.g.
     *        {@code "mute"}, {@code "quarantine"}
     */
    RevocableRoleBasedAction(@NotNull String actionName) {
        this.actionName = actionName;
    }

    @Override
    public @NotNull FailureIdentification handleRevokeFailure(@NotNull Throwable failure,
            long targetId) {

        if (failure instanceof ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                logger.debug(
                        "Attempted to revoke a temporary {} but user '{}' does not exist anymore.",
                        actionName, targetId);
                return FailureIdentification.KNOWN;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                logger.debug(
                        "Attempted to revoke a temporary {} but user '{}' is not a member of the guild anymore.",
                        actionName, targetId);
                return FailureIdentification.KNOWN;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_ROLE) {
                logger.warn("Attempted to revoke a temporary {} but the {} role can not be found.",
                        actionName, actionName);
                return FailureIdentification.KNOWN;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS) {
                logger.warn("Attempted to revoke a temporary {} but the bot lacks permission.",
                        actionName);
                return FailureIdentification.KNOWN;
            }
        }
        return FailureIdentification.UNKNOWN;
    }
}
