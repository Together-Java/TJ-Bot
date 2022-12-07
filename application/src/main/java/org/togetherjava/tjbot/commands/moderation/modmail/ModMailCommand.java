package org.togetherjava.tjbot.commands.moderation.modmail;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.jetbrains.annotations.Nullable;
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
 * Implements the /modmail command, which allows users to contact a moderator within the server
 * which forwards messages to moderators in a dedicated channel given by
 * {@link Config#getModMailChannelPattern()}.
 */

public final class ModMailCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ModMailCommand.class);
    public static final String COMMAND_NAME = "modmail";
    private static final String MESSAGE = "message";
    private static final String STAY_ANONYMOUS = "stay-anonymous";
    private static final int COOLDOWN_DURATION_VALUE = 30;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;
    private static final Color AMBIENT_COLOR = Color.BLACK;
    private static final String YES = "yes";
    private final Cache<Long, Instant> authorToLastModMailInvocation = createCooldownCache();
    private final Predicate<String> modMailChannelNamePredicate;
    private final Predicate<String> configModGroupPattern;
    private final String configModMailChannelPattern;


    /**
     * Creates a new instance.
     *
     * @param jda the JDA instance to retrieve guildCache
     * @param config to get the channel to forward modmails to
     */
    public ModMailCommand(JDA jda, Config config) {
        super(COMMAND_NAME, "Contact the moderators of the selected guild",
                CommandVisibility.GLOBAL);

        modMailChannelNamePredicate =
                Pattern.compile(config.getModMailChannelPattern()).asMatchPredicate();

        configModMailChannelPattern = config.getModMailChannelPattern();

        configModGroupPattern =
                Pattern.compile(config.getHeavyModerationRolePattern()).asMatchPredicate();
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

        if (handleIsOnCooldown(userId, event)) {
            return;
        }

        TextInput body = TextInput.create(MESSAGE, "Message", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Your concerns go here")
            .setMinLength(5)
            .setMaxLength(1000)
            .build();

        TextInput anonymous = TextInput
            .create(STAY_ANONYMOUS, "Do you want to be anonymous?", TextInputStyle.SHORT)
            .setPlaceholder("Type \"yes\" if you want to stay anonymous")
            .setMinLength(0)
            .setMaxLength(3)
            .setRequired(false)
            .build();

        Modal modal = Modal.create(generateComponentId("modmail"), "Modmail")
            .addActionRows(ActionRow.of(body), ActionRow.of(anonymous))
            .build();


        event.replyModal(modal).queue();

        authorToLastModMailInvocation.put(userId, Instant.now());
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {

        long userId = event.getUser().getIdLong();

        event.deferReply().setEphemeral(true).queue();

        long userGuildId = event.getGuild().getIdLong();
        Optional<TextChannel> modMailAuditLog = getModMailChannel(event.getJDA(), userGuildId);
        if (modMailAuditLog.isEmpty()) {
            logger.warn(
                    "Cannot find the designated modmail channel in server by id {} with the pattern {}",
                    userGuildId, configModMailChannelPattern);
            return;
        }

        MessageCreateAction message =
                createModMessage(event, userId, modMailAuditLog.orElseThrow());

        sendMessage(event, message);
    }

    private boolean handleIsOnCooldown(long userId, SlashCommandInteractionEvent event) {
        if (!isChannelOnCooldown(userId)) {
            return false;
        }
        event.reply("Can only be used once per %s minutes.".formatted(COOLDOWN_DURATION_VALUE))
            .setEphemeral(true)
            .queue();
        return true;
    }

    private Optional<TextChannel> getModMailChannel(JDA jda, long guildId) {
        return jda.getGuildById(guildId)
            .getTextChannelCache()
            .stream()
            .filter(channel -> modMailChannelNamePredicate.test(channel.getName()))
            .findAny();
    }

    private MessageCreateAction createModMessage(ModalInteractionEvent event, long userId,
            TextChannel modMailAuditLog) {
        String userMessage = event.getValue(MESSAGE).getAsString();
        String wantsToStayAnonymous = event.getValue(STAY_ANONYMOUS).getAsString();

        User user = wantsToStayAnonymous.equalsIgnoreCase(YES) ? null : event.getUser();
        MessageCreateAction message =
                modMailAuditLog.sendMessageEmbeds(createModMailMessage(user, userMessage));
        if (!wantsToStayAnonymous.equalsIgnoreCase(YES)) {
            message.addActionRow(DiscordClientAction.General.USER.asLinkButton("Author Profile",
                    String.valueOf(userId)));
        }

        Optional<Role> moderatorRole = event.getGuild()
            .getRoles()
            .stream()
            .filter(role -> configModGroupPattern.test(role.getName()))
            .findFirst();

        moderatorRole.ifPresent(role -> message.setContent(role.getAsMention()));

        return message;
    }

    private void sendMessage(ModalInteractionEvent event, MessageCreateAction message) {
        InteractionHook hook = event.getHook();
        message.mapToResult().map(result -> {
            if (result.isSuccess()) {
                return "Your message has been forwarded, thanks.";
            }
            logger.warn("There was an issue with forwarding users message.");
            return "There was an issue forwarding your message, sorry. We are investigating.";
        }).flatMap(hook::editOriginal).queue();
    }

    private MessageEmbed createModMailMessage(@Nullable User author, String userMessage) {
        String authorTag = author == null ? "Anonymous" : author.getAsTag();
        String authorAvatar = author == null ? null : author.getAvatarUrl();
        return new EmbedBuilder().setTitle("Modmail")
            .setAuthor(authorTag, null, authorAvatar)
            .setDescription(userMessage)
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
