package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.entities.MessageEmbed;

interface CodeAction {
    String getLabel();

    MessageEmbed apply(String code);
}
