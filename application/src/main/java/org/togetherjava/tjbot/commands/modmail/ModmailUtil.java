package org.togetherjava.tjbot.commands.modmail;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.config.Config;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Utility methods that interact either directly or indirectly with the {@link ModmailCommand}.
 */
public class ModmailUtil {

    public static final Predicate<String> isModRole =
            Pattern.compile(Config.getInstance().getHeavyModerationRolePattern())
                .asMatchPredicate();

    private ModmailUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Finds the moderators on the given guild and stores it for later use.
     * <p>
     * This call is expensive, thus, it should only be used preferably once by storing the result
     * from the given list or seldomly when moderators want to reload the list of moderators to
     * choose from from the {@link ModmailCommand}.
     * <p/>
     * Since the elements in the given list will not be overwritten, the caller is responsible in
     * doing such actions.
     */
    public static void listOfMod(@NotNull Guild guild, List<SelectOption> modsOptions) {
        Role modRole = getModRole(guild)
            .orElseThrow(() -> new IllegalStateException("No moderator role found"));

        guild.findMembersWithRoles(modRole)
            .onSuccess(mods -> mods.forEach(
                    mod -> modsOptions.add(SelectOption.of(mod.getEffectiveName(), mod.getId()))));
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
     * <p>
     * See {@link Config#getHeavyModerationRolePattern()}.
     *
     * @param member the member to check for moderator role.
     * @param guild the guild to get the moderator role from.
     * @return true if the member has the role Moderator
     */
    public static boolean doesUserHaveModRole(@NotNull Member member, @NotNull Guild guild) {
        return member.canInteract(getModRole(guild)
            .orElseThrow(() -> new IllegalStateException("No moderator role found")));
    }

    /**
     * Creates a color black {@link MessageEmbed} with a non-inline field of the supplied message.
     *
     * @param user the user who invoked the command.
     * @param message the message the user wants to send to to a moderator or the moderators.
     * @return returns a {@link MessageEmbed} to send to the moderator.
     */
    public static MessageEmbed messageEmbed(@NotNull String user, @NotNull String message) {
        return new EmbedBuilder().setAuthor("Modmail Command invoked")
            .setColor(Color.BLACK)
            .setTitle("Message from user '%s' who used /modmail command".formatted(user))
            .addField("Message", message, false)
            .build();
    }

}
