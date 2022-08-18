package org.togetherjava.tjbot.commands.moderation.meme;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.internal.JDAImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.config.Config;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemeListenerTest {

    private final JDA api = mock(JDA.class);

    private final JDAImpl jda = mock(JDAImpl.class);

    private final Message message = mock(Message.class);
    private final long responseNumber = 1L;
    private final User user = mock(User.class);

    private MemeListener memeListener;


    @BeforeEach
    void setUp() {
        Config config = mock(Config.class);
        when(config.getMemeChannelPattern()).thenReturn("memes");
        memeListener = new MemeListener(config);
    }


    @Test
    void validMemePostWithAttachment() {
        Message.Attachment attachment = new Message.Attachment(1L, null, null, "TEST",
                "Test ContentType", null, 1, 1, 1, false, jda);
        List<Message.Attachment> attachments = Collections.singletonList(attachment);

        when(message.getAuthor()).thenReturn(user);
        when(message.getAttachments()).thenReturn(attachments);
        when(user.isBot()).thenReturn(false);

        MessageReceivedEvent messageReceivedEvent = new MessageReceivedEvent(api, responseNumber, message);
        memeListener.onMessageReceived(messageReceivedEvent);
    }

    @Test
    void validMemePostWithUrlEmbedded() {

        MessageEmbed messageEmbed = new MessageEmbed("https://9gag.com/gag/a61A238", "Test", "Test",
                EmbedType.LINK, null, 1, null, null, null, null,
                null, null, null);
        List<MessageEmbed> messageEmbeds = Collections.singletonList(messageEmbed);

        when(message.getAuthor()).thenReturn(user);
        when(message.getEmbeds()).thenReturn(messageEmbeds);
        when(user.isBot()).thenReturn(false);

        MessageReceivedEvent messageReceivedEvent = new MessageReceivedEvent(api, responseNumber, message);
        memeListener.onMessageReceived(messageReceivedEvent);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void unvalidMemePostWithOnlyText() {

        AuditableRestAction<Void> auditableRestAction = mock(AuditableRestAction.class);
        RestAction<PrivateChannel> restActionPrivateChannel = mock(RestAction.class);
        RestAction restActionMessage = mock(RestAction.class);

        when(message.getAuthor()).thenReturn(user);
        when(message.getAttachments()).thenReturn(Collections.emptyList());
        when(message.delete()).thenReturn(auditableRestAction);
        when(user.isBot()).thenReturn(false);
        when(api.openPrivateChannelById(0)).thenReturn(restActionPrivateChannel);
        when(restActionPrivateChannel.flatMap(any())).thenReturn(restActionMessage);

        MessageReceivedEvent messageReceivedEvent = new MessageReceivedEvent(api, responseNumber, message);
        memeListener.onMessageReceived(messageReceivedEvent);
    }


}