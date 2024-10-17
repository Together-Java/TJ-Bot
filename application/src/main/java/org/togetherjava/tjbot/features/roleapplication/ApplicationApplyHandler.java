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
public class ApplicationApplyHandler {

    private final Cache<Member, OffsetDateTime> applicationSubmitCooldown;
    private final Predicate<String> applicationChannelPattern;
    private final RoleApplicationSystemConfig roleApplicationSystemConfig;

    /**
     * Constructs a new {@code ApplicationApplyHandler} instance.
     *
     * @param roleApplicationSystemConfig the configuration that contains the details for the application form
     *        including the cooldown duration and channel pattern.
     */
    public ApplicationApplyHandler(RoleApplicationSystemConfig roleApplicationSystemConfig) {
        this.roleApplicationSystemConfig = roleApplicationSystemConfig;
        this.applicationChannelPattern =
                Pattern.compile(roleApplicationSystemConfig.submissionsChannelPattern()).asMatchPredicate();

        final Duration applicationSubmitCooldownDuration =
                Duration.ofMinutes(roleApplicationSystemConfig.applicationSubmitCooldownMinutes());
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
    protected void sendApplicationResult(final ModalInteractionEvent event, List<String> args,
            String answer) {
        Guild guild = event.getGuild();
        if (args.size() != 2 || guild == null) {
            return;
        }

        Optional<TextChannel> applicationChannel = getApplicationChannel(guild);
        if (applicationChannel.isEmpty()) {
            return;
        }

        User applicant = event.getUser();
        EmbedBuilder embed =
                new EmbedBuilder().setAuthor(applicant.getName(), null, applicant.getAvatarUrl())
                    .setColor(ApplicationCreateCommand.AMBIENT_COLOR)
                    .setTimestamp(Instant.now())
                    .setFooter("Submitted at");

        String roleString = args.getLast();
        MessageEmbed.Field roleField = new MessageEmbed.Field("Role", roleString, false);
        embed.addField(roleField);

        MessageEmbed.Field answerField =
                new MessageEmbed.Field(roleApplicationSystemConfig.defaultQuestion(), answer, false);
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

    protected void submitApplicationFromModalInteraction(ModalInteractionEvent event,
            List<String> args) {
        Guild guild = event.getGuild();

        if (guild == null) {
            return;
        }

        ModalMapping modalAnswer = event.getValues().getFirst();

        sendApplicationResult(event, args, modalAnswer.getAsString());
        event.reply("Your application has been submitted. Thank you for applying! 😎")
            .setEphemeral(true)
            .queue();

        applicationSubmitCooldown.put(event.getMember(), OffsetDateTime.now());
    }
}
