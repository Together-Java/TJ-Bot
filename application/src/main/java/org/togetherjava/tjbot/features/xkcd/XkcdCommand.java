package org.togetherjava.tjbot.features.xkcd;

import com.openai.models.responses.FileSearchTool;
import com.openai.models.responses.Tool;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.apache.commons.lang3.IntegerRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.chatgpt.ChatGptModel;
import org.togetherjava.tjbot.features.chatgpt.ChatGptService;

import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Discord slash command that posts XKCD comics.
 * <p>
 * Supports two subcommands:
 * <ul>
 * <li>{@code /xkcd relevant [amount]} - Uses ChatGPT + RAG vector store to find the most relevant
 * XKCD from recent chat history (default: 100 messages, max: 100).</li>
 * <li>{@code /xkcd custom <id>} - Posts a specific XKCD comic by ID from local cache.</li>
 * </ul>
 *
 * Relies on {@link XkcdService} for local XKCD data and {@link ChatGptService} for AI-powered
 * relevance matching via OpenAI's file search tool and vector stores.
 */
public final class XkcdCommand extends SlashCommandAdapter {

    private static final Logger logger = LoggerFactory.getLogger(XkcdCommand.class);

    private static final String COMMAND_NAME = "xkcd";
    private static final String SUBCOMMAND_RELEVANT = "relevant";
    private static final String SUBCOMMAND_CUSTOM = "custom";
    private static final String LAST_MESSAGES_AMOUNT_OPTION_NAME = "amount";
    private static final String XKCD_ID_OPTION_NAME = "id";
    private static final int MAXIMUM_MESSAGE_HISTORY = 100;
    private static final int MESSAGE_HISTORY_CUTOFF_SIZE_KB = 40_000;
    private static final String VECTOR_STORE_XKCD = "xkcd-comics";
    private static final ChatGptModel CHAT_GPT_MODEL = ChatGptModel.FAST;
    private static final Pattern XKCD_POST_PATTERN = Pattern.compile("^\\D*(\\d+)");
    private static final String CHATGPT_NO_ID_MESSAGE =
            "ChatGPT could not respond with a XKCD post ID.";

    private final ChatGptService chatGptService;
    private final XkcdService xkcdService;

    public XkcdCommand(ChatGptService chatGptService) {
        super(COMMAND_NAME, "Post a relevant XKCD from the chat or your own",
                CommandVisibility.GLOBAL);

        this.chatGptService = chatGptService;
        this.xkcdService = new XkcdService(chatGptService);

        OptionData lastMessagesAmountOption =
                new OptionData(OptionType.INTEGER, LAST_MESSAGES_AMOUNT_OPTION_NAME,
                        "The amount of messages to consider, starting from the most recent")
                    .setMinValue(0)
                    .setRequired(true)
                    .setMaxValue(MAXIMUM_MESSAGE_HISTORY);

        SubcommandData relevantSubcommand = new SubcommandData(SUBCOMMAND_RELEVANT,
                "Let an LLM figure out the most relevant XKCD based on the chat history")
            .addOptions(lastMessagesAmountOption);

        OptionData xkcdIdOption = new OptionData(OptionType.INTEGER, XKCD_ID_OPTION_NAME,
                "The XKCD number to post to the chat")
            .setMinValue(0)
            .setRequired(true)
            .setMaxValue(xkcdService.getXkcdPosts().size());

        SubcommandData customSubcommand = new SubcommandData(SUBCOMMAND_CUSTOM,
                "Post your own XKCD regardless of the recent chat messages")
            .addOptions(xkcdIdOption);

        getData().addSubcommands(relevantSubcommand, customSubcommand);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        String subcommandName = Objects.requireNonNull(event.getSubcommandName());

        switch (subcommandName) {
            case SUBCOMMAND_RELEVANT -> handleRelevantXkcd(event);
            case SUBCOMMAND_CUSTOM -> handleCustomXkcd(event);
            default -> throw new IllegalArgumentException("Unknown subcommand");
        }
    }

    private void handleRelevantXkcd(SlashCommandInteractionEvent event) {
        Integer messagesAmount =
                event.getOption(LAST_MESSAGES_AMOUNT_OPTION_NAME, OptionMapping::getAsInt);

        if (messagesAmount == null) {
            messagesAmount = MAXIMUM_MESSAGE_HISTORY;
        }

        if (messagesAmount <= 0 || messagesAmount > MAXIMUM_MESSAGE_HISTORY) {
            return;
        }

        MessageChannelUnion messageChannelUnion = event.getChannel();

        messageChannelUnion.asTextChannel()
            .getHistory()
            .retrievePast(messagesAmount)
            .queue(messages -> {
                event.deferReply().queue();
                sendRelevantXkcdEmbedFromMessages(messages, event);
            }, error -> logger.error("Failed to retrieve the chat history in #{}",
                    messageChannelUnion.getName(), error));
    }

