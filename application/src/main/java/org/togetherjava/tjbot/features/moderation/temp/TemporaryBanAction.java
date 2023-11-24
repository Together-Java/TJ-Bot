package org.togetherjava.tjbot.features.moderation.temp;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.moderation.ModerationAction;
import org.togetherjava.tjbot.logging.LogMarkers;

/**
 * Action to revoke temporary bans, as applied by
 * {@link org.togetherjava.tjbot.features.moderation.BanCommand} and executed by
 * {@link TemporaryModerationRoutine}.
 */
final class TemporaryBanAction implements RevocableModerationAction {
    private static final Logger logger = LoggerFactory.getLogger(TemporaryBanAction.class);

    @Override
    public ModerationAction getApplyType() {
        return ModerationAction.BAN;
    }

    @Override
    public ModerationAction getRevokeType() {
        return ModerationAction.UNBAN;
    }

    @Override
    public RestAction<Void> revokeAction(Guild guild, User target, String reason) {
        return guild.unban(target).reason(reason);
    }

    @Override
    public FailureIdentification handleRevokeFailure(Throwable failure, long targetId) {
        if (failure instanceof ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                logger.debug(LogMarkers.SENSITIVE,
                        "Attempted to revoke a temporary ban but user '{}' does not exist anymore.",
                        targetId);
                return FailureIdentification.KNOWN;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                logger.debug(LogMarkers.SENSITIVE,
                        "Attempted to revoke a temporary ban but the user '{}' is not banned anymore.",
                        targetId);
                return FailureIdentification.KNOWN;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS) {
                logger.warn("Attempted to revoke a temporary ban but the bot lacks permission.");
                return FailureIdentification.KNOWN;
            }
        }
        return FailureIdentification.UNKNOWN;
    }
}
