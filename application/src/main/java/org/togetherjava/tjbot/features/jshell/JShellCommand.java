package org.togetherjava.tjbot.features.jshell;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.TimeFormat;

import org.togetherjava.tjbot.config.JShellConfig;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.jshell.backend.JShellApi;
import org.togetherjava.tjbot.features.jshell.backend.dto.JShellResult;
import org.togetherjava.tjbot.features.jshell.render.Colors;
import org.togetherjava.tjbot.features.jshell.render.ResultRenderer;
import org.togetherjava.tjbot.features.utils.RateLimiter;
import org.togetherjava.tjbot.features.utils.RequestFailedException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class JShellCommand extends SlashCommandAdapter {
    private static final String JSHELL_TEXT_INPUT_ID = "jshell";
    private static final String JSHELL_COMMAND = "jshell";
    private static final String JSHELL_VERSION_SUBCOMMAND = "version";
    private static final String JSHELL_EVAL_SUBCOMMAND = "eval";
    private static final String JSHELL_SNIPPETS_SUBCOMMAND = "snippets";
    private static final String JSHELL_CLOSE_SUBCOMMAND = "shutdown";
    private static final int MIN_MESSAGE_INPUT_LENGTH = 0;
    private static final int MAX_MESSAGE_INPUT_LENGTH = TextInput.MAX_VALUE_LENGTH;

    private final JShellApi api;
    private final ResultRenderer renderer;
    private final RateLimiter<Long> rateLimiter;

    /**
     * Creates an instance of the command.
     */
    public JShellCommand(JShellConfig config) {
        super(JSHELL_COMMAND, "JShell as a command.", CommandVisibility.GUILD);

        this.api = new JShellApi(new ObjectMapper(), config.baseUrl());
        this.renderer = new ResultRenderer();

        this.rateLimiter = new RateLimiter<>(Duration.ofSeconds(config.rateLimitWindowSeconds()),
                config.rateLimitRequestsInWindow());

        getData().addSubcommands(
                new SubcommandData(JSHELL_VERSION_SUBCOMMAND, "Get the version of JShell"),
                new SubcommandData(JSHELL_EVAL_SUBCOMMAND,
                        "Evaluate java code in JShell, don't fill the optional parameter to access a bigger input box.")
                            .addOption(OptionType.STRING, "code",
                                    "Code to evaluate. If not supplied, open an inout box."),
                new SubcommandData(JSHELL_SNIPPETS_SUBCOMMAND, "Get the evaluated snippets."),
                new SubcommandData(JSHELL_CLOSE_SUBCOMMAND, "Close your session."));
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case JSHELL_VERSION_SUBCOMMAND -> handleVersionCommand(event);
            case JSHELL_EVAL_SUBCOMMAND -> handleEvalCommand(event);
            case JSHELL_SNIPPETS_SUBCOMMAND -> handleSnippetsCommand(event);
            case JSHELL_CLOSE_SUBCOMMAND -> handleCloseCommand(event);
            default -> throw new AssertionError(
                    "Unexpected Subcommand: " + event.getSubcommandName());
        }
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        ModalMapping mapping = event.getValue(JSHELL_TEXT_INPUT_ID);
        if (mapping != null) {
            handleEval(event, event.getUser(), true, false, mapping.getAsString());
        }
    }

    private void handleVersionCommand(SlashCommandInteractionEvent event) {
        String code = """
                System.out.println("```");
                System.out.println("Version: " + Runtime.version());
                System.out.println("Vendor:  " + System.getProperty("java.vendor"));
                System.out.println("OS:      " + System.getProperty("os.name"));
                System.out.println("Arch:    " + System.getProperty("os.arch"));
                 System.out.println("```");""";
        handleEval(event, event.getUser(), false, true, code);
    }

    private void handleEvalCommand(SlashCommandInteractionEvent event) {
        OptionMapping code = event.getOption("code");
        if (code == null) {
            sendEvalModal(event);
        } else {
            handleEval(event, event.getUser(), true, false, code.getAsString());
        }
    }

    private void sendEvalModal(SlashCommandInteractionEvent event) {
        TextInput body = TextInput
            .create(JSHELL_TEXT_INPUT_ID, "Enter code to evaluate.", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Put your code here.")
            .setRequiredRange(MIN_MESSAGE_INPUT_LENGTH, MAX_MESSAGE_INPUT_LENGTH)
            .build();

        Modal modal = Modal.create(generateComponentId(), "JShell").addActionRow(body).build();
        event.replyModal(modal).queue();
    }

    private void handleEval(IReplyCallback replyCallback, User user, boolean showCode,
            boolean oneOffSession, String code) {
        replyCallback.deferReply().queue(interactionHook -> {
            try {
                evaluateAndRespond(user, code, showCode, oneOffSession, interactionHook);
            } catch (RequestFailedException e) {
                interactionHook.editOriginalEmbeds(createUnexpectedErrorEmbed(user, e)).queue();
            }
        });
    }

    private void handleSnippetsCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue(interactionHook -> {
            List<String> snippets;
            try {
                snippets = api.snippetsSession(event.getUser().getId()).snippets();
            } catch (RequestFailedException e) {
                if (e.getStatus() == JShellApi.SESSION_NOT_FOUND) {
                    interactionHook
                        .editOriginalEmbeds(createSessionNotFoundErrorEmbed(event.getUser()))
                        .queue();
                } else {
                    interactionHook
                        .editOriginalEmbeds(createUnexpectedErrorEmbed(event.getUser(), e))
                        .queue();
                }
                return;
            }

            if (snippets.stream().noneMatch(s -> s.length() >= MessageEmbed.VALUE_MAX_LENGTH)
                    && snippets.stream()
                        .mapToInt(s -> (s + "Snippet 10```java\n```").length())
                        .sum() < MessageEmbed.EMBED_MAX_LENGTH_BOT - 100
                    && snippets.size() <= 25/*
                                             * Max visible embed fields in an embed TODO replace
                                             * with constant
                                             */) {
                sendSnippetsAsEmbed(interactionHook, event.getUser(), snippets);
            } else if (snippets.stream()
                .mapToInt(s -> (s + "// Snippet 10").getBytes().length)
                .sum() < Message.MAX_FILE_SIZE) {
                sendSnippetsAsFile(interactionHook, event.getUser(), snippets);
            } else {
                sendSnippetsTooLong(interactionHook, event.getUser());
            }
        });
    }

    private void sendSnippetsAsEmbed(InteractionHook interactionHook, User user,
            List<String> snippets) {
        EmbedBuilder builder = new EmbedBuilder().setColor(Colors.SUCCESS_COLOR)
            .setAuthor(user.getName())
            .setTitle(snippetsTitle(user));
        int i = 1;
        for (String snippet : snippets) {
            builder.addField("Snippet " + i, "```java\n" + snippet + "```", false);
            i++;
        }
        interactionHook.editOriginalEmbeds(builder.build()).queue();
    }

    private void sendSnippetsAsFile(InteractionHook interactionHook, User user,
            List<String> snippets) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String snippet : snippets) {
            sb.append("// Snippet ").append(i).append("\n").append(snippet);
            i++;
        }
        interactionHook
            .editOriginalEmbeds(new EmbedBuilder().setColor(Colors.SUCCESS_COLOR)
                .setAuthor(user.getName())
                .setTitle(snippetsTitle(user))
                .build())
            .setFiles(FileUpload.fromData(sb.toString().getBytes(), snippetsTitle(user)))
            .queue();
    }

    private String snippetsTitle(User user) {
        return user.getName() + "'s snippets";
    }

    private void sendSnippetsTooLong(InteractionHook interactionHook, User user) {
        interactionHook
            .editOriginalEmbeds(new EmbedBuilder().setColor(Colors.ERROR_COLOR)
                .setAuthor(user.getName())
                .setTitle("Too much code to send...")
                .build())
            .queue();
    }

    private void handleCloseCommand(SlashCommandInteractionEvent event) {
        try {
            api.closeSession(event.getUser().getId());
        } catch (RequestFailedException e) {
            if (e.getStatus() == JShellApi.SESSION_NOT_FOUND) {
                event.replyEmbeds(createSessionNotFoundErrorEmbed(event.getUser())).queue();
            } else {
                event.replyEmbeds(createUnexpectedErrorEmbed(event.getUser(), e)).queue();
            }
            return;
        }

        event
            .replyEmbeds(new EmbedBuilder().setColor(Colors.SUCCESS_COLOR)
                .setAuthor(event.getUser().getName())
                .setTitle("Session closed")
                .build())
            .queue();
    }


    private void evaluateAndRespond(User user, String code, boolean showCode, boolean oneOffSession,
            InteractionHook interactionHook) throws RequestFailedException {
        JShellResult result;
        if (oneOffSession) {
            if (wasRateLimited(interactionHook, user, Instant.now())) {
                return;
            }
            result = api.evalOnce(code);
        } else {
            result = api.evalSession(code, user.getId());
        }

        MessageEmbed embed = renderer
            .renderToEmbed(user, showCode ? code : null, !oneOffSession, result, new EmbedBuilder())
            .build();

        interactionHook.editOriginalEmbeds(embed).queue();
    }

    private boolean wasRateLimited(InteractionHook interaction, User user, Instant checkTime) {
        if (rateLimiter.allowRequest(user.getIdLong(), checkTime)) {
            return false;
        }

        String nextAllowedTime = TimeFormat.RELATIVE
            .format(rateLimiter.nextAllowedRequestTime(user.getIdLong(), checkTime));
        interaction
            .editOriginalEmbeds(new EmbedBuilder().setAuthor(user.getName() + "'s result")
                .setDescription(
                        "You are currently rate-limited. Please try again " + nextAllowedTime + ".")
                .setColor(Colors.ERROR_COLOR)
                .build())
            .queue();

        return true;
    }

    private MessageEmbed createSessionNotFoundErrorEmbed(User user) {
        return new EmbedBuilder().setAuthor(user.getName() + "'s result")
            .setColor(Colors.ERROR_COLOR)
            .setDescription("Could not find session for user " + user.getName())
            .build();
    }

    private MessageEmbed createUnexpectedErrorEmbed(User user, RequestFailedException e) {
        return new EmbedBuilder().setAuthor(user.getName() + "'s result")
            .setColor(Colors.ERROR_COLOR)
            .setDescription("Request failed: " + e.getMessage())
            .build();
    }
}
