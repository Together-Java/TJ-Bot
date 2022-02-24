package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonRootName("relatedexample")
@JsonIgnoreProperties(ignoreUnknown = true)
final class RelatedExample {

    @JacksonXmlProperty(isAttribute = true)
    private String input;
    @JacksonXmlProperty(isAttribute = true, localName = "desc")
    private String description;
    @JacksonXmlProperty(isAttribute = true)
    private String category;
    @JacksonXmlProperty(isAttribute = true, localName = "categorythumb")
    private String categoryThumb;
    @JacksonXmlProperty(isAttribute = true, localName = "categorypage")
    private String categoryPage;

    @SuppressWarnings("unused")
    public String getInput() {
        return input;
    }

    @SuppressWarnings("unused")
    public void setInput(String input) {
        this.input = input;
    }

    @SuppressWarnings("unused")
    public String getDescription() {
        return description;
    }

    @SuppressWarnings("unused")
    public void setDescription(String description) {
        this.description = description;
    }

    @SuppressWarnings("unused")
    public String getCategory() {
        return category;
    }

    @SuppressWarnings("unused")
    public void setCategory(String category) {
        this.category = category;
    }

    @SuppressWarnings("unused")
    public String getCategoryThumb() {
        return categoryThumb;
    }

    @SuppressWarnings("unused")
    public void setCategoryThumb(String categoryThumb) {
        this.categoryThumb = categoryThumb;
    }

    @SuppressWarnings("unused")
    public String getCategoryPage() {
        return categoryPage;
    }

    @SuppressWarnings("unused")
    public void setCategoryPage(String categoryPage) {
        this.categoryPage = categoryPage;
    }
}
