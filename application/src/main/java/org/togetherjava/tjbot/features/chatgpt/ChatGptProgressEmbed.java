package org.togetherjava.tjbot.features.chatgpt;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Streams progress for a single {@code /chatgpt} invocation into the Discord embed attached to
 * {@code hook}. Each callback appends a line to a running log (capped at
 * {@link #MAX_PROGRESS_LINES}) and re-edits the embed so users see thinking turns and tool calls as
 * they happen.
 * <p>
 * Edit failures (rate limits, deleted message, etc.) are intentionally swallowed; the final-answer
 * edit issued by the command is what matters.
 */
final class ChatGptProgressEmbed implements ChatGptProgressListener {

    private static final int MAX_PROGRESS_LINES = 12;
    private static final int MAX_VALUE_PREVIEW = 80;

    private static final String WEB_SEARCH = "web_search";
    private static final String FETCH_URL = "fetch_url";

    private final InteractionHook hook;
    private final SelfUser selfUser;
    private final String question;
    private final List<String> lines = new ArrayList<>();

    ChatGptProgressEmbed(InteractionHook hook, SelfUser selfUser, String question) {
        this.hook = hook;
        this.selfUser = selfUser;
        this.question = question;
    }

    /** Initial embed shown before the model has produced any progress events. */
    MessageEmbed initialEmbed() {
        return buildEmbed(List.of("Let me think about that..."));
    }

    @Override
    public synchronized void onThinking(int round) {
        if (round == 0) {
            addLine("Let me think about that...");
        } else {
            addLine("Continuing my research...");
        }
    }

    @Override
    public synchronized void onToolStart(String toolName, Map<String, String> arguments) {
        addLine(describeToolStart(toolName, arguments));
    }

    @Override
    public synchronized void onToolEnd(String toolName, boolean error, long elapsedMs) {
        if (error) {
            addLine("  That didn't work, I'll try something else.");
        } else {
            addLine("  Time taken. (%d ms)".formatted(elapsedMs));
        }
    }

    private static String describeToolStart(String toolName, Map<String, String> arguments) {
        return switch (toolName) {
            case WEB_SEARCH -> {
                String query = arguments.getOrDefault("query", "").trim();
                yield query.isEmpty() ? "Searching the web..."
                        : "Searching the web for \"%s\"...".formatted(truncate(query));
            }
            case FETCH_URL -> {
                String url = arguments.getOrDefault("url", "").trim();
                yield url.isEmpty() ? "Opening a link..."
                        : "Reading %s...".formatted(truncate(url));
            }
            default -> {
                String args = summarizeArgs(arguments);
                yield args.isEmpty() ? "Using %s...".formatted(toolName)
                        : "Using %s (%s)...".formatted(toolName, args);
            }
        };
    }

    private void addLine(String line) {
        lines.add(line);
        while (lines.size() > MAX_PROGRESS_LINES) {
            lines.removeFirst();
        }
        hook.editOriginalEmbeds(buildEmbed(List.copyOf(lines))).queue(null, _ -> {
        });
    }

    private MessageEmbed buildEmbed(List<String> currentLines) {
        String capitalized = Character.toUpperCase(question.charAt(0)) + question.substring(1);
        int limit = MessageEmbed.TITLE_MAX_LENGTH;
        String title = capitalized.length() > limit ? capitalized.substring(0, limit) : capitalized;

        return new EmbedBuilder()
            .setAuthor(selfUser.getName(), null, selfUser.getEffectiveAvatarUrl())
            .setTitle(title)
            .setDescription(String.join("\n", currentLines))
            .setColor(Color.gray)
            .setFooter("One moment, putting an answer together for you.")
            .build();
    }

    private static String summarizeArgs(Map<String, String> arguments) {
        if (arguments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        arguments.forEach((key, value) -> {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(key).append("=").append(truncate(value));
        });
        return sb.toString();
    }

    private static String truncate(String value) {
        return value.length() > MAX_VALUE_PREVIEW ? value.substring(0, MAX_VALUE_PREVIEW) + "..."
                : value;
    }
}
