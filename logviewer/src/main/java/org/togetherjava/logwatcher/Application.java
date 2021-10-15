package org.togetherjava.logwatcher;

import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.togetherjava.logwatcher.config.Config;
import org.vaadin.artur.helpers.LaunchUtil;

/**
 * This is a small Spring-Vaadin Webserver, which main purpose is to show Log-Event's generated from
 * the Bot.
 * <p>
 * You can see the Views you can see in your Browser at {@link org.togetherjava.logwatcher.views}
 * <p>
 * Log-Event's are captured by the REST-API at {@link org.togetherjava.logwatcher.logs.LogREST}
 * <p>
 * Security/OAuth2 Config at {@link org.togetherjava.logwatcher.oauth.OAuth2LoginConfig}
 * <p>
 * And the initial Config at {@link org.togetherjava.logwatcher.config.Config}
 */
@SpringBootApplication(exclude = {R2dbcAutoConfiguration.class})
@Theme(value = "logviewer")
@PWA(name = "LogViewer", shortName = "Logs", offlineResources = {"images/logo.png"})
@NpmPackage(value = "line-awesome", version = "1.3.0")
@Push
public class Application extends SpringBootServletInitializer implements AppShellConfigurator {

    public static void main(String[] args) {
        if (args.length > 1) {
            LoggerFactory.getLogger(Application.class)
                .error("Usage: Provide a single Argument, containing the Path to the Config-File");
            System.exit(1);
        }

        Config.init(args.length == 1 ? args[0] : "./logviewer/config.json");
        LaunchUtil.launchBrowserInDevelopmentMode(SpringApplication.run(Application.class, args));
    }

}
