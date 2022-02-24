package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("error")
@JsonIgnoreProperties(ignoreUnknown = true)
final class Error {
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
