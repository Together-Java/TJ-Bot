package org.togetherjava.tjbot.jda;

import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Response;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.AttachmentOption;
import net.dv8tion.jda.api.utils.ConcurrentSessionController;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.*;
import net.dv8tion.jda.internal.requests.Requester;
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl;
import net.dv8tion.jda.internal.requests.restaction.MessageActionImpl;
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageUpdateActionImpl;
import net.dv8tion.jda.internal.requests.restaction.interactions.ReplyCallbackActionImpl;
import net.dv8tion.jda.internal.utils.config.AuthorizationConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.ArgumentMatchers;
import org.mockito.stubbing.Answer;
import org.togetherjava.tjbot.commands.SlashCommand;
import org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Mockito.*;

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
    private static final long MESSAGE_RESPONSE_NUMBER = 1;
    private final JDAImpl jda;
    private final MemberImpl member;
    private final GuildImpl guild;
    private final ReplyCallbackActionImpl replyAction;
    private final AuditableRestActionImpl<Void> auditableRestAction;
    private final RestAction restActionMessage;
    private final MessageActionImpl messageAction;
    private final WebhookMessageUpdateActionImpl webhookMessageUpdateAction;
    private final TextChannelImpl textChannel;
    private final PrivateChannelImpl privateChannel;
    private final InteractionHook interactionHook;
    private final ReplyCallbackAction replyCallbackAction;

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
        UserImpl user = spy(new UserImpl(USER_ID, jda));
        guild = spy(new GuildImpl(jda, GUILD_ID));
        Member selfMember = spy(new MemberImpl(guild, selfUser));
        member = spy(new MemberImpl(guild, user));
        textChannel = spy(new TextChannelImpl(TEXT_CHANNEL_ID, guild));
        privateChannel = spy(new PrivateChannelImpl(jda, PRIVATE_CHANNEL_ID, user));
        messageAction = mock(MessageActionImpl.class);
        webhookMessageUpdateAction = mock(WebhookMessageUpdateActionImpl.class);
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
        when(jda.getChannelById(ArgumentMatchers.<Class<MessageChannel>>any(), anyLong()))
            .thenReturn(textChannel);
        when(jda.getPrivateChannelById(anyLong())).thenReturn(privateChannel);
        when(jda.getGuildById(anyLong())).thenReturn(guild);
        when(jda.getEntityBuilder()).thenReturn(entityBuilder);

        when(jda.getGatewayPool()).thenReturn(GATEWAY_POOL);
        when(jda.getRateLimitPool()).thenReturn(RATE_LIMIT_POOL);
        when(jda.getSessionController()).thenReturn(new ConcurrentSessionController());
        doReturn(new Requester(jda, new AuthorizationConfig(TEST_TOKEN))).when(jda).getRequester();
        when(jda.getAccountType()).thenReturn(AccountType.BOT);

        replyAction = mock(ReplyCallbackActionImpl.class);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);
        when(replyAction.addActionRow(anyCollection())).thenReturn(replyAction);
        when(replyAction.addActionRow(ArgumentMatchers.<ItemComponent>any()))
            .thenReturn(replyAction);
        when(replyAction.setContent(anyString())).thenReturn(replyAction);
        when(replyAction.addFile(any(byte[].class), any(String.class), any(AttachmentOption.class)))
            .thenReturn(replyAction);
        doNothing().when(replyAction).queue();

        auditableRestAction = (AuditableRestActionImpl<Void>) mock(AuditableRestActionImpl.class);
        doNothing().when(auditableRestAction).queue();

        restActionMessage = mock(RestAction.class);

        doNothing().when(webhookMessageUpdateAction).queue();
        doReturn(webhookMessageUpdateAction).when(webhookMessageUpdateAction)
            .setActionRow(any(ItemComponent.class));

        doReturn(everyoneRole).when(guild).getPublicRole();
        doReturn(selfMember).when(guild).getMember(selfUser);
        doReturn(member).when(guild).getMember(not(eq(selfUser)));

        RestAction<User> userAction = createSucceededActionMock(member.getUser());
        when(jda.retrieveUserById(anyLong())).thenReturn(userAction);

        doReturn(null).when(textChannel).retrieveMessageById(any());

        interactionHook = mock(InteractionHook.class);
        when(interactionHook.editOriginal(anyString())).thenReturn(webhookMessageUpdateAction);
        when(interactionHook.editOriginal(any(Message.class)))
            .thenReturn(webhookMessageUpdateAction);
        when(interactionHook.editOriginal(any(byte[].class), any(), any()))
            .thenReturn(webhookMessageUpdateAction);

        doReturn(messageAction).when(textChannel).sendMessageEmbeds(any(), any());
        doReturn(messageAction).when(textChannel).sendMessageEmbeds(any());

        doNothing().when(messageAction).queue();
        when(messageAction.content(any())).thenReturn(messageAction);

        RestAction<PrivateChannel> privateChannelAction = createSucceededActionMock(privateChannel);
        when(jda.openPrivateChannelById(anyLong())).thenReturn(privateChannelAction);
        when(jda.openPrivateChannelById(anyString())).thenReturn(privateChannelAction);
        doReturn(null).when(privateChannel).retrieveMessageById(any());
        doReturn(messageAction).when(privateChannel).sendMessage(anyString());
        doReturn(messageAction).when(privateChannel).sendMessageEmbeds(any(), any());
        doReturn(messageAction).when(privateChannel).sendMessageEmbeds(any());
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
    public @NotNull SlashCommandInteractionEventBuilder createSlashCommandInteractionEvent(
            @NotNull SlashCommand command) {
        UnaryOperator<SlashCommandInteractionEvent> mockOperator = event -> {
            SlashCommandInteractionEvent SlashCommandInteractionEvent = spy(event);
            mockInteraction(SlashCommandInteractionEvent);
            return SlashCommandInteractionEvent;
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
    public @NotNull ButtonClickEventBuilder createButtonInteractionEvent() {
        Supplier<ButtonInteractionEvent> mockEventSupplier = () -> {
            ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
            mockButtonClickEvent(event);
            return event;
        };

        UnaryOperator<Message> mockMessageOperator = event -> {
            Message message = spy(event);
            mockMessage(message);
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
    public <T extends SlashCommand> @NotNull T spySlashCommand(@NotNull T command) {
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
    public @NotNull Member createMemberSpy(long userId) {
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
    public @NotNull TextChannel createTextChannelSpy(long channelId) {
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
    public @NotNull ReplyCallbackAction getReplyActionMock() {
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
    public @NotNull InteractionHook getInteractionHookMock() {
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
    public @NotNull TextChannel getTextChannelSpy() {
        return textChannel;
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
    public @NotNull PrivateChannel getPrivateChannelSpy() {
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
    public @NotNull Member getMemberSpy() {
        return member;
    }

    /**
     * Gets the JDA mock used as universal instance by all mocks created by this tester instance.
     *
     * @return the JDA mock used by this tester
     */
    public @NotNull JDA getJdaMock() {
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
     *     var action = jdaTester.createSucceededActionMock(message);
     *
     *     doReturn(action).when(jdaTester.getTextChannelSpy()).retrieveMessageById("1");
     * }
     * </pre>
     *
     * @param t the object to consume on success
     * @param <T> the type of the object to consume
     * @return the mocked action
     */
    @SuppressWarnings("unchecked")
    public <T> @NotNull RestAction<T> createSucceededActionMock(@Nullable T t) {
        RestAction<T> action = (RestAction<T>) mock(RestAction.class);

        Answer<Void> successExecution = invocation -> {
            Consumer<? super T> successConsumer = invocation.getArgument(0);
            successConsumer.accept(t);
            return null;
        };
        Answer<RestAction<?>> mapExecution = invocation -> {
            Function<? super T, ?> mapFunction = invocation.getArgument(0);
            Object result = mapFunction.apply(t);
            return createSucceededActionMock(result);
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
     *     var action = jdaTester.createFailedActionMock(reason);
     *
     *     doReturn(action).when(jdaTester.getTextChannelSpy()).retrieveMessageById("1");
     * }
     * </pre>
     *
     * @param failureReason the reason to consume on failure
     * @param <T> the type of the object the action would contain if it would succeed
     * @return the mocked action
     */
    @SuppressWarnings("unchecked")
    public <T> @NotNull RestAction<T> createFailedActionMock(@NotNull Throwable failureReason) {
        RestAction<T> action = (RestAction<T>) mock(RestAction.class);

        Answer<Void> failureExecution = invocation -> {
            Consumer<? super Throwable> failureConsumer = invocation.getArgument(1);
            failureConsumer.accept(failureReason);
            return null;
        };

        Answer<RestAction<?>> errorMapExecution = invocation -> {
            Function<? super Throwable, ?> mapFunction = invocation.getArgument(0);
            Object result = mapFunction.apply(failureReason);
            return createSucceededActionMock(result);
        };

        Answer<RestAction<?>> mapExecution = invocation -> createFailedActionMock(failureReason);
        Answer<RestAction<?>> flatMapExecution =
                invocation -> createFailedActionMock(failureReason);

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
     * Creates an exception used by JDA on failure in most calls to the Discord API.
     * <p>
     * The exception merely wraps around the given reason and has no valid error code or message
     * set.
     *
     * @param reason the reason of the error
     * @return the created exception
     */
    public @NotNull ErrorResponseException createErrorResponseException(
            @NotNull ErrorResponse reason) {
        return ErrorResponseException.create(reason, new Response(null, -1, "", -1, Set.of()));
    }

    /**
     * Creates a MessageReceivedEvent
     * <p>
     * The method creates a MessageReceivedEvent from the input message and mock the message author
     * from the input user.
     *
     * @param message the message to create an event for
     * @param user the author from the message
     * @return the created MessageReceivedEvent
     */
    public MessageReceivedEvent createMessageReceivedEvent(@NotNull Message message,
            @NotNull User user) {
        doReturn(user).when(message).getAuthor();
        return new MessageReceivedEvent(jda, MESSAGE_RESPONSE_NUMBER, message);
    }

    /**
     * Creates a message spy
     *
     * @return the created spy
     */
    public @NotNull Message createMessageSpy() {
        return spy(Message.class);
    }

    /**
     * Creates an attachment
     *
     * @param id id of the attachment
     * @param url url of the attachment
     * @param proxyUrl proxyUrl of the attachment
     * @param fileName fileName of the attachment
     * @param contentType contentType of the attachment
     * @param description description of the attachment
     * @param size size of the attachment
     * @param height height of the attachment
     * @param width width of the attachment
     * @param ephemeral is the attachment ephemeral
     * @return the created attachment
     */
    public Message.Attachment createAttachment(long id, String url, String proxyUrl,
            String fileName, String contentType, String description, int size, int height,
            int width, boolean ephemeral) {
        return new Message.Attachment(id, url, proxyUrl, fileName, contentType, description, size,
                height, width, ephemeral, jda);
    }

    /**
     * Mocks the attachments from the given message
     *
     * @param message the message with the mocked attachments
     * @param attachments the attachments which the mock should return
     */
    public void mockMessageAttachments(Message message, List<Message.Attachment> attachments) {
        when(message.getAttachments()).thenReturn(attachments);
    }

    /**
     * Creates an embedded message
     *
     * @param url url of the embedded message
     * @param title title of the embedded message
     * @param description description of the embedded message
     * @param type type of the embedded message
     * @param timestamp timestamp of the embedded message
     * @param color color of the embedded message
     * @param thumbnail thumbnail of the embedded message
     * @param siteProvider siteProvider of the embedded message
     * @param author author of the embedded message
     * @param videoInfo videoInfo of the embedded message
     * @param footer footer of the embedded message
     * @param image image of the embedded message
     * @param fields fields of the embedded message
     * @return the created embedded message
     */
    public MessageEmbed createMessageEmbed(String url, String title, String description,
            EmbedType type, OffsetDateTime timestamp, int color, MessageEmbed.Thumbnail thumbnail,
            MessageEmbed.Provider siteProvider, MessageEmbed.AuthorInfo author,
            MessageEmbed.VideoInfo videoInfo, MessageEmbed.Footer footer,
            MessageEmbed.ImageInfo image, List<MessageEmbed.Field> fields) {
        return new MessageEmbed(url, title, description, type, timestamp, color, thumbnail,
                siteProvider, author, videoInfo, footer, image, fields);
    }

    /**
     * Mocks the embeds from the given message
     *
     * @param message the message with the mocked embeds
     * @param embeds the embeds which the mock should return
     */
    public void mockMessageEmbeds(Message message, List<MessageEmbed> embeds) {
        when(message.getEmbeds()).thenReturn(embeds);
    }

    /**
     * Mocks delete and flatMap methods from the given message
     *
     * @param message the input message
     */
    public void mockMessageDelete(Message message) {
        doReturn(auditableRestAction).when(message).delete();
        when(auditableRestAction.flatMap(any())).thenReturn(restActionMessage);
    }

    private void mockInteraction(@NotNull IReplyCallback interaction) {
        doReturn(replyAction).when(interaction).reply(anyString());
        doReturn(replyAction).when(interaction).replyEmbeds(ArgumentMatchers.<MessageEmbed>any());
        doReturn(replyAction).when(interaction).replyEmbeds(anyCollection());

        doReturn(member).when(interaction).getMember();
        doReturn(member.getUser()).when(interaction).getUser();

        doReturn(textChannel).when(interaction).getChannel();
        doReturn(textChannel).when(interaction).getMessageChannel();
        doReturn(textChannel).when(interaction).getTextChannel();
        doReturn(textChannel).when(interaction).getGuildChannel();
        doReturn(privateChannel).when(interaction).getPrivateChannel();

        doReturn(interactionHook).when(interaction).getHook();
        doReturn(replyCallbackAction).when(interaction).deferReply();
        doReturn(replyCallbackAction).when(interaction).deferReply(anyBoolean());
    }

    private void mockButtonClickEvent(@NotNull ButtonInteractionEvent event) {
        mockInteraction(event);

        doReturn(replyAction).when(event).editButton(any());
    }

    private void mockMessage(@NotNull Message message) {
        doReturn(messageAction).when(message).reply(anyString());
        doReturn(messageAction).when(message).replyEmbeds(ArgumentMatchers.<MessageEmbed>any());
        doReturn(messageAction).when(message).replyEmbeds(anyCollection());

        doReturn(auditableRestAction).when(message).delete();

        doReturn(auditableRestAction).when(message).addReaction(any(Emote.class));
        doReturn(auditableRestAction).when(message).addReaction(any(String.class));

        doReturn(member).when(message).getMember();
        doReturn(member.getUser()).when(message).getAuthor();
    }
}