    private void handleCustomXkcd(SlashCommandInteractionEvent event) {
        Integer xkcdId = event.getOption(XKCD_ID_OPTION_NAME, OptionMapping::getAsInt);

        event.deferReply().queue();

        if (xkcdId == null) {
            event.getHook().setEphemeral(true).sendMessage("Could not find this XKCD").queue();
            return;
        }

        Optional<MessageEmbed> messageEmbedOptional =
                constructEmbed(xkcdId, "Handpicked by member.");
        messageEmbedOptional.ifPresentOrElse(
                messageEmbed -> event.getHook().sendMessageEmbeds(messageEmbed).queue(), () -> {
                    event.getHook()
                        .setEphemeral(true)
                        .sendMessage("Could not find XKCD with ID #" + xkcdId)
                        .queue();
                    logger.error("Could not find XKCD with ID #{}", xkcdId);
                });
    }

    private void sendRelevantXkcdEmbedFromMessages(List<Message> messages,
            SlashCommandInteractionEvent event) {
        List<Message> discordChatCutoff = cutoffDiscordChatHistory(messages);
        String discordChatFormatted = formatDiscordChatHistory(discordChatCutoff);
        String xkcdComicsFileId = xkcdService.getXkcdUploadedFileId();
        String xkcdVectorStore =
                chatGptService.createOrGetVectorStore(xkcdComicsFileId, VECTOR_STORE_XKCD);
        FileSearchTool fileSearch =
                FileSearchTool.builder().vectorStoreIds(List.of(xkcdVectorStore)).build();

        Tool tool = Tool.ofFileSearch(fileSearch);

        Optional<String> responseOptional = chatGptService.sendPrompt(
                getChatgptRelevantPrompt(discordChatFormatted), CHAT_GPT_MODEL, List.of(tool));

        Optional<Integer> responseIdOptional = getXkcdIdFromMessage(responseOptional.orElseThrow());

        if (responseIdOptional.isEmpty()) {
            event.getHook().setEphemeral(true).sendMessage(CHATGPT_NO_ID_MESSAGE).queue();
            return;
        }

        int responseId = responseIdOptional.orElseThrow();

        logger.debug("ChatGPT chose XKCD ID: {}", responseId);
        Optional<MessageEmbed> embedOptional =
                constructEmbed(responseId, "Most relevant XKCD according to ChatGPT.");

        embedOptional.ifPresentOrElse(embed -> event.getHook().sendMessageEmbeds(embed).queue(),
                () -> event.getHook()
                    .setEphemeral(true)
                    .sendMessage("I could not find post with ID " + responseId)
                    .queue());
    }

    private Optional<MessageEmbed> constructEmbed(int xkcdId, String footer) {
        Optional<XkcdPost> xkcdPostOptional = xkcdService.getXkcdPost(xkcdId);

        if (xkcdPostOptional.isEmpty()) {
            logger.warn("Could not find XKCD post with ID {} from local map", xkcdId);
            return Optional.empty();
        }

        XkcdPost xkcdPost = xkcdPostOptional.get();

        return Optional
            .of(new EmbedBuilder().setTitle("%s (#%d)".formatted(xkcdPost.title(), xkcdId))
                .setImage(xkcdPost.img())
                .setUrl("https://xkcd.com/" + xkcdId)
                .setColor(Color.CYAN)
                .setFooter(footer)
                .build());
    }

    private Optional<Integer> getXkcdIdFromMessage(String response) {
        Matcher matcher = XKCD_POST_PATTERN.matcher(response.trim());

        if (!matcher.find()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException _) {
            logger.warn("Extracted ID '{}' is not a valid integer", matcher.group(1));
            return Optional.empty();
        }
    }

    private String formatDiscordChatHistory(List<Message> messages) {
        return messages.stream()
            .filter(message -> !message.getAuthor().isBot())
            .map(message -> "%s: %s".formatted(message.getAuthor().getName(),
                    message.getContentRaw()))
            .collect(Collectors.toSet())
            .toString();
    }

    private List<Message> cutoffDiscordChatHistory(List<Message> messages) {
        int cutoffMessageIndex = (int) IntegerRange.of(0, messages.size() - 1)
            .toIntStream()
            .map(index -> countMessagesLength(messages.subList(0, index)))
            .filter(length -> length < MESSAGE_HISTORY_CUTOFF_SIZE_KB)
            .count();

        return messages.subList(0, cutoffMessageIndex);
    }

    private int countMessagesLength(List<Message> messages) {
        return messages.stream()
            .mapToInt(message -> message.getContentRaw().length()
                    + message.getAuthor().getName().length())
            .sum();
    }

    private static String getChatgptRelevantPrompt(String discordChat) {
        return """
                <discord-chat>
                %s
                </discord-chat>

                # Role
                You are very experienced with XKCD and you have read every XKCD comic inside and out.
                You also understand online humor very well and have a good history of making peopel laugh.

                # Task
                Carefully read the Discord chat and come up with the MOST relevant XKCD comic you have read.
                You should mention the number FIRST. The more relevant, the more points and money you get.
                You should reason on why it's the most relevant XKCD. If you can pick one that is funnily
                the most relevant, legendary. MAKE SURE THE XKCD ID MATCHES THE ACTUAL
                ARTICLE BY LOOKING AT THE FILES LIST OF XKCD POSTS.

                <example-response>
                    Answer: 219
                    Explanation: Because the user ABC was talking about XYZ, and that XKCD post is the most
                    relevant
                </example-response>
                <example-response>
                    Answer: 74
                    Explanation: ...
                </example-response>
                """
            .formatted(discordChat);
    }
}
