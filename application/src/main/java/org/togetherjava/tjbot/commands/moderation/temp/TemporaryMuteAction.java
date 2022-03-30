package org.togetherjava.tjbot.commands.moderation.temp;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.moderation.ModerationAction;
import org.togetherjava.tjbot.commands.moderation.ModerationUtils;
import org.togetherjava.tjbot.config.Config;

/**
 * Action to revoke temporary mutes, as applied by
 * {@link org.togetherjava.tjbot.commands.moderation.MuteCommand} and executed by
 * {@link TemporaryModerationRoutine}.
 */
final class TemporaryMuteAction implements RevocableModerationAction {
    private final Config config;

    /**
     * Creates a new instance of a temporary mute action.
     * 
     * @param config the config to use to identify the muted role
     */
    TemporaryMuteAction(@NotNull Config config) {
        this.config = config;
    }

    @Override
    public @NotNull String actionName() {
        return "mute";
    }

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
                    ModerationUtils.getMutedRole(guild, config).orElseThrow())
            .reason(reason);
    }
}
