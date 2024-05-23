package org.togetherjava.tjbot.features.mediaonly;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.jda.JdaTester;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        MessageCreateData message = new MessageCreateBuilder().setContent("any").build();

        // WHEN sending the message
        MessageReceivedEvent event = sendMessage(message);

        // THEN it gets deleted
        verify(event.getMessage()).delete();
    }

    @Test
    void keepsMessageWithEmbed() {
        // GIVEN a message with an embed
        MessageEmbed embed = new EmbedBuilder().setDescription("any").build();
        MessageCreateData message =
                new MessageCreateBuilder().setContent("any").setEmbeds(embed).build();

        // WHEN sending the message
        MessageReceivedEvent event = sendMessage(message);

        // THEN it does not get deleted
        verify(event.getMessage(), never()).delete();
    }

    @Test
    void keepsMessageWithAttachment() {
        // GIVEN a message with an attachment
        MessageCreateData message = new MessageCreateBuilder().setContent("any").build();
        List<Message.Attachment> attachments = List.of(mock(Message.Attachment.class));

        // WHEN sending the message
        MessageReceivedEvent event = sendMessage(message, attachments);

        // THEN it does not get deleted
        verify(event.getMessage(), never()).delete();
    }

    @Test
    void keepsMessageWithLinkedMedia() {
        // GIVEN a message with media linked in the message
        MessageCreateData message = new MessageCreateBuilder()
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
        MessageCreateData message = new MessageCreateBuilder().setContent("any").build();

        // WHEN sending the message
        sendMessage(message);

        // THEN the author receives a DM
        verify(jdaTester.getPrivateChannelSpy()).sendMessage(any(MessageCreateData.class));
    }

    private MessageReceivedEvent sendMessage(MessageCreateData message) {
        return sendMessage(message, List.of());
    }

    private MessageReceivedEvent sendMessage(MessageCreateData message,
            List<Message.Attachment> attachments) {
        MessageReceivedEvent event =
                jdaTester.createMessageReceiveEvent(message, attachments, ChannelType.TEXT);
        mediaOnlyChannelListener.onMessageReceived(event);
        return event;
    }
}
