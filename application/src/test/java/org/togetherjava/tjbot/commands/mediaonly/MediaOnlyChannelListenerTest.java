package org.togetherjava.tjbot.commands.mediaonly;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.jda.JdaTester;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

class MediaOnlyChannelListenerTest {

    private JdaTester jdaTester;
    private Message message;
    private User user;
    private MediaOnlyChannelListener mediaOnlyChannelListener;

    @BeforeEach
    void setUp() {
        jdaTester = new JdaTester();
        message = jdaTester.createMessageSpy();
        user = jdaTester.getMemberSpy().getUser();
        Config config = mock(Config.class);
        when(config.getMediaOnlyChannelPattern()).thenReturn("memes");
        mediaOnlyChannelListener = new MediaOnlyChannelListener(config);
    }

    @Test
    void validMessagePostWithAttachment() {
        Message.Attachment attachment = jdaTester.createAttachment("https://test.com", "test",
                "testContentType", "unitTest");
        jdaTester.mockMessageAttachments(message, List.of(attachment));
        sendMessage();
        verify(message).getAttachments();
    }

    @Test
    void validMessagePostWithUrlEmbedded() {
        MessageEmbed messageEmbed =
                new EmbedBuilder().setImage("https://9gag.com/gag/a61A238").build();
        jdaTester.mockMessageEmbeds(message, List.of(messageEmbed));
        sendMessage();
        verify(message).getEmbeds();
    }

    @Test
    void unvalidMessagePostWithOnlyText() {
        jdaTester.mockMessageDelete(message);
        jdaTester.mockMessageAttachments(message, Collections.emptyList());
        sendMessage();
        verify(message).getAttachments();
    }

    private void sendMessage() {
        MessageReceivedEvent messageReceivedEvent =
                jdaTester.createMessageReceivedEvent(message, user);
        mediaOnlyChannelListener.onMessageReceived(messageReceivedEvent);
    }
}
