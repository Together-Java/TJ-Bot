package org.togetherjava.tjbot.features.utils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Utility methods for working with {@link Guild}s.
 * <p>
 * This class is meant to contain all utility methods for {@link Guild}s that can be used on all
 * other commands to avoid similar methods appearing everywhere.
 */
public final class Guilds {
    private Guilds() {
        throw new UnsupportedOperationException("Utility class, construction not supported");
    }

    /**
     * Finds any role in the guild whose name matches the given predicate.
     *
     * @param guild guild to search the role in
     * @param isRoleName a predicate matching the name of the role to search
     * @return the matched role, if any
     */
    public static Optional<Role> findRole(Guild guild, Predicate<? super String> isRoleName) {
        return guild.getRoles().stream().filter(role -> isRoleName.test(role.getName())).findAny();
    }

    /**
     * Checks whether a given member has a role whose name matches a given predicate.
     *
     * @param member the member to check
     * @param isRoleName a predicate matching the name of the role to search
     * @return {@code true} if the member has a matching role, {@code false} otherwise
     * @see #doesMemberNotHaveRole(Member, Predicate)
     */
    public static boolean hasMemberRole(Member member, Predicate<? super String> isRoleName) {
        return member.getRoles().stream().map(Role::getName).anyMatch(isRoleName);
    }

    /**
     * Checks whether a given member does not have a role whose name matches a given predicate.
     *
     * @param member the member to check
     * @param isRoleName a predicate matching the name of the role to search
     * @return {@code true} if the member does not have any matching role, {@code false} otherwise
     * @see #hasMemberRole(Member, Predicate)
     */
    public static boolean doesMemberNotHaveRole(Member member,
            Predicate<? super String> isRoleName) {
        return member.getRoles().stream().map(Role::getName).noneMatch(isRoleName);
    }
}
