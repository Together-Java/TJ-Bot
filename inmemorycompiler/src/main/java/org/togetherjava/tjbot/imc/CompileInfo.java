package org.togetherjava.tjbot.imc;

import javax.tools.Diagnostic;

public record CompileInfo(Diagnostic<?> diagnostic) {
}
