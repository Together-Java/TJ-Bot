package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.time.Instant;
import java.util.Collection;
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

    private static final Logger logger = LoggerFactory.getLogger(TagsCommand.class);
    private static final int MAX_TAGS_THRESHOLD_WARNING = 200;

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
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        Collection<String> tagIds = tagSystem.getAllIds();
        if (tagIds.size() > MAX_TAGS_THRESHOLD_WARNING) {
            // TODO Implement the edge case

            logger.warn(
                    "The amount of tags is very high and it might soon exceed the maximum character limit. The code should be adjusted to support this edge case soon.");
        }
        String tagListText =
                tagIds.stream().sorted().map(tag -> "• " + tag).collect(Collectors.joining("\n"));

        event
            .replyEmbeds(new EmbedBuilder().setTitle("All available tags")
                .setDescription(tagListText)
                .setFooter(event.getUser().getName() + " • used " + event.getCommandString())
                .setTimestamp(Instant.now())
                .setColor(TagSystem.AMBIENT_COLOR)
                .build())
            .addActionRow(
                    TagSystem.createDeleteButton(generateComponentId(event.getUser().getId())))
            .queue();
    }

    @Override
    public void onButtonClick(@NotNull ButtonInteractionEvent event, @NotNull List<String> args) {
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
