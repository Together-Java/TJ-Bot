package org.togetherjava.tjbot.commands.moderation.temp;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.moderation.ModerationAction;

/**
 * Action to revoke temporary bans, as applied by
 * {@link org.togetherjava.tjbot.commands.moderation.BanCommand} and executed by
 * {@link TemporaryModerationRoutine}.
 */
final class TemporaryBanAction implements RevocableModerationAction {
    @Override
    public @NotNull String actionName() {
        return "ban";
    }

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
}
