package org.togetherjava.tjbot.features.mathcommands.wolframalpha.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Example Query: ¿donde soy tu?
 *
 * Response:
 * 
 * <pre>
 * {@code
 * <languagemsg english='Wolfram|Alpha does not yet support Spanish.'
 *      other='Wolfram|Alpha todavía no entiende español.' />
 * }
 * </pre>
 */
@JsonRootName("langugemsg")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LanguageMessage {
    @JacksonXmlProperty(isAttribute = true)
    private String english;

    @JacksonXmlProperty(isAttribute = true)
    private String other;

    public String getEnglish() {
        return english;
    }

    @SuppressWarnings("unused")
    public void setEnglish(String english) {
        this.english = english;
    }

    public String getOther() {
        return other;
    }

    @SuppressWarnings("unused")
    public void setOther(String other) {
        this.other = other;
    }

}
