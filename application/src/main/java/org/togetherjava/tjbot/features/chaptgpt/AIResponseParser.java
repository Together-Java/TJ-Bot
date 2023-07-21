package org.togetherjava.tjbot.features.chaptgpt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AIResponseParser {

    private AIResponseParser() {}

    private static final Logger logger = LoggerFactory.getLogger(AIResponseParser.class);
    private static final int RESPONSE_LENGTH_LIMIT = 2_000;

    public static Optional<String[]> parse(String response) {
        String[] aiResponses;
        if (response.length() > RESPONSE_LENGTH_LIMIT) {
            logger.warn(
                    "Response from AI was longer than allowed limit. "
                            + "The answer was cut up to max {} characters length messages",
                    RESPONSE_LENGTH_LIMIT);
            aiResponses = breakupAiResponse(response);
        } else {
            aiResponses = new String[] {response};
        }

        return Optional.of(aiResponses);
    }

    private static String[] breakupAiResponse(String response) {
        int begin = 0;
        int end = RESPONSE_LENGTH_LIMIT - 3;
        int occurences = 0;
        int lastOccurence = 0;
        boolean addedCodeMark = false;

        List<String> responseChunks = new ArrayList<>();

        while (begin < response.length() - 1) {
            String responseChunk = response.substring(begin, end);
            if (addedCodeMark) {
                addedCodeMark = false;
                responseChunk = responseChunk.replaceFirst("```", "");
            }
            while ((lastOccurence = responseChunk.indexOf("```", lastOccurence) + 1) != 0) {
                occurences++;
            }

            if (responseChunk.contains("```") && occurences % 2 == 1) {
                responseChunk = responseChunk.concat("```");
                addedCodeMark = true;
                responseChunks.add(responseChunk);
            } else {
                responseChunks.add(responseChunk);
            }

            end += RESPONSE_LENGTH_LIMIT - 3;
            if (end > response.length() - 1) {
                end = response.length() - 1;
            }
            begin += RESPONSE_LENGTH_LIMIT - 3;
        }

        return responseChunks.toArray(new String[0]);
    }
}
