package org.togetherjava.tjbot.features.moderation.temp;

import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.logging.LogMarkers;

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
    RevocableRoleBasedAction(String actionName) {
        this.actionName = actionName;
    }

    @Override
    public FailureIdentification handleRevokeFailure(Throwable failure, long targetId) {

        if (failure instanceof ErrorResponseException errorResponseException) {
            switch (errorResponseException.getErrorResponse()) {
                case UNKNOWN_USER -> logger.debug(LogMarkers.SENSITIVE,
                        "Attempted to revoke a temporary {} but user '{}' does not exist anymore.",
                        actionName, targetId);
                case UNKNOWN_MEMBER -> logger.debug(LogMarkers.SENSITIVE,
                        "Attempted to revoke a temporary {} but user '{}' is not a member of the guild anymore.",
                        actionName, targetId);
                case UNKNOWN_ROLE -> logger.warn(
                        "Attempted to revoke a temporary {} but the {} role can not be found.",
                        actionName, actionName);
                case MISSING_PERMISSIONS ->
                    logger.warn("Attempted to revoke a temporary {} but the bot lacks permission.",
                            actionName);
                default -> {
                    return FailureIdentification.UNKNOWN;
                }
            }

            return FailureIdentification.KNOWN;
        }

        return FailureIdentification.UNKNOWN;
    }
}
