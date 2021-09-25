package org.togetherjava.logwatcher.views.usermanagement;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.slf4j.LoggerFactory;
import org.togetherjava.logwatcher.accesscontrol.AllowedRoles;
import org.togetherjava.logwatcher.accesscontrol.Role;
import org.togetherjava.logwatcher.constants.UserConstants;
import org.togetherjava.logwatcher.entities.User;
import org.togetherjava.logwatcher.users.UserRepository;
import org.togetherjava.logwatcher.util.NotificationUtils;
import org.togetherjava.logwatcher.views.MainLayout;
import org.vaadin.crudui.crud.CrudOperation;
import org.vaadin.crudui.crud.impl.GridCrud;
import org.vaadin.crudui.form.CrudFormFactory;
import org.vaadin.crudui.form.impl.field.provider.CheckBoxGroupProvider;

import javax.annotation.security.PermitAll;
import java.util.Arrays;

/**
 * The User-Management View in the Browser
 */

@PageTitle("UserManagement")
@Route(value = "user", layout = MainLayout.class)
@AllowedRoles(roles = {Role.ADMIN})
@PermitAll
public class UserManagement extends VerticalLayout {

    private final transient UserRepository repo;

    public UserManagement(UserRepository repository) {
        this.repo = repository;

        // This is the table you see
        GridCrud<User> grid = new GridCrud<>(User.class);
        grid.setOperations(this.repo::findAll, this::onUpdate, this::onUpdate, this::onRemove);
        grid.getGrid().removeColumnByKey(UserConstants.FIELD_ID);

        final CrudFormFactory<User> factory = grid.getCrudFormFactory();
        factory.setVisibleProperties(CrudOperation.UPDATE, UserConstants.FIELD_NAME,
                UserConstants.FIELD_ROLES);
        factory.setVisibleProperties(CrudOperation.ADD, UserConstants.FIELD_DISCORD_ID,
                UserConstants.FIELD_NAME, UserConstants.FIELD_ROLES, UserConstants.FIELD_USERNAME);
        factory.setFieldProvider(UserConstants.FIELD_ROLES,
                new CheckBoxGroupProvider<>(Arrays.asList(Role.values())));
        add(grid);
    }

    /**
     * Removes the given User from the Repository
     *
     * @param user User to remove
     */
    private void onRemove(User user) {
        try {
            this.repo.delete(user);
        } catch (IllegalArgumentException e) {
            LoggerFactory.getLogger(UserManagement.class)
                .error("Exception occurred while removing.", e);
            NotificationUtils.getNotificationForError(e).open();
        }
    }

    /**
     * Saves a new User or updates an existing User in the Repository
     *
     * @param user User to save
     * @return the same User-Object
     */
    private User onUpdate(User user) {
        try {
            this.repo.save(user);
        } catch (IllegalArgumentException e) {
            LoggerFactory.getLogger(UserManagement.class)
                .error("Exception occurred while saving.", e);
            NotificationUtils.getNotificationForError(e).open();
        }

        return user;
    }

}
