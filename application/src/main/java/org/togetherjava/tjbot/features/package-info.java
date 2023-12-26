/**
 * This package contains the command system and most commands of the bot. Commands can also be
 * created in different modules, if desired.
 * <p>
 * Commands are registered in {@link org.togetherjava.tjbot.features.Features} and then picked up by
 * the {@link org.togetherjava.tjbot.features.system.BotCore}.
 * <p>
 * Custom slash commands can be created by implementing
 * {@link org.togetherjava.tjbot.features.SlashCommand} or using the adapter
 * {@link org.togetherjava.tjbot.features.SlashCommandAdapter} for convenience.
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package org.togetherjava.tjbot.features;

import org.togetherjava.tjbot.annotations.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
