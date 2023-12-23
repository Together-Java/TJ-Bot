package org.togetherjava.tjbot.features.jshell.renderer;

import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;

// TODO delete ?
public sealed

interface RenderResult {
    record EmbedResult(List<MessageEmbed> embeds) implements RenderResult {}
    record FileResult(String content) implements RenderResult {}
}
