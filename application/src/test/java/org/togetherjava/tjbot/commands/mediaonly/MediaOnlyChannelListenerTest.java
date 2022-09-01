package org.togetherjava.tjbot.commands.mediaonly;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.jda.JdaTester;

import java.util.List;

import static org.mockito.Mockito.*;

final class MediaOnlyChannelListenerTest {

    private JdaTester jdaTester;
    private MediaOnlyChannelListener mediaOnlyChannelListener;

    @BeforeEach
    void setUp() {
        jdaTester = new JdaTester();

        Config config = mock(Config.class);
        when(config.getMediaOnlyChannelPattern()).thenReturn("any");

        mediaOnlyChannelListener = new MediaOnlyChannelListener(config);
    }

    @Test
    void deletesMessageWithoutMedia() {
        // GIVEN a message without media
        Message message = new MessageBuilder().setContent("any").build();

        // WHEN sending the message
        MessageReceivedEvent event = sendMessage(message);

        // THEN it gets deleted
        verify(event.getMessage()).delete();
    }

    @Test
    void keepsMessageWithEmbed() {
        // GIVEN a message with an embed
        MessageEmbed embed = new EmbedBuilder().setDescription("any").build();
        Message message = new MessageBuilder().setContent("any").setEmbeds(embed).build();

        // WHEN sending the message
        MessageReceivedEvent event = sendMessage(message);

        // THEN it does not get deleted
        verify(event.getMessage(), never()).delete();
    }

    @Test
    void keepsMessageWithAttachment() {
        // GIVEN a message with an attachment
        Message message = new MessageBuilder().setContent("any").build();
        List<Message.Attachment> attachments = List.of(mock(Message.Attachment.class));

        // WHEN sending the message
        MessageReceivedEvent event = sendMessage(message, attachments);

        // THEN it does not get deleted
        verify(event.getMessage(), never()).delete();
    }

    @Test
    void keepsMessageWithLinkedMedia() {
        // GIVEN a message with media linked in the message
        Message message = new MessageBuilder()
            .setContent("Check out this cute cat https://i.imgur.com/HLFByUJ.png")
            .build();

        // WHEN sending the message
        MessageReceivedEvent event = sendMessage(message);

        // THEN it does not get deleted
        verify(event.getMessage(), never()).delete();
    }

    @Test
    void sendsAuthorDmUponDeletion() {
        // GIVEN a message without media
        Message message = new MessageBuilder().setContent("any").build();

        // WHEN sending the message
        MessageReceivedEvent event = sendMessage(message);

        // THEN the author receives a DM
        verify(jdaTester.getPrivateChannelSpy()).sendMessage(any(Message.class));
    }

    private MessageReceivedEvent sendMessage(Message message) {
        return sendMessage(message, List.of());
    }

    private MessageReceivedEvent sendMessage(Message message,
            List<Message.Attachment> attachments) {
        MessageReceivedEvent event = jdaTester.createMessageReceiveEvent(message, attachments);
        mediaOnlyChannelListener.onMessageReceived(event);
        return event;
    }
}
