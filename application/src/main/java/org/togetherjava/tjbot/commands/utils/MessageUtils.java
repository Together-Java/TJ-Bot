package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;

public class MessageUtils {

    public static void disableButtons(Message message) {
        message
                .editMessageComponents(
                        ActionRow.of(message.getButtons().stream().map(Button::asDisabled).toList()))
                .queue();
    }

}
