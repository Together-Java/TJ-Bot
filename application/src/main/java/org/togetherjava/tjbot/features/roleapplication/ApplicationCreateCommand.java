package org.togetherjava.tjbot.features.roleapplication;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.RoleApplicationSystemConfig;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.componentids.Lifespan;

import javax.annotation.Nullable;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Represents a command to create an application form for members to apply for roles.
 * <p>
 * This command is designed to generate an application form for members to apply for roles within a
 * guild.
 */
public class ApplicationCreateCommand extends SlashCommandAdapter {
    protected static final Color AMBIENT_COLOR = new Color(24, 221, 136, 255);
    private static final int OPTIONAL_ROLES_AMOUNT = 5;
    private static final String ROLE_COMPONENT_ID_HEADER = "application-create";
    private static final String VALUE_DELIMITER = "_";
    private static final int ARG_COUNT = 3;

    private final ApplicationApplyHandler applicationApplyHandler;
    private final RoleApplicationSystemConfig roleApplicationSystemConfig;

    /**
     * Constructs a new {@link ApplicationCreateCommand} with the specified configuration.
     * <p>
     * This command is designed to generate an application form for members to apply for roles.
     *
     * @param config the configuration containing the settings for the application form
     */
    public ApplicationCreateCommand(Config config) {
        super("application-form", "Generates an application form for members to apply for roles.",
                CommandVisibility.GUILD);

        this.roleApplicationSystemConfig = config.getRoleApplicationSystemConfig();

        generateRoleOptions(getData());
        applicationApplyHandler = new ApplicationApplyHandler(roleApplicationSystemConfig);
    }

    /**
     * Populates a {@link SlashCommandData} object with the proper arguments.
     *
     * @param data the object to populate
     */
    private void generateRoleOptions(SlashCommandData data) {
        IntStream.range(1, OPTIONAL_ROLES_AMOUNT + 1).forEach(index -> {
            data.addOption(OptionType.STRING, generateOptionId("title", index),
                    "The title of the role");
            data.addOption(OptionType.STRING, generateOptionId("description", index),
                    "The description of the role");
            data.addOption(OptionType.STRING, generateOptionId("emoji", index),
                    "The emoji of the role");
        });
    }

    private static String generateOptionId(String name, int id) {
        return "%s%s%d".formatted(name, VALUE_DELIMITER, id);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        if (!handleHasPermissions(event)) {
            return;
        }

        final List<OptionMapping> optionMappings = event.getInteraction().getOptions();
        if (optionMappings.isEmpty()) {
            event.reply("You have to select at least one role.").setEphemeral(true).queue();
            return;
        }

        long incorrectArgsCount = getIncorrectRoleArgsCount(optionMappings);
        if (incorrectArgsCount > 0) {
            event.reply("Missing information for %d roles.".formatted(incorrectArgsCount))
                .setEphemeral(true)
                .queue();
            return;
        }

        sendMenu(event);
    }

    @Override
    public void onStringSelectSelection(StringSelectInteractionEvent event, List<String> args) {
        SelectOption selectOption = event.getSelectedOptions().getFirst();
        Member member = event.getMember();

        if (selectOption == null || member == null) {
            return;
        }

        long remainingMinutes = applicationApplyHandler.getMemberCooldownMinutes(member);
        if (remainingMinutes > 0) {
            event
                .reply("Please wait %d minutes before sending a new application form."
                    .formatted(remainingMinutes))
                .setEphemeral(true)
                .queue();
            return;
        }

        TextInput body = TextInput
            .create(generateComponentId(event.getUser().getId()),
                    roleApplicationSystemConfig.defaultQuestion(), TextInputStyle.PARAGRAPH)
            .setRequired(true)
            .setRequiredRange(roleApplicationSystemConfig.minimumAnswerLength(),
                    roleApplicationSystemConfig.maximumAnswerLength())
            .setPlaceholder("Enter your answer here")
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

        event.replyModal(modal).queue();
    }

    /**
     * Checks a given list of passed arguments (from a user) and calculates how many roles have
     * missing data.
     *
     * @param args the list of passed arguments
     * @return the amount of roles with missing data
     */
    private static long getIncorrectRoleArgsCount(final List<OptionMapping> args) {
        final Map<String, Integer> frequencyMap = new HashMap<>();

        args.stream()
            .map(OptionMapping::getName)
            .map(name -> name.split(VALUE_DELIMITER)[1])
            .forEach(number -> frequencyMap.merge(number, 1, Integer::sum));

        return frequencyMap.values().stream().filter(value -> value != 3).count();
    }

    /**
     * Populates a {@link StringSelectMenu.Builder} with application roles.
     *
     * @param menuBuilder the menu builder to populate
     * @param args the arguments which contain data about the roles
     */
    private void addRolesToMenu(StringSelectMenu.Builder menuBuilder,
            final List<OptionMapping> args) {
        final Map<Integer, MenuRole> roles = new HashMap<>();

        for (int i = 0; i < args.size(); i += ARG_COUNT) {
            OptionMapping optionTitle = args.get(i);
            OptionMapping optionDescription = args.get(i + 1);
            OptionMapping optionEmoji = args.get(i + 2);

            roles.put(i,
                    new MenuRole(optionTitle.getAsString(),
                            generateComponentId(ROLE_COMPONENT_ID_HEADER,
                                    optionTitle.getAsString()),
                            optionDescription.getAsString(),
                            Emoji.fromFormatted(optionEmoji.getAsString())));
        }

        roles.values()
            .forEach(role -> menuBuilder.addOption(role.title(), role.value(), role.description(),
                    role.emoji()));
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

        return true;
    }

    /**
     * Sends the initial embed and a button which displays role openings.
     *
     * @param event the command interaction event triggering the menu
     */
    private void sendMenu(final CommandInteraction event) {
        MessageEmbed embed = createApplicationEmbed();

        StringSelectMenu.Builder menuBuilder = StringSelectMenu
            .create(generateComponentId(Lifespan.PERMANENT, event.getUser().getId()))
            .setPlaceholder("Select role to apply for")
            .setRequiredRange(1, 1);

        addRolesToMenu(menuBuilder, event.getOptions());

        event.replyEmbeds(embed).addActionRow(menuBuilder.build()).queue();
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

    public ApplicationApplyHandler getApplicationApplyHandler() {
        return applicationApplyHandler;
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        getApplicationApplyHandler().submitApplicationFromModalInteraction(event, args);
    }

    /**
     * Wrapper class which represents a menu role for the application create command.
     * <p>
     * The reason this exists is due to the fact that {@link StringSelectMenu.Builder} does not have
     * a method which takes emojis as input as of writing this, so we have to elegantly pass in
     * custom data from this POJO.
     */
    private record MenuRole(String title, String value, String description, @Nullable Emoji emoji) {

    }
}
