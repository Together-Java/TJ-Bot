package org.togetherjava.tjbot.commands.mediaonly;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.jda.JdaTester;

import java.util.Collections;

import static org.mockito.Mockito.*;

class MediaOnlyChannelListenerTest {

    JdaTester jdaTester;
    private Message message;
    private User user;
    private MediaOnlyChannelListener mediaOnlyChannelListener;

    @BeforeEach
    void setUp() {
        jdaTester = new JdaTester();
        message = jdaTester.createMessageSpy();
        Member memberSpy = jdaTester.createMemberSpy(1L);
        user = memberSpy.getUser();
        Config config = mock(Config.class);
        when(config.getMediaOnlyChannelPattern()).thenReturn("memes");
        mediaOnlyChannelListener = new MediaOnlyChannelListener(config);
    }

    @Test
    void validMessagePostWithAttachment() {
        Message.Attachment attachment = jdaTester.createAttachment(1L, null, null, "TEST",
                "Test ContentType", null, 1, 1, 1, false);
        jdaTester.mockMessageAttachments(message, Collections.singletonList(attachment));
        MessageReceivedEvent messageReceivedEvent =
                jdaTester.createMessageReceivedEvent(message, user);
        mediaOnlyChannelListener.onMessageReceived(messageReceivedEvent);
        verify(message).getAttachments();
    }

    @Test
    void validMessagePostWithUrlEmbedded() {
        MessageEmbed messageEmbed = jdaTester.createMessageEmbed("https://9gag.com/gag/a61A238",
                "Test", "Test", EmbedType.LINK, null, 1, null, null, null, null, null, null, null);
        jdaTester.mockMessageEmbeds(message, Collections.singletonList(messageEmbed));
        MessageReceivedEvent messageReceivedEvent =
                jdaTester.createMessageReceivedEvent(message, user);
        mediaOnlyChannelListener.onMessageReceived(messageReceivedEvent);
        verify(message).getEmbeds();
    }

    @Test
    void unvalidMessagePostWithOnlyText() {
        jdaTester.mockMessageDelete(message);
        jdaTester.mockMessageAttachments(message, Collections.emptyList());
        MessageReceivedEvent messageReceivedEvent =
                jdaTester.createMessageReceivedEvent(message, user);
        mediaOnlyChannelListener.onMessageReceived(messageReceivedEvent);
        verify(message).getAttachments();
    }
}
