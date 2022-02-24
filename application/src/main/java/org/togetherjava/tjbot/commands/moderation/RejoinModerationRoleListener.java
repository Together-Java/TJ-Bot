package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.EventReceiver;
import org.togetherjava.tjbot.config.Config;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reapplies existing moderation roles, such as mute or quarantine, to users who have left and
 * rejoined a guild.
 * <p>
 * Such actions are realized with roles and roles are removed upon leaving a guild, making it
 * possible for users to otherwise bypass a mute by simply leaving and rejoining a guild. This class
 * listens for join events and reapplies these roles in case the user is supposed to be e.g. muted
 * still (according to the {@link ModerationActionsStore}).
 */
public final class RejoinModerationRoleListener implements EventReceiver {
    private static final Logger logger =
            LoggerFactory.getLogger(RejoinModerationRoleListener.class);

    private final ModerationActionsStore actionsStore;
    private final List<ModerationRole> moderationRoles;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command and to retrieve whether a
     *        user should be e.g. muted
     * @param config the config to use for this
     */
    public RejoinModerationRoleListener(@NotNull ModerationActionsStore actionsStore,
            @NotNull Config config) {
        this.actionsStore = actionsStore;

        moderationRoles = List.of(
                new ModerationRole("mute", ModerationAction.MUTE, ModerationAction.UNMUTE,
                        guild -> ModerationUtils.getMutedRole(guild, config).orElseThrow()),
                new ModerationRole("quarantine", ModerationAction.QUARANTINE,
                        ModerationAction.UNQUARANTINE,
                        guild -> ModerationUtils.getQuarantinedRole(guild, config).orElseThrow()));
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof GuildMemberJoinEvent joinEvent) {
            onGuildMemberJoin(joinEvent);
        }
    }

    private void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        Member member = event.getMember();

        for (ModerationRole moderationRole : moderationRoles) {
            if (shouldApplyModerationRole(moderationRole, member)) {
                applyModerationRole(moderationRole, member);
            }
        }
    }

    private boolean shouldApplyModerationRole(@NotNull ModerationRole moderationRole,
            @NotNull IPermissionHolder member) {
        Optional<ActionRecord> lastApplyAction = actionsStore.findLastActionAgainstTargetByType(
                member.getGuild().getIdLong(), member.getIdLong(), moderationRole.applyAction);
        if (lastApplyAction.isEmpty()) {
            // User was never e.g. muted
            return false;
        }

        Optional<ActionRecord> lastRevokeAction = actionsStore.findLastActionAgainstTargetByType(
                member.getGuild().getIdLong(), member.getIdLong(), moderationRole.revokeAction);
        if (lastRevokeAction.isEmpty()) {
            // User was never e.g. unmuted
            return isActionEffective(lastApplyAction.orElseThrow());
        }

        // The last issued action takes priority
        if (lastApplyAction.orElseThrow()
            .issuedAt()
            .isAfter(lastRevokeAction.orElseThrow().issuedAt())) {
            return isActionEffective(lastApplyAction.orElseThrow());
        }
        return false;
    }

    private static boolean isActionEffective(@NotNull ActionRecord action) {
        // Effective if permanent or expires in the future
        return action.actionExpiresAt() == null || action.actionExpiresAt().isAfter(Instant.now());
    }

    private static void applyModerationRole(@NotNull ModerationRole moderationRole,
            @NotNull Member member) {
        Guild guild = member.getGuild();
        logger.info("Reapplied existing {} to user '{}' ({}) in guild '{}' after rejoining.",
                moderationRole.actionName, member.getUser().getAsTag(), member.getId(),
                guild.getName());

        guild.addRoleToMember(member, moderationRole.guildToRole.apply(guild))
            .reason("Reapplied existing %s after rejoining the server"
                .formatted(moderationRole.actionName))
            .queue();
    }

    private record ModerationRole(@NotNull String actionName, @NotNull ModerationAction applyAction,
            @NotNull ModerationAction revokeAction, @NotNull Function<Guild, Role> guildToRole) {
    }
}
