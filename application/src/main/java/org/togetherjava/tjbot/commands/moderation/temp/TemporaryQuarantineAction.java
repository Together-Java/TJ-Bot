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
import org.togetherjava.tjbot.commands.moderation.ModerationUtils;
import org.togetherjava.tjbot.config.Config;

/**
 * Action to revoke temporary quarantines, as applied by
 * {@link org.togetherjava.tjbot.commands.moderation.QuarantineCommand} and executed by
 * {@link TemporaryModerationRoutine}.
 */
final class TemporaryQuarantineAction implements RevocableModerationAction {
    private static final Logger logger = LoggerFactory.getLogger(TemporaryQuarantineAction.class);
    private final Config config;

    /**
     * Creates a new instance of a temporary quarantine action.
     *
     * @param config the config to use to identify the quarantined role
     */
    TemporaryQuarantineAction(@NotNull Config config) {
        this.config = config;
    }

    @Override
    public @NotNull ModerationAction getApplyType() {
        return ModerationAction.QUARANTINE;
    }

    @Override
    public @NotNull ModerationAction getRevokeType() {
        return ModerationAction.UNQUARANTINE;
    }

    @Override
    public @NotNull RestAction<Void> revokeAction(@NotNull Guild guild, @NotNull User target,
            @NotNull String reason) {
        return guild
            .removeRoleFromMember(target.getIdLong(),
                    ModerationUtils.getQuarantinedRole(guild, config).orElseThrow())
            .reason(reason);
    }

    @Override
    public @NotNull FailureIdentification handleRevokeFailure(@NotNull Throwable failure,
            long targetId) {
        if (failure instanceof ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                logger.debug(
                        "Attempted to revoke a temporary quarantine but user '{}' does not exist anymore.",
                        targetId);
                return FailureIdentification.KNOWN;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                logger.debug(
                        "Attempted to revoke a temporary quarantine but user '{}' is not a member of the guild anymore.",
                        targetId);
                return FailureIdentification.KNOWN;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_ROLE) {
                logger.warn(
                        "Attempted to revoke a temporary quarantine but the quarantine role can not be found.");
                return FailureIdentification.KNOWN;
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS) {
                logger.warn(
                        "Attempted to revoke a temporary quarantine but the bot lacks permission.");
                return FailureIdentification.KNOWN;
            }
        }
        return FailureIdentification.UNKNOWN;
    }
}
