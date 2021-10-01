/**
 * This package contains the command system and most commands of the bot. Commands can also be
 * created in different modules, if desired.
 * <p>
 * Commands are registered in {@link org.togetherjava.tjbot.commands.CommandRegistry} and then
 * picked up by the {@link org.togetherjava.tjbot.commands.system.CommandSystem}.
 * <p>
 * Custom slash commands can be created by implementing
 * {@link org.togetherjava.tjbot.commands.SlashCommand} or using the adapter
 * {@link org.togetherjava.tjbot.commands.SlashCommandAdapter} for convenience.
 */
package org.togetherjava.tjbot.commands;
