package org.togetherjava.tjbot.features.utils;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.jetbrains.annotations.Contract;

import java.util.regex.Pattern;

/**
 * Class, which contains all actions a Discord client accepts.
 * <p>
 * This allows you to open DM's {@link Channels#DM_CHANNEL}, specific settings
 * {@link Settings.App#VOICE} and much more.
 *
 * <p>
 * A few notes;
 * <ul>
 * <li>iOS and Android are NOT supported</li>
 * <li>It opens the LAST installed Discord version (Discord, Canary, PTB)</li>
 * </ul>
 *
 * <p>
 * Example:
 *
 * <pre>
 * {@code
 * event.reply("Open Discord's secret home page!")
 *      .addActionRow(DiscordClientAction.Guild.GUILD_HOME_CHANNEL.asLinkButton("Open home page!", event.getGuild().getId())
 * }
 * </pre>
 *
 * To improve readability, one might want to use a static import like:
 *
 * <pre>
 * {@code
 * event.reply(whoIsCommandOutput)
 *      .addActionRow(USER.asLinkButton("Open home page!", target.getId())
 * }
 * </pre>
 */
public final class DiscordClientAction {

    /**
     * Contains some of the more general actions.
     */
    public enum General {
        ;

        public static final DiscordClientAction HOME = new DiscordClientAction("discord://-/");
        public static final DiscordClientAction FRIENDS = new DiscordClientAction("discord://-/");

        public static final DiscordClientAction USER =
                new DiscordClientAction("discord://-/users/{USER-ID}");
        public static final DiscordClientAction JOIN_INVITE =
                new DiscordClientAction("discord://-/invite/{INVITE-CODE}");
        public static final DiscordClientAction HUB_MEMBERSHIP_SCREENING =
                new DiscordClientAction("discord://-/member-verification-for-hub/{HUB-ID}");
        public static final DiscordClientAction STORE =
                new DiscordClientAction("discord://-/store");

        public static final DiscordClientAction HYPESQUAD =
                new DiscordClientAction("discord://-/settings/hypesquad_online");
        public static final DiscordClientAction CHANGELOGS =
                new DiscordClientAction("discord://-/settings/changelogs");
    }

    /**
     * Contains actions related to guilds.
     */
    public enum Guild {
        ;

        @SuppressWarnings("squid:S1700")
        public static final DiscordClientAction GUILD =
                new DiscordClientAction("discord://-/channels/{GUILD-ID}");
        public static final DiscordClientAction GUILD_CHANNEL =
                new DiscordClientAction("discord://-/channels/{GUILD-ID}/{CHANNEL-ID}");

        public static final DiscordClientAction GUILD_DISCOVERY =
                new DiscordClientAction("discord://-/guild-discovery");
        public static final DiscordClientAction GUILDS_CREATE =
                new DiscordClientAction("discord://-/guilds/create");

        public static final DiscordClientAction GUILD_EVENT =
                new DiscordClientAction("discord://-/events/{GUILD-ID}/{EVENT-ID}");
        public static final DiscordClientAction GUILD_MEMBERSHIP_SCREENING =
                new DiscordClientAction("discord://-/member-verification/{GUILD-ID}");

        /**
         * Beta Discord feature
         */
        public static final DiscordClientAction GUILD_HOME_CHANNEL =
                new DiscordClientAction("discord://-/channels/{GUILD-ID}/@home");
    }

    /**
     * Contains actions related to channels.
     */
    public enum Channels {
        ;

        public static final DiscordClientAction DM_CHANNEL =
                new DiscordClientAction("discord://-/channels/@me/{CHANNEL-ID}");
        public static final DiscordClientAction DM_CHANNEL_MESSAGE =
                new DiscordClientAction("discord://-/channels/@me/{CHANNEL-ID}/{MESSAGE-ID}");
        public static final DiscordClientAction GUILD_CHANNEL =
                new DiscordClientAction("discord://-/channels/{GUILD-ID}/{CHANNEL-ID}");
        public static final DiscordClientAction GUILD_CHANNEL_MESSAGE = new DiscordClientAction(
                "discord://-/channels/{GUILD-ID}/{CHANNEL-ID}/{MESSAGE-ID}");
    }

    /**
     * Contains actions related to the settings menu.
     */
    /*
     * The warning is about this inner class being too long, and that it should be external This
     * won't become an external class since it makes no sense for the design, and it requires the
     * developer to remember all classes.
     */
    @SuppressWarnings("squid:S2972")
    public enum Settings {
        ;

        /**
         * Contains all user settings.
         */
        public enum User {
            ;

            public static final DiscordClientAction ACCOUNT =
                    new DiscordClientAction("discord://-/settings/account");
            public static final DiscordClientAction PROFILE_CUSTOMIZATION =
                    new DiscordClientAction("discord://-/settings/profile-customization");
            public static final DiscordClientAction PRIVACY_AND_SAFETY =
                    new DiscordClientAction("discord://-/settings/privacy-and-safety");
            public static final DiscordClientAction AUTHORIZED_APPS =
                    new DiscordClientAction("discord://-/settings/authorized-apps");
            public static final DiscordClientAction CONNECTIONS =
                    new DiscordClientAction("discord://-/settings/connections");
        }

