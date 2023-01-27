package org.togetherjava.tjbot.features.code;

import net.dv8tion.jda.api.entities.MessageEmbed;

import org.togetherjava.tjbot.features.utils.CodeFence;

/**
 * Actions that can be executed on code, such as running it.
 */
interface CodeAction {
    /**
     * The name of the action, displayed to the user for applying it.
     *
     * @return the label of the action
     */
    String getLabel();

    /**
     * Applies the action to the given code and returns a message.
     *
     * @param codeFence the code to apply the action to
     * @return the message to send to the user
     */
    MessageEmbed apply(CodeFence codeFence);
}
