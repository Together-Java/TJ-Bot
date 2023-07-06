package org.togetherjava.tjbot.features.chatgpt;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.chaptgpt.AIResponseParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

class AIResponseParserTest {
    private static final Logger logger = LoggerFactory.getLogger(AIResponseParserTest.class);
    private AIResponseParser aiResponseParser;

    @ParameterizedTest
    @CsvFileSource(resources = "/AITestResponses/responses")
    void correctResponseLength(File file) {
        try {
            BufferedReader input =
                    new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath())));
            Optional<String[]> aiResponse = AIResponseParser.parse(input.lines().toString());

            aiResponse.ifPresentOrElse((responses) -> {
                testResponseLength(responses);
                toLog(responses);
            }, Assertions.fail());
        } catch (IOException ex) {
            logger.error("{}", ex.getMessage());
            Assertions.fail();
        }
    }

    private ChatCompletionResult createChatCompletionResult(String message) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(message);

        ChatCompletionChoice chatCompletionChoice = new ChatCompletionChoice();
        chatCompletionChoice.setMessage(chatMessage);

        ChatCompletionResult chatCompletionResult = new ChatCompletionResult();
        chatCompletionResult.setChoices(List.of(chatCompletionChoice));
        return chatCompletionResult;
    }

    private void testResponseLength(String[] responses) {
        int AI_RESPONSE_CHARACTER_LIMIT = 2000;
        for (String response : responses) {
            Assertions.assertTrue(response.length() <= AI_RESPONSE_CHARACTER_LIMIT,
                    "Response length is NOT within character limit: " + response.length());
            logger.warn("Response length was: {}", response.length());
        }
    }

    private void toLog(String[] responses) {
        for (String response : responses) {
            logger.warn(response);
        }
    }
}
