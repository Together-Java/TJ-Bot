package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

public final class TestCommand extends SlashCommandAdapter {

    public TestCommand() {
        super("test", "Local testing of bot'", CommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        SelectMenu menu = SelectMenu.create("menu:class")
            .setPlaceholder("Choose your class")
            .setRequiredRange(1, 1)
            .addOption("Arcane Mage", "mage-arcane")
            .addOption("Fire Mage", "mage-fire")
            .addOption("Frost Mage", "mage-frost")
            .build();

        TextInput subject = TextInput.create("subject", "Subject", TextInputStyle.SHORT)
            .setPlaceholder("Subject of this ticket")
            .setMinLength(10)
            .setMaxLength(100) // or setRequiredRange(10, 100)
            .build();

        TextInput body = TextInput.create("body", "Body", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Your concerns go here")
            .setMinLength(5)
            .setMaxLength(1000)
            .build();

        TextInput anonymous =
                TextInput.create("anonymous", "Do you want to be anonymous?", TextInputStyle.SHORT)
                    .setPlaceholder("Type \"yes\" if you want to stay anonymous")
                    .setMinLength(0)
                    .setMaxLength(3)
                    .setRequired(false)
                    .build();

        Modal modal = Modal.create("modmail", "Modmail")
            .addActionRows(ActionRow.of(subject), ActionRow.of(body), ActionRow.of(anonymous))
            .build();


        event.replyModal(modal).queue();
    }
}
