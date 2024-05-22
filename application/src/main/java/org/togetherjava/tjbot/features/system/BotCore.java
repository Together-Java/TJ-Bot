package org.togetherjava.tjbot.features.system;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.features.EventReceiver;
import org.togetherjava.tjbot.features.Feature;
import org.togetherjava.tjbot.features.Features;
import org.togetherjava.tjbot.features.MessageContextCommand;
import org.togetherjava.tjbot.features.MessageReceiver;
import org.togetherjava.tjbot.features.Routine;
import org.togetherjava.tjbot.features.SlashCommand;
import org.togetherjava.tjbot.features.UserContextCommand;
import org.togetherjava.tjbot.features.UserInteractionType;
import org.togetherjava.tjbot.features.UserInteractor;
import org.togetherjava.tjbot.features.componentids.ComponentId;
import org.togetherjava.tjbot.features.componentids.ComponentIdParser;
import org.togetherjava.tjbot.features.componentids.ComponentIdStore;
import org.togetherjava.tjbot.features.componentids.InvalidComponentIdFormatException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The bot core is the core of command handling in this application.
 * <p>
 * It knows and manages all commands, registers them towards Discord and is the entry point of all
 * events. It forwards events to their corresponding commands and does the heavy lifting on all sort
 * of event parsing.
 * <p>
 * Commands are made available via {@link Features}, then the system has to be added to JDA as an
 * event listener, using {@link net.dv8tion.jda.api.JDA#addEventListener(Object...)}. Afterwards,
 * the system is ready and will correctly forward events to all commands.
 */
public final class BotCore extends ListenerAdapter implements CommandProvider {
    private static final Logger logger = LoggerFactory.getLogger(BotCore.class);
    private static final ExecutorService COMMAND_SERVICE = Executors.newCachedThreadPool();
    private static final ScheduledExecutorService ROUTINE_SERVICE =
            Executors.newScheduledThreadPool(5);
    private final Config config;
    private final Map<String, UserInteractor> prefixedNameToInteractor;
    private final List<Routine> routines;
    private final ComponentIdParser componentIdParser;
    private final ComponentIdStore componentIdStore;
    private final Map<Pattern, MessageReceiver> channelNameToMessageReceiver = new HashMap<>();

    /**
     * Creates a new command system which uses the given database to allow commands to persist data.
     * <p>
     * Commands are fetched from {@link Features}.
     *
     * @param jda the JDA instance that this command system will be used with
     * @param database the database that commands may use to persist data
     * @param config the configuration to use for this system
     */
    public BotCore(JDA jda, Database database, Config config) {
        this.config = config;
        Collection<Feature> features = Features.createFeatures(jda, database, config);

        // Message receivers
        features.stream()
            .filter(MessageReceiver.class::isInstance)
            .map(MessageReceiver.class::cast)
            .forEach(messageReceiver -> channelNameToMessageReceiver
                .put(messageReceiver.getChannelNamePattern(), messageReceiver));

        // Event receivers
        features.stream()
            .filter(EventReceiver.class::isInstance)
            .map(EventReceiver.class::cast)
            .forEach(jda::addEventListener);

        // Routines (are scheduled once the core is ready)
        routines = features.stream()
            .filter(Routine.class::isInstance)
            .map(Routine.class::cast)
            .toList();

        // User Interactors (e.g. slash commands)
        prefixedNameToInteractor =
                features.stream()
                    .filter(UserInteractor.class::isInstance)
                    .map(UserInteractor.class::cast)
                    .filter(validateInteractorPredicate())
                    .collect(
                            Collectors.toMap(
                                    userInteractor -> userInteractor.getInteractionType()
                                        .getPrefixedName(userInteractor.getName()),
                                    Function.identity()));


        // Component Id Store
        componentIdStore = new ComponentIdStore(database);
        componentIdStore.addComponentIdRemovedListener(BotCore::onComponentIdRemoved);
        componentIdParser = uuid -> componentIdStore.get(UUID.fromString(uuid));
        Collection<UserInteractor> interactors = getInteractors();

        interactors.forEach(
                interactor -> interactor.acceptComponentIdGenerator(((componentId, lifespan) -> {
                    UUID uuid = UUID.randomUUID();
                    componentIdStore.putOrThrow(uuid, componentId, lifespan);
                    return uuid.toString();
                })));

        if (logger.isInfoEnabled()) {
            logger.info("Available user interactors: {}", interactors);
        }
    }

    /**
     * Returns a predicate, which validates the given interactor
     *
     * @return A predicate, which validates the given interactor
     */
    private static Predicate<UserInteractor> validateInteractorPredicate() {
        return interactor -> {
            String name = Objects.requireNonNull(interactor.getName());

            for (UserInteractionType interactionType : UserInteractionType.values()) {
                if (interactionType == UserInteractionType.OTHER) {
                    continue;
                }

                String prefix = interactionType.getPrefix();

                if (name.startsWith(prefix)) {
                    throw new IllegalArgumentException(
                            "The interactor's name must not start with any of the reserved prefixes. ("
                                    + prefix + ")");
                }
            }

            return true;
        };
    }

    @Override
    @Unmodifiable
    public Collection<UserInteractor> getInteractors() {
        return prefixedNameToInteractor.values();
    }

    /**
     * Gets the interactor registered under the given name, if any.
     *
     * @param prefixedName the name of the command (including its prefix, see
     *        {@link UserInteractionType UserInteractionType}
     * @return the interactor registered under this name, if any
     */
    private Optional<UserInteractor> getInteractor(final String prefixedName) {
        return Optional.ofNullable(prefixedNameToInteractor.get(prefixedName));
    }

    /**
     * Schedules the registered routines.
     * <p>
     * This needs a ready {@link JDA} instance.
     *
     * @param jda a ready JDA instance
     */
    public void scheduleRoutines(JDA jda) {
        routines.forEach(routine -> {
            Runnable command = () -> {
                String routineName = routine.getClass().getSimpleName();
                try {
                    logger.debug("Running routine %s...".formatted(routineName));
                    routine.runRoutine(jda);
                    logger.debug("Finished routine %s.".formatted(routineName));
                } catch (Exception e) {
                    logger.error("Unknown error in routine {}.", routineName, e);
                }
            };

            Routine.Schedule schedule = routine.createSchedule();
            switch (schedule.mode()) {
                case FIXED_RATE -> ROUTINE_SERVICE.scheduleAtFixedRate(command,
                        schedule.initialDuration(), schedule.duration(), schedule.unit());
                case FIXED_DELAY -> ROUTINE_SERVICE.scheduleWithFixedDelay(command,
                        schedule.initialDuration(), schedule.duration(), schedule.unit());
                default -> throw new AssertionError("Unsupported schedule mode");
            }
        });
    }

    @Override
    public void onMessageReceived(final MessageReceivedEvent event) {
        if (event.isFromGuild()) {
            getMessageReceiversSubscribedTo(event.getChannel())
                .forEach(messageReceiver -> messageReceiver.onMessageReceived(event));
        }
    }

    @Override
    public void onMessageUpdate(final MessageUpdateEvent event) {
        if (event.isFromGuild()) {
            getMessageReceiversSubscribedTo(event.getChannel())
                .forEach(messageReceiver -> messageReceiver.onMessageUpdated(event));
        }
    }

    @Override
    public void onMessageDelete(final MessageDeleteEvent event) {
        if (event.isFromGuild()) {
            getMessageReceiversSubscribedTo(event.getChannel())
                .forEach(messageReceiver -> messageReceiver.onMessageDeleted(event));
        }
    }

    private Stream<MessageReceiver> getMessageReceiversSubscribedTo(Channel channel) {
        String channelName = channel.getName();
        return channelNameToMessageReceiver.entrySet()
            .stream()
            .filter(patternAndReceiver -> patternAndReceiver.getKey()
                .matcher(channelName)
                .matches())
            .map(Map.Entry::getValue);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String name = event.getName();

        logger.debug("Received slash command '{}' (#{}) on guild '{}'", name, event.getId(),
                event.getGuild());
        COMMAND_SERVICE.execute(
                () -> requireUserInteractor(UserInteractionType.SLASH_COMMAND.getPrefixedName(name),
                        SlashCommand.class)
                    .onSlashCommand(event));
    }

    @Override
    public void onCommandAutoCompleteInteraction(final CommandAutoCompleteInteractionEvent event) {
        String name = event.getName();

        logger.debug("Received auto completion from command '{}' (#{}) on guild '{}'",
                event.getFullCommandName(), event.getId(), event.getGuild());
        COMMAND_SERVICE.execute(
                () -> requireUserInteractor(UserInteractionType.SLASH_COMMAND.getPrefixedName(name),
                        SlashCommand.class)
                    .onAutoComplete(event));
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        logger.debug("Received button click '{}' (#{}) on guild '{}'", event.getComponentId(),
                event.getId(), event.getGuild());
        COMMAND_SERVICE
            .execute(() -> forwardComponentCommand(event, UserInteractor::onButtonClick));
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        logger.debug("Received entity selection menu event '{}' (#{}) on guild '{}'",
                event.getComponentId(), event.getId(), event.getGuild());
        COMMAND_SERVICE
            .execute(() -> forwardComponentCommand(event, UserInteractor::onEntitySelectSelection));
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        logger.debug("Received string selection menu event '{}' (#{}) on guild '{}'",
                event.getComponentId(), event.getId(), event.getGuild());
        COMMAND_SERVICE
            .execute(() -> forwardComponentCommand(event, UserInteractor::onStringSelectSelection));
    }

    @Override
    public void onModalInteraction(final ModalInteractionEvent event) {
        logger.debug("Received modal event '{}' (#{}) on guild '{}'", event.getModalId(),
                event.getId(), event.getGuild());
        COMMAND_SERVICE.execute(() -> {
            Optional<ComponentId> componentIdOptional =
                    handleParseComponentId(event, event.getModalId());

            if (componentIdOptional.isEmpty()) {
                return;
            }

            ComponentId componentId = componentIdOptional.orElseThrow();

            UserInteractor interactor =
                    requireUserInteractor(componentId.userInteractorName(), UserInteractor.class);
            logger.trace("Routing a modal event with id '{}' back to user interactor '{}'",
                    event.getModalId(), interactor.getName());
            interactor.onModalSubmitted(event, componentId.elements());
        });
    }

    @Override
    public void onMessageContextInteraction(final MessageContextInteractionEvent event) {
        String name = event.getName();

        logger.debug("Received message context command '{}' (#{}) on guild '{}'", name,
                event.getId(), event.getGuild());
        COMMAND_SERVICE.execute(() -> requireUserInteractor(
                UserInteractionType.MESSAGE_CONTEXT_COMMAND.getPrefixedName(name),
                MessageContextCommand.class)
            .onMessageContext(event));
    }

    @Override
    public void onUserContextInteraction(final UserContextInteractionEvent event) {
        String name = event.getName();

        logger.debug("Received user context command '{}' (#{}) on guild '{}'", name, event.getId(),
                event.getGuild());
        COMMAND_SERVICE.execute(() -> requireUserInteractor(
                UserInteractionType.USER_CONTEXT_COMMAND.getPrefixedName(name),
                UserContextCommand.class)
            .onUserContext(event));
    }

    /**
     * Returns the {@link ComponentId} instace of the given componentId. If the component has
     * expired, the user gets a message stating that.
     *
     * @param event the {@link IReplyCallback event} to reply to
     * @param componentId the component's ID
     * @return the relating {@link ComponentId}
     */
    public Optional<ComponentId> handleParseComponentId(IReplyCallback event, String componentId) {
        Optional<ComponentId> componentIdOpt;
        try {
            componentIdOpt = componentIdParser.parse(componentId);
        } catch (InvalidComponentIdFormatException | IllegalArgumentException e) {
            logger.error(
                    """
                            Unable to route event (#{}) back to its corresponding {}}.
                            The component ID was in an unexpected format. All button and menu events have to use a component ID created in a specific format
                            (refer to the documentation of {}}). Component ID was: {}
                            {}
                            """,
                    event.getId(), UserInteractor.class.getSimpleName(),
                    UserInteractor.class.getSimpleName(), componentId, e);
            // Unable to forward, simply fade out the event
            return Optional.empty();
        }

        if (componentIdOpt.isEmpty()) {
            logger.warn("The event (#{}) has an expired component ID, which was: {}.",
                    event.getId(), componentId);
            event.reply("Sorry, but this event has expired. You can not use it anymore.")
                .setEphemeral(true)
                .queue();
        }

        return componentIdOpt;
    }

    /**
     * Forwards the given component event to the associated user interactor.
     * <p>
     * An example call might look like:
     *
     * <pre>
     * {@code
     * forwardComponentCommand(event, UserInteractor::onSelectionMenu);
     * }
     * </pre>
     *
     * @param event the component event that should be forwarded
     * @param interactorArgumentConsumer the action to trigger on the associated user interactor,
     *        providing the event and list of arguments for consumption
     * @param <T> the type of the component interaction that should be forwarded
     */
    private <T extends ComponentInteraction> void forwardComponentCommand(T event,
            TriConsumer<? super UserInteractor, ? super T, ? super List<String>> interactorArgumentConsumer) {

        Optional<ComponentId> componentIdOptional =
                handleParseComponentId(event, event.getComponentId());

        if (componentIdOptional.isEmpty()) {
            return;
        }

        ComponentId componentId = componentIdOptional.orElseThrow();

        UserInteractor interactor =
                requireUserInteractor(componentId.userInteractorName(), UserInteractor.class);
        logger.trace("Routing a component event with id '{}' back to user interactor '{}'",
                event.getComponentId(), interactor.getName());
        interactorArgumentConsumer.accept(interactor, event, componentId.elements());
    }

    /**
     * Gets the given user interactor by its full name, requires it exists and is of the given type.
     *
     * @param prefixedName the prefixed name of the interactor
     * @param typeToken a token of the type to expect
     * @param <T> the type to expect the user interactor to be of
     * @return the user interactor with the given name
     */
    private <T extends UserInteractor> T requireUserInteractor(String prefixedName,
            Class<T> typeToken) {
        UserInteractor userInteractor =
                getInteractor(prefixedName).orElseThrow(() -> new IllegalArgumentException(
                        "There is no interactor with name " + prefixedName));

        if (!typeToken.isInstance(userInteractor)) {
            throw new IllegalArgumentException(
                    "The interactor %s is not of the expected type %s, but instead %s".formatted(
                            prefixedName, typeToken.getSimpleName(),
                            prefixedName.getClass().getSimpleName()));
        }

        return typeToken.cast(userInteractor);
    }


    @SuppressWarnings("EmptyMethod")
    private static void onComponentIdRemoved(ComponentId componentId) {
        // NOTE As of now, we do not act on this event, but we could use it
        // in the future to, for example, disable buttons or delete the associated message
    }

    /**
     * Extension of {@link java.util.function.BiConsumer} but for 3 elements.
     * <p>
     * Represents an operation that accepts three input arguments and returns no result. This is the
     * three-arity specialization of {@link java.util.function.Consumer}. Unlike most other
     * functional interfaces, TriConsumer is expected to operate via side effects.
     *
     * @param <A> the type of the first argument to the operation
     * @param <B> the type of the second argument to the operation
     * @param <C> the type of the third argument to the operation
     */
    @FunctionalInterface
    private interface TriConsumer<A, B, C> {
        /**
         * Performs this operation on the given arguments.
         *
         * @param first the first input argument
         * @param second the second input argument
         * @param third the third input argument
         */
        void accept(A first, B second, C third);
    }
}
