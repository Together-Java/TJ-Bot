package org.togetherjava.tjbot.features.basic;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.config.ApplicationFormConfig;
import org.togetherjava.tjbot.config.ApplyRoleConfig;
import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.componentids.Lifespan;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Represents a command to create an application form for members to apply for roles.
 * <p>
 * This command is designed to generate an application form for members to apply for roles within a
 * guild.
 */
public class ApplicationCreateCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationCreateCommand.class);

    private static final Color AMBIENT_COLOR = new Color(24, 221, 136, 255);
    private static final int MIN_REASON_LENGTH = 50;
    private static final int MAX_REASON_LENGTH = 500;
    private static final int APPLICATION_SUBMIT_COOLDOWN = 5;
    private static final String DEFAULT_QUESTION =
            "What makes you a valuable addition to the team? 😎";

    private final Cache<Member, OffsetDateTime> applicationSubmitCooldown;
    private final Predicate<String> applicationChannelPattern;
    private final ApplicationFormConfig config;

    /**
     * Constructs a new {@code ApplicationCreateCommand} with the specified configuration.
     * <p>
     * This command is designed to generate an application form for members to apply for roles.
     *
     * @param config the configuration containing the settings for the application form
     */
    public ApplicationCreateCommand(Config config) {
        super("application-form", "Generates an application form for members to apply for roles.",
                CommandVisibility.GUILD);

        this.config = config.getApplicationFormConfig();
        this.applicationChannelPattern =
                Pattern.compile(this.config.applicationChannelPattern()).asMatchPredicate();

        this.applicationSubmitCooldown = Caffeine.newBuilder()
            .expireAfterWrite(APPLICATION_SUBMIT_COOLDOWN, TimeUnit.MINUTES)
            .build();
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        if (!handleHasPermissions(event)) {
            return;
        }

        sendMenu(event);
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        User user = event.getUser();
        SelectMenu.Builder menu =
                SelectMenu.create(generateComponentId(Lifespan.REGULAR, event.getUser().getId()))
                    .setPlaceholder("Select role to apply for");

        config.applyRoleConfig()
            .stream()
            .map(option -> mapToSelectOption(user, option))
            .forEach(menu::addOptions);

        event.reply("").addActionRow(menu.build()).setEphemeral(true).queue();
    }

    /**
     * Maps a user and an {@link ApplyRoleConfig} option to a SelectOption object.
     * <p>
     * This method is used to create a SelectOption object that represents a role configuration
     * option for a user, including a unique component ID generated based on the user's ID and the
     * option's name, a description, and an emoji.
     *
     * @param user the user for whom the role configuration option is being mapped
     * @param option the {@link ApplyRoleConfig} option to be mapped to a SelectOption
     * @return a {@link SelectOption} object with the specified details
     */
    private SelectOption mapToSelectOption(User user, ApplyRoleConfig option) {
        return SelectOption.of(option.name(), generateComponentId(user.getId(), option.name()))
            .withDescription(option.description())
            .withEmoji(Emoji.fromFormatted(option.emoji()));
    }

    @Override
    public void onSelectMenuSelection(SelectMenuInteractionEvent event, List<String> args) {
        SelectOption selectOption = event.getSelectedOptions().getFirst();

        if (selectOption == null) {
            return;
        }

        OffsetDateTime timeSentCache = applicationSubmitCooldown.getIfPresent(event.getMember());
        if (timeSentCache != null) {
            Duration duration = Duration.between(timeSentCache, OffsetDateTime.now());

            if (duration.toMinutes() < APPLICATION_SUBMIT_COOLDOWN) {
                event.reply("Please wait before sending a new application form.")
                    .setEphemeral(true)
                    .queue();
                return;
            }
        }

        TextInput body = TextInput
            .create(generateComponentId(event.getUser().getId()), "Question",
                    TextInputStyle.PARAGRAPH)
            .setRequired(true)
            .setRequiredRange(MIN_REASON_LENGTH, MAX_REASON_LENGTH)
            .setPlaceholder(DEFAULT_QUESTION)
            .build();

        EmojiUnion emoji = selectOption.getEmoji();
        String roleDisplayName;

        if (emoji == null) {
            roleDisplayName = selectOption.getLabel();
        } else {
            roleDisplayName = "%s %s".formatted(emoji.getFormatted(), selectOption.getLabel());
        }

        Modal modal = Modal
            .create(generateComponentId(event.getUser().getId(), roleDisplayName),
                    String.format("Application form - %s", selectOption.getLabel()))
            .addActionRow(ActionRow.of(body).getComponents())
            .build();

        event.getHook().deleteOriginal().queue();
        event.replyModal(modal).queue();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
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

    /**
     * Retrieves the application channel from the given {@link Guild}.
     *
     * @param guild the guild from which to retrieve the application channel
     * @return an {@link Optional} containing the {@code TextChannel} representing the application
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

    private boolean handleHasPermissions(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (member == null || guild == null) {
            return false;
        }

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply("You do not have the required manage role permission to use this command")
                .setEphemeral(true)
                .queue();
            return false;
        }

        Member selfMember = guild.getSelfMember();
        if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            event.reply(
                    "Sorry, but I was not set up correctly. I need the manage role permissions for this.")
                .setEphemeral(true)
                .queue();
            logger.error("The bot requires the manage role permissions for /{}.", getName());
            return false;
        }

        return true;
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
                    .setColor(AMBIENT_COLOR)
                    .setTimestamp(Instant.now())
                    .setFooter("Submitted at");

        String roleString = args.getLast();
        MessageEmbed.Field roleField = new MessageEmbed.Field("Role", roleString, false);
        embed.addField(roleField);

        MessageEmbed.Field answerField = new MessageEmbed.Field(DEFAULT_QUESTION, answer, false);
        embed.addField(answerField);

        applicationChannel.get().sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Sends the initial embed and a button which displays role openings.
     *
     * @param event the command interaction event triggering the menu
     */
    private void sendMenu(final CommandInteraction event) {
        MessageEmbed embed = createApplicationEmbed();

        String buttonComponentId = generateComponentId(Lifespan.PERMANENT, event.getUser().getId());
        Button button = Button.primary(buttonComponentId, "Check openings");

        event.replyEmbeds(embed).addActionRow(button).queue();
    }

    private static MessageEmbed createApplicationEmbed() {
        return new EmbedBuilder().setTitle("Apply for roles")
            .setDescription(
                    """
                            We are always looking for community members that want to contribute to our community \
                            and take charge. If you are interested, you can apply for various positions here!""")
            .setColor(AMBIENT_COLOR)
            .build();
    }
}
