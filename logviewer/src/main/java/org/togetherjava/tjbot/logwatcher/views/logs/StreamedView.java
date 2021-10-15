package org.togetherjava.tjbot.logwatcher.views.logs;

import com.vaadin.componentfactory.EnhancedDialog;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.LocalDateTimeRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.SessionDestroyEvent;
import com.vaadin.flow.server.VaadinService;
import org.togetherjava.tjbot.logwatcher.accesscontrol.AllowedRoles;
import org.togetherjava.tjbot.logwatcher.accesscontrol.Role;
import org.togetherjava.tjbot.logwatcher.constants.LogEventsConstants;
import org.togetherjava.tjbot.logwatcher.logs.LogRepository;
import org.togetherjava.tjbot.logwatcher.views.MainLayout;
import org.togetherjava.tjbot.logwatcher.watcher.StreamWatcher;
import org.togetherjava.tjbot.db.generated.tables.pojos.Logevents;
import org.vaadin.crudui.crud.impl.GridCrud;

import javax.annotation.security.PermitAll;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The Logs View in the Browser
 */

@PageTitle("Streamed")
@Route(value = "streamed", layout = MainLayout.class)
@AllowedRoles(roles = {Role.USER})
@PermitAll
public class StreamedView extends HorizontalLayout {

    private final GridCrud<Logevents> grid = new GridCrud<>(Logevents.class);
    private final UUID uuid = UUID.randomUUID();

    public StreamedView(LogRepository logs) {
        addClassName("hello-world-view");

        add(new Button("Change Columns", this::onChangeColumns), this.grid);

        this.grid.setOperations(logs::findAll, null, null, null);
        this.grid.setAddOperationVisible(false);
        this.grid.setDeleteOperationVisible(false);
        this.grid.setUpdateOperationVisible(false);


        this.grid.getGrid()
            .setColumns(LogEventsConstants.FIELD_INSTANT, LogEventsConstants.FIELD_LOGGER_NAME,
                    LogEventsConstants.FIELD_MESSAGE);
        setInstantFormatter();
        this.grid.getGrid().getColumns().forEach(c -> c.setAutoWidth(true));
        this.grid.getGrid().getColumns().forEach(c -> c.setResizable(true));
        this.grid.getGrid().recalculateColumnWidths();
        this.grid.getGrid().setColumnReorderingAllowed(true);

        VaadinService.getCurrent().addSessionDestroyListener(this::onDestroy);
        final UI ui = UI.getCurrent();
        StreamWatcher.addSubscription(this.uuid, () -> ui.access(this.grid::refreshGrid));
    }


    private void onDestroy(SessionDestroyEvent event) {
        removeHook();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        removeHook();
        super.onDetach(detachEvent);
    }

    private void removeHook() {
        StreamWatcher.removeSubscription(this.uuid);
    }

    private void setInstantFormatter() {
        final Grid<Logevents> innerGrid = this.grid.getGrid();
        final Optional<Grid.Column<Logevents>> column =
                Optional.ofNullable(innerGrid.getColumnByKey(LogEventsConstants.FIELD_INSTANT));
        if (column.isEmpty()) {
            return;
        }

        final Grid.Column<Logevents> instant = column.orElseThrow();
        innerGrid.removeColumn(instant);

        final String[] keys =
                innerGrid.getColumns().stream().map(Grid.Column::getKey).toArray(String[]::new);
        innerGrid.removeAllColumns();


        innerGrid
            .addColumn(new LocalDateTimeRenderer<>(Logevents::getTime,
                    DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm:ss.SSS")))
            .setHeader("Instant")
            .setComparator(Comparator.comparing(Logevents::getTime))
            .setKey(LogEventsConstants.FIELD_INSTANT);

        innerGrid.addColumns(keys);
    }

    private void onChangeColumns(ClickEvent<Button> event) {
        final EnhancedDialog dialog = new EnhancedDialog();
        dialog.setHeader("Choose the Columns you want to see.");
        final Set<String> columns = this.grid.getGrid()
            .getColumns()
            .stream()
            .map(Grid.Column::getKey)
            .collect(Collectors.toSet());

        final List<Checkbox> checkBoxes = new ArrayList<>();
        final List<String> fields =
                Arrays.asList(LogEventsConstants.FIELD_INSTANT, LogEventsConstants.FIELD_THREAD,
                        LogEventsConstants.FIELD_LOGGER_LEVEL, LogEventsConstants.FIELD_LOGGER_NAME,
                        LogEventsConstants.FIELD_MESSAGE, LogEventsConstants.FIELD_LOGGER_FQCN);
        for (String field : fields) {
            Checkbox c = new Checkbox(field);
            c.setValue(columns.contains(c.getLabel()));
            checkBoxes.add(c);
        }
        dialog.setContent(checkBoxes.toArray(Component[]::new));
        dialog.setFooter(new Button("Accept", evt -> this.onOkay(dialog, checkBoxes)),
                new Button("Cancel", e -> dialog.close()));
        dialog.open();
    }

    private void onOkay(final EnhancedDialog dialog, final List<Checkbox> checkboxes) {
        final String[] columns = checkboxes.stream()
            .filter(AbstractField::getValue)
            .map(Checkbox::getLabel)
            .toArray(String[]::new);


        this.grid.getGrid().setColumns(columns);
        final boolean includeTime = checkboxes.stream()
            .filter(AbstractField::getValue)
            .anyMatch(c -> LogEventsConstants.FIELD_INSTANT.equals(c.getLabel()));

        if (includeTime) {
            setInstantFormatter();
        }

        this.grid.getGrid().getColumns().forEach(c -> c.setAutoWidth(true));
        this.grid.getGrid().getColumns().forEach(c -> c.setResizable(true));
        this.grid.getGrid().recalculateColumnWidths();
        dialog.close();
    }
}
