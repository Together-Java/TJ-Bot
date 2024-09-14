package org.togetherjava.tjbot.features.tophelper.ai;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.chatgpt.ChatGptService;

import java.awt.*;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AITopHelperCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(AITopHelperCommand.class);
    private static final String COMMAND_NAME = "top-helper-ai";
    private static final String QUESTIONS_CHANNEL_NAME = "questions";
    private static final String CHATGPT_PROMPT =
            """
                    The following contains user IDs and their message. Using the messages provided by each user,
                    which user ID was the most helpful/answered the question? If there are no meaningful messages, you must still choose somebody.
                    ONLY provide the user ID of that person and the reason. Do not reply with anything else. Reply in the format userID|reason
                    %s
                    """;

    private final ChatGptService chatGptService;

    public AITopHelperCommand(ChatGptService chatGptService) {
        super(COMMAND_NAME,
                "Uses AI to determine who the current top helper is from the beginning of the month",
                CommandVisibility.GUILD);
        this.chatGptService = chatGptService;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        List<ForumChannel> channels =
                event.getJDA().getForumChannelsByName(QUESTIONS_CHANNEL_NAME, true);

        if (channels.isEmpty()) {
            event.reply("No forum " + QUESTIONS_CHANNEL_NAME + " found").queue();
            return;
        }

        List<ThreadChannel> questions = channels.getFirst().getThreadChannels();

        if (questions.isEmpty()) {
            event.reply("No thread channels found").queue();
            return;
        }

        event.deferReply().queue();
        determineTopHelper(event, questions);
    }

    private void determineTopHelper(SlashCommandInteractionEvent event,
            List<ThreadChannel> questions) {
        List<String> potentialTopHelpers = new ArrayList<>();

        List<CompletableFuture<Void>> futures = questions.stream()
            .filter(question -> !question.getTimeCreated().isBefore(getFirstDayOfMonth()))
            .map(question -> processQuestionAsync(question, potentialTopHelpers))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        sendCommandResponse(event, potentialTopHelpers);
    }

    private CompletableFuture<Void> processQuestionAsync(ThreadChannel question,
            List<String> potentialTopHelpers) {
        return MessageHistory.getHistoryFromBeginning(question)
            .submit()
            .thenApply(MessageHistory::getRetrievedHistory)
            .thenAccept(history -> {
                StringBuilder allMessages = new StringBuilder();
                history.forEach(message -> {
                    User author = message.getAuthor();
                    if (author.getIdLong() != question.getOwnerIdLong() && !author.isBot()) {
                        String content = message.getContentStripped();
                        allMessages.append(author.getIdLong())
                            .append(": ")
                            .append(content)
                            .append("\n\n");
                    }
                });

                if (!allMessages.isEmpty()) {
                    Optional<String> topHelper =
                            chatGptService.ask(CHATGPT_PROMPT.formatted(allMessages.toString()));
                    topHelper.ifPresent(potentialTopHelpers::add);
                } else {
                    logger.trace("No messages found");
                }
            });
    }

    private void sendCommandResponse(SlashCommandInteractionEvent event,
            List<String> potentialTopHelpers) {
        Map<String, Integer> topHelpers = calculateTopHelpers(potentialTopHelpers);

        String response = topHelpers.entrySet().stream().map(entry -> {
            String result = entry.getKey();
            String[] parts = result.split("\\|");
            String userId = parts[0];
            int count = entry.getValue();
            try {
                User user = event.getJDA().getUserById(userId);
                if (user != null) {
                    return user.getAsMention() + " " + count;
                }
                return null;
            } catch (NumberFormatException e) {
                logger.debug("Invalid user ID encountered: {}", userId);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.joining("\n"));

        EmbedBuilder eb = new EmbedBuilder().setColor(Color.CYAN)
            .setTitle("Top Helpers")
            .setDescription(response.isEmpty() ? "None at the moment" : response)
            .setFooter("The higher the number next to their name, the better the helper they are");

        event.getHook().editOriginalEmbeds(eb.build()).queue();
    }

    private static Map<String, Integer> calculateTopHelpers(List<String> potentialTopHelpers) {
        Map<String, Integer> frequencyMap = new HashMap<>();
        for (String helper : potentialTopHelpers) {
            String userId = helper.split("\\|")[0];
            if (frequencyMap.containsKey(userId)) {
                int count = frequencyMap.get(userId) + 1;
                frequencyMap.put(userId, count);
            } else {
                frequencyMap.put(userId, 1);
            }
        }
        return frequencyMap.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1,
                    LinkedHashMap::new));
    }

    private static OffsetDateTime getFirstDayOfMonth() {
        OffsetDateTime now = OffsetDateTime.now();
        return YearMonth.from(now).atDay(1).atStartOfDay().atOffset(now.getOffset());
    }
}
