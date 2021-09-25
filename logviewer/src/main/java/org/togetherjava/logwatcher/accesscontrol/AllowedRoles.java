package org.togetherjava.logwatcher.accesscontrol;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * Annotation for handling Access Control on Views
 */
@Retention(RUNTIME)
@Target({TYPE})
public @interface AllowedRoles {


    /**
     * Roles to permit access to the View {@link Role}
     */
    Role[] roles();
}
