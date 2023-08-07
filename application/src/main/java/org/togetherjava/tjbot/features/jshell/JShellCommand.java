package org.togetherjava.tjbot.features.jshell;

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

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.jshell.backend.JShellApi;
import org.togetherjava.tjbot.features.jshell.render.Colors;
import org.togetherjava.tjbot.features.utils.RequestFailedException;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Objects;

public class JShellCommand extends SlashCommandAdapter {
    private static final String JSHELL_TEXT_INPUT_ID = "jshell";
    private static final String JSHELL_COMMAND = "jshell";
    private static final String JSHELL_VERSION_SUBCOMMAND = "version";
    private static final String JSHELL_EVAL_SUBCOMMAND = "eval";
    private static final String JSHELL_SNIPPETS_SUBCOMMAND = "snippets";
    private static final String JSHELL_CLOSE_SUBCOMMAND = "shutdown";
    private static final String JSHELL_STARTUP_SCRIPT_SUBCOMMAND = "startup-script";
    private static final String JSHELL_CODE_PARAMETER = "code";
    private static final String JSHELL_STARTUP_SCRIPT_PARAMETER = "startup-script";
    private static final String JSHELL_USER_PARAMETER = "user";
    private static final String JSHELL_INCLUDE_STARTUP_SCRIPT_PARAMETER = "include-startup-script";

    private static final int MIN_MESSAGE_INPUT_LENGTH = 0;
    private static final int MAX_MESSAGE_INPUT_LENGTH = TextInput.MAX_VALUE_LENGTH;

    private final JShellEval jshellEval;

    /**
     * Creates an instance of the command.
     */
    public JShellCommand(JShellEval jshellEval) {
        super(JSHELL_COMMAND, "JShell as a command.", CommandVisibility.GUILD);

        this.jshellEval = jshellEval;

        getData().addSubcommands(
                new SubcommandData(JSHELL_VERSION_SUBCOMMAND, "Get the version of JShell"),
                new SubcommandData(JSHELL_EVAL_SUBCOMMAND,
                        "Evaluate java code in JShell, don't fill the optional parameter to access a bigger input box.")
                            .addOption(OptionType.STRING, JSHELL_CODE_PARAMETER,
                                    "Code to evaluate. If not supplied, open an inout box.")
                            .addOption(OptionType.BOOLEAN, JSHELL_STARTUP_SCRIPT_PARAMETER,
                                    "If the startup script should be loaded, true by default."),
                new SubcommandData(JSHELL_SNIPPETS_SUBCOMMAND,
                        "Get the evaluated snippets of the user who sent the command, or the user specified user if any.")
                            .addOption(OptionType.USER, JSHELL_USER_PARAMETER,
                                    "User to get the snippets from. If null, get the snippets of the user who sent the command.")
                            .addOption(OptionType.BOOLEAN, JSHELL_INCLUDE_STARTUP_SCRIPT_PARAMETER,
                                    "if the startup script should be included, false by default."),
                new SubcommandData(JSHELL_CLOSE_SUBCOMMAND, "Close your session."),
                new SubcommandData(JSHELL_STARTUP_SCRIPT_SUBCOMMAND,
                        "Display the startup script."));
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case JSHELL_VERSION_SUBCOMMAND -> handleVersionCommand(event);
            case JSHELL_EVAL_SUBCOMMAND -> handleEvalCommand(event);
            case JSHELL_SNIPPETS_SUBCOMMAND -> handleSnippetsCommand(event);
            case JSHELL_CLOSE_SUBCOMMAND -> handleCloseCommand(event);
            case JSHELL_STARTUP_SCRIPT_SUBCOMMAND -> handleStartupScriptCommand(event);
            default -> throw new AssertionError(
                    "Unexpected Subcommand: " + event.getSubcommandName());
        }
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        ModalMapping mapping =
                event.getValue(JSHELL_TEXT_INPUT_ID + "|" + JSHELL_STARTUP_SCRIPT_PARAMETER);
        boolean startupScript = mapping != null;
        if (mapping == null) {
            mapping = event.getValue(JSHELL_TEXT_INPUT_ID);
        }
        if (mapping != null) {
            handleEval(event, event.getUser(), true, mapping.getAsString(), startupScript);
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
        handleEval(event, null, false, code, false);
    }

