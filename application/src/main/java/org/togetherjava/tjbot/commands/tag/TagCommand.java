package org.togetherjava.tjbot.commands.tag;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.List;

/**
 * Tag command. Usage: {@code /tag (id) (raw)}<br>
 * Example disord usages:<br>
 * {@code /tag}<br>
 * {@code /tag ask}<br>
 * {@code /tag ask true}
 */
public final class TagCommand extends SlashCommandAdapter {
    private final TagSystem tagSystem;

    public TagCommand(TagSystem tagSystem) {
        super("tag", "View a tag", SlashCommandVisibility.GUILD);

        this.tagSystem = tagSystem;

        getData().addOption(OptionType.STRING, "id", "Tag id")
            .addOption(OptionType.BOOLEAN, "raw", "Raw");
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        OptionMapping idOption = event.getOption("id");

        if (idOption != null) {
            String tagId = idOption.getAsString();

            TagUtility.replyTag(event, tagId, event.getUser().getAsTag(), tagSystem,
                    event.getOption("raw") != null, event.getUser().getId());

            return;
        }

        SelectionMenu.Builder menu =
                SelectionMenu.create(generateComponentId(event.getUser().getId()))
                    .setRequiredRange(1, 1);

        tagSystem.retrieveIds().stream().limit(25).forEach(tag -> menu.addOption(tag, tag));

        event.reply("Choose a tag")
            .addActionRow(menu.build())
            .addActionRow(
                    Button.of(ButtonStyle.DANGER, generateComponentId(event.getUser().getId()),
                            "Delete", Emoji.fromUnicode("\uD83D\uDDD1")))
            .queue();
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args) {
        String userId = args.get(0);

        if (!event.getUser().getId().equals(userId)
                && !event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply(":police_car: Button theft is not allowed").setEphemeral(true).queue();

            return;
        }

        event.getMessage().delete().queue();
    }

    @Override
    public void onSelectionMenu(@NotNull SelectionMenuEvent event, @NotNull List<String> args) {
        String userId = args.get(0);

        if (!event.getUser().getId().equals(userId)) {
            event.reply(":police_car: Selection menu theft is not allowed")
                .setEphemeral(true)
                .queue();

            return;
        }

        SelectOption option = event.getSelectedOptions().get(0);
        String tagId = option.getLabel();

        event.getMessage().delete().queue();

        TagUtility.sendTag(event.getMessageChannel(), tagId, event.getUser().getAsTag(), tagSystem,
                false, generateComponentId(userId));
    }
}
