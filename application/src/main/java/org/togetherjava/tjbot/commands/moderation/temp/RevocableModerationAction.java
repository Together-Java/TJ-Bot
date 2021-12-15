package org.togetherjava.tjbot.commands.moderation.temp;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.moderation.ModerationAction;

// FIXME Javadoc
interface RevocableModerationAction {
    enum FailureIdentification {
        KNOWN,
        UNKNOWN
    }

    @NotNull
    ModerationAction getApplyType();

    @NotNull
    ModerationAction getRevokeType();

    @NotNull
    RestAction<Void> revokeAction(@NotNull Guild guild, @NotNull User target,
            @NotNull String reason);

    @NotNull
    FailureIdentification handleRevokeFailure(@NotNull Throwable failure, long targetId);
}
