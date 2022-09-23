package org.togetherjava.tjbot.logging.discord.api;

import java.util.List;

/**
 * The Jackson representation of Discords Webhook messages: <a href=
 * "https://discord.com/developers/docs/resources/webhook#execute-webhook-jsonform-params">API
 * Documentation</a>
 */
public record DiscordLogBatch(List<DiscordLogMessageEmbed> embeds) {
}
