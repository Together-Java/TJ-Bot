package org.togetherjava.tjbot.features.mathcommands.wolframalpha.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

/**
 * See the Wolfram Alpha API.
 */
@JsonRootName("error")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Error {
    private int code;
    @JsonProperty("msg")
    private String message;

    public int getCode() {
        return code;
    }

    @SuppressWarnings("unused")
    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    @SuppressWarnings("unused")
    public void setMessage(String message) {
        this.message = message;
    }

}
