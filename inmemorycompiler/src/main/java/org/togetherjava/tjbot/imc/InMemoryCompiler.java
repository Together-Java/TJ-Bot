package org.togetherjava.tjbot.imc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.tools.*;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Javac interface that keeps everything in memory.
 *
 * @see javax.tools.JavaCompiler
 */
public final class InMemoryCompiler {
    // Hide constructor
    private InMemoryCompiler() {}

    private static final String TEMP_CLASS_NAME = "tmp";
    private static final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    private static final JavaFileManager standardFileManager =
            javac.getStandardFileManager(null, null, null);
    private static final ByteJavaFileObjectLoader bcl =
            new ByteJavaFileObjectLoader(ClassLoader.getSystemClassLoader());

    /**
     * Compiles a given code snippet with certain javac executable arguments
     *
     * @param code code to compile
     * @param javacOptions javac options to use
     * @return the compiled code in form of a {@link CompilationResult}
     */
    public static @NotNull CompilationResult compile(@Nullable String code,
            @NotNull JavacOption @NotNull... javacOptions) {
        if (code == null || code.isEmpty()) {
            return CompilationResult.empty();
        }

        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        boolean hasSucceeded = javac
            .getTask(null, new InMemoryJavacFileManager(standardFileManager, bcl), collector,
                    Arrays.stream(javacOptions).map(JavacOption::getOption).toList(), null,
                    List.of(genCompilationUnitJFO(code)))
            .call();

        List<CompileInfo> compileInfos = new ArrayList<>();

        if (!collector.getDiagnostics().isEmpty()) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                compileInfos.add(new CompileInfo(diagnostic));
            }
        }

        if (!hasSucceeded) {
            return CompilationResult.fail(compileInfos);
        }

        return CompilationResult.success(bcl.getLastBytes(), compileInfos);
    }

    /**
     * Generates a single compilation unit in form of a {@link SimpleJavaFileObject} for
     * {@link JavaCompiler#getTask(Writer, JavaFileManager, DiagnosticListener, Iterable, Iterable, Iterable)}
     *
     * @param content content of the imfjo
     * @return the generated compilation unit
     */
    private static @NotNull SimpleJavaFileObject genCompilationUnitJFO(@NotNull String content) {
        return new SimpleJavaFileObject(URI.create("string:///" + TEMP_CLASS_NAME.replace('.', '/') // random
                                                                                                    // protocol,
                                                                                                    // any
                                                                                                    // works
                + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE) {
            /**
             * Returns the content given in the generation of this {@code SimpleJavaFileObject}
             *
             * @param ignoreEncodingErrors does nothing
             * @return content
             */
            @Override
            public @NotNull String getCharContent(boolean ignoreEncodingErrors) {
                return content;
            }
        };
    }
}
