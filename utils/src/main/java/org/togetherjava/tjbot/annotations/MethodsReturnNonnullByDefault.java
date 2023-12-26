package org.togetherjava.tjbot.annotations;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation can be applied to a package, class or method to indicate that the methods return
 * value is nonnull by default unless there is:
 * <ul>
 * <li>An explicit nullness annotation
 * <li>The method overrides a method in a superclass (in which case the annotation of the
 * corresponding method in the superclass applies)
 * <li>There is a default parameter annotation (like {@link MethodsReturnNonnullByDefault}) applied
 * to a more tightly nested element.
 * </ul>
 *
 * @see Nonnull
 */
@Documented
@Nonnull
@TypeQualifierDefault(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MethodsReturnNonnullByDefault {
}
