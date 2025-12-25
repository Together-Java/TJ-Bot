package org.togetherjava.tjbot.features.moderation.scam;

import net.dv8tion.jda.api.entities.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class AttachmentTest {
    @ParameterizedTest
    @ValueSource(strings = {"foo.jpg", "a.png", ".jpeg", "image.gif"})
    @DisplayName("Can detect attachments that represent images")
    void detectsImage(String fileName) {
        // GIVEN an attachment representing an image
        Attachment imageAttachment = new Attachment(fileName);

        // WHEN checking if image
        boolean isImage = imageAttachment.isImage();

        // THEN detects it as image
        assertTrue(isImage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "foo.pdf", "foo/bar", "", "jpg"})
    @DisplayName("Can detect attachments that do not represent images")
    void detectsNonImage(String fileName) {
        // GIVEN an attachment not representing an image
        Attachment nonImageAttachment = new Attachment(fileName);

        // WHEN checking if image
        boolean isImage = nonImageAttachment.isImage();

        // THEN detects that it is not an image
        assertFalse(isImage);
    }

    @ParameterizedTest
    @MethodSource("provideUrlPathTests")
    @DisplayName("Can extract attachment from a URL path")
    void extractsFromUrlPath(String urlPath, String expectedFileName, boolean expectedIsImage) {
        // GIVEN a URL path
        // WHEN extracting the attachment
        Attachment attachment = Attachment.fromUrlPath(urlPath);

        // THEN values are extracted correctly
        assertEquals(expectedFileName, attachment.fileName());
        assertEquals(expectedIsImage, attachment.isImage());
    }

    private static Stream<Arguments> provideUrlPathTests() {
        return Stream.of(Arguments.of("http://foo.com/bar/baz.png", "baz.png", true),
                Arguments.of("http://foo.com/bar/baz.exe", "baz.exe", false),
                Arguments.of("foo/bar", "bar", false), Arguments.of("foo", "", false));
    }

    @ParameterizedTest
    @MethodSource("provideDiscordTests")
    @DisplayName("Can extract attachment from a Discord attachment")
    void extractsFromDiscord(String expectedFileName, boolean expectedIsImage) {
        // GIVEN a Discord attachment
        Message.Attachment discordAttachment = mock(Message.Attachment.class);
        when(discordAttachment.getFileName()).thenReturn(expectedFileName);

        // WHEN extracting the attachment
        Attachment attachment = Attachment.fromDiscord(discordAttachment);

        // THEN values are extracted correctly
        assertEquals(expectedFileName, attachment.fileName());
        assertEquals(expectedIsImage, attachment.isImage());
    }

    private static Stream<Arguments> provideDiscordTests() {
        return Stream.of(Arguments.of("foo.png", true), Arguments.of("foo/bar.jpg", true),
                Arguments.of("foo.exe", false), Arguments.of("foo", false),
                Arguments.of("", false));
    }
}
