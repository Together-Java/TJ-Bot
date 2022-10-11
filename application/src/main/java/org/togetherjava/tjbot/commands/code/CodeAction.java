package org.togetherjava.tjbot.commands.code;

import net.dv8tion.jda.api.entities.MessageEmbed;

interface CodeAction {
    MessageEmbed apply(String code);
}
