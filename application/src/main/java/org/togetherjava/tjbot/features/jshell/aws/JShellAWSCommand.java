package org.togetherjava.tjbot.features.jshell.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.jshell.aws.exceptions.JShellAPIException;

import java.awt.Color;

/**
 * This class contains the complete logic for the /jshell-aws command.
 *
 * @author Suraj Kumar
 */
public class JShellAWSCommand extends SlashCommandAdapter {
    private static final Logger logger = LogManager.getLogger(JShellAWSCommand.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CODE_PARAMETER = "code";
    private final JShellService jShellService;

    /**
     * Constructs a new JShellAWSCommand
     *
     * @param jShellService The service class to make requests against AWS
     */
    public JShellAWSCommand(JShellService jShellService) {
        super("jshell-aws", "Execute Java code in Discord!", CommandVisibility.GUILD);
        getData().addOption(OptionType.STRING, CODE_PARAMETER, "The code to execute using JShell");
        this.jShellService = jShellService;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        Member member = event.getMember();

        if (member == null) {
            event.reply("Member that executed the command is no longer available, won't execute")
                .queue();
            return;
        }

        logger.info("JShell AWS invoked by {} in channel {}", member.getAsMention(),
                event.getChannelId());

        OptionMapping input = event.getOption(CODE_PARAMETER);

        if (input == null || input.getAsString().isEmpty()) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setDescription(member.getAsMention()
                    + ", you forgot to provide the code for JShell to evaluate or it was too short!\nTry running the command again and make sure to select the code option");
            eb.setColor(Color.ORANGE);
            event.replyEmbeds(eb.build()).queue();
            return;
        }

        event.deferReply().queue();

        InteractionHook hook = event.getHook();

        String code = input.getAsString();

        try {
            respondWithJShellOutput(hook, jShellService.sendRequest(new JShellRequest(code)), code);
        } catch (JShellAPIException jShellAPIException) {
            handleJShellAPIException(hook, jShellAPIException, member, code);
        } catch (Exception e) {
            logger.error(
                    "An error occurred while sending/receiving request from the AWS JShell API", e);
            respondWithSevereAPIError(hook, code);
        }
    }

    private static void handleJShellAPIException(InteractionHook hook,
            JShellAPIException jShellAPIException, Member member, String code) {
        switch (jShellAPIException.getStatusCode()) {
            case 400 -> {
                logger.warn("HTTP 400 error occurred with the JShell AWS API {}",
                        jShellAPIException.getBody());
                respondWithInputError(hook, jShellAPIException.getBody());
            }
            case 408 -> respondWithTimeout(hook, member, code);
            default -> {
                logger.error("HTTP {} received from JShell AWS API {}",
                        jShellAPIException.getStatusCode(), jShellAPIException.getBody());
                respondWithSevereAPIError(hook, code);
            }
        }
    }

    private static void respondWithJShellOutput(InteractionHook hook, JShellResponse response,
            String code) {
        // Extracted as fields to be compliant with Sonar
        final String SNIPPET_SECTION_TITLE = "## Snippets\n";
        final String BACKTICK = "`";
        final String NEWLINE = "\n";
        final String DOUBLE_NEWLINE = "\n\n";
        final String STATUS = "**Status**: ";
        final String OUTPUT_SECTION_TITLE = "**Output**\n";
        final String JAVA_CODE_BLOCK_START = "```java\n";
        final String CODE_BLOCK_END = "```\n";
        final String DIAGNOSTICS_SECTION_TITLE = "**Diagnostics**\n";
        final String CONSOLE_OUTPUT_SECTION_TITLE = "## Console Output\n";
        final String ERROR_OUTPUT_SECTION_TITLE = "## Error Output\n";

        StringBuilder sb = new StringBuilder();
        sb.append(SNIPPET_SECTION_TITLE);

        for (JShellSnippet snippet : response.events()) {
            sb.append(BACKTICK);
            sb.append(snippet.statement());
            sb.append(BACKTICK).append(DOUBLE_NEWLINE);
            sb.append(STATUS);
            sb.append(snippet.status());
            sb.append(NEWLINE);

            if (snippet.value() != null && !snippet.value().isEmpty()) {
                sb.append(OUTPUT_SECTION_TITLE);
                sb.append(JAVA_CODE_BLOCK_START);
                sb.append(snippet.value());
                sb.append(CODE_BLOCK_END);
            }

            if (!snippet.diagnostics().isEmpty()) {
                sb.append(DIAGNOSTICS_SECTION_TITLE);
                for (String diagnostic : snippet.diagnostics()) {
                    sb.append(BACKTICK).append(diagnostic).append(BACKTICK).append(NEWLINE);
                }
            }
        }

        if (response.outputStream() != null && !response.outputStream().isEmpty()) {
            sb.append(CONSOLE_OUTPUT_SECTION_TITLE);
            sb.append(JAVA_CODE_BLOCK_START);
            sb.append(response.outputStream());
            sb.append(CODE_BLOCK_END);
        }

        if (response.errorStream() != null && !response.errorStream().isEmpty()) {
            sb.append(ERROR_OUTPUT_SECTION_TITLE);
            sb.append(JAVA_CODE_BLOCK_START);
            sb.append(response.errorStream());
            sb.append(CODE_BLOCK_END);
        }

        String description;
        if (sb.length() > 4000) {
            description = sb.substring(0, 500) + "...``` truncated " + (sb.length() - 500)
                    + " characters";
        } else {
            description = sb.toString();
        }

        sendEmbed(hook, description, Color.GREEN, code);
    }

    private static void respondWithInputError(InteractionHook hook, String response) {
        JShellErrorResponse errorResponse;
        try {
            errorResponse = OBJECT_MAPPER.readValue(response, JShellErrorResponse.class);
        } catch (JsonProcessingException e) {
            errorResponse = new JShellErrorResponse(
                    "There was a problem with the input you provided, please check and try again");
        }
        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(errorResponse.error());
        eb.setColor(Color.ORANGE);
        hook.editOriginalEmbeds(eb.build()).queue();
    }

    private static void respondWithTimeout(InteractionHook hook, Member member, String code) {
        sendEmbed(hook, member.getAsMention()
                + " the code you provided took too long and the request has timed out! Consider tweaking your code to run a little faster.",
                Color.ORANGE, code);
    }

    private static void respondWithSevereAPIError(InteractionHook hook, String code) {
        sendEmbed(hook, "An internal error occurred, please try again later", Color.RED, code);
    }

    private static void sendEmbed(InteractionHook hook, String description, Color color,
            String code) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(description);
        eb.setColor(color);
        eb.setFooter("Code that was executed:\n" + code);
        hook.editOriginalEmbeds(eb.build()).queue();
    }
}
