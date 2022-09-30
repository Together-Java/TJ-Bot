package org.togetherjava.tjbot.commands.moderation.modmail;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.moderation.ModAuditLogWriter;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final Cache<Long, Instant> authorIdToLastCommandInvocation = createCooldownCache();
    private final Predicate<String> modMailChannelNamePredicate;
    private static final Color AMBIENT_COLOR = Color.black;


    /**
     * Creates a new instance.
     *
     * @param config the config to use for this
     */
    public ModMailCommand(Config config) {
        super(COMMAND_NAME, "Send a message to the moderators of the selected guild",
                SlashCommandVisibility.GLOBAL);

        getData()
            .addOption(OptionType.STRING, OPTION_MESSAGE, "What do you want to tell them?", true)
            .addOption(OptionType.STRING, OPTION_SERVER_GUILD, "Your guild name", true)
            .addOption(OptionType.BOOLEAN, OPTION_STAY_ANONYMOUS, "if set, your name is hidden",
                    false);


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
        MessageChannel messageChannel = event.getChannel();
        if (isChannelOnCooldown(messageChannel)) {
            event
                .reply("Can only be used once per %s."
                    .formatted(COOLDOWN_DURATION_UNIT.toString().toLowerCase(Locale.US)))
                .setEphemeral(true)
                .queue();
            return;
        }
        authorIdToLastCommandInvocation.put(messageChannel.getIdLong(), Instant.now());

        Optional<TextChannel> modMailAuditLog = getChannel(event);
        if (modMailAuditLog.isEmpty()) {
            logger.warn("This modmail audit log is empty.");
            return;
        }

        String content = event.getOption(OPTION_MESSAGE).getAsString();
        List<ModAuditLogWriter.Attachment> attachments = getAttachments(content);

        String user = getAuthorName(event);
        MessageAction message =
                modMailAuditLog.orElseThrow().sendMessageEmbeds(createModMailMessage(user));

        message = buildAttachment(attachments, message);
        message.queue();

        event.reply("Thank you for contacting the moderators").setEphemeral(true).queue();

    }


    private String getAuthorName(SlashCommandInteractionEvent event) {
        boolean wantsToStayAnonymous = event.getOption(OPTION_STAY_ANONYMOUS).getAsBoolean();
        if (wantsToStayAnonymous) {
            return "Anonymous";
        }
        return event.getUser().getAsMention();
    }

    private MessageAction buildAttachment(List<ModAuditLogWriter.Attachment> attachments,
            MessageAction message) {
        for (ModAuditLogWriter.Attachment attachment : attachments) {
            message = message.addFile(attachment.getContentRaw(), attachment.name());
        }
        return message;
    }

    private MessageEmbed createModMailMessage(String user) {
        return new EmbedBuilder().setAuthor("/modmail from '%s' ".formatted(user))
            .setColor(AMBIENT_COLOR)
            .setTitle("Modmail")
            .build();
    }

    private List<ModAuditLogWriter.Attachment> getAttachments(String content) {
        List<ModAuditLogWriter.Attachment> attachments = new ArrayList<>();
        attachments.add(new ModAuditLogWriter.Attachment("content.md", content));
        return attachments;
    }

    private Optional<TextChannel> getChannel(SlashCommandInteractionEvent event) {
        String userGuildName = event.getOption(OPTION_SERVER_GUILD).getAsString();
        return event.getJDA()
            .getGuildCache()
            .stream()
            .filter(guild -> guild.getName().equals(userGuildName))
            .findAny()
            .orElseThrow()
            .getTextChannelCache()
            .stream()
            .filter(channel -> modMailChannelNamePredicate.test(channel.getName()))
            .findAny();
    }

    private boolean isChannelOnCooldown(MessageChannel channel) {
        return Optional
            .ofNullable(authorIdToLastCommandInvocation.getIfPresent(channel.getIdLong()))
            .map(sinceCommandInvoked -> sinceCommandInvoked.plus(COOLDOWN_DURATION_VALUE,
                    COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }

}
