package org.togetherjava.tjbot.features.chatgpt;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

class AIResponseParserTest {
    private static final Logger logger = LoggerFactory.getLogger(AIResponseParserTest.class);

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void correctResponseLength(int fileNumber) {
        try (InputStream in = getClass().getClassLoader()
            .getResourceAsStream("AITestsResponses/test" + fileNumber + ".txt")) {
            String response = new String(Objects.requireNonNull(in).readAllBytes());
            String[] aiResponse = AIResponseParser.parse(response);

            testResponseLength(aiResponse);
            toLog(aiResponse);
        } catch (IOException | NullPointerException ex) {
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
            logger.info(response);
        }
    }
}
