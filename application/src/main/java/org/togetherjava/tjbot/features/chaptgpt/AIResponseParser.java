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
        List<CodeBlockIndexPair> codeMarkIndexPairs = new ArrayList<>();

        int firstCodeBlockMarkIndex;
        while ((firstCodeBlockMarkIndex = response.indexOf("```")) != -1) {
            // Assuming that code marks come in pairs...
            int secondCodeBlockMarkIndex = response.indexOf("```");
            codeMarkIndexPairs
                .add(new CodeBlockIndexPair(firstCodeBlockMarkIndex, secondCodeBlockMarkIndex));
        }

        List<String> brokenUpAiResponse = new ArrayList<>();
        if (codeMarkIndexPairs.stream()
            .mapToInt(CodeBlockIndexPair::getLength)
            .allMatch(i -> i < 2000)) {

            int begin = 0;
            for (CodeBlockIndexPair codeBlockIndexPair : codeMarkIndexPairs) {
                int end = codeBlockIndexPair.getBeginIndex();
                brokenUpAiResponse.add(response.substring(begin, end));

                begin = end;
                // Add three because index only really captures first `.
                end = codeBlockIndexPair.getEndIndex() + 3;
                brokenUpAiResponse.add(response.substring(begin, end));

                begin = end;
            }
        } else {
            //
        }

        return brokenUpAiResponse.toArray(new String[0]);
    }

    static class CodeBlockIndexPair {
        private final int beginIndex;
        private final int endIndex;

        public CodeBlockIndexPair(int beginIndex, int endIndex) {
            this.beginIndex = beginIndex;
            this.endIndex = endIndex;
        }

        public int getBeginIndex() {
            return beginIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }

        public int getLength() {
            return endIndex - beginIndex;
        }
    }
}
