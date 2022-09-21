package org.togetherjava.tjbot.logging.discord;

/**
 * The Jackson representation of Discords Webhook embeds: <a href=
 * "https://discord.com/developers/docs/resources/webhook#execute-webhook-jsonform-params">API
 * Documentation</a>
 */
record DiscordLogMessageEmbed(DiscordLogMessageEmbedAuthor author, String title, String description,
        int color, String timestamp) {
}
