package org.togetherjava.tjbot;

import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.utils.ConcurrentSessionController;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.EntityBuilder;
import net.dv8tion.jda.internal.entities.PrivateChannelImpl;
import net.dv8tion.jda.internal.entities.SelfUserImpl;
import net.dv8tion.jda.internal.entities.UserImpl;
import net.dv8tion.jda.internal.interactions.CommandInteractionImpl;
import net.dv8tion.jda.internal.interactions.InteractionHookImpl;
import net.dv8tion.jda.internal.requests.Requester;
import net.dv8tion.jda.internal.requests.restaction.MessageActionImpl;
import net.dv8tion.jda.internal.requests.restaction.interactions.ReplyActionImpl;
import net.dv8tion.jda.internal.utils.config.AuthorizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class mocks a valid JDA instance with a user, an api and a message channel mock
 * implementations
 */
public abstract class AbstractJdaMock {

    private final long userId;
    private final long applicationId;
    protected static final Logger logger = LoggerFactory.getLogger(AbstractJdaMock.class);
    /**
     * is a mock on which we can stub out methods and simulate a real JDA instance that can send
     * messages to the bot
     */
    protected JDAImpl jdaImplMock;
    /**
     * is spied upon to validate actions performed by the bot
     */
    protected PrivateChannelImpl privateChannel;

    public AbstractJdaMock(long userId, long applicationId) {
        this.userId = userId;
        this.applicationId = applicationId;
    }

    // TODO - Improve these mocks. Some of them could actually be the real concrete class
    @BeforeEach
    protected void setup() {
        EntityBuilder entityBuilderMock = mock(EntityBuilder.class);
        jdaImplMock = mock(JDAImpl.class);
        SelfUser selfUser = mock(SelfUserImpl.class);
        UserImpl user = Mockito.spy(new UserImpl(userId, jdaImplMock));
        privateChannel = Mockito.spy(new PrivateChannelImpl(userId, user));
        MessageAction mockMessageAction = Mockito.mock(MessageActionImpl.class);

        when(entityBuilderMock.createUser(any())).thenReturn(user);
        when(selfUser.getApplicationId()).thenReturn(String.valueOf(applicationId));
        when(selfUser.getApplicationIdLong()).thenReturn(applicationId);
        doReturn(selfUser).when(jdaImplMock).getSelfUser();
        when(jdaImplMock.getPrivateChannelById(anyLong())).thenReturn(privateChannel);
        when(jdaImplMock.getGatewayPool()).thenReturn(new ScheduledThreadPoolExecutor(4));
        when(jdaImplMock.getEntityBuilder()).thenReturn(entityBuilderMock);
        when(jdaImplMock.getSessionController()).thenReturn(new ConcurrentSessionController());
        doReturn(new Requester(jdaImplMock, new AuthorizationConfig("TOKEN"))).when(jdaImplMock)
            .getRequester();
        doReturn(mockMessageAction).when(privateChannel).sendMessage(anyString());

    }

    protected SlashCommandEvent createSlashCommand(@Nonnull String command) {
        DataObject dataObject = DataObject.fromJson("""
                {
                  "id": 2,
                  "token": "asd",
                  "channel_id": 2,
                  "user": {
                    "id": 2
                  },
                  "type": 2,
                  "data": {
                    "id": 2,
                    "name": "%s"
                  }
                }""".formatted(command));

        SlashCommandEvent slashCommandEvent = new SlashCommandEvent(jdaImplMock, 0,
                new CommandInteractionImpl(jdaImplMock, dataObject));

        slashCommandEvent = Mockito.spy(slashCommandEvent);

        ReplyActionImpl replyActionImplMock = mock(ReplyActionImpl.class);

        doReturn(replyActionImplMock).when(slashCommandEvent).reply(anyString());

        return slashCommandEvent;
    }
}
