package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import javax.annotation.Nullable;

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

    public static final String WATCH_TOGETHER_NAME = "Watch Together";
    public static final String POKER_NAME = "Poker Night";
    public static final String CHESS_NAME = "Chess In The Park";
    public static final String SPELLCAST_NAME = "SpellCast";
    public static final String DOODLE_CREW_NAME = "Doodle Crew";
    public static final String WORD_SNACKS_NAME = "Word Snacks";
    public static final String LETTER_LEAGUE_NAME = "Letter League";
    public static final String CHECKERS_NAME = "Checkers In The Park";
    public static final String BLAZING_EIGHTS_NAME = "Blazing 8s";
    public static final String SKETCH_HEADS_NAME = "Sketch Heads";
    public static final String PUTT_PARTY_NAME = "Putt Party";
    public static final String LAND_IO_NAME = "Land-io";
    public static final String BOBBLE_LEAGUE_NAME = "Bobble League";
    public static final String ASK_AWAY_NAME = "Ask Away";
    public static final String KNOW_WHAT_I_MEME_NAME = "Know What I Meme";

    private static final List<Command.Choice> VC_APPLICATIONS = List.of(
            new Command.Choice(WATCH_TOGETHER_NAME, WATCH_TOGETHER_NAME),
            new Command.Choice(POKER_NAME, POKER_NAME), new Command.Choice(CHESS_NAME, CHESS_NAME),
            new Command.Choice(SPELLCAST_NAME, SPELLCAST_NAME),
            new Command.Choice(DOODLE_CREW_NAME, DOODLE_CREW_NAME),
            new Command.Choice(WORD_SNACKS_NAME, WORD_SNACKS_NAME),
            new Command.Choice(LETTER_LEAGUE_NAME, LETTER_LEAGUE_NAME),
            new Command.Choice(CHECKERS_NAME, CHECKERS_NAME),
            new Command.Choice(BLAZING_EIGHTS_NAME, BLAZING_EIGHTS_NAME),
            new Command.Choice(SKETCH_HEADS_NAME, SKETCH_HEADS_NAME),
            new Command.Choice(PUTT_PARTY_NAME, PUTT_PARTY_NAME),
            new Command.Choice(LAND_IO_NAME, LAND_IO_NAME),
            new Command.Choice(BOBBLE_LEAGUE_NAME, BOBBLE_LEAGUE_NAME),
            new Command.Choice(ASK_AWAY_NAME, ASK_AWAY_NAME),
            new Command.Choice(KNOW_WHAT_I_MEME_NAME, KNOW_WHAT_I_MEME_NAME));

    /**
     * List comes from
     * <a href="https://gist.github.com/GeneralSadaf/42d91a2b6a93a7db7a39208f2d8b53ad">this public
     * list obtained by GeneralSadaf.</a>. There is no official list from Discord themselves, so
     * this is our best bet.
     */
    private static final Map<String, String> VC_APPLICATION_TO_ID =
            Map.ofEntries(Map.entry(WATCH_TOGETHER_NAME, "880218394199220334"),
                    Map.entry(POKER_NAME, "755827207812677713"),
                    Map.entry(CHESS_NAME, "832012586023256104"),
                    Map.entry(SPELLCAST_NAME, "852509694341283871"),
                    Map.entry(DOODLE_CREW_NAME, "878067389634314250"),
                    Map.entry(WORD_SNACKS_NAME, "879863976006127627"),
                    Map.entry(LETTER_LEAGUE_NAME, "879863686565621790"),
                    Map.entry(CHECKERS_NAME, "832013003968348200"),
                    Map.entry(BLAZING_EIGHTS_NAME, "832025144389533716"),
                    Map.entry(SKETCH_HEADS_NAME, "902271654783242291"),
                    Map.entry(PUTT_PARTY_NAME, "945737671223947305"),
                    Map.entry(LAND_IO_NAME, "903769130790969345"),
                    Map.entry(BOBBLE_LEAGUE_NAME, "947957217959759964"),
                    Map.entry(ASK_AWAY_NAME, "976052223358406656"),
                    Map.entry(KNOW_WHAT_I_MEME_NAME, "950505761862189096"));

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
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        Member member = Objects.requireNonNull(event.getMember(), "member is null");
        GuildVoiceState voiceState = Objects.requireNonNull(member.getVoiceState(),
                "Voicestates aren't being cached, check the JDABuilder");

        if (!voiceState.inAudioChannel()
                || !(voiceState.getChannel() instanceof VoiceChannel voiceChannel)) {
            event.reply("You need to be in a voicechannel to run this command!")
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

    private static <K, V> Optional<K> getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (value.equals(entry.getKey())) {
                return Optional.of(entry.getKey());
            }
        }

        return Optional.empty();
    }

    private static void handleSubcommand(SlashCommandInteractionEvent event,
            VoiceChannel voiceChannel, String applicationId, @Nullable Integer maxUses,
            @Nullable Integer maxAgeDays, String applicationName) {

        voiceChannel.createInvite()
            .setTargetApplication(applicationId)
            .setMaxUses(maxUses)
            .setMaxAge(maxAgeDays == null ? null
                    : Math.toIntExact(TimeUnit.DAYS.toSeconds(maxAgeDays)))
            .flatMap(invite -> replyInvite(event, invite, applicationName))
            .queue(null, throwable -> handleErrors(event, throwable));

    }

    private static ReplyCallbackAction replyInvite(SlashCommandInteractionEvent event,
            Invite invite, String applicationName) {
        return event.reply("""
                %s wants to start %s.
                Feel free to join by clicking %s , enjoy!
                If it says the activity ended, click on the URL instead.
                 """.formatted(event.getUser().getAsTag(), applicationName, invite.getUrl()));
    }

    private static void handleErrors(SlashCommandInteractionEvent event,
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
    @Nullable
    private static Integer requireIntOptionIfPresent(@Nullable OptionMapping option) {

        return option == null ? null : Math.toIntExact(option.getAsLong());

    }
}
