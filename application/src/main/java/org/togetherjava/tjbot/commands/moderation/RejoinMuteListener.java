package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.EventReceiver;
import org.togetherjava.tjbot.config.Config;

import java.time.Instant;
import java.util.Optional;

/**
 * Reapplies existing mutes to users who have left and rejoined a guild.
 * <p>
 * Mutes are realized with roles and roles are removed upon leaving a guild, making it possible for
 * users to otherwise bypass a mute by simply leaving and rejoining a guild. This class listens for
 * join events and reapplies the mute role in case the user is supposed to be muted still (according
 * to the {@link ModerationActionsStore}).
 */
public final class RejoinMuteListener implements EventReceiver {
    private static final Logger logger = LoggerFactory.getLogger(RejoinMuteListener.class);

    private final ModerationActionsStore actionsStore;
    private final Config config;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command and to retrieve whether a
     *        user should be muted
     * @param config the config to use for this
     */
    public RejoinMuteListener(@NotNull ModerationActionsStore actionsStore,
            @NotNull Config config) {
        this.actionsStore = actionsStore;
        this.config = config;
    }

    private void muteMember(@NotNull Member member) {
        Guild guild = member.getGuild();
        logger.info("Reapplied existing mute to user '{}' ({}) in guild '{}' after rejoining.",
                member.getUser().getAsTag(), member.getId(), guild.getName());

        guild.addRoleToMember(member, ModerationUtils.getMutedRole(guild, config).orElseThrow())
            .reason("Reapplied existing mute after rejoining the server")
            .queue();
    }

    private static boolean isActionEffective(@NotNull ActionRecord action) {
        // Effective if permanent or expires in the future
        return action.actionExpiresAt() == null || action.actionExpiresAt().isAfter(Instant.now());
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof GuildMemberJoinEvent joinEvent) {
            onGuildMemberJoin(joinEvent);
        }
    }

    private void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        Member member = event.getMember();
        if (!shouldMemberBeMuted(member)) {
            return;
        }
        muteMember(member);
    }

    private boolean shouldMemberBeMuted(@NotNull IPermissionHolder member) {
        Optional<ActionRecord> lastMute = actionsStore.findLastActionAgainstTargetByType(
                member.getGuild().getIdLong(), member.getIdLong(), ModerationAction.MUTE);
        if (lastMute.isEmpty()) {
            // User was never muted
            return false;
        }

        Optional<ActionRecord> lastUnmute = actionsStore.findLastActionAgainstTargetByType(
                member.getGuild().getIdLong(), member.getIdLong(), ModerationAction.UNMUTE);
        if (lastUnmute.isEmpty()) {
            // User was never unmuted
            return isActionEffective(lastMute.orElseThrow());
        }

        // The last issued action takes priority
        if (lastMute.orElseThrow().issuedAt().isAfter(lastUnmute.orElseThrow().issuedAt())) {
            return isActionEffective(lastMute.orElseThrow());
        }
        return false;
    }
}
