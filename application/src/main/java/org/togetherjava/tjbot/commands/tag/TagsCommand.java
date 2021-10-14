package org.togetherjava.tjbot.commands.tag;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.List;

/**
 * Tags command. Usage: {@code /tags}. Displays all available tags.
 */
public final class TagsCommand extends SlashCommandAdapter {
    private final TagSystem tagSystem;

    public TagsCommand(TagSystem tagSystem) {
        super("tags", "Displays all tags", SlashCommandVisibility.GUILD);

        this.tagSystem = tagSystem;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event
            .replyEmbeds(TagUtility.buildAllTagsEmbed(event.getUser().getAsTag(), tagSystem)
                .setTitle("All available tags")
                .build())
            .addActionRow(
                    Button.of(ButtonStyle.DANGER, generateComponentId(event.getUser().getId()),
                            "Delete", Emoji.fromUnicode("\uD83D\uDDD1")))
            .queue();
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args) {
        String userId = args.get(0);

        if (event.getUser().getId().equals(userId)
                || event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            event.getMessage().delete().queue();
        } else {
            event.reply(":police_car: Button theft is not allowed").setEphemeral(true).queue();
        }
    }
}
