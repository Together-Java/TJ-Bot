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
 * Example Query: btuyghe
 *
 * Response:
 * 
 * <pre>
 * {@code
 * <didyoumeans count='1'>
 *   <didyoumean score='0.415939' level='medium'>tighe</didyoumean>
 *  </didyoumeans>
 * }
 * </pre>
 */
@JsonRootName("didyoumeans")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DidYouMeans {
    @JacksonXmlProperty(isAttribute = true)
    private int count;

    @JsonProperty("didyoumean")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<DidYouMean> didYouMeanTips;

    @SuppressWarnings("unused")
    public int getCount() {
        return count;
    }

    @SuppressWarnings("unused")
    public void setCount(int count) {
        this.count = count;
    }

    public List<DidYouMean> getDidYouMeanTips() {
        return Collections.unmodifiableList(didYouMeanTips);
    }

    @SuppressWarnings("unused")
    public void setDidYouMeanTips(List<DidYouMean> didYouMeanTips) {
        this.didYouMeanTips = new ArrayList<>(didYouMeanTips);
    }

}
