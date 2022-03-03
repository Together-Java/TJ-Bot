package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.commands.utils.DiscordClientAction;

import javax.annotation.CheckReturnValue;
import java.awt.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This command allows you to look up user (or member) info.
 */
@SuppressWarnings("ClassWithoutLogger")
public final class WhoIsCommand extends SlashCommandAdapter {
    private static final String USER_OPTION = "user";
    private static final String SHOW_SERVER_INFO_OPTION = "show_server_specific_info";

    private static final String USER_PROFILE_PICTURE_SIZE = "4096";

    // Sun, December 11, 2016, 13:36:30
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("E, MMMM d, u, HH:mm:ss");

    /**
     * Creates an instance.
     */
    public WhoIsCommand() {
        super("whois", "Provides info about the given user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "the user to look up", true)
            .addOption(OptionType.BOOLEAN, SHOW_SERVER_INFO_OPTION,
                    "Whenever to show info that is specific to this server, such as their roles. This is true by default.",
                    false);
    }

    @Override
    public void onSlashCommand(@NotNull final SlashCommandEvent event) {
        OptionMapping userOption = Objects.requireNonNull(event.getOption(USER_OPTION),
                "The given user option cannot be null");
        OptionMapping showServerSpecificInfoOption = event.getOption(SHOW_SERVER_INFO_OPTION);

        User user = userOption.getAsUser();
        Member member = userOption.getAsMember();

        boolean showServerSpecificInfo = null != member && (null == showServerSpecificInfoOption
                || showServerSpecificInfoOption.getAsBoolean());

        user.retrieveProfile().flatMap((User.Profile profile) -> {
            if (showServerSpecificInfo) {
                return handleWhoIsMember(event, member, profile);
            } else {
                return handleWhoIsUser(event, user, profile);
            }
        }).queue();
    }

    @CheckReturnValue
    private static @NotNull ReplyAction handleWhoIsUser(final @NotNull SlashCommandEvent event,
            final @NotNull User user, final @NotNull User.Profile profile) {

        StringBuilder descriptionBuilder =
                new StringBuilder().append(userIdentificationToStringItem(user))
                    .append("\n**Is bot:** ")
                    .append(user.isBot())
                    .append(userFlagsToStringItem(user.getFlags()))
                    .append("\n**Registration date:** ")
                    .append(DATE_TIME_FORMAT.format(user.getTimeCreated()));

        EmbedBuilder embedBuilder =
                generateEmbedBuilder(event, user, profile, profile.getAccentColor()).setAuthor(
                        user.getName(), user.getEffectiveAvatarUrl(), user.getEffectiveAvatarUrl())
                    .setDescription(descriptionBuilder);

        return event.replyEmbeds(embedBuilder.build())
            .addActionRow(Button.of(ButtonStyle.LINK, "discord://-/users/" + user.getId(),
                    "Click to see profile"));
    }

    @CheckReturnValue
    private static @NotNull ReplyAction handleWhoIsMember(final @NotNull SlashCommandEvent event,
            final @NotNull Member member, final @NotNull User.Profile profile) {
        User user = member.getUser();

        Color memberColor = member.getColor();
        Color effectiveColor = (null == memberColor) ? profile.getAccentColor() : memberColor;

        StringBuilder descriptionBuilder =
                new StringBuilder().append(userIdentificationToStringItem(user))
                    .append(voiceStateToStringItem(member))
                    .append("\n**Is bot:** ")
                    .append(user.isBot())
                    .append(possibleBoosterToStringItem(member))
                    .append(userFlagsToStringItem(user.getFlags()))
                    .append("\n**Join date:** ")
                    .append(DATE_TIME_FORMAT.format(member.getTimeJoined()))
                    .append("\n**Registration date:** ")
                    .append(DATE_TIME_FORMAT.format(user.getTimeCreated()))
                    .append("\n**Roles:** ")
                    .append(formatRoles(member));

        EmbedBuilder embedBuilder = generateEmbedBuilder(event, user, profile, effectiveColor)
            .setAuthor(member.getEffectiveName(), member.getEffectiveAvatarUrl(),
                    member.getEffectiveAvatarUrl())
            .setDescription(descriptionBuilder);

        return event.replyEmbeds(embedBuilder.build())
            .addActionRow(DiscordClientAction.General.USER.asLinkButton("Click to see profile!",
                    user.getId()));
    }

    private static @NotNull String voiceStateToStringItem(@NotNull final Member member) {
        GuildVoiceState voiceState = Objects.requireNonNull(member.getVoiceState(),
                "The given voiceState cannot be null");
        if (voiceState.inVoiceChannel()) {
            return "\n**In voicechannel:** " + (voiceState.getChannel().getAsMention());
        } else {
            return "";
        }
    }


    /**
     * Generates whois embed based on the given parameters.
     *
     * @param event the {@link SlashCommandEvent}
     * @param user the {@link User} getting whois'd
     * @param profile the {@link net.dv8tion.jda.api.entities.User.Profile} of the whois'd user
     * @param effectiveColor the {@link Color} that the embed will become
     * @return the generated {@link EmbedBuilder}
     */
    private static @NotNull EmbedBuilder generateEmbedBuilder(
            @NotNull final SlashCommandEvent event, @NotNull final User user,
            final @NotNull User.Profile profile, final Color effectiveColor) {

        EmbedBuilder embedBuilder = new EmbedBuilder().setThumbnail(user.getEffectiveAvatarUrl())
            .setColor(effectiveColor)
            .setFooter("Requested by " + event.getUser().getAsTag(),
                    event.getMember().getEffectiveAvatarUrl())
            .setTimestamp(Instant.now());

        if (null != profile.getBannerId()) {
            embedBuilder.setImage(profile.getBannerUrl() + "?size=" + USER_PROFILE_PICTURE_SIZE);
        }

        return embedBuilder;
    }

    /**
     * Handles boosting properties of a {@link Member}
     *
     * @param member the {@link Member} to take the booster properties from
     * @return user readable {@link String}
     */
    private static @NotNull String possibleBoosterToStringItem(final @NotNull Member member) {
        OffsetDateTime timeBoosted = member.getTimeBoosted();
        if (null != timeBoosted) {
            return "\n**Is booster:** true \n**Boosting since:** "
                    + DATE_TIME_FORMAT.format(timeBoosted);
        } else {
            return "\n**Is booster:** false";
        }
    }

    /**
     * Handles the user's identifying properties (such as ID, tag)
     *
     * @param user the {@link User} to take the identifiers from
     * @return user readable {@link StringBuilder}
     */
    private static @NotNull StringBuilder userIdentificationToStringItem(final @NotNull User user) {
        return new StringBuilder("**Mention:** ").append(user.getAsMention())
            .append("\n**Tag:** ")
            .append(user.getAsTag())
            .append("\n**ID:** ")
            .append(user.getId());
    }

    /**
     * Formats the roles into a user readable {@link String}
     *
     * @param member member to take the Roles from
     * @return user readable {@link String} of the roles
     */
    private static String formatRoles(final @NotNull Member member) {
        return member.getRoles().stream().map(Role::getAsMention).collect(Collectors.joining(", "));
    }

    /**
     * Formats Hypesquad and the flags
     *
     * @param flags the {@link Collection} of {@link net.dv8tion.jda.api.entities.User.UserFlag}
     *        (recommend {@link java.util.EnumSet}
     * @return user readable {@link StringBuilder}
     */
    private static @NotNull StringBuilder userFlagsToStringItem(
            final @NotNull Collection<User.UserFlag> flags) {
        String formattedFlags = formatUserFlags(flags);

        if (formattedFlags.isBlank()) {
            return hypeSquadToStringItem(flags);
        } else {
            return hypeSquadToStringItem(flags).append("\n**Flags:** ")
                .append(formatUserFlags(flags));
        }
    }

    /**
     * Formats user readable Hypesquad item
     *
     * @param flags the {@link Collection} of {@link net.dv8tion.jda.api.entities.User.UserFlag}
     *        (recommend {@link java.util.EnumSet}
     * @return user readable {@link StringBuilder}
     */
    private static @NotNull StringBuilder hypeSquadToStringItem(
            final @NotNull Collection<User.UserFlag> flags) {
        StringBuilder stringBuilder = new StringBuilder("**\nHypesquad:** ");

        if (flags.contains(User.UserFlag.HYPESQUAD_BALANCE)) {
            stringBuilder.append(User.UserFlag.HYPESQUAD_BALANCE.getName());
        } else if (flags.contains(User.UserFlag.HYPESQUAD_BRAVERY)) {
            stringBuilder.append(User.UserFlag.HYPESQUAD_BRAVERY.getName());
        } else if (flags.contains(User.UserFlag.HYPESQUAD_BRILLIANCE)) {
            stringBuilder.append(User.UserFlag.HYPESQUAD_BRILLIANCE.getName());
        } else {
            stringBuilder.append("joined none");
        }

        return stringBuilder;
    }

    /**
     * Formats the flags into a user readable {@link String}, filters Hypesquad relating flags
     *
     * @param flags the {@link Collection} of {@link net.dv8tion.jda.api.entities.User.UserFlag}
     *        (recommend {@link java.util.EnumSet}
     * @return the user readable string
     */
    @NotNull
    private static String formatUserFlags(final @NotNull Collection<User.UserFlag> flags) {
        return flags.stream()
            .map(User.UserFlag::getName)
            .filter(name -> (name.contains("Hypesquad")))
            .collect(Collectors.joining(", "));
    }
}
