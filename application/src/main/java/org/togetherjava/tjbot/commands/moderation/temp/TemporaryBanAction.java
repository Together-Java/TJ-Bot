package org.togetherjava.tjbot.commands.moderation.temp;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.moderation.ModerationAction;

/**
 * Action to revoke temporary bans, as applied by
 * {@link org.togetherjava.tjbot.commands.moderation.BanCommand} and executed by
 * {@link TemporaryModerationRoutine}.
 */
final class TemporaryBanAction implements RevocableModerationAction {
    private static final Logger logger = LoggerFactory.getLogger(TemporaryBanAction.class);

    @Override
    public @NotNull ModerationAction getApplyType() {
        return ModerationAction.BAN;
    }

    @Override
    public @NotNull ModerationAction getRevokeType() {
        return ModerationAction.UNBAN;
    }

    @Override
    public @NotNull RestAction<Void> revokeAction(@NotNull Guild guild, @NotNull User target,
            @NotNull String reason) {
        return guild.unban(target).reason(reason);
    }

    @Override
    public @NotNull FailureIdentification handleRevokeFailure(@NotNull Throwable failure,
            long targetId) {
        if (failure instanceof ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                logger.debug(
                        "Attempted to revoke a temporary ban but user '{}' does not exist anymore.",
                        targetId);
                return FailureIdentification.KNOWN;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                logger.debug(
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
