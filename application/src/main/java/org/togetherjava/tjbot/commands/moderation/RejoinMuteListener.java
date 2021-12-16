package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.*;

/**
 * Reapplies existing mutes to users who have left and rejoined a guild.
 * <p>
 * Mutes are realized with roles and roles are removed upon leaving a guild, making it possible for
 * users to otherwise bypass a mute by simply leaving and rejoining a guild. This class listens for
 * join events and reapplies the mute role in case the user is supposed to be muted still (according
 * to the {@link ModerationActionsStore}).
 */
public final class RejoinMuteListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RejoinMuteListener.class);

    private final ModerationActionsStore actionsStore;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command and to retrieve whether a
     *        user should be muted
     */
    public RejoinMuteListener(@NotNull ModerationActionsStore actionsStore) {
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static void muteMember(@NotNull Member member) {
        Guild guild = member.getGuild();
        logger.info("Reapplied existing mute to user '{}' ({}) in guild '{}' after rejoining.",
                member.getUser().getAsTag(), member.getId(), guild.getName());

        guild.addRoleToMember(member, ModerationUtils.getMutedRole(guild).orElseThrow())
            .reason("Reapplied existing mute after rejoining the server")
            .queue();
    }

    private static boolean isActionEffective(@NotNull ActionRecord action) {
        // Effective if permanent or expires in the future
        return action.actionExpiresAt() == null || action.actionExpiresAt().isAfter(Instant.now());
    }

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
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
