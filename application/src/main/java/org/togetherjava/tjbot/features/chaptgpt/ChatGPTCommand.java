package org.togetherjava.tjbot.features.chaptgpt;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

import java.util.Objects;

/**
 * <p>
 * The implemented command is {@code /chatgpt}, upon which the bot will respond with a response
 * generated by ChatGPT.
 */
public final class ChatGPTCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ChatGPTCommand.class);
    private static final ChatGPTService chatGPTService = new ChatGPTService();

    /**
     * Creates an instance of the chatgpt command.
     */
    public ChatGPTCommand() {
        super("chatgpt", "User enters a question and then gets a response generated by ChatGPT",
                CommandVisibility.GUILD);

        getData().addOption(OptionType.STRING, "question", "Question to send to ChatGPT", true);
    }

    /**
     * When triggered with {@code /chatgpt}, the bot will respond with a ChatGPT generated response.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        try {
            String response = chatGPTService
                .ask(Objects.requireNonNull(event.getOption("question")).getAsString());
            event.reply(response).queue();
        } catch (NullPointerException nullPointerException) {
            logger.error("Null was passed as a question in ChatGPT command");
        }
    }
}
