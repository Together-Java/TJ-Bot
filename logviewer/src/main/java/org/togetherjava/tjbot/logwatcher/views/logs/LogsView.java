package org.togetherjava.tjbot.logwatcher.views.logs;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.logwatcher.accesscontrol.AllowedRoles;
import org.togetherjava.tjbot.logwatcher.accesscontrol.Role;
import org.togetherjava.tjbot.logwatcher.util.LogReader;
import org.togetherjava.tjbot.logwatcher.util.NotificationUtils;
import org.togetherjava.tjbot.logwatcher.views.MainLayout;

import javax.annotation.security.PermitAll;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The Logs View in the Browser
 */

@PageTitle("Logs")
@Route(value = "logs", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@AllowedRoles(roles = {Role.USER})
@PermitAll
public class LogsView extends HorizontalLayout {

    /**
     * Field where the events are displayed
     */
    private final TextArea text = new TextArea();

    private final transient LogReader watcher;

    public LogsView(LogReader watcher) {
        this.watcher = watcher;
        addClassName("hello-world-view");

        final ComboBox<Path> logs = createComboBox();
        logs.getOptionalValue().ifPresent(this::fillTextField);

        this.text.setWidthFull();
        add(logs, this.text);
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
        this.text.setValue(String.join("\n", getLogEntries(logFileName)));
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
