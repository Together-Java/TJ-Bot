package org.togetherjava.tjbot.logging.discord;

/**
 * The Jackson representation of Discords Webhook embeds author: <a href=
 * "https://discord.com/developers/docs/resources/webhook#execute-webhook-jsonform-params">API
 * Documentation</a>
 */
record DiscordLogMessageEmbedAuthor(String name) {
}
