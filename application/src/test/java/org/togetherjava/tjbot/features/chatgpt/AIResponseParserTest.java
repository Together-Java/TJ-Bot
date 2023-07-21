package org.togetherjava.tjbot.features.chatgpt;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.chaptgpt.AIResponseParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

class AIResponseParserTest {
    private static final Logger logger = LoggerFactory.getLogger(AIResponseParserTest.class);

    @ParameterizedTest
    @ValueSource(strings = {"test1"})
    void correctResponseLength(String filename) {
        try (InputStream in =
                getClass().getClassLoader().getResourceAsStream("AITestResponses/" + filename)) {
            assert in != null;
            String response = new String(in.readAllBytes());
            Optional<String[]> aiResponse = AIResponseParser.parse(response);

            if (aiResponse.isPresent()) {
                String[] chunks = aiResponse.get();
                testResponseLength(chunks);
                toLog(chunks);
            } else {
                Assertions.fail();
            }
        } catch (IOException ex) {
            logger.error("{}", ex.getMessage());
            Assertions.fail();
        }
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
