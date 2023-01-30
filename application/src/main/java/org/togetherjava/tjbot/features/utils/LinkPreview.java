package org.togetherjava.tjbot.features.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.FileUpload;

import javax.annotation.Nullable;

import java.io.InputStream;

/**
 * Preview of a URL for display as embed in Discord.
 * <p>
 * If the attachment is not {@code null}, it has to be made available to the message as well, for
 * example
 * {@code new MessageCreateBuilder().addEmbeds(linkPreview.embed).addFiles(linkPreview.attachment)}.
 *
 * @param attachment the thumbnail image of the link, referenced within the embed, if present
 * @param embed the embed representing the preview of the link
 */
public record LinkPreview(@Nullable FileUpload attachment, MessageEmbed embed) {
    /**
     * Creates a new instance of this link preview, but with the given thumbnail.
     * <p>
     * Any previous thumbnail is overridden and replaced.
     *
     * @param thumbnailName the name of the thumbnail, with extension, e.g. {@code foo.png}
     * @param thumbnail the thumbnails data as raw data stream
     * @return this preview, but with a thumbnail
     */
    LinkPreview withThumbnail(String thumbnailName, InputStream thumbnail) {
        return createWithThumbnail(embed, thumbnailName, thumbnail);
    }

    /**
     * Creates a link preview that only has a thumbnail and no other text.
     * 
     * @param thumbnailName the name of the thumbnail, with extension, e.g. {@code foo.png}
     * @param thumbnail the thumbnails data as raw data stream
     * @return the thumbnail as link preview
     */
    static LinkPreview ofThumbnail(String thumbnailName, InputStream thumbnail) {
        return createWithThumbnail(null, thumbnailName, thumbnail);
    }

    /**
     * Creates a link preview that consists of the given text.
     * <p>
     * Use {@link #withThumbnail(String, InputStream)} to decorate the preview also with a thumbnail
     * image.
     *
     * @param title the title of the preview, if present
     * @param url the link to the resource this preview represents
     * @param description the description of the preview, if present
     * @return the text as link preview
     */
    static LinkPreview ofText(@Nullable String title, String url, @Nullable String description) {
        MessageEmbed embed =
                new EmbedBuilder().setTitle(title, url).setDescription(description).build();

        return new LinkPreview(null, embed);
    }

    private static LinkPreview createWithThumbnail(@Nullable MessageEmbed embedToDecorate,
            String thumbnailName, InputStream thumbnail) {
        FileUpload attachment = FileUpload.fromData(thumbnail, thumbnailName);
        MessageEmbed embed =
                new EmbedBuilder(embedToDecorate).setThumbnail("attachment://" + thumbnailName)
                    .build();

        return new LinkPreview(attachment, embed);
    }
}
