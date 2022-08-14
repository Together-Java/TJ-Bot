/**
 * This package contains the command system and most commands of the bot. Commands can also be
 * created in different modules, if desired.
 * <p>
 * Commands are registered in {@link org.togetherjava.tjbot.feature.Features} and then picked up by
 * the {@link org.togetherjava.tjbot.feature.system.BotCore}.
 * <p>
 * Custom slash commands can be created by implementing
 * {@link org.togetherjava.tjbot.feature.SlashCommand} or using the adapter
 * {@link org.togetherjava.tjbot.feature.SlashCommandAdapter} for convenience.
 */
package org.togetherjava.tjbot.feature;
