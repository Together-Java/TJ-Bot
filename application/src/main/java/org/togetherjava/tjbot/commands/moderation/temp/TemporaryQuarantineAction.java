package org.togetherjava.tjbot.commands.moderation.temp;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.moderation.ModerationAction;
import org.togetherjava.tjbot.commands.moderation.ModerationUtils;
import org.togetherjava.tjbot.config.Config;

/**
 * Action to revoke temporary quarantines, as applied by
 * {@link org.togetherjava.tjbot.commands.moderation.QuarantineCommand} and executed by
 * {@link TemporaryModerationRoutine}.
 */
final class TemporaryQuarantineAction implements RevocableModerationAction {
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
    public @NotNull String actionName() {
        return "quarantine";
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
}
