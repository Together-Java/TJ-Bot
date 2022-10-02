package org.togetherjava.tjbot.imc;

import org.jetbrains.annotations.NotNull;

import javax.tools.Diagnostic;
import java.util.Objects;

/**
 * Wrapper class for a {@link Diagnostic} of unknown type
 *
 * @see javax.tools.Diagnostic
 */
public record CompileInfo(@NotNull Diagnostic<?> diagnostic) {
}
