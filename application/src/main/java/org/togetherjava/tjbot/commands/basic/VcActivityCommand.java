package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


/**
 * Implements the {@code vc-activity} command. Creates VC activities.
 *
 * <p>
 * An VC activity is a so called "Embedded application". To explain it extremely simple, interactive
 * screensharing. <br />
 * To give you a better idea of what it actually is, think about games like Poker, Chess, or
 * watching YouTube Together using one of these. <br />
 */
public final class VcActivityCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(VcActivityCommand.class);

    private static final String APPLICATION_SUBCOMMAND = "application";

    private static final String APPLICATION_OPTION = "application";
    private static final String ID_OPTION = "id";

    private static final String MAX_USES_OPTION = "max-uses";
    private static final String MAX_AGE_OPTION = "max-age";

    private static final long MAX_AGE_DAYS_LIMIT = 7;
    private static final long MAX_USES_LIMIT = 100;

    public static final String POKER_NAME = "Poker";
    public static final String BETRAYAL_IO_NAME = "Betrayal.io";
    public static final String FISHINGTON_IO_NAME = "Fishington.io";
    public static final String SPELLCAST_NAME = "Spellcast";
    public static final String DOODLECREW_NAME = "Doodlecrew";
    public static final String WORDSNACK_NAME = "Wordsnack";
    public static final String LETTERTILE_NAME = "Lettertile";


    private static final List<Command.Choice> VC_APPLICATIONS =
            List.of(new Command.Choice(POKER_NAME, POKER_NAME),
                    new Command.Choice(BETRAYAL_IO_NAME, BETRAYAL_IO_NAME),
                    new Command.Choice(FISHINGTON_IO_NAME, FISHINGTON_IO_NAME),
                    new Command.Choice(SPELLCAST_NAME, SPELLCAST_NAME),
                    new Command.Choice(DOODLECREW_NAME, DOODLECREW_NAME),
                    new Command.Choice(WORDSNACK_NAME, WORDSNACK_NAME),
                    new Command.Choice(LETTERTILE_NAME, LETTERTILE_NAME));

    /**
     * List comes from <a href="https://github.com/DV8FromTheWorld/JDA/pull/1628">the "Implement
     * invite targets" PR on JDA</a>. There is no official list from Discord themselves, so this is
     * our best bet.
     */
    private static final Map<String, String> VC_APPLICATION_TO_ID =
            Map.of(POKER_NAME, "755827207812677713", BETRAYAL_IO_NAME, "773336526917861400",
                    FISHINGTON_IO_NAME, "814288819477020702", SPELLCAST_NAME, "852509694341283871",
                    DOODLECREW_NAME, "878067389634314250", WORDSNACK_NAME, "879863976006127627",
                    LETTERTILE_NAME, "879863686565621790");

    private static final List<OptionData> inviteOptions = List.of(new OptionData(OptionType.INTEGER,
            MAX_USES_OPTION,
            "How many times this invite can be used, 0 infinite (default) - %d being the highest."
                .formatted(MAX_USES_LIMIT),
            false).setRequiredRange(0, MAX_USES_LIMIT),
            new OptionData(OptionType.INTEGER, MAX_AGE_OPTION,
                    "How long, in days this activity can be used before it expires, 0 (No expiry), Max is %d days."
                        .formatted(MAX_AGE_DAYS_LIMIT),
                    false).setRequiredRange(0, MAX_AGE_DAYS_LIMIT));

    /**
     * Constructs an instance
     *
     * @see VcActivityCommand
     */
    public VcActivityCommand() {
        super("vc-activity",
                "Starts a VC activity (you need to be in an voice channel to run this command)",
                CommandVisibility.GUILD);


        SubcommandData applicationSubCommand =
                new SubcommandData(APPLICATION_SUBCOMMAND, "Choose an application from our list")
                    .addOptions(new OptionData(OptionType.STRING, APPLICATION_OPTION,
                            "the application", true).addChoices(VC_APPLICATIONS))
                    .addOptions(inviteOptions);


        SubcommandData idSubCommand =
                new SubcommandData("id", "specify the ID for the application manually")
                    .addOption(OptionType.STRING, ID_OPTION, "the ID of the application", true)
                    .addOptions(inviteOptions);


        getData().addSubcommands(applicationSubCommand, idSubCommand);
    }


    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        Member member = Objects.requireNonNull(event.getMember(), "member is null");
        GuildVoiceState voiceState = Objects.requireNonNull(member.getVoiceState(),
                "Voicestates aren't being cached, check the JDABuilder");

        if (!voiceState.inAudioChannel()) {
            event.reply("You need to be in a voicechannel to run this command!")
                .setEphemeral(true)
                .queue();

            return;
        }

        AudioChannel audioChannel = Objects.requireNonNull(voiceState.getChannel());

        if (!(audioChannel instanceof VoiceChannel voiceChannel)) {
            event.reply("You've to be in a voicechannel, not a stage channel!")
                .setEphemeral(true)
                .queue();
            return;
        }

        Member selfMember = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!selfMember.hasPermission(Permission.CREATE_INSTANT_INVITE)) {
            event.reply("The bot needs the create instant invite permission!")
                .setEphemeral(true)
                .queue();
            logger.warn("Bot doesn't have the create instant permission");
            return;
        }

        OptionMapping applicationOption = event.getOption(APPLICATION_OPTION);
        OptionMapping idOption = event.getOption(ID_OPTION);

        OptionMapping maxUsesOption = event.getOption(MAX_USES_OPTION);
        OptionMapping maxAgeOption = event.getOption(MAX_AGE_OPTION);

        String applicationId;
        String applicationName;
        Integer maxUses = requireIntOptionIfPresent(maxUsesOption);
        Integer maxAgeDays = requireIntOptionIfPresent(maxAgeOption);

        if (applicationOption != null) {
            applicationName = applicationOption.getAsString();
            applicationId = VC_APPLICATION_TO_ID.get(applicationName);
        } else {
            applicationId = idOption.getAsString();

            applicationName =
                    getKeyByValue(VC_APPLICATION_TO_ID, applicationId).orElse("an activity");
        }

        handleSubcommand(event, voiceChannel, applicationId, maxUses, maxAgeDays, applicationName);
    }


    private static <K, V> @NotNull Optional<K> getKeyByValue(@NotNull Map<K, V> map,
            @NotNull V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (value.equals(entry.getKey())) {
                return Optional.of(entry.getKey());
            }
        }

        return Optional.empty();
    }

    private static void handleSubcommand(@NotNull SlashCommandInteractionEvent event,
            @NotNull VoiceChannel voiceChannel, @NotNull String applicationId,
            @Nullable Integer maxUses, @Nullable Integer maxAgeDays,
            @NotNull String applicationName) {


        voiceChannel.createInvite()
            .setTargetApplication(applicationId)
            .setMaxUses(maxUses)
            .setMaxAge(maxAgeDays == null ? null
                    : Math.toIntExact(TimeUnit.DAYS.toSeconds(maxAgeDays)))
            .flatMap(invite -> replyInvite(event, invite, applicationName))
            .queue(null, throwable -> handleErrors(event, throwable));

    }

    private static @NotNull ReplyCallbackAction replyInvite(
            @NotNull SlashCommandInteractionEvent event, @NotNull Invite invite,
            @NotNull String applicationName) {
        return event.reply("""
                %s wants to start %s.
                Feel free to join by clicking %s , enjoy!
                If it says the activity ended, click on the URL instead.
                 """.formatted(event.getUser().getAsTag(), applicationName, invite.getUrl()));
    }

    private static void handleErrors(@NotNull SlashCommandInteractionEvent event,
            @Nullable Throwable throwable) {
        event.reply("Something went wrong :/").queue();
        logger.warn("Something went wrong in the VcActivityCommand", throwable);
    }

    /**
     * Interprets the given option as integer. Throws if the option is not an integer.
     *
     * @param option the option that contains the integer to extract, or null if not present
     * @return the extracted integer if present, null otherwise
     **/
    @Contract("null -> null")
    private static @Nullable Integer requireIntOptionIfPresent(@Nullable OptionMapping option) {

        return option == null ? null : Math.toIntExact(option.getAsLong());

    }
}
