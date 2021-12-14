package org.togetherjava.tjbot.logging;

import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.filter.AbstractFilterable;

import java.io.Serializable;

@Plugin(name = "ConditionalHTTPAppender", category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE)
public final class ConditionalHTTPAppender extends AbstractAppender {

    public static final String LOGGING_FLAG = "TJ_APPENDER";

    private final AppenderRef ref;
    private final Configuration map;

    private ConditionalHTTPAppender(final String name, final Filter filter,
            final Layout<? extends Serializable> layout, final boolean ignoreExceptions,
            final Property[] properties, AppenderRef ref, Configuration map) {
        super(name, filter, layout, ignoreExceptions, properties);

        this.ref = ref;
        this.map = map;
    }

    @Override
    public void append(LogEvent event) {
        if (System.getenv().containsKey(LOGGING_FLAG)) {
            this.map.getAppender(this.ref.getRef()).append(event);
        }
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder<B extends Builder<B>> extends AbstractFilterable.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<ConditionalHTTPAppender> {


        @PluginElement("AppenderRef")
        @Required(message = "No appender references provided to AsyncAppender")
        private AppenderRef[] appenderRefs;

        @PluginBuilderAttribute
        @Required(message = "No name provided for AsyncAppender")
        private String name;

        @PluginConfiguration
        private Configuration configuration;

        @Override
        public ConditionalHTTPAppender build() {
            return new ConditionalHTTPAppender(this.name, getFilter(), null, false, null,
                    this.appenderRefs[0], this.configuration);
        }
    }
}
