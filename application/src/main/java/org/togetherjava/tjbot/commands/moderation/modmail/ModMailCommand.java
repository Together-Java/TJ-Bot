package org.togetherjava.tjbot.commands.moderation.modmail;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.moderation.ModAuditLogWriter;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Implements the '/modmail' command which can be used to automatically redirects messages from DM
 * or Guild.
 * <p>
 * Actions are then logged to a dedicated channel, given by
 * {@link Config#getModAuditLogChannelPattern()}.
 */

public class ModMailCommand extends SlashCommandAdapter {

    private static final String COMMAND_NAME = "modmail";
    private static final String MESSAGE = "message";
    private static final String OPTION_MESSAGE_PRIVATE = "message-private";
    private static final int COOLDOWN_DURATION_VALUE = 30;
    private static final ChronoUnit COOLDOWN_DURATION_UNIT = ChronoUnit.MINUTES;
    private final Cache<Long, Instant> channelIdToLastCommandInvocation;
    private final Predicate<String> modMailChannelNamePredicate;


    /**
     * Creates a new instance.
     *
     * @param config the config to use for this
     */
    public ModMailCommand(@NotNull Config config) {
        super(COMMAND_NAME, "Sends a message to the moderators", SlashCommandVisibility.GLOBAL);
        getData().addOption(OptionType.STRING, MESSAGE, "Message to the moderators", true)
            .addOption(OptionType.BOOLEAN, OPTION_MESSAGE_PRIVATE,
                    "do you wish for your message to stay private", false);

        channelIdToLastCommandInvocation = setCooldown();

        modMailChannelNamePredicate =
                Pattern.compile(config.getModMailChannelPattern()).asMatchPredicate();
    }

    @NotNull
    private Cache<Long, Instant> setCooldown() {
        return Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(COOLDOWN_DURATION_VALUE, TimeUnit.of(COOLDOWN_DURATION_UNIT))
            .build();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        MessageChannel messageChannel = event.getChannel();
        if (isChannelOnCooldown(messageChannel)) {
            event
                .reply("Please wait a bit, this command can only be used once per %d %s.".formatted(
                        COOLDOWN_DURATION_VALUE,
                        COOLDOWN_DURATION_UNIT.toString().toLowerCase(Locale.US)))
                .setEphemeral(true)
                .queue();
            return;
        }
        channelIdToLastCommandInvocation.put(messageChannel.getIdLong(), Instant.now());

        Optional<TextChannel> auditLogChannel = getChannel(event);
        if (auditLogChannel.isEmpty()) {
            return;
        }

        List<ModAuditLogWriter.Attachment> attachments = getAttachments(event);

        String user = getName(event);
        MessageAction message = auditLogChannel.orElseThrow().sendMessageEmbeds(messageEmbed(user));

        message = buildAttachment(attachments, message);
        message.queue();

        event.reply("Thank you for contacting the moderators").setEphemeral(true).queue();

    }

    @NotNull
    private String getName(@NotNull SlashCommandInteractionEvent event) {
        String user = "@" + event.getUser().getName();
        boolean optionalMessage =
                Objects.requireNonNull(event.getOption(OPTION_MESSAGE_PRIVATE)).getAsBoolean();
        if (optionalMessage) {
            user = "Anonymous";
        }
        return user;
    }

    private MessageAction buildAttachment(List<ModAuditLogWriter.Attachment> attachments,
            MessageAction message) {
        for (ModAuditLogWriter.Attachment attachment : attachments) {
            message = message.addFile(attachment.getContentRaw(), attachment.name());
        }
        return message;
    }

    @NotNull
    private MessageEmbed messageEmbed(String user) {
        return new EmbedBuilder().setAuthor("Modmail Command invoked")
            .setColor(Color.BLACK)
            .setTitle("/modmail from '%s' ".formatted(user))
            .build();
    }

    @NotNull
    private List<ModAuditLogWriter.Attachment> getAttachments(
            @NotNull SlashCommandInteractionEvent event) {
        String content = Objects.requireNonNull(event.getOption(MESSAGE)).getAsString();
        List<ModAuditLogWriter.Attachment> attachments = new ArrayList<>();
        attachments.add(new ModAuditLogWriter.Attachment("content.md", content));
        return attachments;
    }

    private @NotNull Optional<TextChannel> getChannel(@NotNull SlashCommandInteractionEvent event) {
        long guildId = 1004371840245964851L;
        Guild guild = event.getChannel().getJDA().getGuildById(guildId);
        if (guild == null) {
            return Optional.empty();
        }
        return guild.getTextChannelCache()
            .stream()
            .filter(channel -> modMailChannelNamePredicate.test(channel.getName()))
            .findAny();
    }

    private boolean isChannelOnCooldown(@NotNull MessageChannel channel) {
        return Optional
            .ofNullable(channelIdToLastCommandInvocation.getIfPresent(channel.getIdLong()))
            .map(sinceCommandInvoked -> sinceCommandInvoked.plus(COOLDOWN_DURATION_VALUE,
                    COOLDOWN_DURATION_UNIT))
            .filter(Instant.now()::isBefore)
            .isPresent();
    }

}
