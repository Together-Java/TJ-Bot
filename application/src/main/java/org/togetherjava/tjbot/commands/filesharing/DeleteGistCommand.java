package org.togetherjava.tjbot.commands.filesharing;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class DeleteGistCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DeleteGistCommand.class);

    private static final String MESSAGE_ID_OPTION = "message-id";

    public DeleteGistCommand(@NotNull Config config) {
        super("remove-gist", "Deletes gist's from auto file-sharing feature!",
                SlashCommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        String messageId = event.getOption(MESSAGE_ID_OPTION).getAsString();

        Message gistMessage = event.getMessageChannel().retrieveMessageById(messageId).complete();

        String content =
                "I uploaded your file(s) as gist, it is much easier to read for everyone like that, especially for mobile users";

        if (!gistMessage.getAuthor().isBot() || !gistMessage.getContentRaw().equals(content)) {
            return;
        }

        User fileSender = gistMessage.getReferencedMessage().getAuthor();
        User user = event.getUser();

        if (!user.equals(fileSender)) {
            return;
        }

        String url = gistMessage.getButtons()
            .stream()
            .filter(button -> button.getLabel().equals("gist"))
            .toList()
            .get(0)
            .getUrl();
        url = url.replace("https://api.github.com/gists/", "");

        // send api stuff to
    }
}
