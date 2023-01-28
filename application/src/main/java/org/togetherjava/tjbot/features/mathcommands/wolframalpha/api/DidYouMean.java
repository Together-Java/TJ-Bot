package org.togetherjava.tjbot.features.mathcommands.wolframalpha.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

/**
 * See the Wolfram Alpha API.
 */
@JsonRootName("didyoumean")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DidYouMean {
    @JacksonXmlText
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
