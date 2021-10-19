package org.togetherjava.tjbot.logwatcher.views.usermanagement;

import com.vaadin.componentfactory.EnhancedDialog;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.logwatcher.accesscontrol.AllowedRoles;
import org.togetherjava.tjbot.logwatcher.accesscontrol.Role;
import org.togetherjava.tjbot.logwatcher.entities.UserWrapper;
import org.togetherjava.tjbot.logwatcher.users.UserRepository;
import org.togetherjava.tjbot.logwatcher.util.NotificationUtils;
import org.togetherjava.tjbot.logwatcher.views.MainLayout;
import org.togetherjava.tjbot.db.DatabaseException;
import org.togetherjava.tjbot.db.generated.tables.pojos.Users;

import javax.annotation.security.PermitAll;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The User-Management View in the Browser
 */

@PageTitle("UserManagement")
@Route(value = "user", layout = MainLayout.class)
@AllowedRoles(roles = {Role.ADMIN})
@PermitAll
public class UserManagement extends VerticalLayout {

    private final transient UserRepository repo;
    private final Grid<UserWrapper> grid = new Grid<>(UserWrapper.class, false);

    public UserManagement(UserRepository repository) {
        this.repo = repository;
        add(grid);


        GridContextMenu<UserWrapper> menu = grid.addContextMenu();
        menu.addItem("Add", this::onAdd);
        menu.addItem("Edit", this::onEdit);
        menu.addItem("Delete", this::onDelete);

        grid.setColumns("discordID", "userName", "roles");

        // The int-cast is necessary here, since DataProvider.fromCallback expects an int on the
        // Counting Callback, and we are not going to display more than 2 Billion entries at once
        grid.setItems(DataProvider.fromCallbacks(this::onAll, query -> (int) onAll(query).count()));
    }

    private void onDelete(GridContextMenu.GridContextMenuItemClickEvent<UserWrapper> event) {
        event.getItem().map(DeleteDialog::new).ifPresent(EnhancedDialog::open);
    }

    private void onAdd(GridContextMenu.GridContextMenuItemClickEvent<UserWrapper> event) {
        new CreateDialog().open();
    }

    private void onEdit(GridContextMenu.GridContextMenuItemClickEvent<UserWrapper> event) {
        event.getItem().map(EditDialog::new).ifPresent(EditDialog::open);
    }

    private Stream<UserWrapper> onAll(Query<UserWrapper, Void> query) {
        try {
            return this.repo.findAll()
                .stream()
                .skip(query.getOffset())
                .limit(query.getLimit())
                .map(user -> new UserWrapper(user.getDiscordid(), user.getUsername(),
                        this.repo.fetchRolesForUser(user)));
        } catch (final DatabaseException e) {
            LoggerFactory.getLogger(UserManagement.class)
                .error("Exception occurred while fetching.", e);
            NotificationUtils.getNotificationForError(e).open();
            return Stream.empty();
        }
    }

    private class EditDialog extends EnhancedDialog {
        protected final TextField userNameField;
        protected final CheckboxGroup<Role> rolesGroup;
        protected final UserWrapper person;

        private EditDialog(UserWrapper person) {
            this.person = person;
            this.userNameField = new TextField("UserName", person.getUserName(), "");
            this.rolesGroup = new CheckboxGroup<>();
            this.rolesGroup.setLabel("Roles");
            this.rolesGroup.setItems(Role.getDisplayableRoles());
            this.rolesGroup.setValue(person.getRoles());

            initCenter();
            initFooter();
        }

        protected void initCenter() {
            setContent(new HorizontalLayout(this.userNameField, this.rolesGroup));
        }

        protected void initFooter() {
            addToFooter(new HorizontalLayout(new Button("Save", this::onSave),
                    new Button("Cancel", ev -> this.close())));
        }

        protected void onSave(ClickEvent<Button> event) {
            this.doUpdate(new UserWrapper(this.person.getDiscordID(), this.userNameField.getValue(),
                    this.rolesGroup.getValue()));
            UserManagement.this.grid.getDataProvider().refreshAll();
            this.close();
        }

        /**
         * Saves a new User or updates an existing User in the Repository
         *
         * @param user User to save
         */
        private void doUpdate(UserWrapper user) {
            try {
                Users toSave = new Users(user.getDiscordID(), user.getUserName());
                UserManagement.this.repo.save(toSave);

                UserManagement.this.repo.saveRolesForUser(toSave, user.getRoles());
            } catch (DatabaseException e) {
                LoggerFactory.getLogger(UserManagement.class)
                    .error("Exception occurred while saving.", e);
                NotificationUtils.getNotificationForError(e).open();
            }

        }
    }


    @SuppressWarnings("java:S110") // Inherits a single time to reduce duplicated Code
    private class CreateDialog extends EditDialog {
        private final NumberField id = new NumberField("Discord ID", "0");

        private CreateDialog() {
            super(new UserWrapper(0L, "", Collections.emptySet()));
            this.id.setWidth("150px");
            setHeader(new Text("Create new User"));
            setContent(new HorizontalLayout(this.id, this.userNameField, this.rolesGroup));
        }


        @Override
        protected void onSave(ClickEvent<Button> event) {
            Optional<Double> discordID = this.id.getOptionalValue();
            if (discordID.isEmpty()) {
                return;
            }

            this.person.setDiscordID(discordID.orElseThrow().longValue());
            super.onSave(event);
        }
    }


    private class DeleteDialog extends EnhancedDialog {
        private final UserWrapper person;

        private DeleteDialog(UserWrapper person) {
            this.person = person;
            setContent(new Text("Are you sure you want to delete the Entry of %s?"
                .formatted(person.getUserName())));
            setFooter(new HorizontalLayout(new Button("Delete", this::onDelete),
                    new Button("Cancel", e -> this.close())));
        }

        private void onDelete(ClickEvent<Button> event) {
            this.doRemove(this.person);
            UserManagement.this.grid.getDataProvider().refreshAll();
            this.close();
        }

        /**
         * Removes the given User from the Repository
         *
         * @param user User to remove
         */
        private void doRemove(UserWrapper user) {
            try {
                UserManagement.this.repo.delete(new Users(user.getDiscordID(), user.getUserName()));
            } catch (DatabaseException e) {
                LoggerFactory.getLogger(UserManagement.class)
                    .error("Exception occurred while removing.", e);
                NotificationUtils.getNotificationForError(e).open();
            }
        }

    }
}
