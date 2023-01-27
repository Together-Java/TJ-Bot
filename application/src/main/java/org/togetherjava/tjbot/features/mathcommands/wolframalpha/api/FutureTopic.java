package org.togetherjava.tjbot.features.mathcommands.wolframalpha.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Example Query: Operating Systems
 *
 * Response:
 * 
 * <pre>
 * {@code
 * <futuretopic topic='Operating Systems'
 *      msg='Development of this topic is under investigation...' />
 * }
 * </pre>
 */
@JsonRootName("futuretopic")
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FutureTopic {
    @JacksonXmlProperty(isAttribute = true)
    private String topic;

    @JacksonXmlProperty(isAttribute = true)
    private String msg;

    public String getTopic() {
        return topic;
    }

    @SuppressWarnings("unused")
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @SuppressWarnings("unused")
    public String getMsg() {
        return msg;
    }

    @SuppressWarnings("unused")
    public void setMsg(String msg) {
        this.msg = msg;
    }
}
