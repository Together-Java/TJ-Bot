package org.togetherjava.tjbot.commands.modmail;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.config.Config;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Utility methods that interact either directly or indirectly with the {@link ModmailCommand}.
 */
public class ModMailUtil {

    public static final Predicate<String> isModRole =
            Pattern.compile(Config.getInstance().getHeavyModerationRolePattern())
                .asMatchPredicate();

    private ModMailUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Clears the list of moderators in the {@link ModmailCommand}.
     */
    public static void clearMods() {
        ModmailCommand.mods.clear();
        ModmailCommand.modsMap.clear();
    }

    /**
     * Finds the moderators on the given guild and stores it for later use.
     * <p>
     * This call is expensive to make thus, it shall only be used preferably once by storing the
     * result somewhere or seldomly when moderators want to reload the list of moderators to choose
     * from from the {@link ModmailCommand}.
     * <p/>
     * The previous result stored by this method will be overwritten if there was any.
     */
    public static void loadMenuOptions(@NotNull Guild guild) {
        clearMods();

        Role modRole = getModRole(guild)
            .orElseThrow(() -> new IllegalStateException("No moderator role found"));

        guild.findMembersWithRoles(modRole).get().stream().forEach(mod -> {
            String modId = mod.getId();
            ModmailCommand.mods.add(SelectOption.of(mod.getEffectiveName(), modId));
            ModmailCommand.modsMap.put(modId, mod.getUser());
        });
    }

    /**
     * Gets the moderator role.
     *
     * @param guild the guild to get the moderator role from
     * @return the moderator role, if found
     */
    public static @NotNull Optional<Role> getModRole(@NotNull Guild guild) {
        return guild.getRoles().stream().filter(role -> isModRole.test(role.getName())).findAny();
    }

    /**
     * Checks whether the given member is a moderator on the given guild.
     *
     * @param member the member to check for moderator role.
     * @param guild the guild to get the moderator role from.
     * @return
     */
    public static boolean doesUserHaveModRole(@NotNull Member member, @NotNull Guild guild) {
        return member.canInteract(getModRole(guild)
            .orElseThrow(() -> new IllegalStateException("No moderator role found")));
    }

}
