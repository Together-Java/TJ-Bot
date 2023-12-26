package org.togetherjava.tjbot.config;


/**
 * Config for automatic pruning of helper roles, see
 * {@link org.togetherjava.tjbot.features.help.AutoPruneHelperRoutine}.
 *
 * @param roleFullLimit if a helper role contains that many users, it is considered full and pruning
 *        must occur
 * @param roleFullThreshold if a helper role contains that many users, pruning will start to occur
 *        to prevent reaching the limit
 * @param pruneMemberAmount amount of users to remove from helper roles during a prune
 * @param inactivateAfterDays after how many days of inactivity a user is eligible for pruning
 * @param recentlyJoinedDays if a user is with the server for just this amount of days, they are
 *        protected from pruning
 */
public record HelperPruneConfig(int roleFullLimit, int roleFullThreshold, int pruneMemberAmount,
        int inactivateAfterDays, int recentlyJoinedDays) {
}
