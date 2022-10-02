package org.togetherjava.tjbot.imc;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;

/**
 * Class representing a compilation result of {@link InMemoryCompiler}
 */
public record CompilationResult(boolean success, byte @NotNull [] bytes,
        @NotNull Iterable<CompileInfo> compileInfos) {

    private static final @NotNull CompilationResult EMPTY_RESULT =
            new CompilationResult(false, new byte[0], Collections.emptyList());

    /**
     * @return an empty compilation result
     */
    public static @NotNull CompilationResult empty() {
        return EMPTY_RESULT;
    }

    /**
     * Creates an unsuccessful compilation result
     *
     * @param compileInfos compilation infos
     * @return the generated compilation result
     */
    public static @NotNull CompilationResult fail(@NotNull Iterable<CompileInfo> compileInfos) {
        return new CompilationResult(false, new byte[0], compileInfos);
    }

    /**
     * Creates a successful compilation result
     *
     * @param bytes classfile bytecode
     * @param compileInfos compilation infos
     * @return the generated compilation result
     */
    public static @NotNull CompilationResult success(byte @NotNull [] bytes,
            @NotNull Iterable<CompileInfo> compileInfos) {
        return new CompilationResult(true, bytes, compileInfos);
    }
}
