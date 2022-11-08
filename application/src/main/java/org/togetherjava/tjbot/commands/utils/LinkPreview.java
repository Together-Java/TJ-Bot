package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.FileUpload;

import javax.annotation.Nullable;

import java.io.InputStream;

public record LinkPreview(FileUpload attachment, MessageEmbed embed) {
    static LinkPreview ofContents(@Nullable String title, String url, @Nullable String description,
            String thumbnailName, InputStream thumbnail) {
        FileUpload attachment = FileUpload.fromData(thumbnail, thumbnailName);
        MessageEmbed embed = new EmbedBuilder().setTitle(title, url)
            .setDescription(description)
            .setThumbnail("attachment://" + thumbnailName)
            .build();

        return new LinkPreview(attachment, embed);
    }

    static LinkPreview ofThumbnail(String thumbnailName, InputStream thumbnail) {
        FileUpload attachment = FileUpload.fromData(thumbnail, thumbnailName);
        MessageEmbed embed =
                new EmbedBuilder().setThumbnail("attachment://" + thumbnailName).build();

        return new LinkPreview(attachment, embed);
    }
}
