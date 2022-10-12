package org.togetherjava.tjbot.commands.moderation.modmail;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;


/**
 * Implements the /modmail command, which allows users to contact a moderator within the server or
 * even if not part of the server anymore, e.g. after a ban.
 * <p>
 * Therefore, the user uses the SlashCommand /modmail and the message are forwarded to moderators in
 * a dedicated channel.
 * <p>
 * Messages are then forwarded to a dedicated channel, given by
 * {@link Config#getModAuditLogChannelPattern()}.
 */

public final class ModMailCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ModMailCommand.class);
    private static final String COMMAND_NAME = "modmail";
    private static final String OPTION_MESSAGE = "message";
    private static final String OPTION_STAY_ANONYMOUS = "stay-anonymous";
    private static final String OPTION_GUILD = "server";
    private static final int COOLDOWN_DURATION_VALUE = 30;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;
    private static final Color AMBIENT_COLOR = Color.BLACK;
    private final Cache<Long, Instant> authorToLastModMailInvocation = createCooldownCache();
    private final Predicate<String> modMailChannelNamePredicate;


    /**
     * Creates a new instance.
     *
     * @param jda the JDA instance to retrieve guildCache
     * @param config to get the channel to forward modmails to
     */
    public ModMailCommand(JDA jda, Config config) {
        super(COMMAND_NAME, "Contact the moderators of the selected guild",
                CommandVisibility.GLOBAL);

        OptionData messageOption = new OptionData(OptionType.STRING, OPTION_MESSAGE,
                "What do you want to tell them?", true);
        OptionData guildOption = new OptionData(OptionType.STRING, OPTION_GUILD,
                "The server to contact mods from", true);
        OptionData anonymousOption = new OptionData(OptionType.BOOLEAN, OPTION_STAY_ANONYMOUS,
                "If set, your name is hidden - note that mods then can not get " + "back to you",
                true);

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

        handleCooldown(userId, event);

        event.deferReply().setEphemeral(true).queue();

        long userGuildId = event.getOption(OPTION_GUILD).getAsLong();
        Optional<TextChannel> modMailAuditLog = getModMailChannel(event.getJDA(), userGuildId);
        if (modMailAuditLog.isEmpty()) {
            logger.warn("Cannot find the designated modmail channel in server by id {}",
                    userGuildId);
            return;
        }

        MessageCreateAction message = createMessage(event, userId, modMailAuditLog.orElseThrow());

        sendMessage(event, message);

    }

    private void handleCooldown(long userId, SlashCommandInteractionEvent event) {
        if (isChannelOnCooldown(userId)) {
            event.reply("Can only be used once per %s minutes.".formatted(COOLDOWN_DURATION_VALUE))
                .setEphemeral(true)
                .queue();
            return;
        }
        authorToLastModMailInvocation.put(userId, Instant.now());
    }

    private Optional<TextChannel> getModMailChannel(JDA jda, long userGuildI) {
        return jda.getGuildById(userGuildI)
            .getTextChannelCache()
            .stream()
            .filter(channel -> modMailChannelNamePredicate.test(channel.getName()))
            .findAny();
    }

    private MessageCreateAction createMessage(SlashCommandInteractionEvent event, long userId,
            TextChannel modMailAuditLog) {
        String userMessage = event.getOption(OPTION_MESSAGE).getAsString();
        boolean wantsToStayAnonymous = event.getOption(OPTION_STAY_ANONYMOUS).getAsBoolean();
        String user;
        MessageCreateAction message;
        if (wantsToStayAnonymous) {
            user = "Anonymous";
            message = modMailAuditLog.sendMessageEmbeds(createModMailMessage(user, userMessage));
        } else {
            user = event.getUser().getAsMention();
            message = modMailAuditLog.sendMessageEmbeds(createModMailMessage(user, userMessage))
                .addActionRow(DiscordClientAction.General.USER.asLinkButton("Author Profile",
                        String.valueOf(userId)));
        }
        return message;
    }

    private void sendMessage(SlashCommandInteractionEvent event, MessageCreateAction message) {
        InteractionHook hook = event.getHook();
        message.mapToResult()
            .map(result -> result.isSuccess() ? "Your message has been forwarded, thanks."
                    : "There was an issue forwarding your message, sorry. We are investigating.")
            .flatMap(hook::editOriginal)
            .queue(any -> {
            }, error -> logger.warn(
                    "There was an problem with forwarding the message to the modmail channel.",
                    error)); // need to add on queue on success
    }

    private MessageEmbed createModMailMessage(String user, String userMessage) {
        return new EmbedBuilder().setDescription("**/modmail from %s** ".formatted(user))
            .setFooter(userMessage)
            .setColor(AMBIENT_COLOR)
            .build();
    }

    private boolean isChannelOnCooldown(long userId) {
        return Optional.ofNullable(authorToLastModMailInvocation.getIfPresent(userId))
            .map(sinceCommandInvoked -> sinceCommandInvoked.plus(COOLDOWN_DURATION_VALUE,
                    COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }

}