        /**
         * Contains all payment settings.
         */
        public enum Payment {
            ;

            public static final DiscordClientAction PREMIUM =
                    new DiscordClientAction("discord://-/settings/premium");
            public static final DiscordClientAction SUBSCRIPTIONS =
                    new DiscordClientAction("discord://-/settings/subscriptions");
            public static final DiscordClientAction INVENTORY =
                    new DiscordClientAction("discord://-/settings/inventory");
            public static final DiscordClientAction BILLING =
                    new DiscordClientAction("discord://-/settings/billing");
        }

        /**
         * Contains all app settings.
         */
        public enum App {
            ;

            public static final DiscordClientAction APPEARANCE =
                    new DiscordClientAction("discord://-/settings/appearance");
            public static final DiscordClientAction ACCESSIBILITY =
                    new DiscordClientAction("discord://-/settings/accessibility");
            public static final DiscordClientAction VOICE =
                    new DiscordClientAction("discord://-/settings/voice");
            public static final DiscordClientAction TEXT =
                    new DiscordClientAction("discord://-/settings/text");
            public static final DiscordClientAction NOTIFICATIONS =
                    new DiscordClientAction("discord://-/settings/notifications");
            public static final DiscordClientAction KEYBINDS =
                    new DiscordClientAction("discord://-/settings/keybinds");
            public static final DiscordClientAction LOCALE =
                    new DiscordClientAction("discord://-/settings/locale");

            /**
             * @see #LINUX
             */
            public static final DiscordClientAction WINDOWS =
                    new DiscordClientAction("discord://-/settings/windows");

            /**
             * @see #WINDOWS
             */
            public static final DiscordClientAction LINUX =
                    new DiscordClientAction("discord://-/settings/linux");

            public static final DiscordClientAction STREAMER_MODE =
                    new DiscordClientAction("discord://-/settings/streamer-mode");
            public static final DiscordClientAction ADVANCED =
                    new DiscordClientAction("discord://-/settings/advanced");
        }

        /**
         * Contains some of the more general settings.
         */
        public enum General {
            ;

            public static final DiscordClientAction ACTIVITY_STATUS =
                    new DiscordClientAction("discord://-/settings/activity-status");
            public static final DiscordClientAction ACTIVITY_OVERLAY =
                    new DiscordClientAction("discord://-/settings/overlay");
            public static final DiscordClientAction HYPESQUAD =
                    new DiscordClientAction("discord://-/settings/hypesquad_online");
            public static final DiscordClientAction CHANGELOGS =
                    new DiscordClientAction("discord://-/settings/changelogs");
        }
    }

    /**
     * Contains actions related to game libraries.
     */
    public enum Library {
        ;

        public static final DiscordClientAction LIBRARY_GAMES =
                new DiscordClientAction("discord://-/library");
        public static final DiscordClientAction LIBRARY_SETTINGS =
                new DiscordClientAction("discord://-/library/settings");
        public static final DiscordClientAction LIBRARY_ITEM_ACTION =
                new DiscordClientAction("discord://-/library/{SKU-ID}/LAUNCH");
        public static final DiscordClientAction SKU_STORE_PAGE =
                new DiscordClientAction("discord://-/store/skus/{SKU-ID}");
        public static final DiscordClientAction APPLICATION_STORE_PAGE =
                new DiscordClientAction("discord://-/store/applications/{APPLICATION-ID}");
    }

    /**
     * Pattern for the arguments, finds everything within brackets.
     */
    public static final Pattern argumentPattern = Pattern.compile("\\{[^}]*}");

    private final String rawUrl;

    @Contract(pure = true)
    private DiscordClientAction(final String url) {
        rawUrl = url;
    }

    /**
     * The raw URL without any arguments.
     *
     * <p>
     * Most likely you should use {@link #formatUrl(String...)} instead, that one throws when an
     * argument is lacking.
     *
     * @return A {@link String} of the URL
     * @see #formatUrl(String...)
     */
    public String getRawUrl() {
        return rawUrl;
    }

    /**
     * Format's the URL with the given arguments.
     *
     * @param arguments An array of the arguments this action requires
     * @return The formatted URL as an {@link String}
     * @throws IllegalArgumentException When missing arguments
     */
    public String formatUrl(final String... arguments) {
        String localUrl = rawUrl;

        for (final String argument : arguments) {
            localUrl = argumentPattern.matcher(localUrl).replaceFirst(argument);
        }

        if (argumentPattern.matcher(localUrl).find()) {
            throw new IllegalArgumentException("Missing arguments for URL " + localUrl + "!");
        }

        return localUrl;
    }

    /**
     * Format's the action as a link button.
     *
     * @param label The label of the button, see {@link Button#link(String, String)} for the
     *        requirements
     * @param arguments An array of the arguments this action requires
     * @return A {@link Button} of {@link ButtonStyle#LINK} with the given label
     * @throws IllegalArgumentException When missing arguments
     */
    public Button asLinkButton(final String label, final String... arguments) {
        return Button.link(formatUrl(arguments), label);
    }

    @Override
    public String toString() {
        return "DiscordClientAction{" + "url='" + rawUrl + '\'' + '}';
    }
}
