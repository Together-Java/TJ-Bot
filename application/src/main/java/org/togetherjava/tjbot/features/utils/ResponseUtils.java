package org.togetherjava.tjbot.features.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscribers;
import java.util.Optional;

/**
 * Handle the parsing of json in a http request.
 */
public class ResponseUtils {
    private ResponseUtils() {
    }

    /**
     * Creates a body handler which will parse the body of the request. If the parsing fails, an
     * IOException is thrown. if the request status code is not 200 or 204, a
     * UncheckedRequestFailedException is thrown wrapped in an IOException.
     * 
     * @param type the class to parse the json into
     * @param mapper the json mapper
     * @return the body handler
     * @param <T> the type of the class to parse the json into
     */
    public static <T> BodyHandler<T> ofJson(Class<T> type, ObjectMapper mapper) {
        return responseInfo -> BodySubscribers.mapping(BodySubscribers.ofByteArray(), bytes -> {
            if (responseInfo.statusCode() == 200 || responseInfo.statusCode() == 204) {
                return uncheckedParseJson(type, mapper, bytes);
            }
            String errorMessage = tryParseError(bytes, mapper)
                .orElse("Request failed with status: " + responseInfo.statusCode());
            throw new UncheckedRequestFailedException(errorMessage, responseInfo.statusCode());
        });
    }

    private static <T> T uncheckedParseJson(Class<T> type, ObjectMapper mapper, byte[] value) {
        try {
            return mapper.readValue(value, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Error parsing json", e);
        }
    }

    private static Optional<String> tryParseError(byte[] bytes, ObjectMapper mapper) {
        try {
            return Optional.ofNullable(mapper.readTree(bytes).get("error").asText());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

}
