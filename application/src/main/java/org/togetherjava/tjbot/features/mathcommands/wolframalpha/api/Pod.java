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
@JsonRootName("pod")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Pod {
    @JacksonXmlProperty(isAttribute = true)
    private String title;
    @JacksonXmlProperty(isAttribute = true)
    private boolean error;
    @JacksonXmlProperty(isAttribute = true)
    private int position;
    @JacksonXmlProperty(isAttribute = true)
    private String scanner;
    @JacksonXmlProperty(isAttribute = true)
    private String id;
    @JacksonXmlProperty(isAttribute = true, localName = "numsubpods")
    private int numberOfSubPods;

    @JsonProperty("subpod")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SubPod> subPods;

    public String getTitle() {
        return title;
    }

    @SuppressWarnings("unused")
    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    @SuppressWarnings("unused")
    public int getPosition() {
        return position;
    }

    @SuppressWarnings("unused")
    public void setPosition(int position) {
        this.position = position;
    }

    @SuppressWarnings("unused")
    public String getScanner() {
        return scanner;
    }

    @SuppressWarnings("unused")
    public void setScanner(String scanner) {
        this.scanner = scanner;
    }

    @SuppressWarnings("unused")
    public String getId() {
        return id;
    }

    @SuppressWarnings("unused")
    public void setId(String id) {
        this.id = id;
    }

    @SuppressWarnings("unused")
    public int getNumberOfSubPods() {
        return numberOfSubPods;
    }

    @SuppressWarnings("unused")
    public void setNumberOfSubPods(int numberOfSubPods) {
        this.numberOfSubPods = numberOfSubPods;
    }

    public List<SubPod> getSubPods() {
        return Collections.unmodifiableList(subPods);
    }

    @SuppressWarnings("unused")
    public void setSubPods(List<SubPod> subPods) {
        this.subPods = new ArrayList<>(subPods);
    }
}
