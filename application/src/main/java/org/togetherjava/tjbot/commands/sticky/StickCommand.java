package org.togetherjava.tjbot.commands.sticky;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

import org.togetherjava.tjbot.commands.*;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.db.generated.tables.records.StickyMessageRecord;

import java.util.List;

import static org.togetherjava.tjbot.db.generated.Tables.STICKY_MESSAGE;

/**
 * Implements the /stick command which can be used to keep a message the bottom of the chat.
 */
public final class StickCommand extends SlashCommandAdapter {
    private static final String TEXT_INPUT_ID = "text";

    private Database database;

    /**
     * Creates a new Instance.
     *
     * @param database the database to get Sticky data from
     */
    public StickCommand(Database database) {
        super("stick", "Keeps a message at the bottom by deleting and resending it",
                CommandVisibility.GUILD);

        this.database = database;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        TextInput.Builder messageInput =
                TextInput.create(TEXT_INPUT_ID, "Text", TextInputStyle.PARAGRAPH)
                    .setMaxLength(2000)
                    .setPlaceholder("Text for the sticky message...");

        StickyMessageRecord stickyMessageRecord =
                StickyUtils.getSticky(database, event.getChannel());
        if (stickyMessageRecord != null) {
            messageInput.setValue(stickyMessageRecord.getText());
        }

        Modal modal = Modal.create(generateComponentId(), "Stick Message")
            .addActionRow(messageInput.build())
            .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        String text = event.getValue(TEXT_INPUT_ID).getAsString();

        event.getChannel().sendMessage(text).queue(this::addStickyMessageToDB);
        event.reply("Your message has been sticked to this channel").setEphemeral(true).queue();
    }

    private void addStickyMessageToDB(Message message) {
        Channel channel = message.getChannel();

        StickyUtils.deleteSticky(database, channel);
        database.write(context -> context.newRecord(STICKY_MESSAGE)
            .setChannelId(channel.getIdLong())
            .setText(message.getContentRaw())
            .setMessageId(message.getIdLong())
            .insert());
    }
}
