package org.togetherjava.tjbot.features.mathcommands.wolframalpha.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * See the Wolfram Alpha API.
 */
@JsonRootName("subpod")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SubPod {
    @JacksonXmlProperty(isAttribute = true)
    private String title;

    @JsonProperty("img")
    private WolframAlphaImage image;
    @JsonProperty("plaintext")
    private PlainText plainText;

    @SuppressWarnings("unused")
    public String getTitle() {
        return title;
    }

    @SuppressWarnings("unused")
    public void setTitle(String title) {
        this.title = title;
    }

    public WolframAlphaImage getImage() {
        return image;
    }

    public void setImage(WolframAlphaImage image) {
        this.image = image;
    }

    public PlainText getPlainText() {
        return plainText;
    }

    public void setPlainText(PlainText plainText) {
        this.plainText = plainText;
    }
}
