package org.togetherjava.tjbot.features.jshell;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.FileUpload;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.jshell.backend.JShellApi;
import org.togetherjava.tjbot.features.utils.Colors;
import org.togetherjava.tjbot.features.utils.ConnectionFailedException;
import org.togetherjava.tjbot.features.utils.MessageUtils;
import org.togetherjava.tjbot.features.utils.RequestFailedException;

import javax.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The JShell command AKA {@code /jshell}, provide functionalities to create JShell sessions,
 * evaluate code, etc.
 * <p>
 * Example: {@code /jshell eval code:2+2}
 */
public class JShellCommand extends SlashCommandAdapter {
    private static final String TEXT_INPUT_PART_ID = "jshell";
    private static final String JSHELL_COMMAND = "jshell";
    private static final String VERSION_SUBCOMMAND = "version";
    private static final String EVAL_SUBCOMMAND = "eval";
    private static final String SNIPPETS_SUBCOMMAND = "snippets";
    private static final String CLOSE_SUBCOMMAND = "shutdown";
    private static final String STARTUP_SCRIPT_SUBCOMMAND = "startup-script";
    private static final String CODE_PARAMETER = "code";
    private static final String STARTUP_SCRIPT_PARAMETER = "startup-script";
    private static final String USER_PARAMETER = "user";
    private static final String INCLUDE_STARTUP_SCRIPT_PARAMETER = "include-startup-script";

    private static final int MIN_MESSAGE_INPUT_LENGTH = 0;
    private static final int MAX_MESSAGE_INPUT_LENGTH = TextInput.MAX_VALUE_LENGTH;

    private static final String MAX_SNIPPETS_FILE_PREFIX = " // Snippet 1000";
    private static final String MAX_SNIPPETS_EMBED_PREFIX = "Snippet 10```java\n```";
    private final JShellEval jshellEval;

    /**
     * Creates an instance of the command.
     * 
     * @param jshellEval used to execute java code and build visual result
     */
    public JShellCommand(JShellEval jshellEval) {
        super(JSHELL_COMMAND, "Execute Java code in Discord!", CommandVisibility.GUILD);

        this.jshellEval = jshellEval;

        getData().addSubcommands(
                new SubcommandData(VERSION_SUBCOMMAND, "Get the version of JShell"),
                new SubcommandData(EVAL_SUBCOMMAND,
                        "Evaluate java code in JShell, submit the command without code for inputting longer, multi-line code.")
                    .addOption(OptionType.STRING, CODE_PARAMETER,
                            "Code to evaluate. Leave empty to input longer, multi-line code.")
                    .addOption(OptionType.BOOLEAN, STARTUP_SCRIPT_PARAMETER,
                            "If the startup script should be loaded, true by default."),
                new SubcommandData(SNIPPETS_SUBCOMMAND,
                        "Display your snippets, or the snippets of the specified user if any.")
                    .addOption(OptionType.USER, USER_PARAMETER,
                            "User to get the snippets from. If null, get your snippets.")
                    .addOption(OptionType.BOOLEAN, INCLUDE_STARTUP_SCRIPT_PARAMETER,
                            "if the startup script should be included, false by default."),
                new SubcommandData(CLOSE_SUBCOMMAND, "Close your session."),
                new SubcommandData(STARTUP_SCRIPT_SUBCOMMAND, "Display the startup script."));
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        switch (Objects.requireNonNull(event.getSubcommandName())) {
            case VERSION_SUBCOMMAND -> handleVersionCommand(event);
            case EVAL_SUBCOMMAND -> handleEvalCommand(event);
            case SNIPPETS_SUBCOMMAND -> handleSnippetsCommand(event);
            case CLOSE_SUBCOMMAND -> handleCloseCommand(event);
            case STARTUP_SCRIPT_SUBCOMMAND -> handleStartupScriptCommand(event);
            default ->
                throw new AssertionError("Unexpected Subcommand: " + event.getSubcommandName());
        }
    }

