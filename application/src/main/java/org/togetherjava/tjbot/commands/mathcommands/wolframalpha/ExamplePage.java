package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonRootName("examplepage")
@JsonIgnoreProperties(ignoreUnknown = true)
final class ExamplePage {

    @JacksonXmlProperty(isAttribute = true)
    private String category;
    @JacksonXmlProperty(isAttribute = true)
    private String url;

    @SuppressWarnings("unused")
    public String getCategory() {
        return category;
    }

    @SuppressWarnings("unused")
    public void setCategory(String category) {
        this.category = category;
    }

    @SuppressWarnings("unused")
    public String getUrl() {
        return url;
    }

    @SuppressWarnings("unused")
    public void setUrl(String url) {
        this.url = url;
    }
}
