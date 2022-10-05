package org.togetherjava.tjbot.imc;

import java.util.Arrays;
import java.util.Collections;

/**
 * Class representing a compilation result of {@link InMemoryCompiler}
 */
public record CompilationResult(boolean success, byte[] bytes,
        Iterable<CompileInfo> compileInfos) {

    private static final CompilationResult EMPTY_RESULT =
            new CompilationResult(false, new byte[0], Collections.emptyList());

    /**
     * @return an empty compilation result
     */
    public static CompilationResult empty() {
        return EMPTY_RESULT;
    }

    /**
     * Creates an unsuccessful compilation result
     *
     * @param compileInfos compilation infos
     * @return the generated compilation result
     */
    public static CompilationResult fail(Iterable<CompileInfo> compileInfos) {
        return new CompilationResult(false, new byte[0], compileInfos);
    }

    /**
     * Creates a successful compilation result
     *
     * @param bytes classfile bytecode
     * @param compileInfos compilation infos
     * @return the generated compilation result
     */
    public static CompilationResult success(byte[] bytes,
            Iterable<CompileInfo> compileInfos) {
        return new CompilationResult(true, bytes, compileInfos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CompilationResult that) || success != that.success
                || !Arrays.equals(bytes, that.bytes)) {
            return false;
        }

        return compileInfos.equals(that.compileInfos);
    }

    @Override
    public int hashCode() {
        int result = (success ? 1 : 0);

        result = 31 * result + Arrays.hashCode(bytes);
        result = 31 * result + compileInfos.hashCode();

        return result;
    }

    @Override
    public String toString() {
        return "CompilationResult{" + "success=" + success + ", bytes=" + Arrays.toString(bytes)
                + ", compileInfos=" + compileInfos + '}';
    }
}
