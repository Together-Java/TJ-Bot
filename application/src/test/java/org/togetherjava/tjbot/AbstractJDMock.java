package org.togetherjava.tjbot;

import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.EntityBuilder;
import net.dv8tion.jda.internal.entities.PrivateChannelImpl;
import net.dv8tion.jda.internal.entities.SelfUserImpl;
import net.dv8tion.jda.internal.entities.UserImpl;
import net.dv8tion.jda.internal.interactions.CommandInteractionImpl;
import net.dv8tion.jda.internal.requests.Requester;
import net.dv8tion.jda.internal.utils.config.AuthorizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import javax.annotation.Nonnull;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

public abstract class AbstractJDMock {

    protected JDAImpl jdaImplMock;
    private final long userId;
    private final long applicationId;

    public AbstractJDMock(long userId, long applicationId) {
        this.userId = userId;
        this.applicationId = applicationId;
    }

    @BeforeEach
    void setup() {
        EntityBuilder entityBuilderMock = Mockito.mock(EntityBuilder.class);
        jdaImplMock = Mockito.mock(JDAImpl.class);
        SelfUser selfUser = Mockito.mock(SelfUserImpl.class);
        UserImpl user = new UserImpl(2, jdaImplMock);

        Mockito.when(entityBuilderMock.createUser(any())).thenReturn(user);
        Mockito.when(selfUser.getApplicationId()).thenReturn(String.valueOf(applicationId));
        Mockito.when(selfUser.getApplicationIdLong()).thenReturn(applicationId);
        Mockito.doReturn(selfUser).when(jdaImplMock).getSelfUser();
        Mockito.when(jdaImplMock.getPrivateChannelById(anyLong()))
            .thenReturn(new PrivateChannelImpl(userId, user));
        Mockito.when(jdaImplMock.getGatewayPool()).thenReturn(new ScheduledThreadPoolExecutor(4));
        Mockito.when(jdaImplMock.getEntityBuilder()).thenReturn(entityBuilderMock);
        Mockito.doReturn(new Requester(jdaImplMock, new AuthorizationConfig("TOKEN")))
            .when(jdaImplMock)
            .getRequester();
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

        return new SlashCommandEvent(jdaImplMock, 0,
                new CommandInteractionImpl(jdaImplMock, dataObject));
    }
}
