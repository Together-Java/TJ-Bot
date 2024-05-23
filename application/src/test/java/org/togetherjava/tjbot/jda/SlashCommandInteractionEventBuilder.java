package org.togetherjava.tjbot.jda;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.interactions.command.SlashCommandInteractionImpl;

import org.togetherjava.tjbot.features.SlashCommand;
import org.togetherjava.tjbot.jda.payloads.PayloadChannel;
import org.togetherjava.tjbot.jda.payloads.PayloadMember;
import org.togetherjava.tjbot.jda.payloads.PayloadUser;
import org.togetherjava.tjbot.jda.payloads.slashcommand.PayloadSlashCommand;
import org.togetherjava.tjbot.jda.payloads.slashcommand.PayloadSlashCommandData;
import org.togetherjava.tjbot.jda.payloads.slashcommand.PayloadSlashCommandMembers;
import org.togetherjava.tjbot.jda.payloads.slashcommand.PayloadSlashCommandOption;
import org.togetherjava.tjbot.jda.payloads.slashcommand.PayloadSlashCommandResolved;
import org.togetherjava.tjbot.jda.payloads.slashcommand.PayloadSlashCommandUsers;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Builder to create slash command events that can be used for example with
 * {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}.
 * <p>
 * Create instances of this class by using
 * {@link JdaTester#createSlashCommandInteractionEvent(SlashCommand)}.
 * <p>
 * Among other Discord related things, the builder optionally accepts a subcommand
 * ({@link #setSubcommand(String)}) and options ({@link #setOption(String, String)}). An already set
 * subcommand can be cleared by using {@link #setSubcommand(String)} with {@code null}, options are
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
 *     .setOption("start", "10.01.2021")
 *     .setOption("end", "13.01.2021")
 *     .build();
 *
 * // /db put key:foo value:bar
 * jdaTester.createSlashCommandInteractionEvent(command)
 *     .setSubcommand("put")
 *     .setOption("key", "foo")
 *     .setOption("value", "bar")
 *     .build();
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
    private final Map<String, Option<?>> nameToOption = new HashMap<>();
    private String subcommand;
    private Member userWhoTriggered;

    SlashCommandInteractionEventBuilder(JDAImpl jda,
            UnaryOperator<SlashCommandInteractionEvent> mockOperator) {
        this.jda = jda;
        this.mockOperator = mockOperator;
    }

    /**
     * Sets the given option, overriding an existing value under the same name.
     * <p>
     * If {@link #setSubcommand(String)} is set, this option will be interpreted as option to the
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
    public SlashCommandInteractionEventBuilder setOption(String name, String value) {
        putOptionRaw(name, value, OptionType.STRING);
        return this;
    }

    /**
     * Sets the given option, overriding an existing value under the same name.
     * <p>
     * If {@link #setSubcommand(String)} is set, this option will be interpreted as option to the
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
    public SlashCommandInteractionEventBuilder setOption(String name, long value) {
        putOptionRaw(name, value, OptionType.INTEGER);
        return this;
    }

    /**
     * Sets the given option, overriding an existing value under the same name.
     * <p>
     * If {@link #setSubcommand(String)} is set, this option will be interpreted as option to the
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
    public SlashCommandInteractionEventBuilder setOption(String name, User value) {
        putOptionRaw(name, value, OptionType.USER);
        return this;
    }

    /**
     * Sets the given option, overriding an existing value under the same name.
     * <p>
     * If {@link #setSubcommand(String)} is set, this option will be interpreted as option to the
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
    public SlashCommandInteractionEventBuilder setOption(String name, Member value) {
        putOptionRaw(name, value, OptionType.USER);
        return this;
    }

    /**
     * Clears all options previously set with {@link #setOption(String, String)}.
     *
     * @return this builder instance for chaining
     */
    public SlashCommandInteractionEventBuilder clearOptions() {
        nameToOption.clear();
        return this;
    }

    /**
     * Sets the given subcommand. Call with {@code null} to clear any previously set subcommand.
     * <p>
     * Once set, all options set by {@link #setOption(String, String)} will be interpreted as
     * options to this subcommand.
     *
     * @param subcommand the name of the subcommand or {@code null} to clear it
     * @return this builder instance for chaining
     * @throws IllegalArgumentException if the subcommand does not exist in the corresponding
     *         command, as specified by its {@link SlashCommand#getData()}
     */
    public SlashCommandInteractionEventBuilder setSubcommand(@Nullable String subcommand) {
        if (subcommand != null) {
            requireSubcommand(subcommand);
        }

        this.subcommand = subcommand;
        return this;
    }

    /**
     * Sets the user who triggered the slash command.
     *
     * @param userWhoTriggered the user who triggered the slash command
     * @return this builder instance for chaining
     */
    public SlashCommandInteractionEventBuilder setUserWhoTriggered(Member userWhoTriggered) {
        this.userWhoTriggered = userWhoTriggered;
        return this;
    }

    SlashCommandInteractionEventBuilder setCommand(SlashCommand command) {
        this.command = command;
        return this;
    }

    SlashCommandInteractionEventBuilder setChannelId(String channelId) {
        this.channelId = channelId;
        return this;
    }

    SlashCommandInteractionEventBuilder setToken(String token) {
        this.token = token;
        return this;
    }

    SlashCommandInteractionEventBuilder setApplicationId(String applicationId) {
        this.applicationId = applicationId;
        return this;
    }

    SlashCommandInteractionEventBuilder setGuildId(String guildId) {
        this.guildId = guildId;
        return this;
    }

    SlashCommandInteractionEventBuilder setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    /**
     * Builds an instance of a slash command event, corresponding to the current configuration of
     * the builder.
     *
     * @return the created slash command instance
     */
    public SlashCommandInteractionEvent build() {
        PayloadSlashCommand event = createEvent();

        String json;
        try {
            json = JSON.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }

        return spySlashCommandEvent(json);
    }

    private SlashCommandInteractionEvent spySlashCommandEvent(String jsonData) {
        SlashCommandInteractionEvent event = spy(new SlashCommandInteractionEvent(jda, 0,
                new SlashCommandInteractionImpl(jda, DataObject.fromJson(jsonData))));
        event = mockOperator.apply(event);

        when(event.getMember()).thenReturn(userWhoTriggered);
        User asUser = userWhoTriggered.getUser();
        when(event.getUser()).thenReturn(asUser);

        return event;
    }

    private PayloadSlashCommand createEvent() {
        // TODO Validate that required options are set, check that subcommand is given if the
        // command has one
        // TODO Make as much of this configurable as needed
        PayloadUser user =
                new PayloadUser(false, 0, userId, "286b894dc74634202d251d591f63537d", "Test-User");
        PayloadMember member = new PayloadMember(null, null, "2021-09-07T18:25:16.615000+00:00",
                "1099511627775", List.of(), false, false, false, null, false, user);
        PayloadChannel channel = new PayloadChannel(channelId, 1);

        List<PayloadSlashCommandOption> options;
        if (subcommand == null) {
            options = extractOptionsOrNull(nameToOption);
        } else {
            options = List.of(new PayloadSlashCommandOption(subcommand, 1, null,
                    extractOptionsOrNull(nameToOption)));
        }
        PayloadSlashCommandData data = new PayloadSlashCommandData(command.getName(), "1", 1,
                options, extractResolvedOrNull(nameToOption));

        return new PayloadSlashCommand(guildId, "897425767397466123", 2, 1, applicationId, token,
                member, channel, data);
    }

    @Nullable
    private static List<PayloadSlashCommandOption> extractOptionsOrNull(
            Map<String, Option<?>> nameToOption) {
        if (nameToOption.isEmpty()) {
            return null;
        }
        return nameToOption.values()
            .stream()
            .map(option -> new PayloadSlashCommandOption(option.name(), option.type().ordinal(),
                    serializeOptionValue(option.value(), option.type()), null))
            .toList();
    }

    private static <T> String serializeOptionValue(T value, OptionType type) {
        if (type == OptionType.STRING) {
            return (String) value;
        } else if (type == OptionType.INTEGER) {
            if (value instanceof Long) {
                return value.toString();
            }

            throw new IllegalArgumentException(
                    "Expected a long, since the type was set to INTEGER. But got '%s'"
                        .formatted(value.getClass()));
        } else if (type == OptionType.USER) {
            if (value instanceof User user) {
                return user.getId();
            } else if (value instanceof Member member) {
                return member.getId();
            }

            throw new IllegalArgumentException(
                    "Expected a user or member, since the type was set to USER. But got '%s'"
                        .formatted(value.getClass()));
        }

        throw new IllegalArgumentException(
                "Unsupported type ('%s'), can not deserialize yet. Value is of type '%s'"
                    .formatted(type, value.getClass()));
    }

    @Nullable
    private static PayloadSlashCommandResolved extractResolvedOrNull(
            Map<String, Option<?>> nameToOption) {
        PayloadSlashCommandUsers users = extractUsersOrNull(nameToOption);
        PayloadSlashCommandMembers members = extractMembersOrNull(nameToOption);

        if (users == null && members == null) {
            return null;
        }

        return new PayloadSlashCommandResolved(members, users);
    }

    @Nullable
    private static PayloadSlashCommandUsers extractUsersOrNull(
            Map<String, Option<?>> nameToOption) {
        Map<String, PayloadUser> idToUser = nameToOption.values()
            .stream()
            .filter(option -> option.type == OptionType.USER)
            .map(Option::value)
            .map(userOrMember -> {
                if (userOrMember instanceof Member member) {
                    return member.getUser();
                }
                return (User) userOrMember;
            })
            .collect(Collectors.toMap(User::getId, PayloadUser::of));

        return idToUser.isEmpty() ? null : new PayloadSlashCommandUsers(idToUser);
    }

    @Nullable
    private static PayloadSlashCommandMembers extractMembersOrNull(
            Map<String, Option<?>> nameToOption) {
        Map<String, PayloadMember> idToMember = nameToOption.values()
            .stream()
            .filter(option -> option.type == OptionType.USER)
            .map(Option::value)
            .filter(Member.class::isInstance)
            .map(Member.class::cast)
            .collect(Collectors.toMap(Member::getId, PayloadMember::of));

        return idToMember.isEmpty() ? null : new PayloadSlashCommandMembers(idToMember);
    }

    private <T> void putOptionRaw(String name, T value, OptionType type) {
        requireOption(name, type);
        nameToOption.put(name, new Option<>(name, value, type));
    }

    @SuppressWarnings("UnusedReturnValue")
    private OptionData requireOption(String name, OptionType type) {
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

    private SubcommandData requireSubcommand(String name) {
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

    private record Option<T>(String name, T value, OptionType type) {
    }
}
