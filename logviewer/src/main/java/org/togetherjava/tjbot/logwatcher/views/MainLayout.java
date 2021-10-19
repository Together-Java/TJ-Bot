package org.togetherjava.tjbot.logwatcher.views;

import com.google.common.collect.Sets;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.RouterLink;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.logwatcher.accesscontrol.AllowedRoles;
import org.togetherjava.tjbot.logwatcher.accesscontrol.Role;
import org.togetherjava.tjbot.logwatcher.users.AuthenticatedUser;
import org.togetherjava.tjbot.logwatcher.views.logs.LogsView;
import org.togetherjava.tjbot.logwatcher.views.logs.StreamedView;
import org.togetherjava.tjbot.logwatcher.views.usermanagement.UserManagement;
import org.togetherjava.tjbot.db.generated.tables.pojos.Users;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The main view is a top-level placeholder for other views.
 */
@PageTitle("Main")
@SuppressWarnings({"java:S1192"})
// Those are css names, and those don't need an extra Constants Class
public class MainLayout extends AppLayout {

    private final transient AuthenticatedUser authenticatedUser;
    private H1 viewTitle;

    public MainLayout(AuthenticatedUser authUser) {
        this.authenticatedUser = authUser;
        setPrimarySection(Section.DRAWER);
        addToNavbar(true, createHeaderContent());
        addToDrawer(createDrawerContent());

        if (authUser.canRegister() && !authUser.isRegistered()) {
            authUser.register();
        }


        if (this.authenticatedUser.getRoles().isEmpty()) {
            authUser.logout();
        }

    }

    private static RouterLink createLink(MenuItemInfo menuItemInfo) {
        RouterLink link = new RouterLink();
        link.addClassNames("flex", "mx-s", "p-s", "relative", "text-secondary");
        link.setRoute(menuItemInfo.view());

        Span icon = new Span();
        icon.addClassNames("me-s", "text-l");
        if (!menuItemInfo.iconClass().isEmpty()) {
            icon.addClassNames(menuItemInfo.iconClass());
        }

        Span text = new Span(menuItemInfo.text());
        text.addClassNames("font-medium", "text-s");

        link.add(icon, text);
        return link;
    }

    private Component createHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.addClassName("text-secondary");
        toggle.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        toggle.getElement().setAttribute("aria-label", "Menu toggle");

        viewTitle = new H1();
        viewTitle.addClassNames("m-0", "text-l");

        Header header = new Header(toggle, viewTitle);
        header.addClassNames("bg-base", "border-b", "border-contrast-10", "box-border", "flex",
                "h-xl", "items-center", "w-full");
        return header;
    }

    private Component createDrawerContent() {
        H2 appName = new H2("Logviewer");
        appName.addClassNames("flex", "items-center", "h-xl", "m-0", "px-m", "text-m");

        com.vaadin.flow.component.html.Section section = new com.vaadin.flow.component.html.Section(
                appName, createNavigation(), createFooter());
        section.addClassNames("flex", "flex-col", "items-stretch", "max-h-full", "min-h-full");
        return section;
    }

    private Nav createNavigation() {
        Nav nav = new Nav();
        nav.addClassNames("border-b", "border-contrast-10", "flex-grow", "overflow-auto");
        nav.getElement().setAttribute("aria-labelledby", "views");

        H3 views = new H3("Views");
        views.addClassNames("flex", "h-m", "items-center", "mx-m", "my-0", "text-s",
                "text-tertiary");
        views.setId("views");

        createLinks().forEach(nav::add);

        return nav;
    }

    private List<RouterLink> createLinks() {
        return Stream
            .of(new MenuItemInfo("Logs", "la la-globe", LogsView.class),
                    new MenuItemInfo("Streamed", "la la-globe", StreamedView.class),
                    new MenuItemInfo("User Management", "la la-file", UserManagement.class))
            .filter(this::checkAccess)
            .map(MainLayout::createLink)
            .toList();
    }

    private boolean checkAccess(MenuItemInfo menuItemInfo) {
        final Class<? extends Component> view = menuItemInfo.view;
        final AllowedRoles annotation = view.getAnnotation(AllowedRoles.class);

        if (annotation == null) {
            LoggerFactory.getLogger(MainLayout.class)
                .warn("Class {} not properly secured with Annotation", view);
            return false;
        }

        final Set<Role> roles = Set.of(annotation.roles());

        return !Sets.intersection(this.authenticatedUser.getRoles(), roles).isEmpty();
    }

    private Footer createFooter() {
        Footer layout = new Footer();
        layout.addClassNames("flex", "items-center", "my-s", "px-m", "py-xs");

        Users user = this.authenticatedUser.get();

        Avatar avatar = new Avatar(user.getUsername());
        avatar.addClassNames("me-xs");

        ContextMenu userMenu = new ContextMenu(avatar);
        userMenu.setOpenOnClick(true);
        userMenu.addItem("Logout", e -> authenticatedUser.logout());

        Span name = new Span(user.getUsername());
        name.addClassNames("font-medium", "text-s", "text-secondary");

        layout.add(avatar, name);

        return layout;
    }

    @Override
    protected void afterNavigation() {
        super.afterNavigation();
        viewTitle.setText(getCurrentPageTitle());
    }

    private String getCurrentPageTitle() {
        PageTitle title = getContent().getClass().getAnnotation(PageTitle.class);
        return title == null ? "" : title.value();
    }


    private record MenuItemInfo(String text, String iconClass, Class<? extends Component> view) {
    }
}