    @Override
    public void onModalSubmitted(ModalInteractionEvent event, List<String> args) {
        ModalMapping mapping = event.getValue(TEXT_INPUT_PART_ID + "|" + STARTUP_SCRIPT_PARAMETER);
        boolean startupScript = mapping != null;
        if (mapping == null) {
            mapping = event.getValue(TEXT_INPUT_PART_ID);
        }
        if (mapping != null) {
            handleEval(event, event.getMember(), true, mapping.getAsString(), startupScript);
        }
    }

    private void handleVersionCommand(SlashCommandInteractionEvent event) {
        String code = """
                System.out.println("Version: " + Runtime.version());
                System.out.println("Vendor:  " + System.getProperty("java.vendor"));
                System.out.println("OS:      " + System.getProperty("os.name"));
                System.out.println("Arch:    " + System.getProperty("os.arch"));
                """;
        handleEval(event, null, false, code, false);
    }

    private void handleEvalCommand(SlashCommandInteractionEvent event) {
        OptionMapping code = event.getOption(CODE_PARAMETER);
        boolean startupScript = event.getOption(STARTUP_SCRIPT_PARAMETER) == null
                || Objects.requireNonNull(event.getOption(STARTUP_SCRIPT_PARAMETER)).getAsBoolean();
        if (code == null) {
            sendEvalModal(event, startupScript);
        } else {
            handleEval(event, event.getMember(), true, code.getAsString(), startupScript);
        }
    }

