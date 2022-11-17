package org.togetherjava.tjbot.imc;

import javax.tools.Diagnostic;

import java.util.Objects;

/**
 * Wrapper class for a {@link Diagnostic} of unknown type
 *
 * @see javax.tools.Diagnostic
 */
public record CompileInfo(Diagnostic<?> diagnostic) {
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CompileInfo that))
            return false;
        return diagnostic.equals(that.diagnostic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(diagnostic);
    }

    @Override
    public String toString() {
        return "CompileInfo{" + "diagnostic=" + diagnostic + '}';
    }
}
