package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.FileUpload;

import javax.annotation.Nullable;

import java.io.InputStream;

public record LinkPreview(@Nullable FileUpload attachment, MessageEmbed embed) {
    LinkPreview withThumbnail(String thumbnailName, InputStream thumbnail) {
        return withThumbnail(embed, thumbnailName, thumbnail);
    }

    static LinkPreview ofThumbnail(String thumbnailName, InputStream thumbnail) {
        return withThumbnail(null, thumbnailName, thumbnail);
    }

    static LinkPreview ofContents(@Nullable String title, String url,
            @Nullable String description) {
        MessageEmbed embed =
                new EmbedBuilder().setTitle(title, url).setDescription(description).build();

        return new LinkPreview(null, embed);
    }

    private static LinkPreview withThumbnail(@Nullable MessageEmbed embedToDecorate,
            String thumbnailName, InputStream thumbnail) {
        FileUpload attachment = FileUpload.fromData(thumbnail, thumbnailName);
        MessageEmbed embed =
                new EmbedBuilder(embedToDecorate).setThumbnail("attachment://" + thumbnailName)
                    .build();

        return new LinkPreview(attachment, embed);
    }
}
