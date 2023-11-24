package org.togetherjava.tjbot.features.mathcommands.wolframalpha.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * See the Wolfram Alpha API.
 */
@JsonRootName("img")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WolframAlphaImage {
    @JacksonXmlProperty(isAttribute = true, localName = "src")
    private String source;
    @JacksonXmlProperty(isAttribute = true)
    private int width;
    @JacksonXmlProperty(isAttribute = true)
    private int height;
    @JacksonXmlProperty(isAttribute = true)
    private String title;

    public String getTitle() {
        return title;
    }

    @SuppressWarnings("unused")
    public void setTitle(String title) {
        this.title = title;
    }

    public String getSource() {
        return source;
    }

    @SuppressWarnings("unused")
    public void setSource(String source) {
        this.source = source;
    }

    @SuppressWarnings("unused")
    public int getWidth() {
        return width;
    }

    @SuppressWarnings("unused")
    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    @SuppressWarnings("unused")
    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public String toString() {
        return "WolframAlphaImage{" + "source='" + source + '\'' + ", width=" + width + ", height="
                + height + ", title='" + title + '\'' + '}';
    }
}
