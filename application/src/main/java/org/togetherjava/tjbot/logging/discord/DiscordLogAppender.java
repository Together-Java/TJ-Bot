package org.togetherjava.tjbot.logging.discord;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

@Plugin(name = "Discord", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
final class DiscordLogAppender extends AbstractAppender {
    private static final Property[] NO_PROPERTIES = {};

    private final DiscordLogForwarder logForwarder;

    private DiscordLogAppender(String name, Filter filter, StringLayout layout,
            boolean ignoreExceptions, URI webhook, String sourceCodeBaseUrl) {
        super(name, filter, layout, ignoreExceptions, NO_PROPERTIES);

        logForwarder = new DiscordLogForwarder(webhook, sourceCodeBaseUrl);
    }

    @Override
    public void append(LogEvent event) {
        logForwarder.forwardLogEvent(event);
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

        @Required
        private String sourceCodeBaseUrl;

        public DiscordLogAppenderBuilder setWebhook(URI webhook) {
            this.webhook = webhook;
            return asBuilder();
        }

        public DiscordLogAppenderBuilder setSourceCodeBaseUrl(String sourceCodeBaseUrl) {
            this.sourceCodeBaseUrl = sourceCodeBaseUrl;
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
                    isIgnoreExceptions(), webhook, sourceCodeBaseUrl);
        }
    }
}
