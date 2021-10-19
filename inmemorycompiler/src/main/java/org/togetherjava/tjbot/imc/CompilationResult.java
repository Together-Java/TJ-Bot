package org.togetherjava.tjbot.imc;

import java.util.Collections;

public record CompilationResult(boolean success, byte[] bytes, Iterable<CompileInfo> compileInfos) {

    private static final CompilationResult EMPTY_RESULT =
            new CompilationResult(false, new byte[0], Collections.emptyList());

    public static CompilationResult empty() {
        return EMPTY_RESULT;
    }

    public static CompilationResult fail(Iterable<CompileInfo> compileInfos) {
        return new CompilationResult(false, new byte[0], compileInfos);
    }

    public static CompilationResult success(byte[] bytes, Iterable<CompileInfo> compileInfos) {
        return new CompilationResult(true, bytes, compileInfos);
    }
}
