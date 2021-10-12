package org.togetherjava.tjbot.jda;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.utils.ConcurrentSessionController;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.*;
import net.dv8tion.jda.internal.requests.Requester;
import net.dv8tion.jda.internal.requests.restaction.MessageActionImpl;
import net.dv8tion.jda.internal.requests.restaction.interactions.ReplyActionImpl;
import net.dv8tion.jda.internal.utils.config.AuthorizationConfig;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentMatchers;
import org.togetherjava.tjbot.commands.SlashCommand;

import java.util.EnumSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.UnaryOperator;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public final class JdaTester {
    private final JDAImpl jda;
    private static final int GATEWAY_POOL_SIZE = 4;
    private static final int RATE_LIMIT_POOL_SIZE = 4;
    private static final String TEST_TOKEN = "TEST_TOKEN";
    private static final long USER_ID = 1;
    private static final long APPLICATION_ID = 1;
    private static final long PRIVATE_CHANNEL_ID = 1;
    private static final long GUILD_ID = 1;
    private static final long TEXT_CHANNEL_ID = 1;
    private final MemberImpl member;

    public JdaTester() {
        // TODO Extend this functionality, make it nicer.
        // Maybe offer a builder for multiple users and channels and what not
        jda = mock(JDAImpl.class);
        when(jda.getCacheFlags()).thenReturn(EnumSet.noneOf(CacheFlag.class));

        SelfUser selfUser = mock(SelfUserImpl.class);
        UserImpl user = spy(new UserImpl(USER_ID, jda));
        GuildImpl guild = spy(new GuildImpl(jda, GUILD_ID));
        member = spy(new MemberImpl(guild, user));
        TextChannelImpl textChannel = spy(new TextChannelImpl(TEXT_CHANNEL_ID, guild));
        PrivateChannelImpl privateChannel = spy(new PrivateChannelImpl(PRIVATE_CHANNEL_ID, user));
        MessageAction messageAction = mock(MessageActionImpl.class);
        EntityBuilder entityBuilder = mock(EntityBuilder.class);

        // TODO Depending on the commands we might need a lot more mocking here
        when(entityBuilder.createUser(any())).thenReturn(user);
        when(entityBuilder.createMember(any(), any())).thenReturn(member);
        // TODO Giving out all permissions makes it impossible to test permission requirements on
        // commands
        doReturn(true).when(member).hasPermission(ArgumentMatchers.<Permission>any());
        when(selfUser.getApplicationId()).thenReturn(String.valueOf(APPLICATION_ID));
        when(selfUser.getApplicationIdLong()).thenReturn(APPLICATION_ID);
        doReturn(selfUser).when(jda).getSelfUser();
        when(jda.getGuildChannelById(anyLong())).thenReturn(textChannel);
        when(jda.getPrivateChannelById(anyLong())).thenReturn(privateChannel);
        when(jda.getGuildById(anyLong())).thenReturn(guild);
        when(jda.getEntityBuilder()).thenReturn(entityBuilder);

        when(jda.getGatewayPool()).thenReturn(new ScheduledThreadPoolExecutor(GATEWAY_POOL_SIZE));
        when(jda.getRateLimitPool())
            .thenReturn(new ScheduledThreadPoolExecutor(RATE_LIMIT_POOL_SIZE));
        when(jda.getSessionController()).thenReturn(new ConcurrentSessionController());
        doReturn(new Requester(jda, new AuthorizationConfig(TEST_TOKEN))).when(jda).getRequester();

        doReturn(messageAction).when(privateChannel).sendMessage(anyString());
    }

    public @NotNull SlashCommandEventBuilder createSlashCommandEvent(
            @NotNull SlashCommand command) {
        UnaryOperator<SlashCommandEvent> mockOperator = event -> {
            SlashCommandEvent slashCommandEvent = spy(event);
            ReplyActionImpl replyAction = mock(ReplyActionImpl.class);

            doReturn(replyAction).when(slashCommandEvent).reply(anyString());
            when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
            doReturn(member).when(slashCommandEvent).getMember();

            return slashCommandEvent;
        };

        return new SlashCommandEventBuilder(jda, mockOperator).command(command)
            .token(TEST_TOKEN)
            .channelId(String.valueOf(TEXT_CHANNEL_ID))
            .applicationId(String.valueOf(APPLICATION_ID))
            .guildId(String.valueOf(GUILD_ID))
            .userId(String.valueOf(USER_ID));
    }

    // TODO Add methods to create button and menu events as well
}
