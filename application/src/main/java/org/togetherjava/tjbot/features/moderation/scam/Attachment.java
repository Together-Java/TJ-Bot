package org.togetherjava.tjbot.features.moderation.scam;

import net.dv8tion.jda.api.entities.Message;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

record Attachment(String fileName) {
    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "tiff", "svg", "apng");

    boolean isImage() {
        return getFileExtension().map(ext -> ext.toLowerCase(Locale.US))
            .map(IMAGE_EXTENSIONS::contains)
            .orElse(false);
    }

    private Optional<String> getFileExtension() {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) {
            return Optional.empty();
        }
        String extension = fileName.substring(dot + 1);
        return Optional.of(extension);
    }

    static Attachment fromDiscord(Message.Attachment attachment) {
        return new Attachment(attachment.getFileName());
    }

    static Attachment fromUrlPath(String urlPath) {
        int fileNameStart = urlPath.lastIndexOf('/');
        String fileName = fileNameStart == -1 ? "" : urlPath.substring(fileNameStart + 1);
        return new Attachment(fileName);
    }
}
