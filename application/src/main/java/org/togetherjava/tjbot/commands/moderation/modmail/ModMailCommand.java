package org.togetherjava.tjbot.commands.moderation.modmail;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.utils.DiscordClientAction;
import org.togetherjava.tjbot.config.Config;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;


/**
 * Implements the /modmail command, which allows users to contact a moderator within the server or
 * even if not part of the server anymore, e.g. after a ban.
 * <p>
 * Therefore, the user DMs the bot and the message are forwarded to moderators in a dedicated
 * channel.
 * <p>
 * Actions are then logged to a dedicated channel, given by
 * {@link Config#getModAuditLogChannelPattern()}.
 */

public final class ModMailCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ModMailCommand.class);
    private static final String COMMAND_NAME = "modmail";
    private static final String OPTION_MESSAGE = "message";
    private static final String OPTION_STAY_ANONYMOUS = "stay-anonymous";
    private static final String OPTION_SERVER_GUILD = "server-guild";
    private static final int COOLDOWN_DURATION_VALUE = 30;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;
    private static final Color AMBIENT_COLOR = Color.black;
    private final Cache<Long, Instant> authorIdToLastCommandInvocation = createCooldownCache();
    private final Predicate<String> modMailChannelNamePredicate;


    /**
     * Creates a new instance.
     *
     * @param jda the JDA instance to use to retrieve guildCache
     * @param config the config to use for this
     */
    public ModMailCommand(JDA jda, Config config) {
        super(COMMAND_NAME, "Send a message to the moderators of the selected guild",
                CommandVisibility.GLOBAL);

        OptionData messageOption = new OptionData(OptionType.STRING, OPTION_MESSAGE,
                "What do you want to tell them?", true);
        OptionData guildOption = new OptionData(OptionType.STRING, OPTION_SERVER_GUILD,
                "Your server guild ID", true);
        OptionData anonymousOption = new OptionData(OptionType.BOOLEAN, OPTION_STAY_ANONYMOUS,
                "if set, your name is hidden", true);

        List<Command.Choice> choices = jda.getGuildCache()
            .stream()
            .map(guild -> new Command.Choice(guild.getName(), guild.getIdLong()))
            .toList();

        guildOption.addChoices(choices);

        getData().addOptions(messageOption, guildOption, anonymousOption);

        modMailChannelNamePredicate =
                Pattern.compile(config.getModMailChannelPattern()).asMatchPredicate();
    }

    private Cache<Long, Instant> createCooldownCache() {
        return Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
            .build();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        event.deferReply().setEphemeral(true).queue();

        if (isChannelOnCooldown(userId)) {
            event.reply("Can only be used once per %s minutes.".formatted(COOLDOWN_DURATION_VALUE))
                .setEphemeral(true)
                .queue();
            return;
        }
        authorIdToLastCommandInvocation.put(userId, Instant.now());

        Optional<TextChannel> modMailAuditLog = getChannel(event);
        if (modMailAuditLog.isEmpty()) {
            logger.warn("Cannot find the designated modmail channel in this server.");
            return;
        }

        String userMessage = event.getOption(OPTION_MESSAGE).getAsString();
        boolean wantsToStayAnonymous = event.getOption(OPTION_STAY_ANONYMOUS).getAsBoolean();
        String user = event.getUser().getAsMention();
        MessageCreateAction message = modMailAuditLog.orElseThrow()
            .sendMessageEmbeds(createModMailMessage(user, userMessage))
            .addActionRow(DiscordClientAction.General.USER.asLinkButton("Click to see profile!",
                    String.valueOf(userId)));

        if (wantsToStayAnonymous) {
            user = "Anonymous";
            message = modMailAuditLog.orElseThrow()
                .sendMessageEmbeds(createModMailMessage(user, userMessage));
        }

        message.mapToResult().flatMap(result -> {
            if (result.isSuccess()) {
                return event.getHook().editOriginal("Your message has been forwarded, thanks.");
            } else {
                return event.getHook()
                    .editOriginal("There was an issue forwarding your message, sorry. We are "
                            + "investigating.");
            }
        }).queue();

    }

    private MessageEmbed createModMailMessage(String user, String userMessage) {
        return new EmbedBuilder().setDescription("**/modmail from %s** ".formatted(user))
            .setFooter(userMessage)
            .setColor(AMBIENT_COLOR)
            .build();
    }

    private Optional<TextChannel> getChannel(SlashCommandInteractionEvent event) {
        long userGuildId = event.getOption(OPTION_SERVER_GUILD).getAsLong();
        Guild guild = Objects.requireNonNull(event.getJDA().getGuildById(userGuildId),
                "Something went wrong with selecting the guild.");
        return guild.getTextChannelCache()
            .stream()
            .filter(channel -> modMailChannelNamePredicate.test(channel.getName()))
            .findAny();
    }

    private boolean isChannelOnCooldown(long userId) {
        return Optional.ofNullable(authorIdToLastCommandInvocation.getIfPresent(userId))
            .map(sinceCommandInvoked -> sinceCommandInvoked.plus(COOLDOWN_DURATION_VALUE,
                    COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }

}
