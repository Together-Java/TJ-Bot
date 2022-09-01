/**
 * This package provides utilities to generate, persist and parse component IDs.
 * <p>
 * See
 * {@link org.togetherjava.tjbot.commands.SlashCommand#onSlashCommand(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent)}
 * for details on component IDs.
 * <p>
 * The class {@link org.togetherjava.tjbot.commands.componentids.ComponentIdStore} is the central
 * point of this package and is generally exposed as
 * {@link org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator} and
 * {@link org.togetherjava.tjbot.commands.componentids.ComponentIdParser}.
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
package org.togetherjava.tjbot.commands.componentids;

import org.togetherjava.tjbot.annotations.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
