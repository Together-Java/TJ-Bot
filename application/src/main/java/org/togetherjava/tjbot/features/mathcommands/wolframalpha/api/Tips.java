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
 * Example Query: bhefjuhkynmbtg
 *
 * Response:
 * 
 * <pre>
 * {@code
 * <tips count='1'>
 *   <tip text='Check your spelling, and use English' />
 *  </tips>
 *  }
 * </pre>
 */
@JsonRootName("tips")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Tips {
    @JacksonXmlProperty(isAttribute = true)
    private int count;

    @JsonProperty("tip")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Tip> tipList;

    @SuppressWarnings("unused")
    public int getCount() {
        return count;
    }

    @SuppressWarnings("unused")
    public void setCount(int count) {
        this.count = count;
    }

    @SuppressWarnings("unused")
    public List<Tip> getTipList() {
        return Collections.unmodifiableList(tipList);
    }

    @SuppressWarnings("unused")
    public void setTipList(List<Tip> tipList) {
        this.tipList = new ArrayList<>(tipList);
    }

}
