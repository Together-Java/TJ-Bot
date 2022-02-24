package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonRootName("queryresult")
@JsonIgnoreProperties(ignoreUnknown = true)
final class QueryResult {
    private static final XmlMapper XML = new XmlMapper();

    @JacksonXmlProperty(isAttribute = true)
    private boolean success;
    @JsonIgnore
    private boolean errorAttribute;
    @JacksonXmlProperty(isAttribute = true, localName = "numpods")
    private int numberOfPods;
    @JacksonXmlProperty(isAttribute = true)
    private String version;
    @JacksonXmlProperty(isAttribute = true, localName = "datatypes")
    private String dataTypes;
    @JacksonXmlProperty(isAttribute = true)
    private double timing;
    @JacksonXmlProperty(isAttribute = true, localName = "timedout")
    private String timedOutPods;
    @JacksonXmlProperty(isAttribute = true, localName = "parsetiming")
    private double parseTiming;
    @JacksonXmlProperty(isAttribute = true, localName = "parsetimedout")
    private boolean parseTimedOut;
    @JacksonXmlProperty(isAttribute = true, localName = "recalculate")
    private String recalculateUrl;

    private Tips tips;
    @JsonProperty("didyoumeans")
    private DidYouMeans didYouMeans;
    @JsonProperty("languagemsg")
    private LanguageMessage languageMessage;
    @JsonProperty("examplepage")
    private ExamplePage examplePage;
    @JsonProperty("futuretopic")
    private FutureTopic futureTopic;
    @JsonProperty("relatedexamples")
    private RelatedExamples relatedExamples;
    @JsonProperty("pod")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Pod> pods;
    @JsonIgnore
    private Error errorTag;

    public boolean isSuccess() {
        return success;
    }

    @SuppressWarnings("unused")
    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isError() {
        return errorAttribute;
    }

    @SuppressWarnings("unused")
    public int getNumberOfPods() {
        return numberOfPods;
    }

    @SuppressWarnings("unused")
    public void setNumberOfPods(int numberOfPods) {
        this.numberOfPods = numberOfPods;
    }

    @SuppressWarnings("unused")
    public String getVersion() {
        return version;
    }

    @SuppressWarnings("unused")
    public void setVersion(String version) {
        this.version = version;
    }

    @SuppressWarnings("unused")
    public String getDataTypes() {
        return dataTypes;
    }

    @SuppressWarnings("unused")
    public void setDataTypes(String dataTypes) {
        this.dataTypes = dataTypes;
    }

    @SuppressWarnings("unused")
    public double getTiming() {
        return timing;
    }

    @SuppressWarnings("unused")
    public void setTiming(double timing) {
        this.timing = timing;
    }

    @SuppressWarnings("unused")
    public String getTimedOutPods() {
        return timedOutPods;
    }

    @SuppressWarnings("unused")
    public void setTimedOutPods(String timedOutPods) {
        this.timedOutPods = timedOutPods;
    }

    @SuppressWarnings("unused")
    public double getParseTiming() {
        return parseTiming;
    }

    @SuppressWarnings("unused")
    public void setParseTiming(double parseTiming) {
        this.parseTiming = parseTiming;
    }

    @SuppressWarnings("unused")
    public boolean isParseTimedOut() {
        return parseTimedOut;
    }

    @SuppressWarnings("unused")
    public void setParseTimedOut(boolean parseTimedOut) {
        this.parseTimedOut = parseTimedOut;
    }

    @SuppressWarnings("unused")
    public String getRecalculateUrl() {
        return recalculateUrl;
    }

    @SuppressWarnings("unused")
    public void setRecalculateUrl(String recalculateUrl) {
        this.recalculateUrl = recalculateUrl;
    }

    public List<Pod> getPods() {
        return Collections.unmodifiableList(pods);
    }

    @SuppressWarnings("unused")
    public void setPods(List<Pod> pods) {
        this.pods = new ArrayList<>(pods);
    }

    public Tips getTips() {
        return tips;
    }

    @SuppressWarnings("unused")
    public void setTips(Tips tips) {
        this.tips = tips;
    }

    public DidYouMeans getDidYouMeans() {
        return didYouMeans;
    }

    @SuppressWarnings("unused")
    public void setDidYouMeans(DidYouMeans didYouMeans) {
        this.didYouMeans = didYouMeans;
    }

    public LanguageMessage getLanguageMessage() {
        return languageMessage;
    }

    @SuppressWarnings("unused")
    public void setLanguageMessage(LanguageMessage languageMessage) {
        this.languageMessage = languageMessage;
    }

    @SuppressWarnings("unused")
    public ExamplePage getExamplePage() {
        return examplePage;
    }

    @SuppressWarnings("unused")
    public void setExamplePage(ExamplePage examplePage) {
        this.examplePage = examplePage;
    }

    @SuppressWarnings("unused")
    public FutureTopic getFutureTopic() {
        return futureTopic;
    }

    @SuppressWarnings("unused")
    public void setFutureTopic(FutureTopic futureTopic) {
        this.futureTopic = futureTopic;
    }

    @SuppressWarnings("unused")
    public RelatedExamples getRelatedExamples() {
        return relatedExamples;
    }

    @SuppressWarnings("unused")
    public void setRelatedExamples(RelatedExamples relatedExamples) {
        this.relatedExamples = relatedExamples;
    }

    @SuppressWarnings("unused")
    public Error getErrorTag() {
        return errorTag;
    }

    @JsonAnySetter
    @SuppressWarnings("ChainOfInstanceofChecks")
    public void setError(String name, Object value) {
        if (!"error".equals(name)) {
            return;
        }

        // NOTE Unfortunately the WA API returns "error" as attribute and tag at the same time.
        // There is no elegant fix to differentiate them other than doing it manually,
        // see https://github.com/FasterXML/jackson-dataformat-xml/issues/65
        // and https://github.com/FasterXML/jackson-dataformat-xml/issues/383
        if (value instanceof String) {
            errorAttribute = XML.convertValue(value, boolean.class);
            return;
        }
        if (value instanceof Map) {
            errorTag = XML.convertValue(value, Error.class);
            return;
        }
        throw new IllegalArgumentException("Unsupported error format");
    }
}
