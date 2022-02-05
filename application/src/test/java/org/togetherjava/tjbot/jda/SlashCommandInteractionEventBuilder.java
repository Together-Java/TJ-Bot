package org.togetherjava.tjbot.jda;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.interactions.command.SlashCommandInteractionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.togetherjava.tjbot.commands.SlashCommand;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Builder to create slash command events that can be used for example with
 * {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}.
 * <p>
 * Create instances of this class by using {@link JdaTester#createSlashCommandEvent(SlashCommand)}.
 * <p>
 * Among other Discord related things, the builder optionally accepts a subcommand
 * ({@link #subcommand(String)}) and options ({@link #option(String, String)}). An already set
 * subcommand can be cleared by using {@link #subcommand(String)} with {@code null}, options are
 * cleared using {@link #clearOptions()}.
 * <p>
 * Refer to the following examples: the command {@code ping} is build using
 *
 * <pre>
 * {@code
 * // /ping
 * jdaTester.createSlashCommandInteractionEvent(command).build();
 *
 * // /days start:10.01.2021 end:13.01.2021
 * jdaTester.createSlashCommandInteractionEvent(command)
 *   .option("start", "10.01.2021")
 *   .option("end", "13.01.2021")
 *   .build();
 *
 * // /db put key:foo value:bar
 * jdaTester.createSlashCommandInteractionEvent(command)
 *   .subcommand("put")
 *   .option("key", "foo")
 *   .option("value", "bar")
 *   .build();
 * }
 * </pre>
 */
@SuppressWarnings("ClassWithTooManyFields")
public final class SlashCommandInteractionEventBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JDAImpl jda;
    private final UnaryOperator<SlashCommandInteractionEvent> mockOperator;
    private String token;
    private String channelId;
    private String applicationId;
    private String guildId;
    private String userId;
    private SlashCommand command;
    private final Map<String, Option> nameToOption = new HashMap<>();
    private String subcommand;

    SlashCommandInteractionEventBuilder(@NotNull JDAImpl jda,
            UnaryOperator<SlashCommandInteractionEvent> mockOperator) {
        this.jda = jda;
        this.mockOperator = mockOperator;
    }

    /**
     * Sets the given option, overriding an existing value under the same name.
     * <p>
     * If {@link #subcommand(String)} is set, this option will be interpreted as option to the
     * subcommand.
     * <p>
     * Use {@link #clearOptions()} to clear any set options.
     *
     * @param name the name of the option
     * @param value the value of the option
     * @return this builder instance for chaining
     * @throws IllegalArgumentException if the option does not exist in the corresponding command,
     *         as specified by its {@link SlashCommand#getData()}
     */
    public @NotNull SlashCommandInteractionEventBuilder option(@NotNull String name,
            @NotNull String value) {
        // TODO Also add overloads for other types
        requireOption(name, OptionType.STRING);
        nameToOption.put(name, new Option(name, value, OptionType.STRING));
        return this;
    }

    /**
     * Clears all options previously set with {@link #option(String, String)}.
     *
     * @return this builder instance for chaining
     */
    public @NotNull SlashCommandInteractionEventBuilder clearOptions() {
        nameToOption.clear();
        return this;
    }

    /**
     * Sets the given subcommand. Call with {@code null} to clear any previously set subcommand.
     * <p>
     * Once set, all options set by {@link #option(String, String)} will be interpreted as options
     * to this subcommand.
     *
     * @param subcommand the name of the subcommand or {@code null} to clear it
     * @return this builder instance for chaining
     * @throws IllegalArgumentException if the subcommand does not exist in the corresponding
     *         command, as specified by its {@link SlashCommand#getData()}
     */
    public @NotNull SlashCommandInteractionEventBuilder subcommand(@Nullable String subcommand) {
        if (subcommand != null) {
            requireSubcommand(subcommand);
        }

        this.subcommand = subcommand;
        return this;
    }

    @NotNull
    SlashCommandInteractionEventBuilder command(@NotNull SlashCommand command) {
        this.command = command;
        return this;
    }

    @NotNull
    SlashCommandInteractionEventBuilder channelId(@NotNull String channelId) {
        this.channelId = channelId;
        return this;
    }

    @NotNull
    SlashCommandInteractionEventBuilder token(@NotNull String token) {
        this.token = token;
        return this;
    }

    @NotNull
    SlashCommandInteractionEventBuilder applicationId(@NotNull String applicationId) {
        this.applicationId = applicationId;
        return this;
    }

    @NotNull
    SlashCommandInteractionEventBuilder guildId(@NotNull String guildId) {
        this.guildId = guildId;
        return this;
    }

    @NotNull
    SlashCommandInteractionEventBuilder userId(@NotNull String userId) {
        this.userId = userId;
        return this;
    }

    /**
     * Builds an instance of a slash command event, corresponding to the current configuration of
     * the builder.
     *
     * @return the created slash command instance
     */
    public @NotNull SlashCommandInteractionEvent build() {
        PayloadSlashCommand event = createEvent();

        String json;
        try {
            json = JSON.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }

        return mockOperator.apply(new SlashCommandInteractionEvent(jda, 1,
                new SlashCommandInteractionImpl(jda, DataObject.fromJson(json))));
    }

    private @NotNull PayloadSlashCommand createEvent() {
        // TODO Validate that required options are set, check that subcommand is given if the
        // command has one
        // TODO Make as much of this configurable as needed
        PayloadSlashCommandUser user = new PayloadSlashCommandUser(0, userId,
                "286b894dc74634202d251d591f63537d", "Test-User", "3452");
        PayloadSlashCommandMember member =
                new PayloadSlashCommandMember(null, null, "2021-09-07T18:25:16.615000+00:00",
                        "1099511627775", List.of(), false, false, false, null, false, user);

        List<PayloadSlashCommandOption> options;
        if (subcommand == null) {
            options = extractOptionsOrNull(nameToOption);
        } else {
            options = List.of(new PayloadSlashCommandOption(subcommand, 1, null,
                    extractOptionsOrNull(nameToOption)));
        }
        PayloadSlashCommandData data =
                new PayloadSlashCommandData(command.getName(), "1", 1, options);

        return new PayloadSlashCommand(guildId, "897425767397466123", 2, 1, channelId,
                applicationId, token, member, data);
    }

    private static @Nullable List<PayloadSlashCommandOption> extractOptionsOrNull(
            @NotNull Map<String, Option> nameToOption) {
        if (nameToOption.isEmpty()) {
            return null;
        }
        return nameToOption.values()
            .stream()
            .map(option -> new PayloadSlashCommandOption(option.name(), option.type.ordinal(),
                    option.value(), null))
            .toList();
    }

    @SuppressWarnings("UnusedReturnValue")
    private @NotNull OptionData requireOption(@NotNull String name, @NotNull OptionType type) {
        List<OptionData> options = subcommand == null ? command.getData().getOptions()
                : requireSubcommand(subcommand).getOptions();

        Supplier<String> exceptionMessageSupplier = () -> subcommand == null
                ? "The command '%s' does not support an option with name '%s' and type '%s'"
                    .formatted(command.getName(), name, type)
                : "The subcommand '%s' of command '%s' does not support an option with name '%s' and type '%s'"
                    .formatted(command.getName(), subcommand, name, type);

        return options.stream()
            .filter(option -> name.equals(option.getName()))
            .filter(option -> type == option.getType())
            .findAny()
            .orElseThrow(() -> {
                throw new IllegalArgumentException(exceptionMessageSupplier.get());
            });
    }

    private @NotNull SubcommandData requireSubcommand(@NotNull String name) {
        return command.getData()
            .getSubcommands()
            .stream()
            .filter(subcommandData -> name.equals(subcommandData.getName()))
            .findAny()
            .orElseThrow(() -> {
                throw new IllegalArgumentException(
                        "The command '%s' does not support a subcommand with name '%s'"
                            .formatted(command.getName(), name));
            });
    }

    private record Option(@NotNull String name, @NotNull String value, @NotNull OptionType type) {
    }
}
