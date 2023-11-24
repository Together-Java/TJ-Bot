package org.togetherjava.tjbot.features.mathcommands.wolframalpha.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * See the Wolfram Alpha API.
 */
@JsonRootName("relatedexamples")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RelatedExamples {
    @JacksonXmlProperty(isAttribute = true)
    private int count;

    @JsonProperty("relatedexample")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<RelatedExample> relatedExampleTips;

    @SuppressWarnings("unused")
    public int getCount() {
        return count;
    }

    @SuppressWarnings("unused")
    public void setCount(int count) {
        this.count = count;
    }

    public List<RelatedExample> getRelatedExampleTips() {
        return Collections.unmodifiableList(relatedExampleTips);
    }

    @SuppressWarnings("unused")
    public void setRelatedExampleTips(List<RelatedExample> relatedExampleTips) {
        this.relatedExampleTips = new ArrayList<>(relatedExampleTips);
    }
}
