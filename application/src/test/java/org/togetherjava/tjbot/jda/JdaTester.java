package org.togetherjava.tjbot.jda;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Response;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.RestConfig;
import net.dv8tion.jda.api.requests.RestRateLimiter;
import net.dv8tion.jda.api.requests.SequentialRestRateLimiter;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.AttachmentProxy;
import net.dv8tion.jda.api.utils.ConcurrentSessionController;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.EntityBuilder;
import net.dv8tion.jda.internal.entities.GuildImpl;
import net.dv8tion.jda.internal.entities.MemberImpl;
import net.dv8tion.jda.internal.entities.RoleImpl;
import net.dv8tion.jda.internal.entities.SelfUserImpl;
import net.dv8tion.jda.internal.entities.UserImpl;
import net.dv8tion.jda.internal.entities.channel.concrete.PrivateChannelImpl;
import net.dv8tion.jda.internal.entities.channel.concrete.TextChannelImpl;
import net.dv8tion.jda.internal.entities.channel.concrete.ThreadChannelImpl;
import net.dv8tion.jda.internal.requests.Requester;
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl;
import net.dv8tion.jda.internal.requests.restaction.MessageCreateActionImpl;
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageEditActionImpl;
import net.dv8tion.jda.internal.requests.restaction.interactions.ReplyCallbackActionImpl;
import net.dv8tion.jda.internal.utils.config.AuthorizationConfig;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.MockingDetails;
import org.mockito.internal.util.MockUtil;
import org.mockito.stubbing.Answer;

import org.togetherjava.tjbot.features.SlashCommand;
import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;

