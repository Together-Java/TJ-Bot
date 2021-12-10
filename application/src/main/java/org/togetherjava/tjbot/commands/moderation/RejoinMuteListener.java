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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        Member member = event.getMember();
        if (!shouldMemberBeMuted(member)) {
            return;
        }
        muteMember(member);
    }

    private boolean shouldMemberBeMuted(@NotNull IPermissionHolder member) {
        List<ActionRecord> actions = actionsStore
            .getActionsByTargetAscending(member.getGuild().getIdLong(), member.getIdLong());
        Collections.reverse(actions);

        Optional<ActionRecord> lastMute = actions.stream()
            .filter(action -> action.actionType() == ModerationUtils.Action.MUTE)
            .findFirst();
        if (lastMute.isEmpty()) {
            // User was never muted
            return false;
        }

        Optional<ActionRecord> lastUnmute = actions.stream()
            .filter(action -> action.actionType() == ModerationUtils.Action.UNMUTE)
            .findFirst();
        if (lastUnmute.isEmpty()) {
            // User was never unmuted
            return true;
        }

        // The last issued action takes priority
        return lastMute.orElseThrow().issuedAt().isAfter(lastUnmute.orElseThrow().issuedAt());
    }
}
