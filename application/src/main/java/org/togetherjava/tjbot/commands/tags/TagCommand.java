package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

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

    private static final String COMMAND_NAME = "tag";
    private static final String ID_OPTION = "id";
    private static final String REPLY_TO_USER_OPTION = "reply-to";
    private static final int MAX_OPTIONS = 25;

    /**
     * Creates a new instance, using the given tag system as base.
     *
     * @param tagSystem the system providing the actual tag data
     */
    public TagCommand(TagSystem tagSystem) {
        super(COMMAND_NAME, "Display a tags content", CommandVisibility.GUILD);

        this.tagSystem = tagSystem;

        // TODO Think about adding an ephemeral selection menu with pagination support
        // if the user calls this without id or similar
        getData().addOptions(
                new OptionData(OptionType.STRING, ID_OPTION, "The id of the tag to display", true, true),
                new OptionData(OptionType.USER, REPLY_TO_USER_OPTION, "Optionally, the user who you want to reply to", false)
        );
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
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

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (event.getName().equals(COMMAND_NAME) && event.getFocusedOption().getName().equals(ID_OPTION)) {
            event.replyChoices(
                    tagSystem.getAllIds().stream()
                            .filter(id -> id.startsWith(event.getFocusedOption().getValue()))
                            .map(id -> new Command.Choice(id, id))
                            .limit(MAX_OPTIONS)
                            .toList()
            ).queue();
        }
    }
}
