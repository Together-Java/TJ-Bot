package org.togetherjava.tjbot.features.chatgpt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.chatgpt.tools.AiTool;
import org.togetherjava.tjbot.features.help.HelpSystemHelper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The implemented command is {@code /chatgpt}, which allows users to ask ChatGPT a question, upon
 * which it will respond with an AI generated answer.
 */
public final class ChatGptCommand extends SlashCommandAdapter {
    private static final ChatGptModel CHAT_GPT_MODEL = ChatGptModel.HIGH_QUALITY;
    public static final String COMMAND_NAME = "chatgpt";
    private static final String THINKING_OPTION = "enable_thinking";
    private static final String QUESTION_INPUT = "question";
    private static final int MAX_MESSAGE_INPUT_LENGTH = 200;
    private static final int MIN_MESSAGE_INPUT_LENGTH = 4;
    private static final Duration COMMAND_COOLDOWN = Duration.of(10, ChronoUnit.SECONDS);
    private static final String ERROR_RESPONSE = """
                An error has occurred while trying to communicate with ChatGPT.
                Please try again later.
            """;
    private static final String SYSTEM_PROMPT =
            """
                    You are a helpful assistant answering questions in a Discord server.
                    Keep responses concise (no more than 280 words) and use markdown when helpful.
                    When the user's question depends on live or external information, prefer calling \
                    the `web_search` tool for current facts and the `fetch_url` tool to read a specific page.
                    For code review questions, refer to the supplied code rather than rewriting it.""";

    private final ChatGptService chatGptService;
    private final HelpSystemHelper helper;
    private final List<AiTool<?>> tools;
    private final Executor worker = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "chatgpt-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final Cache<String, Instant> userIdToAskedAtCache =
            Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(COMMAND_COOLDOWN).build();

    /**
     * Creates an instance of the chatgpt command.
     *
     * @param chatGptService ChatGptService - Needed to make calls to ChatGPT API
     * @param helper HelpSystemHelper - Needed to generate response embed for prompt
     * @param tools tools the model may invoke while answering; pass an empty list to disable
     */
    public ChatGptCommand(ChatGptService chatGptService, HelpSystemHelper helper,
            List<AiTool<?>> tools) {
        super(COMMAND_NAME, "Ask the ChatGPT AI a question!", CommandVisibility.GUILD);

        this.chatGptService = chatGptService;
        this.helper = helper;
        this.tools = tools;

        getData().addOption(OptionType.BOOLEAN, THINKING_OPTION,
                "let the model use web tools (search/fetch) to answer", false);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        Instant previousAskTime = userIdToAskedAtCache
            .getIfPresent(Objects.requireNonNull(event.getMember()).getId());
        if (previousAskTime != null) {
            long timeRemainingUntilNextAsk =
                    COMMAND_COOLDOWN.minus(Duration.between(previousAskTime, Instant.now()))
                        .toSeconds();

            event
                .reply("Sorry, you need to wait another " + timeRemainingUntilNextAsk
                        + " second(s) before asking chatGPT another question.")
                .setEphemeral(true)
                .queue();
            return;
        }

        OptionMapping thinkingOption = event.getOption(THINKING_OPTION);
        boolean thinkingEnabled = thinkingOption != null && thinkingOption.getAsBoolean();

        TextInput body = TextInput
            .create(QUESTION_INPUT, "Ask ChatGPT a question or get help with code",
                    TextInputStyle.PARAGRAPH)
            .setPlaceholder("Put your question for ChatGPT here")
            .setRequiredRange(MIN_MESSAGE_INPUT_LENGTH, MAX_MESSAGE_INPUT_LENGTH)
            .build();

        Modal modal =
                Modal.create(generateComponentId(Boolean.toString(thinkingEnabled)), "ChatGPT")
                    .addActionRow(body)
                    .build();
        event.replyModal(modal).queue();
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        event.deferReply().queue();

        String question = Objects.requireNonNull(event.getValue(QUESTION_INPUT)).getAsString();
        SelfUser selfUser = event.getJDA().getSelfUser();
        InteractionHook hook = event.getHook();
        MessageChannelUnion channel = event.getChannel();
        String userId = Objects.requireNonNull(event.getMember()).getId();
        boolean thinkingEnabled = !args.isEmpty() && Boolean.parseBoolean(args.getFirst());
        List<AiTool<?>> activeTools = thinkingEnabled ? tools : List.<AiTool<?>>of();

        ChatGptProgressEmbed progress = new ChatGptProgressEmbed(hook, selfUser, question);
        hook.editOriginalEmbeds(progress.initialEmbed()).queue();

        Instant startedAt = Instant.now();
        CompletableFuture
            .supplyAsync(() -> chatGptService.askWithTools(question, CHAT_GPT_MODEL, activeTools,
                    SYSTEM_PROMPT, progress), worker)
            .whenComplete((result, throwable) -> {
                String response;
                if (throwable == null && result.isPresent()) {
                    response = result.get();
                    userIdToAskedAtCache.put(userId, Instant.now());
                } else {
                    response = ERROR_RESPONSE;
                }
                finishResponse(hook, channel, selfUser, question, response,
                        Duration.between(startedAt, Instant.now()));
            });
    }

    private void finishResponse(InteractionHook hook, MessageChannelUnion channel,
            SelfUser selfUser, String question, String response, Duration elapsed) {
        MessageEmbed baseEmbed =
                helper.generateGptResponseEmbed(response, selfUser, question, CHAT_GPT_MODEL);
        MessageEmbed finalEmbed = withTimingFooter(baseEmbed, elapsed);

        channel.sendMessageEmbeds(finalEmbed).queue(_ -> hook.deleteOriginal().queue(null, _ -> {
        }), _ -> hook.deleteOriginal().queue(null, _ -> {
        }));
    }

    private static MessageEmbed withTimingFooter(MessageEmbed embed, Duration elapsed) {
        String existing = embed.getFooter() == null ? "" : embed.getFooter().getText();
        String suffix = "took %s".formatted(formatDuration(elapsed));
        String footer = existing == null || existing.isBlank() ? suffix
                : "%s · %s".formatted(existing, suffix);
        return new EmbedBuilder(embed).setFooter(footer).build();
    }

    private static String formatDuration(Duration elapsed) {
        long totalMs = Math.max(0, elapsed.toMillis());
        if (totalMs < 1_000) {
            return totalMs + "ms";
        }
        long totalSeconds = totalMs / 1_000;
        if (totalSeconds < 60) {
            return String.format(Locale.ROOT, "%.1fs", totalMs / 1000.0);
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return "%dm %ds".formatted(minutes, seconds);
    }
}
