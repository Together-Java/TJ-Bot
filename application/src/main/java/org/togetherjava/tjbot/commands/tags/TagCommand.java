package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

import org.togetherjava.tjbot.commands.Colors;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.utils.StringDistances;

import java.time.Instant;
import java.util.Collection;

/**
 * Implements the {@code /tag} command which lets the bot respond content of a tag that has been
 * added previously.
 * <p>
 * Tags can be added by using {@link TagManageCommand} and a list of all tags is available using
 * {@link TagsCommand}.
 */
public final class TagCommand extends SlashCommandAdapter {
    private final TagSystem tagSystem;
    private static final int MAX_SUGGESTIONS = 5;
    static final String ID_OPTION = "id";
    static final String REPLY_TO_USER_OPTION = "reply-to";

    /**
     * Creates a new instance, using the given tag system as base.
     *
     * @param tagSystem the system providing the actual tag data
     */
    public TagCommand(TagSystem tagSystem) {
        super("tag", "Display a tags content", CommandVisibility.GUILD);

        this.tagSystem = tagSystem;

        getData().addOptions(
                new OptionData(OptionType.STRING, ID_OPTION, "The id of the tag to display", true,
                        true),
                new OptionData(OptionType.USER, REPLY_TO_USER_OPTION,
                        "Optionally, the user who you want to reply to", false));
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String id = event.getOption(ID_OPTION).getAsString();
        OptionMapping replyToUserOption = event.getOption(REPLY_TO_USER_OPTION);

        if (tagSystem.handleIsUnknownTag(id, event)) {
            return;
        }

        ReplyCallbackAction message = event
            .replyEmbeds(new EmbedBuilder().setDescription(tagSystem.getTag(id).orElseThrow())
                .setFooter(event.getUser().getName() + " • used " + event.getCommandString())
                .setTimestamp(Instant.now())
                .setColor(Colors.TAG)
                .build());

        if (replyToUserOption != null) {
            message = message.setContent(replyToUserOption.getAsUser().getAsMention());
        }
        message.queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();

        if (!focusedOption.getName().equals(ID_OPTION)) {
            throw new IllegalArgumentException(
                    "Unexpected option, was: " + focusedOption.getName());
        }

        Collection<Command.Choice> choices = StringDistances
            .closeMatches(focusedOption.getValue(), tagSystem.getAllIds(), MAX_SUGGESTIONS)
            .stream()
            .map(id -> new Command.Choice(id, id))
            .toList();

        event.replyChoices(choices).queue();
    }
}
