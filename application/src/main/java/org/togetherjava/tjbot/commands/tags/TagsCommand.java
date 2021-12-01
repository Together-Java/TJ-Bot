package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.commands.utils.MessageUtils;
import org.slf4j.Logger;
import java.util.List;
import java.util.Objects;
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
    private static final Logger logger = LoggerFactory.getLogger(TagsCommand.class);

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
        int MAX_TAGS_THRESHOLD_WARNING = 200;
        if (tagSystem.getAllIds().size() > MAX_TAGS_THRESHOLD_WARNING) {
            // TODO Implement the edge case

            logger.warn(
                    "The amount of tags is very high and it might soon exceed the maximum character limit. The code should be adjusted to support this edge case soon.\n");
        }
        String tagListText = tagSystem.getAllIds()
            .stream()
            .sorted()
            .map(tag -> "* " + tag)
            .collect(Collectors.joining("\n"));

        event
            .replyEmbeds(MessageUtils.generateEmbed("All available tags", tagListText,
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
