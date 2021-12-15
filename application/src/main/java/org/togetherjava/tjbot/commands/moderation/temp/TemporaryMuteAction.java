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

// FIXME Javadoc
final class TemporaryMuteAction implements RevocableModerationAction {
    private static final Logger logger = LoggerFactory.getLogger(TemporaryMuteAction.class);

    @Override
    public @NotNull ModerationAction getApplyType() {
        return ModerationAction.MUTE;
    }

    @Override
    public @NotNull ModerationAction getRevokeType() {
        return ModerationAction.UNMUTE;
    }

    @Override
    public @NotNull RestAction<Void> revokeAction(@NotNull Guild guild, @NotNull User target,
            @NotNull String reason) {
        return guild
            .removeRoleFromMember(target.getIdLong(),
                    ModerationUtils.getMutedRole(guild).orElseThrow())
            .reason(reason);
    }

    @Override
    public @NotNull FailureIdentification handleRevokeFailure(@NotNull Throwable failure,
            long targetId) {
        if (failure instanceof ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                logger.info(
                        "Attempted to revoke a temporary mute but user '{}' does not exist anymore.",
                        targetId);
                return FailureIdentification.KNOWN;
            }
        }
        return FailureIdentification.UNKNOWN;
    }
}
