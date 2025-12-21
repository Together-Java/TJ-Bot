package org.togetherjava.tjbot.jda.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public class PayloadGuild {

    private long id;
    @JsonProperty("preferred_locale")
    private String preferredLocale;
    private Set<String> features;

    public PayloadGuild(long id, String preferredLocale, Set<String> features) {
        this.id = id;
        this.preferredLocale = preferredLocale;
        this.features = features;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPreferredLocale() {
        return preferredLocale;
    }

    public void setPreferredLocale(String preferredLocale) {
        this.preferredLocale = preferredLocale;
    }

    public Set<String> getFeatures() {
        return features;
    }

    public void setFeatures(Set<String> features) {
        this.features = features;
    }

}
