package org.togetherjava.tjbot.features.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.utils.DiscordClientAction;

import javax.annotation.CheckReturnValue;

import java.awt.Color;
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
        super("whois", "Provides info about the given user", CommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "the user to look up", true)
            .addOption(OptionType.BOOLEAN, SHOW_SERVER_INFO_OPTION,
                    "Whenever to show info that is specific to this server, such as their roles. This is true by default.",
                    false);
    }

    @Override
    public void onSlashCommand(final SlashCommandInteractionEvent event) {
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
    private static ReplyCallbackAction handleWhoIsUser(final IReplyCallback event, final User user,
            final User.Profile profile) {
        String description = userIdentificationToStringItem(user) + "\n**Is bot:** " + user.isBot()
                + userFlagsToStringItem(user.getFlags()) + "\n**Registration date:** "
                + DATE_TIME_FORMAT.format(user.getTimeCreated());

        EmbedBuilder embedBuilder = generateEmbedBuilder(user, profile, profile.getAccentColor())
            .setAuthor(user.getName(), user.getEffectiveAvatarUrl(), user.getEffectiveAvatarUrl())
            .setDescription(description);

        return sendEmbedWithProfileAction(event, embedBuilder.build(), user.getId());
    }

    @CheckReturnValue
    private static ReplyCallbackAction handleWhoIsMember(final IReplyCallback event,
            final Member member, final User.Profile profile) {
        User user = member.getUser();

        Color memberColor = member.getColor();
        Color effectiveColor = (null == memberColor) ? profile.getAccentColor() : memberColor;

        String description = userIdentificationToStringItem(user) + voiceStateToStringItem(member)
                + "\n**Is bot:** " + user.isBot() + possibleBoosterToStringItem(member)
                + userFlagsToStringItem(user.getFlags()) + "\n**Join date:** "
                + DATE_TIME_FORMAT.format(member.getTimeJoined()) + "\n**Registration date:** "
                + DATE_TIME_FORMAT.format(user.getTimeCreated()) + "\n**Roles:** "
                + formatRoles(member);

        EmbedBuilder embedBuilder = generateEmbedBuilder(user, profile, effectiveColor)
            .setAuthor(member.getEffectiveName(), member.getEffectiveAvatarUrl(),
                    member.getEffectiveAvatarUrl())
            .setDescription(description);

        return sendEmbedWithProfileAction(event, embedBuilder.build(), user.getId());
    }

    private static ReplyCallbackAction sendEmbedWithProfileAction(final IReplyCallback event,
            MessageEmbed embed, String userId) {
        return event.replyEmbeds(embed)
            .addActionRow(
                    DiscordClientAction.General.USER.asLinkButton("Click to see profile!", userId));
    }

    private static String voiceStateToStringItem(final Member member) {
        GuildVoiceState voiceState = Objects.requireNonNull(member.getVoiceState(),
                "The given voiceState cannot be null");

        if (!voiceState.inAudioChannel()) {
            return "";
        }

        return "\n**In voicechannel:** " + (voiceState.getChannel().getAsMention());
    }

    /**
     * Generates whois embed based on the given parameters.
     *
     * @param user the {@link User} getting whois'd
     * @param profile the {@link net.dv8tion.jda.api.entities.User.Profile} of the whois'd user
     * @param effectiveColor the {@link Color} that the embed will become
     * @return the generated {@link EmbedBuilder}
     */
    private static EmbedBuilder generateEmbedBuilder(final User user, final User.Profile profile,
            final Color effectiveColor) {
        EmbedBuilder embedBuilder = new EmbedBuilder().setThumbnail(user.getEffectiveAvatarUrl())
            .setColor(effectiveColor);

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
    private static String possibleBoosterToStringItem(final Member member) {
        OffsetDateTime timeBoosted = member.getTimeBoosted();

        if (null == timeBoosted) {
            return "\n**Is booster:** false";
        }

        return "\n**Is booster:** true \n**Boosting since:** "
                + DATE_TIME_FORMAT.format(timeBoosted);
    }

    /**
     * Handles the user's identifying properties (such as ID, tag)
     *
     * @param user the {@link User} to take the identifiers from
     * @return user readable {@link String}
     */
    private static String userIdentificationToStringItem(final User user) {
        return "**Mention:** " + user.getAsMention() + "\n**Tag:** " + user.getName() + "\n**ID:** "
                + user.getId();
    }

    /**
     * Formats the roles into a user readable {@link String}
     *
     * @param member member to take the Roles from
     * @return user readable {@link String} of the roles
     */
    private static String formatRoles(final Member member) {
        return member.getRoles().stream().map(Role::getAsMention).collect(Collectors.joining(", "));
    }

    /**
     * Formats Hypesquad and the flags
     *
     * @param flags the {@link Collection} of {@link net.dv8tion.jda.api.entities.User.UserFlag}
     *        (recommend {@link java.util.EnumSet}
     * @return user readable {@link StringBuilder}
     */
    private static StringBuilder userFlagsToStringItem(final Collection<User.UserFlag> flags) {
        String formattedFlags = formatUserFlags(flags);
        StringBuilder result = hypeSquadToStringItem(flags);

        if (!formattedFlags.isBlank()) {
            result.append("\n**Flags:** ").append(formattedFlags);
        }

        return result;
    }

    /**
     * Formats user readable Hypesquad item
     *
     * @param flags the {@link Collection} of {@link net.dv8tion.jda.api.entities.User.UserFlag}
     *        (recommend {@link java.util.EnumSet}
     * @return user readable {@link StringBuilder}
     */
    private static StringBuilder hypeSquadToStringItem(final Collection<User.UserFlag> flags) {
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
    private static String formatUserFlags(final Collection<User.UserFlag> flags) {
        return flags.stream()
            .map(User.UserFlag::getName)
            .filter(name -> (name.contains("Hypesquad")))
            .collect(Collectors.joining(", "));
    }
}
