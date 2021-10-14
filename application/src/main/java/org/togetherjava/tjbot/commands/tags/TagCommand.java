package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.commands.utils.MessageUtils;

import java.util.Objects;

/**
 * Implements the {@code /tag} command which lets the bot respond content of a tag that has been
 * added previously.
 * <p>
 * Tags can be added by using {@link TagManageCommand} and a list of all tags is available using
 * {@link TagsCommand}.
 */
public final class TagCommand extends SlashCommandAdapter {
    private final TagSystem tagSystem;

    private static final String ID_OPTION = "id";

    /**
     * Creates a new instance, using the given tag system as base.
     *
     * @param tagSystem the system providing the actual tag data
     */
    public TagCommand(TagSystem tagSystem) {
        super("tag", "Display a tags content", SlashCommandVisibility.GUILD);

        this.tagSystem = tagSystem;

        // TODO Thing about adding an ephemeral selection menu with pagination support
        // if the user calls this without id or similar

        getData().addOption(OptionType.STRING, ID_OPTION, "the id of the tag to display", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        String id = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();
        if (tagSystem.isUnknownTagAndHandle(id, event)) {
            return;
        }

        event
            .replyEmbeds(MessageUtils.generateEmbed(null, tagSystem.getTag(id).orElseThrow(),
                    event.getUser(), TagSystem.AMBIENT_COLOR))
            .queue();
    }
}
