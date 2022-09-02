package org.togetherjava.tjbot.features.mathcommands.wolframalpha.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

/**
 * See the Wolfram Alpha API.
 */
@JsonRootName("plaintext")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlainText {
    @JacksonXmlText
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
