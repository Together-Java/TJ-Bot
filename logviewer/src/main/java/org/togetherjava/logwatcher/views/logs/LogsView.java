package org.togetherjava.logwatcher.views.logs;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import org.slf4j.LoggerFactory;
import org.togetherjava.logwatcher.accesscontrol.AllowedRoles;
import org.togetherjava.logwatcher.accesscontrol.Role;
import org.togetherjava.logwatcher.util.LogReader;
import org.togetherjava.logwatcher.util.NotificationUtils;
import org.togetherjava.logwatcher.views.MainLayout;

import javax.annotation.security.PermitAll;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Logs View in the Browser
 */

@PageTitle("Logs")
@Route(value = "logs", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@AllowedRoles(roles = {Role.USER})
@PermitAll
public class LogsView extends VerticalLayout {

    /**
     * Field where the events are displayed
     */
    private final VerticalLayout events = new VerticalLayout();

    private static final Pattern LOGLEVEL_MATCHER =
            Pattern.compile("(ERROR|WARN|INFO|DEBUG|TRACE)");

    private final transient LogReader watcher;

    public LogsView(LogReader watcher) {
        this.watcher = watcher;
        this.events.setWidthFull();

        addClassName("logs-view");

        HorizontalLayout options = new HorizontalLayout();
        options.setAlignItems(Alignment.START);

        final Set<String> levels = Set.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE");
        for (final String level : levels) {
            final Checkbox ch = new Checkbox(level);
            ch.setValue(true);
            ch.addValueChangeListener(this::onValueChange);
            options.add(ch);
        }

        ComboBox<Path> logs = createComboBox();
        logs.getOptionalValue().ifPresent(this::fillTextField);

        add(logs, options, new Scroller(this.events, Scroller.ScrollDirection.VERTICAL));
    }

    private void onValueChange(AbstractField.ComponentValueChangeEvent<Checkbox, Boolean> event) {
        if (!event.isFromClient()) {
            return;
        }

        final String level = event.getSource().getLabel().toLowerCase(Locale.ENGLISH);

        this.events.getChildren()
            .filter(Paragraph.class::isInstance)
            .map(Paragraph.class::cast)
            .filter(c -> c.getClassName().equals(level))
            .forEach(c -> c.setVisible(event.getValue()));
    }

    /**
     * Creates the Combobox of Logfile-Names where the User can choose what File to view
     *
     * @return The created Combobox
     */
    private ComboBox<Path> createComboBox() {
        final ComboBox<Path> logs = new ComboBox<>("Log-Files");
        logs.setAllowCustomValue(false);
        logs.setRenderer(new TextRenderer<>(p -> p.getFileName().toString()));

        final List<Path> logFiles = getLogFiles();
        logs.setItems(DataProvider.ofCollection(logFiles));
        logFiles.stream().findFirst().ifPresent(logs::setValue);

        logs.addValueChangeListener(this::onValueChange);
        return logs;
    }

    /**
     * When User chooses another Logfile, reload the Log and set it in the textField
     *
     * @param event Generated Event, containing old and new Value
     */
    private void onValueChange(HasValue.ValueChangeEvent<Path> event) {
        if (Objects.equals(event.getOldValue(), event.getValue())) {
            return;
        }

        fillTextField(event.getValue());
    }

    /**
     * Reload the Log and set it in the textField
     *
     * @param logFileName Name of the Logfile
     */
    private void fillTextField(final Path logFileName) {
        this.events.removeAll();

        final List<String> logEntries = getLogEntries(logFileName);

        String previousGroup = "unknown";
        for (final String event : logEntries) {
            final String trimmed = event.trim();
            final Paragraph text = new Paragraph(trimmed);

            final Matcher matcher = LOGLEVEL_MATCHER.matcher(trimmed);
            if (matcher.find()) {
                previousGroup = matcher.group().toLowerCase(Locale.ENGLISH);
            }

            text.addClassName(previousGroup);
            this.events.add(text);
        }
    }

    /**
     * Gathers all available Logfiles
     *
     * @return Names of available Logfiles
     */
    private List<Path> getLogFiles() {
        try {
            return this.watcher.getLogs();
        } catch (final UncheckedIOException e) {
            LoggerFactory.getLogger(LogsView.class).error("Exception while gathering LogFiles", e);
            NotificationUtils.getNotificationForError(e).open();
            return Collections.emptyList();
        }
    }

    /**
     * Reads the log for the given Logfile
     *
     * @param logFile Name of the log to read
     * @return Contents of the LogFile
     */
    private List<String> getLogEntries(final Path logFile) {
        try {
            return this.watcher.readLog(logFile);
        } catch (final UncheckedIOException e) {
            LoggerFactory.getLogger(LogsView.class).error("Exception while gathering LogFiles", e);
            NotificationUtils.getNotificationForError(e).open();
            return Collections.emptyList();
        }
    }
}
