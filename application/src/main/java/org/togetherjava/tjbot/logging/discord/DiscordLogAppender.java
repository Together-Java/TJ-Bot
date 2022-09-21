package org.togetherjava.tjbot.logging.discord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

@Plugin(name = "Discord", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
final class DiscordLogAppender extends AbstractAppender {

    private static final Property[] NO_PROPERTIES = {};
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI webhook;

    private DiscordLogAppender(String name, Filter filter, StringLayout layout,
            boolean ignoreExceptions, URI webhook) {
        super(name, filter, layout, ignoreExceptions, NO_PROPERTIES);

        this.webhook = webhook;
    }

    @Override
    public void append(LogEvent event) {
        DiscordLogMessage message = DiscordLogMessage.ofEvent(event);
        String messageRaw;
        try {
            messageRaw = JSON.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            // FIXME Some error reporting
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(webhook)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(messageRaw))
            .build();

        // Fire and forget
        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        // FIXME 429 Rate limiting, Retry-After
    }

    @PluginBuilderFactory
    static DiscordLogAppenderBuilder newBuilder() {
        return new DiscordLogAppenderBuilder();
    }

    static final class DiscordLogAppenderBuilder
            extends AbstractAppender.Builder<DiscordLogAppenderBuilder>
            implements org.apache.logging.log4j.core.util.Builder<DiscordLogAppender> {

        @PluginBuilderAttribute
        @Required
        private URI webhook;

        public DiscordLogAppenderBuilder setWebhook(URI webhook) {
            this.webhook = webhook;
            return asBuilder();
        }

        @Override
        public DiscordLogAppender build() {
            Layout<? extends Serializable> layout = getOrCreateLayout();
            if (!(layout instanceof StringLayout)) {
                throw new IllegalArgumentException("Layout must be a StringLayout");
            }

            String name = Objects.requireNonNull(getName());

            return new DiscordLogAppender(name, getFilter(), (StringLayout) layout,
                    isIgnoreExceptions(), webhook);
        }
    }
}
