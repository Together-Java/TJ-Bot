package org.togetherjava.tjbot.commands.tags;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implements the {@code /tag} command which lets the bot respond content of a tag that has been
 * added previously.
 * <p>
 * Tags can be added by using {@link TagManageCommand} and a list of all tags is available using
 * {@link TagsCommand}.
 */
public final class TagCommand extends SlashCommandAdapter {
    private final TagSystem tagSystem;

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

        // TODO Think about adding an ephemeral selection menu with pagination support
        // if the user calls this without id or similar
        getData().addOption(OptionType.STRING, ID_OPTION, "The id of the tag to display", true)
            .addOption(OptionType.USER, REPLY_TO_USER_OPTION,
                    "Optionally, the user who you want to reply to", false);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String id = Objects.requireNonNull(event.getOption(ID_OPTION)).getAsString();
        OptionMapping replyToUserOption = event.getOption(REPLY_TO_USER_OPTION);

        if (tagSystem.handleIsUnknownTag(id, event, super.getComponentIdGenerator(),
                replyToUserOption)) {
            return;
        }

        sendTagReply(event, event.getUser().getName(), id, event.getCommandString(),
                replyToUserOption == null ? null : replyToUserOption.getAsUser().getAsMention());
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        sendTagReply(event, event.getUser().getName(), args.get(0), null, args.get(1));
    }

    /**
     * Sends the reply for a successfull /tag use (i.e. the given tag exists)
     */
    private void sendTagReply(IReplyCallback callback, String userName, String tag,
            @Nullable String commandString, @Nullable String replyToUser) {
        Optional<String> commandStringOpt = Optional.ofNullable(commandString);
        Optional<String> replyToUserOpt = Optional.ofNullable(replyToUser);

        callback
            .replyEmbeds(new EmbedBuilder().setDescription(tagSystem.getTag(tag).orElseThrow())
                .setFooter(userName + commandStringOpt.map(s -> " • used " + s).orElse(""))
                .setTimestamp(Instant.now())
                .setColor(TagSystem.AMBIENT_COLOR)
                .build())
            .setContent(replyToUserOpt.orElse(""))
            .queue();
    }
}
