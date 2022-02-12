package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;

import java.time.Instant;
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
    private static final String REPLY_TO_USER_OPTION = "reply-to";

    /**
     * Creates a new instance, using the given tag system as base.
     *
     * @param tagSystem the system providing the actual tag data
     */
    public TagCommand(TagSystem tagSystem) {
        super("tag", "Display a tags content", CommandVisibility.GUILD);

        this.tagSystem = tagSystem;

        // TODO Think about adding an ephemeral selection menu with pagination support
        // if the user calls this without id or similar
        getData().addOption(OptionType.STRING, ID_OPTION, "The id of the tag to display", true)
            .addOption(OptionType.USER, REPLY_TO_USER_OPTION,
                    "Optionally, the user who you want to reply to", false);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        String id = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();
        OptionMapping replyToUserOption = event.getOption(REPLY_TO_USER_OPTION);

        if (tagSystem.handleIsUnknownTag(id, event)) {
            return;
        }

        ReplyCallbackAction message = event
            .replyEmbeds(new EmbedBuilder().setDescription(tagSystem.getTag(id).orElseThrow())
                .setFooter(event.getUser().getName() + " • used " + event.getCommandString())
                .setTimestamp(Instant.now())
                .setColor(TagSystem.AMBIENT_COLOR)
                .build());

        if (replyToUserOption != null) {
            message = message.setContent(replyToUserOption.getAsUser().getAsMention());
        }
        message.queue();
    }
}