    private void sendEvalModal(SlashCommandInteractionEvent event, boolean startupScript) {
        TextInput body = TextInput
            .create(TEXT_INPUT_PART_ID + (startupScript ? "|" + STARTUP_SCRIPT_PARAMETER : ""),
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
     * @param member the member, if null, will create a single use session
     * @param showCode if the embed should contain the original code
     * @param startupScript if the startup script should be used or not
     * @param code the code
     */
    private void handleEval(IReplyCallback replyCallback, @Nullable Member member, boolean showCode,
            String code, boolean startupScript) {
        replyCallback.deferReply().queue(interactionHook -> {
            try {
                MessageEmbed messageEmbed =
                        jshellEval.evaluateAndRespond(member, code, showCode, startupScript);
                interactionHook.sendMessageEmbeds(messageEmbed).queue();
            } catch (RequestFailedException | ConnectionFailedException e) {
                interactionHook.editOriginalEmbeds(createUnexpectedErrorEmbed(member, e)).queue();
            }
        });
    }

    private void handleSnippetsCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue(interactionHook -> {
            OptionMapping userOption = event.getOption(USER_PARAMETER);
            Member member = Objects
                .requireNonNull(userOption == null ? event.getMember() : userOption.getAsMember());
            OptionMapping includeStartupScriptOption =
                    event.getOption(INCLUDE_STARTUP_SCRIPT_PARAMETER);
            boolean includeStartupScript =
                    includeStartupScriptOption != null && includeStartupScriptOption.getAsBoolean();
            List<String> snippets;
            try {
                snippets = jshellEval.getApi()
                    .snippetsSession(member.getId(), includeStartupScript)
                    .snippets();
            } catch (RequestFailedException e) {
                if (e.getStatus() == JShellApi.SESSION_NOT_FOUND) {
                    interactionHook.editOriginalEmbeds(createSessionNotFoundErrorEmbed(member))
                        .queue();
                } else {
                    interactionHook.editOriginalEmbeds(createUnexpectedErrorEmbed(member, e))
                        .queue();
                }
                return;
            } catch (ConnectionFailedException e) {
                interactionHook.editOriginalEmbeds(createUnexpectedErrorEmbed(member, e)).queue();
                return;
            }

            sendSnippets(interactionHook, member, snippets);
        });
    }

    private void sendSnippets(InteractionHook interactionHook, Member member,
            List<String> snippets) {
        if (canBeSentAsEmbed(snippets)) {
            sendSnippetsAsEmbed(interactionHook, member, snippets);
        } else if (canBeSentAsFile(snippets)) {
            sendSnippetsAsFile(interactionHook, member, snippets);
        } else {
            sendSnippetsTooLong(interactionHook, member);
        }
    }

    private boolean canBeSentAsEmbed(List<String> snippets) {
        return snippets.stream().noneMatch(s -> s.length() >= MessageEmbed.VALUE_MAX_LENGTH)
                && snippets.stream()
                    .mapToInt(s -> (s + MAX_SNIPPETS_EMBED_PREFIX).length())
                    .sum() < MessageEmbed.EMBED_MAX_LENGTH_BOT - 100
                && snippets.size() <= MessageUtils.MAXIMUM_VISIBLE_EMBEDS;
    }

    private void sendSnippetsAsEmbed(InteractionHook interactionHook, Member member,
            List<String> snippets) {
        EmbedBuilder builder = new EmbedBuilder().setColor(Colors.SUCCESS_COLOR)
            .setAuthor(member.getEffectiveName())
            .setTitle(snippetsTitle(member));
        int i = 1;
        for (String snippet : snippets) {
            builder.addField("Snippet " + i, "```java\n" + snippet + "```", false);
            i++;
        }
        interactionHook.editOriginalEmbeds(builder.build()).queue();
    }

    private boolean canBeSentAsFile(List<String> snippets) {
        return snippets.stream()
            .mapToInt(s -> (s + MAX_SNIPPETS_FILE_PREFIX).getBytes().length)
            .sum() < Message.MAX_FILE_SIZE;
    }

    private void sendSnippetsAsFile(InteractionHook interactionHook, Member member,
            List<String> snippets) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String snippet : snippets) {
            snippet = snippet.replaceAll("^\n+", "");
            if (!snippet.endsWith("\n")) {
                snippet += "\n";
            }
            int idxOf = snippet.indexOf("\n");
            int insertIndex = idxOf != -1 ? idxOf : snippet.length();
            sb.append(snippet, 0, insertIndex)
                .append(" // Snippet ")
                .append(i)
                .append(snippet.substring(insertIndex));
            i++;
        }
        interactionHook
            .editOriginalEmbeds(new EmbedBuilder().setColor(Colors.SUCCESS_COLOR)
                .setAuthor(member.getEffectiveName())
                .setTitle(snippetsTitle(member))
                .build())
            .setFiles(
                    FileUpload.fromData(sb.toString().getBytes(), snippetsTitle(member) + ".java"))
            .queue();
    }

    private String snippetsTitle(Member member) {
        return member.getEffectiveName() + "'s snippets";
    }

    private void sendSnippetsTooLong(InteractionHook interactionHook, Member member) {
        interactionHook
            .editOriginalEmbeds(new EmbedBuilder().setColor(Colors.ERROR_COLOR)
                .setAuthor(member.getEffectiveName())
                .setTitle("Too much code to send...")
                .build())
            .queue();
    }

    private void handleCloseCommand(SlashCommandInteractionEvent event) {
        try {
            jshellEval.getApi().closeSession(event.getUser().getId());
        } catch (RequestFailedException e) {
            if (e.getStatus() == JShellApi.SESSION_NOT_FOUND) {
                event
                    .replyEmbeds(createSessionNotFoundErrorEmbed(
                            Objects.requireNonNull(event.getMember())))
                    .queue();
            } else {
                event.replyEmbeds(createUnexpectedErrorEmbed(event.getMember(), e)).queue();
            }
            return;
        } catch (ConnectionFailedException e) {
            event.replyEmbeds(createUnexpectedErrorEmbed(event.getMember(), e)).queue();
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
            } catch (RequestFailedException | ConnectionFailedException e) {
                event.replyEmbeds(createUnexpectedErrorEmbed(event.getMember(), e)).queue();
            }
        });
    }

    private MessageEmbed createSessionNotFoundErrorEmbed(Member member) {
        return new EmbedBuilder().setAuthor(member.getEffectiveName() + "'s result")
            .setColor(Colors.ERROR_COLOR)
            .setDescription("Could not find session for member " + member.getEffectiveName())
            .build();
    }

    private MessageEmbed createUnexpectedErrorEmbed(@Nullable Member member, Exception e) {
        EmbedBuilder embedBuilder = new EmbedBuilder().setColor(Colors.ERROR_COLOR)
            .setDescription("Request failed: " + e.getMessage());
        if (member != null) {
            embedBuilder.setAuthor(member.getEffectiveName() + "'s result");
        }
        return embedBuilder.build();
    }
}
