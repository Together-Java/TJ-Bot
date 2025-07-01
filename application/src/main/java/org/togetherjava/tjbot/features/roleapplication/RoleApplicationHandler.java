package org.togetherjava.tjbot.features.roleapplication;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.RoleApplicationSystemConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Handles the actual process of submitting role applications.
 * <p>
 * This class is responsible for managing application submissions via modal interactions, ensuring
 * that submissions are sent to the appropriate application channel, and enforcing cooldowns for
 * users to prevent spamming.
 */
public final class RoleApplicationHandler {
    private static final Logger logger = LoggerFactory.getLogger(RoleApplicationHandler.class);

    private static final int APPLICATION_SUBMIT_COOLDOWN_MINUTES = 5;
    private final Cache<Member, OffsetDateTime> applicationSubmitCooldown;
    private final Predicate<String> applicationChannelPattern;
    private final RoleApplicationSystemConfig roleApplicationSystemConfig;

    /**
     * Constructs a new {@code RoleApplicationHandler} instance.
     *
     * @param roleApplicationSystemConfig the configuration that contains the details for the
     *        application form including the cooldown duration and channel pattern.
     */
    public RoleApplicationHandler(RoleApplicationSystemConfig roleApplicationSystemConfig) {
        this.roleApplicationSystemConfig = roleApplicationSystemConfig;
        this.applicationChannelPattern =
                Pattern.compile(roleApplicationSystemConfig.submissionsChannelPattern())
                    .asMatchPredicate();

        final Duration applicationSubmitCooldownDuration =
                Duration.ofMinutes(APPLICATION_SUBMIT_COOLDOWN_MINUTES);
        applicationSubmitCooldown =
                Caffeine.newBuilder().expireAfterWrite(applicationSubmitCooldownDuration).build();
    }

    /**
     * Sends the result of an application submission to the designated application channel in the
     * guild.
     * <p>
     * The {@code args} parameter should contain the applicant's name and the role they are applying
     * for.
     *
     * @param event the modal interaction event triggering the application submission
     * @param args the arguments provided in the application submission
     * @param answer the answer provided by the applicant to the default question
     */
    private void sendApplicationResult(final ModalInteractionEvent event, List<String> args,
            String answer) throws IllegalArgumentException {
        Guild guild = event.getGuild();

        if (guild == null) {
            throw new IllegalArgumentException(
                    "sendApplicationResult() got fired in a non-guild environment.");
        }

        if (args.size() != 2) {
            throw new IllegalArgumentException(
                    "Received application result after user submitted one, and did not receive 2 arguments. Args: "
                            + args);
        }

        String roleString = args.get(1);

        Optional<TextChannel> applicationChannel = getApplicationChannel(guild);
        if (applicationChannel.isEmpty()) {
            throw new IllegalArgumentException("Application channel %s could not be found."
                .formatted(roleApplicationSystemConfig.submissionsChannelPattern()));
        }

        User applicant = event.getUser();
        EmbedBuilder embed =
                new EmbedBuilder().setAuthor(applicant.getName(), null, applicant.getAvatarUrl())
                    .setColor(CreateRoleApplicationCommand.AMBIENT_COLOR)
                    .setFooter("Submitted at")
                    .setTimestamp(Instant.now());

        MessageEmbed.Field roleField = new MessageEmbed.Field("Role", roleString, false);
        embed.addField(roleField);

        MessageEmbed.Field answerField = new MessageEmbed.Field(
                roleApplicationSystemConfig.defaultQuestion(), answer, false);
        embed.addField(answerField);

        applicationChannel.get().sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Retrieves the application channel from the given {@link Guild}.
     *
     * @param guild the guild from which to retrieve the application channel
     * @return an {@link Optional} containing the {@link TextChannel} representing the application
     *         channel, or an empty {@link Optional} if no such channel is found
     */
    private Optional<TextChannel> getApplicationChannel(Guild guild) {
        return guild.getChannels()
            .stream()
            .filter(channel -> applicationChannelPattern.test(channel.getName()))
            .filter(channel -> channel.getType().isMessage())
            .map(TextChannel.class::cast)
            .findFirst();
    }

    public Cache<Member, OffsetDateTime> getApplicationSubmitCooldown() {
        return applicationSubmitCooldown;
    }

    void submitApplicationFromModalInteraction(ModalInteractionEvent event, List<String> args) {
        Guild guild = event.getGuild();

        if (guild == null) {
            return;
        }

        ModalMapping modalAnswer = event.getValues().getFirst();

        try {
            sendApplicationResult(event, args, modalAnswer.getAsString());
            event.reply("Your application has been submitted. Thank you for applying! 😎")
                .setEphemeral(true)
                .queue();
        } catch (IllegalArgumentException e) {
            logger.error("A role application could not be submitted. ", e);
            event.reply("Your application could not be submitted. Please contact the staff team.")
                .setEphemeral(true)
                .queue();
        }

        applicationSubmitCooldown.put(event.getMember(), OffsetDateTime.now());
    }

    long getMemberCooldownMinutes(Member member) {
        OffsetDateTime timeSentCache = getApplicationSubmitCooldown().getIfPresent(member);
        if (timeSentCache != null) {
            Duration duration = Duration.between(timeSentCache, OffsetDateTime.now());
            return APPLICATION_SUBMIT_COOLDOWN_MINUTES - duration.toMinutes();
        }
        return 0L;
    }
}
