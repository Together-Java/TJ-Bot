package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * Example Query: btuyghe <br>
 * Result: {@code <didyoumeans count='1'>
 *   <didyoumean score='0.415939' level='medium'>tighe</didyoumean>
 *  </didyoumeans> }
 * </p>
 */
@JsonRootName("didyoumeans")
@JsonIgnoreProperties(ignoreUnknown = true)
final class DidYouMeans {

    @JacksonXmlProperty(isAttribute = true)
    private int count;

    @JsonProperty("didyoumean")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<DidYouMean> didYouMeans;

    @SuppressWarnings("unused")
    public int getCount() {
        return count;
    }

    @SuppressWarnings("unused")
    public void setCount(int count) {
        this.count = count;
    }

    public List<DidYouMean> getDidYouMeans() {
        return Collections.unmodifiableList(didYouMeans);
    }

    @SuppressWarnings("unused")
    public void setDidYouMeans(List<DidYouMean> didYouMeans) {
        this.didYouMeans = new ArrayList<>(didYouMeans);
    }



}
