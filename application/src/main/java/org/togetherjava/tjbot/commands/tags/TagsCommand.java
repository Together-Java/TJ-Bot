package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.commands.utils.MessageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implements the {@code /tags} command which lets the bot respond with all available tags.
 * <p>
 * Tags can be added by using {@link TagManageCommand} and viewed by {@link TagCommand}.
 * <p>
 * For example, suppose there is a tag with id {@code foo} and content {@code bar}, then:
 *
 * <pre>
 * {@code
 * /tag foo
 * // TJ-Bot: bar
 * }
 * </pre>
 */
public final class TagsCommand extends SlashCommandAdapter {
    private final TagSystem tagSystem;

    /**
     * Creates a new instance, using the given tag system as base.
     *
     * @param tagSystem the system providing the actual tag data
     */
    public TagsCommand(TagSystem tagSystem) {
        super("tags", "Displays all available tags", SlashCommandVisibility.GUILD);

        this.tagSystem = tagSystem;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {

        ArrayList<String> list = new ArrayList<>(tagSystem.getAllIds());

        if (list.size() > 200) {

            Logger logger = Logger.getLogger(TagsCommand.class.getName());

            logger.setLevel(Level.WARNING);
            logger.warning("- WARNING - TAGS ARE BEYOND 200 LINES ");
        }

        event.replyEmbeds(MessageUtils.generateEmbed("All available tags",
                        "* " + String.join("\n ",
                                list.stream().sorted().collect(Collectors.joining("\n * "))),
                        event.getUser(), TagSystem.AMBIENT_COLOR))
                .addActionRow(
                        TagSystem.createDeleteButton(generateComponentId(event.getUser().getId())))
                .queue();
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args) {
        String userId = args.get(0);

        if (!event.getUser().getId().equals(userId) && !Objects.requireNonNull(event.getMember())
            .hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply(
                    "The message can only be deleted by its author or an user with 'MESSAGE_MANAGE' permissions.")
                .setEphemeral(true)
                .queue();
            return;
        }

        event.getMessage().delete().queue();
    }
}
