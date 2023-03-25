package org.togetherjava.tjbot.features.chaptgpt;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;

/**
 * The implemented command is {@code /chatgpt}, which allows users to ask ChatGPT a question, upon
 * which it will respond with an AI generated answer.
 */
public final class ChatGptCommand extends SlashCommandAdapter {
    private static final String QUESTION_OPTION = "question";
    private final ChatGptService chatGptService;

    /**
     * Creates an instance of the chatgpt command.
     * 
     * @param chatGptService ChatGptService - Needed to make calls to ChatGPT API
     */
    public ChatGptCommand(ChatGptService chatGptService) {
        super("chatgpt", "Ask the ChatGPT AI a question!", CommandVisibility.GUILD);

        this.chatGptService = chatGptService;

        getData().addOption(OptionType.STRING, QUESTION_OPTION, "What do you want to ask?", true);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String response = chatGptService.ask(event.getOption(QUESTION_OPTION).getAsString())
            .orElse("An error has occurred while trying to communication with ChatGPT. Please try again later");
        event.getHook().sendMessage(response).queue();
    }
}