    private void handleEvalCommand(SlashCommandInteractionEvent event) {
        OptionMapping code = event.getOption(JSHELL_CODE_PARAMETER);
        boolean startupScript = event.getOption(JSHELL_STARTUP_SCRIPT_PARAMETER) == null
                || Objects.requireNonNull(event.getOption(JSHELL_STARTUP_SCRIPT_PARAMETER))
                    .getAsBoolean();
        if (code == null) {
            sendEvalModal(event, startupScript);
        } else {
            handleEval(event, event.getUser(), true, code.getAsString(), startupScript);
        }
    }

    private void sendEvalModal(SlashCommandInteractionEvent event, boolean startupScript) {
        TextInput body = TextInput
            .create(JSHELL_TEXT_INPUT_ID
                    + (startupScript ? "|" + JSHELL_STARTUP_SCRIPT_PARAMETER : ""),
                    "Enter code to evaluate.", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Put your code here.")
            .setRequiredRange(MIN_MESSAGE_INPUT_LENGTH, MAX_MESSAGE_INPUT_LENGTH)
            .build();

        Modal modal = Modal.create(generateComponentId(), "JShell").addActionRow(body).build();
        event.replyModal(modal).queue();
    }

    /**
     * Handle evaluation of code.
     *
     * @param replyCallback the callback to reply to
     * @param user the user, if null, will create a single use session
     * @param showCode if the embed should contain the original code
     * @param startupScript if the startup script should be used or not
     * @param code the code
     */
    private void handleEval(IReplyCallback replyCallback, @Nullable User user, boolean showCode,
            String code, boolean startupScript) {
        replyCallback.deferReply().queue(interactionHook -> {
            try {
                interactionHook
                    .editOriginalEmbeds(
                            jshellEval.evaluateAndRespond(user, code, showCode, startupScript))
                    .queue();
            } catch (RequestFailedException e) {
                interactionHook.editOriginalEmbeds(createUnexpectedErrorEmbed(user, e)).queue();
            }
        });
    }

    private void handleSnippetsCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue(interactionHook -> {
            OptionMapping userOption = event.getOption(JSHELL_USER_PARAMETER);
            User user = userOption == null ? event.getUser() : userOption.getAsUser();
            OptionMapping includeStartupScriptOption =
                    event.getOption(JSHELL_INCLUDE_STARTUP_SCRIPT_PARAMETER);
            boolean includeStartupScript =
                    includeStartupScriptOption != null && includeStartupScriptOption.getAsBoolean();
            List<String> snippets;
            try {
                snippets = jshellEval.getApi()
                    .snippetsSession(user.getId(), includeStartupScript)
                    .snippets();
            } catch (RequestFailedException e) {
                if (e.getStatus() == JShellApi.SESSION_NOT_FOUND) {
                    interactionHook.editOriginalEmbeds(createSessionNotFoundErrorEmbed(user))
                        .queue();
                } else {
                    interactionHook.editOriginalEmbeds(createUnexpectedErrorEmbed(user, e)).queue();
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
                sendSnippetsAsEmbed(interactionHook, user, snippets);
            } else if (snippets.stream()
                .mapToInt(s -> (s + "// Snippet 10").getBytes().length)
                .sum() < Message.MAX_FILE_SIZE) {
                sendSnippetsAsFile(interactionHook, user, snippets);
            } else {
                sendSnippetsTooLong(interactionHook, user);
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
            jshellEval.getApi().closeSession(event.getUser().getId());
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

    private void handleStartupScriptCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue(interactionHook -> {
            try {
                String startupScript = jshellEval.getApi().startupScript();
                interactionHook
                    .editOriginalEmbeds(new EmbedBuilder().setColor(Colors.SUCCESS_COLOR)
                        .setAuthor(event.getUser().getName())
                        .setTitle("Startup script")
                        .setDescription("```java\n" + startupScript + "```")
                        .build())
                    .queue();
            } catch (RequestFailedException e) {
                event.replyEmbeds(createUnexpectedErrorEmbed(event.getUser(), e)).queue();
            }
        });
    }

    private MessageEmbed createSessionNotFoundErrorEmbed(User user) {
        return new EmbedBuilder().setAuthor(user.getName() + "'s result")
            .setColor(Colors.ERROR_COLOR)
            .setDescription("Could not find session for user " + user.getName())
            .build();
    }

    private MessageEmbed createUnexpectedErrorEmbed(@Nullable User user, RequestFailedException e) {
        EmbedBuilder embedBuilder = new EmbedBuilder().setColor(Colors.ERROR_COLOR)
            .setDescription("Request failed: " + e.getMessage());
        if (user != null) {
            embedBuilder.setAuthor(user.getName() + "'s result");
        }
        return embedBuilder.build();
    }
}
