package org.togetherjava.tjbot.commands.moderation.temp;

import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class RevocableRoleBasedAction implements RevocableModerationAction {
    private static final Logger logger = LoggerFactory.getLogger(RevocableRoleBasedAction.class);

    private final String actionName;

    RevocableRoleBasedAction(String actionName) {
        this.actionName = actionName;
    }

    @Override
    public boolean handleRevokeFailure(@NotNull ErrorResponse error, long targetId) {
        switch (error) {
            case UNKNOWN_USER -> logger.debug(
                    "Attempted to revoke a temporary {} but user '{}' does not exist anymore.",
                    actionName, targetId);
            case UNKNOWN_MEMBER -> logger.debug(
                    "Attempted to revoke a temporary {} but user '{}' is not a member of the guild anymore.",
                    actionName, targetId);
            case UNKNOWN_ROLE -> logger.warn(
                    "Attempted to revoke a temporary {} but the {} role can not be found.",
                    actionName, actionName);
            case MISSING_PERMISSIONS -> logger.warn(
                    "Attempted to revoke a temporary {} but the bot lacks permission.", actionName);
            default -> {
                return true;
            }
        }

        return false;
    }
}
