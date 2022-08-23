package org.togetherjava.tjbot.commands.mediaonly;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.internal.JDAImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.jda.JdaTester;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaOnlyChannelListenerTest {

    private JDA jda;
    private final Message message = mock(Message.class);
    private final long responseNumber = 1L;
    private final User user = mock(User.class);
    private MediaOnlyChannelListener mediaOnlyChannelListener;

    @BeforeEach
    void setUp() {
        JdaTester jdaTester = new JdaTester();
        jda = jdaTester.getJdaMock();

        Config config = mock(Config.class);
        when(config.getMediaOnlyChannelPattern()).thenReturn("memes");
        mediaOnlyChannelListener = new MediaOnlyChannelListener(config);
    }

    @Test
    void validMessagePostWithAttachment() {
        Message.Attachment attachment = new Message.Attachment(1L, null, null, "TEST",
                "Test ContentType", null, 1, 1, 1, false, (JDAImpl) jda);
        List<Message.Attachment> attachments = Collections.singletonList(attachment);

        when(message.getAuthor()).thenReturn(user);
        when(message.getAttachments()).thenReturn(attachments);

        MessageReceivedEvent messageReceivedEvent =
                new MessageReceivedEvent(jda, responseNumber, message);
        mediaOnlyChannelListener.onMessageReceived(messageReceivedEvent);
        verify(message).getAttachments();
    }

    @Test
    void validMessagePostWithUrlEmbedded() {
        MessageEmbed messageEmbed = new MessageEmbed("https://9gag.com/gag/a61A238", "Test", "Test",
                EmbedType.LINK, null, 1, null, null, null, null, null, null, null);
        List<MessageEmbed> messageEmbeds = Collections.singletonList(messageEmbed);

        when(message.getAuthor()).thenReturn(user);
        when(message.getEmbeds()).thenReturn(messageEmbeds);

        MessageReceivedEvent messageReceivedEvent =
                new MessageReceivedEvent(jda, responseNumber, message);
        mediaOnlyChannelListener.onMessageReceived(messageReceivedEvent);
        verify(message).getEmbeds();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void unvalidMessagePostWithOnlyText() {
        AuditableRestAction<Void> auditableRestAction = mock(AuditableRestAction.class);
        RestAction restActionMessage = mock(RestAction.class);

        when(message.getAuthor()).thenReturn(user);
        when(message.getAttachments()).thenReturn(Collections.emptyList());
        when(message.delete()).thenReturn(auditableRestAction);
        when(auditableRestAction.flatMap(any())).thenReturn(restActionMessage);

        MessageReceivedEvent messageReceivedEvent =
                new MessageReceivedEvent(jda, responseNumber, message);
        mediaOnlyChannelListener.onMessageReceived(messageReceivedEvent);
        verify(message).getAttachments();
    }
}