import javax.annotation.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Utility class for testing {@link SlashCommand}s.
 * <p>
 * Mocks JDA and can create events that can be used to test {@link SlashCommand}s, e.g.
 * {@link #createSlashCommandInteractionEvent(SlashCommand)}. The created events are Mockito mocks,
 * which can be exploited for testing.
 * <p>
 * An example test using this class might look like:
 *
 * <pre>
 * {
 *     &#64;code
 *     SlashCommand command = new PingCommand();
 *     JdaTester jdaTester = new JdaTester();
 *
 *     SlashCommandInteractionEvent event =
 *             jdaTester.createSlashCommandInteractionEvent(command).build();
 *     command.onSlashCommand(event);
 *
 *     verify(event).reply("Pong!");
 * }
 * </pre>
 */
public final class JdaTester {
    private static final ScheduledExecutorService GATEWAY_POOL = new ScheduledThreadPoolExecutor(4);
    private static final ScheduledExecutorService RATE_LIMIT_POOL =
            new ScheduledThreadPoolExecutor(4);
    private static final String TEST_TOKEN = "TEST_TOKEN";
    private static final long USER_ID = 1;
    private static final long SELF_USER_ID = 2;
    private static final long APPLICATION_ID = 1;
    private static final long PRIVATE_CHANNEL_ID = 1;
    private static final long GUILD_ID = 1;
    private static final long TEXT_CHANNEL_ID = 1;
    private static final long THREAD_CHANNEL_ID = 2;
    private final JDAImpl jda;
    private final MemberImpl member;
    private final GuildImpl guild;
    private final RestRateLimiter rateLimiter;
    private final ReplyCallbackActionImpl replyAction;
    private final AuditableRestActionImpl<Void> auditableRestAction;
    private final MessageCreateActionImpl messageCreateAction;
    private final WebhookMessageEditActionImpl webhookMessageEditAction;
    private final TextChannelImpl textChannel;
    private final ThreadChannelImpl threadChannel;
    private final PrivateChannelImpl privateChannel;
    private final InteractionHook interactionHook;
    private final ReplyCallbackAction replyCallbackAction;
    private final AtomicLong responseNumber = new AtomicLong(0);

    /**
     * Creates a new instance. The instance uses a fresh and isolated mocked JDA setup.
     * <p>
     * Reusing this instance also means to reuse guilds, text channels and such from this JDA setup,
     * which can have an impact on tests. For example a previous text that already send messages to
     * a channel, the messages will then still be present in the instance.
     */
    @SuppressWarnings("unchecked")
    public JdaTester() {
        // TODO Extend this functionality, make it nicer.
        // Maybe offer a builder for multiple users and channels and what not
        jda = mock(JDAImpl.class);
        when(jda.getCacheFlags()).thenReturn(EnumSet.noneOf(CacheFlag.class));

        SelfUserImpl selfUser = spy(new SelfUserImpl(SELF_USER_ID, jda));
        selfUser.setName("Self Tester");
        UserImpl user = spy(new UserImpl(USER_ID, jda));
        user.setName("John Doe Tester");
        guild = spy(new GuildImpl(jda, GUILD_ID));
        rateLimiter =
                new SequentialRestRateLimiter(new RestRateLimiter.RateLimitConfig(RATE_LIMIT_POOL,
                        RestRateLimiter.GlobalRateLimit.create(), true));
        Member selfMember = spy(new MemberImpl(guild, selfUser));
        member = spy(new MemberImpl(guild, user));
        textChannel = spy(new TextChannelImpl(TEXT_CHANNEL_ID, guild));
        threadChannel = spy(
                new ThreadChannelImpl(THREAD_CHANNEL_ID, guild, ChannelType.GUILD_PUBLIC_THREAD));
        privateChannel = spy(new PrivateChannelImpl(jda, PRIVATE_CHANNEL_ID, user));
        messageCreateAction = mock(MessageCreateActionImpl.class);
        webhookMessageEditAction = mock(WebhookMessageEditActionImpl.class);
        replyCallbackAction = mock(ReplyCallbackAction.class);
        EntityBuilder entityBuilder = mock(EntityBuilder.class);
        Role everyoneRole = new RoleImpl(GUILD_ID, guild);

        when(entityBuilder.createUser(any())).thenReturn(user);
        when(entityBuilder.createMember(any(), any())).thenReturn(member);
        doReturn(true).when(member).hasPermission(any(Permission.class));
        doReturn(true).when(member).hasPermission(any(GuildChannel.class), any(Permission.class));
        doReturn(true).when(selfMember).hasPermission(any(Permission.class));
        doReturn(true).when(selfMember)
            .hasPermission(any(GuildChannel.class), any(Permission.class));

        doReturn(String.valueOf(APPLICATION_ID)).when(selfUser).getApplicationId();
        doReturn(APPLICATION_ID).when(selfUser).getApplicationIdLong();
        doReturn(selfUser).when(jda).getSelfUser();
        when(jda.getGuildChannelById(anyLong())).thenReturn(textChannel);
        when(jda.getTextChannelById(anyLong())).thenReturn(textChannel);
        when(jda.getThreadChannelById(anyLong())).thenReturn(threadChannel);
        when(jda.getChannelById(ArgumentMatchers.<Class<MessageChannel>>any(), anyLong()))
            .thenReturn(textChannel);
        when(jda.getPrivateChannelById(anyLong())).thenReturn(privateChannel);
        when(jda.getGuildById(anyLong())).thenReturn(guild);
        when(jda.getEntityBuilder()).thenReturn(entityBuilder);

        when(jda.getGatewayPool()).thenReturn(GATEWAY_POOL);
        when(jda.getRateLimitPool()).thenReturn(RATE_LIMIT_POOL);
        when(jda.getSessionController()).thenReturn(new ConcurrentSessionController());
        doReturn(new Requester(jda, new AuthorizationConfig(TEST_TOKEN), new RestConfig(),
                rateLimiter))
            .when(jda)
            .getRequester();

        replyAction = mock(ReplyCallbackActionImpl.class);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        when(replyAction.addActionRow(anyCollection())).thenReturn(replyAction);
        when(replyAction.addActionRow(ArgumentMatchers.<ItemComponent>any()))
            .thenReturn(replyAction);
        when(replyAction.setContent(anyString())).thenReturn(replyAction);
        when(replyAction.addFiles(anyCollection())).thenReturn(replyAction);
        when(replyAction.addFiles(any(FileUpload.class))).thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        auditableRestAction = createSucceededActionMock(null, AuditableRestActionImpl.class);

        doNothing().when(webhookMessageEditAction).queue();
        doReturn(webhookMessageEditAction).when(webhookMessageEditAction)
            .setActionRow(any(ItemComponent.class));

        when(guild.getGuildChannelById(anyLong())).thenReturn(textChannel);
        doReturn(everyoneRole).when(guild).getPublicRole();
        doReturn(selfMember).when(guild).getMember(selfUser);
        doReturn(member).when(guild).getMember(not(eq(selfUser)));

        CacheRestAction<User> userAction =
                createSucceededActionMock(member.getUser(), CacheRestAction.class);
        when(jda.retrieveUserById(anyLong())).thenReturn(userAction);

        doReturn(null).when(textChannel).retrieveMessageById(any());
        doReturn(null).when(threadChannel).retrieveMessageById(any());

        interactionHook = mock(InteractionHook.class);
        when(interactionHook.editOriginal(anyString())).thenReturn(webhookMessageEditAction);
        when(interactionHook.editOriginal(any(MessageEditData.class)))
            .thenReturn(webhookMessageEditAction);
        when(interactionHook.editOriginalAttachments(any(FileUpload.class)))
            .thenReturn(webhookMessageEditAction);

        doReturn(messageCreateAction).when(textChannel).sendMessageEmbeds(any(), any());
        doReturn(messageCreateAction).when(threadChannel).sendMessageEmbeds(any(), any());
        doReturn(messageCreateAction).when(textChannel).sendMessageEmbeds(any());
        doReturn(messageCreateAction).when(threadChannel).sendMessageEmbeds(any());
        doReturn(privateChannel).when(textChannel).asPrivateChannel();
        doReturn(textChannel).when(threadChannel).getParentChannel();

        doNothing().when(messageCreateAction).queue();
        when(messageCreateAction.setContent(any())).thenReturn(messageCreateAction);
        when(messageCreateAction.addContent(any())).thenReturn(messageCreateAction);
        when(messageCreateAction.addFiles(any(FileUpload.class))).thenReturn(messageCreateAction);
        when(messageCreateAction.addFiles(anyCollection())).thenReturn(messageCreateAction);

        CacheRestAction<PrivateChannel> privateChannelAction =
                createSucceededActionMock(privateChannel, CacheRestAction.class);
        when(jda.openPrivateChannelById(anyLong())).thenReturn(privateChannelAction);
        when(jda.openPrivateChannelById(anyString())).thenReturn(privateChannelAction);
        doReturn(privateChannelAction).when(user).openPrivateChannel();
        doReturn(null).when(privateChannel).retrieveMessageById(any());
        doReturn(messageCreateAction).when(privateChannel).sendMessage(anyString());
        doReturn(messageCreateAction).when(privateChannel)
            .sendMessage(any(MessageCreateData.class));
        doReturn(messageCreateAction).when(privateChannel).sendMessageEmbeds(any(), any());
        doReturn(messageCreateAction).when(privateChannel).sendMessageEmbeds(any());
    }

    /**
     * Creates a Mockito mocked slash command event, which can be used for
     * {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}.
     * <p>
     * The method creates a builder that can be used to further adjust the event before creation,
     * e.g. provide options.
     *
     * @param command the command to create an event for
     * @return a builder used to create a Mockito mocked slash command event
     */
    public SlashCommandInteractionEventBuilder createSlashCommandInteractionEvent(
            SlashCommand command) {
        UnaryOperator<SlashCommandInteractionEvent> mockOperator = event -> {
            if (!MockUtil.isMock(event)) {
                event = spy(event);
            }
            mockInteraction(event);
            return event;
        };

        return new SlashCommandInteractionEventBuilder(jda, mockOperator).setCommand(command)
            .setToken(TEST_TOKEN)
            .setChannelId(String.valueOf(TEXT_CHANNEL_ID))
            .setApplicationId(String.valueOf(APPLICATION_ID))
            .setGuildId(String.valueOf(GUILD_ID))
            .setUserId(String.valueOf(USER_ID))
            .setUserWhoTriggered(member);
    }

    /**
     * Creates a Mockito mocked button click event, which can be used for
     * {@link SlashCommand#onButtonClick(ButtonInteractionEvent, List)}.
     * <p>
     * The method creates a builder that can be used to further adjust the event before creation,
     * e.g. provide options.
     *
     * @return a builder used to create a Mockito mocked slash command event
     */
    public ButtonClickEventBuilder createButtonInteractionEvent() {
        Supplier<ButtonInteractionEvent> mockEventSupplier = () -> {
            ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
            mockButtonClickEvent(event);
            return event;
        };

        UnaryOperator<Message> mockMessageOperator = event -> {
            MockingDetails mockingDetails = mockingDetails(event);
            Message message =
                    mockingDetails.isMock() || mockingDetails.isSpy() ? event : spy(event);

            mockMessage(message, ChannelType.TEXT);
            return message;
        };

        return new ButtonClickEventBuilder(mockEventSupplier, mockMessageOperator)
            .setUserWhoClicked(member);
    }

    /**
     * Creates a Mockito spy on the given slash command.
     * <p>
     * The spy is also prepared for mocked execution, e.g. attributes such as
     * {@link SlashCommand#acceptComponentIdGenerator(ComponentIdGenerator)} are filled with mocks.
     *
     * @param command the command to spy on
     * @param <T> the type of the command to spy on
     * @return the created spy
     */
    public <T extends SlashCommand> T spySlashCommand(T command) {
        T spiedCommand = spy(command);
        spiedCommand
            .acceptComponentIdGenerator((componentId, lifespan) -> UUID.randomUUID().toString());
        return spiedCommand;
    }

    /**
     * Creates a Mockito spy for a member with the given user id.
     * <p>
     * See {@link #getMemberSpy()} to get the default member used by this tester.
     *
     * @param userId the id of the member to create
     * @return the created spy
     */
    public Member createMemberSpy(long userId) {
        UserImpl user = spy(new UserImpl(userId, jda));
        return spy(new MemberImpl(guild, user));
    }

    /**
     * Creates a Mockito spy for a text channel with the given channel id.
     * <p>
     * See {@link #getTextChannelSpy()} to get the default text channel used by this tester.
     *
     * @param channelId the id of the text channel to create
     * @return the created spy
     */
    public TextChannel createTextChannelSpy(long channelId) {
        return spy(new TextChannelImpl(channelId, guild));
    }

    /**
     * Gets the Mockito mock used as universal reply action by all mocks created by this tester
     * instance.
     * <p>
     * For example the events created by {@link #createSlashCommandInteractionEvent(SlashCommand)}
     * will return this mock on several of their methods.
     *
     * @return the reply action mock used by this tester
     */
    public ReplyCallbackAction getReplyActionMock() {
        return replyAction;
    }

    /**
     * Gets the Mockito mock used as universal interaction hook by all mocks created by this tester
     * instance.
     * <p>
     * For example the events created by {@link #createSlashCommandInteractionEvent(SlashCommand)}
     * will return this mock on several of their methods.
     *
     * @return the interaction hook mock used by this tester
     */
    public InteractionHook getInteractionHookMock() {
        return interactionHook;
    }

    /**
     * Gets the text channel spy used as universal text channel by all mocks created by this tester
     * instance.
     * <p>
     * For example the events created by {@link #createSlashCommandInteractionEvent(SlashCommand)}
     * will return this spy on several of their methods.
     *
     * @return the text channel spy used by this tester
     */
    public TextChannel getTextChannelSpy() {
        return textChannel;
    }

    /**
     * Gets the thread channel spy used as universal thread channel by all mocks created by this
     * tester instance.
     * <p>
     * For example the events created by
     * {@link #createMessageReceiveEvent(MessageCreateData, List, ChannelType)} can return this
     * channel spy
     *
     * @return the thread channel spy used by this tester
     */
    public ThreadChannel getThreadChannelSpy() {
        return threadChannel;
    }

    /**
     * Gets the private channel spy used as universal private channel by all mocks created by this
     * tester instance.
     * <p>
     * For example {@link JDA#openPrivateChannelById(long)} will return this spy if used on the
     * instance returned by {@link #getJdaMock()}.
     *
     * @return the private channel spy used by this tester
     */
    public PrivateChannel getPrivateChannelSpy() {
        return privateChannel;
    }

    /**
     * Gets the member spy used as universal member by all mocks created by this tester instance.
     * <p>
     * For example the events created by {@link #createSlashCommandInteractionEvent(SlashCommand)}
     * will return this spy on several of their methods.
     * <p>
     * See {@link #createMemberSpy(long)} to create other members.
     *
     * @return the member spy used by this tester
     */
    public Member getMemberSpy() {
        return member;
    }

    /**
     * Gets the JDA mock used as universal instance by all mocks created by this tester instance.
     *
     * @return the JDA mock used by this tester
     */
    public JDA getJdaMock() {
        return jda;
    }

    /**
     * Creates a mocked action that always succeeds and consumes the given object.
     * <p>
     * Such an action is useful for testing things involving calls like
     * {@link TextChannel#retrieveMessageById(long)} or similar, example:
     *
     * <pre>
     * {
     *     &#64;code
     *     var jdaTester = new JdaTester();
     *
     *     var message = new MessageBuilder("Hello World!").build();
     *     var action = jdaTester.createSucceededActionMock(message, RestAction.class);
     *
     *     doReturn(action).when(jdaTester.getTextChannelSpy()).retrieveMessageById("1");
     * }
     * </pre>
     *
     * @param t the object to consume on success
     * @param restActionType class token of the type of the Rest Action to return
     * @param <T> the type of the object to consume
     * @param <R> the specific type of the Rest Action to return
     * @return the mocked action
     */
    @SuppressWarnings("unchecked")
    public <T, R extends RestAction<T>> R createSucceededActionMock(@Nullable T t,
            Class<R> restActionType) {
        R action = mock(restActionType);

        Answer<Void> successExecution = invocation -> {
            Consumer<? super T> successConsumer = invocation.getArgument(0);
            successConsumer.accept(t);
            return null;
        };
        Answer<RestAction<?>> mapExecution = invocation -> {
            Function<? super T, ?> mapFunction = invocation.getArgument(0);
            Object result = mapFunction.apply(t);
            return createSucceededActionMock(result, RestAction.class);
        };
        Answer<RestAction<?>> flatMapExecution = invocation -> {
            Function<? super T, RestAction<?>> flatMapFunction = invocation.getArgument(0);
            return flatMapFunction.apply(t);
        };

        doNothing().when(action).queue();

        doAnswer(successExecution).when(action).queue(any());
        doAnswer(successExecution).when(action).queue(any(), any());
        when(action.onErrorMap(any())).thenReturn(action);
        when(action.onErrorMap(any(), any())).thenReturn(action);

        doAnswer(mapExecution).when(action).map(any());
        doAnswer(flatMapExecution).when(action).flatMap(any());

        return action;
    }

    /**
     * Variant of {@link #createSucceededActionMock(Object, Class)} returning a plain
     * {@link RestAction}.
     *
     * @param t the object to consume on success
     * @param <T> the type of the object to consume
     * @return the mocked action
     * @see #createSucceededActionMock(Object, Class)
     */
    @SuppressWarnings("unchecked")
    public <T> RestAction<T> createSucceededActionMock(@Nullable T t) {
        return createSucceededActionMock(t, RestAction.class);
    }

    /**
     * Creates a mocked action that always fails and consumes the given failure reason.
     * <p>
     * Such an action is useful for testing things involving calls like
     * {@link TextChannel#retrieveMessageById(long)} or similar, example:
     *
     * <pre>
     * {
     *     &#64;code
     *     var jdaTester = new JdaTester();
     *
     *     var reason = new FooException();
     *     var action = jdaTester.createFailedActionMock(reason, RestAction.class);
     *
     *     doReturn(action).when(jdaTester.getTextChannelSpy()).retrieveMessageById("1");
     * }
     * </pre>
     *
     * @param failureReason the reason to consume on failure
     * @param restActionType class token of the type of the Rest Action to return
     * @param <T> the type of the object the action would contain if it would succeed
     * @param <R> the specific type of the Rest Action to return
     * @return the mocked action
     */
    @SuppressWarnings("unchecked")
    public <T, R extends RestAction<T>> R createFailedActionMock(Throwable failureReason,
            Class<R> restActionType) {
        R action = mock(restActionType);

        Answer<Void> failureExecution = invocation -> {
            Consumer<? super Throwable> failureConsumer = invocation.getArgument(1);
            failureConsumer.accept(failureReason);
            return null;
        };

        Answer<RestAction<?>> errorMapExecution = invocation -> {
            Function<? super Throwable, ?> mapFunction = invocation.getArgument(0);
            Object result = mapFunction.apply(failureReason);
            return createSucceededActionMock(result, RestAction.class);
        };

        Answer<RestAction<?>> mapExecution =
                invocation -> createFailedActionMock(failureReason, RestAction.class);
        Answer<RestAction<?>> flatMapExecution =
                invocation -> createFailedActionMock(failureReason, RestAction.class);

        doNothing().when(action).queue();
        doNothing().when(action).queue(any());

        doAnswer(errorMapExecution).when(action).onErrorMap(any());
        doAnswer(errorMapExecution).when(action).onErrorMap(any(), any());

        doAnswer(failureExecution).when(action).queue(any(), any());

        doAnswer(mapExecution).when(action).map(any());
        doAnswer(flatMapExecution).when(action).flatMap(any());

        return action;
    }

    /**
     * Variant of {@link #createFailedActionMock(Throwable, Class)} returning a plain
     * {@link RestAction}.
     *
     * @param failureReason the reason to consume on failure
     * @param <T> the type of the object the action would contain if it would succeed
     * @return the mocked action
     * @see #createFailedActionMock(Throwable, Class)
     */
    @SuppressWarnings("unchecked")
    public <T> RestAction<T> createFailedActionMock(Throwable failureReason) {
        return createFailedActionMock(failureReason, RestAction.class);
    }

    /**
     * Creates an exception used by JDA on failure in most calls to the Discord API.
     * <p>
     * The exception merely wraps around the given reason and has no valid error code or message
     * set.
     *
     * @param reason the reason of the error
     * @return the created exception
     */
    public ErrorResponseException createErrorResponseException(ErrorResponse reason) {
        return ErrorResponseException.create(reason, new Response(null, -1, "", -1, Set.of()));
    }

    /**
     * Creates a Mockito mocked message receive event, which can be used for
     * {@link org.togetherjava.tjbot.features.MessageReceiver#onMessageReceived(MessageReceivedEvent)}.
     *
     * @param message the message that has been received
     * @param attachments attachments of the message, empty if none
     * @param channelType the type of the channel the message was sent in. See
     *        {@link #mockMessage(Message, ChannelType)} for supported channel types
     * @return the event of receiving the given message
     */
    public MessageReceivedEvent createMessageReceiveEvent(MessageCreateData message,
            List<Message.Attachment> attachments, ChannelType channelType) {
        Message receivedMessage = clientMessageToReceivedMessageMock(message);
        mockMessage(receivedMessage, channelType);
        doReturn(attachments).when(receivedMessage).getAttachments();

        return new MessageReceivedEvent(jda, responseNumber.getAndIncrement(), receivedMessage);
    }

    /**
     * Creates an argument matcher that asserts that an attachment has the given content.
     * <p>
     * This requires the data-stream in the attachment to support
     * {@link InputStream#markSupported()}. This is the case for most simpler streams, such as
     * strings or files.
     * <p>
     * An example would be
     * 
     * <pre>
     * {
     *     &#64;code
     *     verify(jdaTester.getReplyActionMock())
     *       .addFiles(
     *         argThat(jdaTester.createAttachmentHasContentMatcher("foo"))
     *       );
     *
     *     // checking that the following has been called
     *
     *     event.reply("")
     *       .addFiles(
     *         FileUpload.fromData("foo".getBytes(StandardCharsets.UTF_8), "")
     *       );
     * }
     * </pre>
     *
     * @param content the content the attachment should have
     * @return the created matcher
     */
    public ArgumentMatcher<FileUpload> createAttachmentHasContentMatcher(String content) {
        return attachment -> {
            if (attachment == null) {
                return false;
            }

            InputStream dataStream = attachment.getData();
            if (!dataStream.markSupported()) {
                return false;
            }

            byte[] expectedContentRaw = content.getBytes(StandardCharsets.UTF_8);
            dataStream.mark(expectedContentRaw.length);

            byte[] actualContent = assertDoesNotThrow(() -> attachment.getData().readAllBytes());
            assertDoesNotThrow(dataStream::reset);

            return Arrays.equals(expectedContentRaw, actualContent);
        };
    }

    private void mockInteraction(IReplyCallback interaction) {
        doReturn(replyAction).when(interaction).reply(anyString());
        doReturn(replyAction).when(interaction).replyEmbeds(ArgumentMatchers.<MessageEmbed>any());
        doReturn(replyAction).when(interaction).replyEmbeds(anyCollection());

        doReturn(member).when(interaction).getMember();
        doReturn(member.getUser()).when(interaction).getUser();

        doReturn(textChannel).when(interaction).getChannel();
        doReturn(textChannel).when(interaction).getMessageChannel();
        doReturn(textChannel).when(interaction).getGuildChannel();

        doReturn(interactionHook).when(interaction).getHook();
        doReturn(replyCallbackAction).when(interaction).deferReply();
        doReturn(replyCallbackAction).when(interaction).deferReply(anyBoolean());
    }

    private void mockButtonClickEvent(ButtonInteractionEvent event) {
        mockInteraction(event);

        doReturn(replyAction).when(event).editButton(any());
    }

    private void mockMessage(Message message, ChannelType channelType) {
        MessageChannelUnion channel = switch (channelType) {
            case TEXT -> textChannel;
            case GUILD_PUBLIC_THREAD -> threadChannel;
            default ->
                throw new IllegalArgumentException("Unsupported channel type: " + channelType);
        };

        doReturn(messageCreateAction).when(message).reply(anyString());
        doReturn(messageCreateAction).when(message)
            .replyEmbeds(ArgumentMatchers.<MessageEmbed>any());
        doReturn(messageCreateAction).when(message).replyEmbeds(anyCollection());

        doReturn(auditableRestAction).when(message).delete();

        doReturn(auditableRestAction).when(message).addReaction(any(Emoji.class));

        doReturn(member).when(message).getMember();
        doReturn(member.getUser()).when(message).getAuthor();

        doReturn(channel).when(message).getChannel();
        doReturn(1L).when(message).getIdLong();
        doReturn(false).when(message).isWebhookMessage();

        doReturn(message.getContentRaw()).when(message).getContentDisplay();
        doReturn(message.getContentRaw()).when(message).getContentStripped();
    }

    /**
     * Transforms the given client-side message to a mocked message received from Discord.
     *
     * @param clientMessage the client-side message to transform
     * @return the mocked copy of the given message, but as message received from Discord
     */
    public Message clientMessageToReceivedMessageMock(MessageCreateData clientMessage) {
        Message receivedMessage = mock(Message.class);
        when(receivedMessage.getJDA()).thenReturn(jda);
        when(receivedMessage.getEmbeds()).thenReturn(clientMessage.getEmbeds());
        when(receivedMessage.getContentRaw()).thenReturn(clientMessage.getContent());
        when(receivedMessage.getContentDisplay()).thenReturn(clientMessage.getContent());
        when(receivedMessage.getContentStripped()).thenReturn(clientMessage.getContent());

        when(receivedMessage.getComponents()).thenReturn(clientMessage.getComponents());
        when(receivedMessage.getButtons()).thenReturn(clientMessage.getComponents()
            .stream()
            .map(LayoutComponent::getButtons)
            .flatMap(List::stream)
            .toList());

        List<Message.Attachment> attachments = clientMessage.getAttachments()
            .stream()
            .map(this::clientAttachmentToReceivedAttachmentMock)
            .toList();
        when(receivedMessage.getAttachments()).thenReturn(attachments);

        return receivedMessage;
    }

    private Message.Attachment clientAttachmentToReceivedAttachmentMock(
            FileUpload clientAttachment) {
        Message.Attachment receivedAttachment = mock(Message.Attachment.class);
        AttachmentProxy attachmentProxy = mock(AttachmentProxy.class);

        when(receivedAttachment.getJDA()).thenReturn(jda);
        when(receivedAttachment.getFileName()).thenReturn(clientAttachment.getName());
        when(receivedAttachment.getFileExtension())
            .thenReturn(getFileExtension(clientAttachment.getName()).orElse(null));
        when(receivedAttachment.getProxy()).thenReturn(attachmentProxy);

        when(attachmentProxy.download())
            .thenReturn(CompletableFuture.completedFuture(clientAttachment.getData()));

        return receivedAttachment;
    }

    private static Optional<String> getFileExtension(String fileName) {
        int extensionStartIndex = fileName.lastIndexOf('.');
        if (extensionStartIndex == -1) {
            return Optional.empty();
        }
        return Optional.of(fileName.substring(extensionStartIndex + 1));
    }
}
