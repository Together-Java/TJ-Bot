package org.togetherjava.tjbot.imc;

import javax.tools.*;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * In-Memory-Compiler
 */
public final class IMCompiler {
    private static final IMCompiler instance = new IMCompiler();

    public static IMCompiler getInstance() {
        return instance;
    }

    private final String TEMP_CLASS_NAME = "tmp";
    private final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    private final JavaFileManager standardFileManager =
            javac.getStandardFileManager(null, null, null);
    private final ByteJFOLoader bcl = new ByteJFOLoader(ClassLoader.getSystemClassLoader());

    /**
     * Compiles a given code snippet with certain javac executable arguments
     *
     * @param code code to compile
     * @param javacOptions javac options to use
     * @return the compiled code in form of a {@link CompilationResult}
     */
    public CompilationResult compile(String code, JavacOption... javacOptions) {
        if (code == null || code.isEmpty()) {
            return CompilationResult.empty();
        }

        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        boolean result = javac
            .getTask(null, new IMJavacFileManager(standardFileManager, bcl), collector,
                    Arrays.stream(javacOptions).map(JavacOption::getOption).toList(), null,
                    List.of(genCompilationUnitJFO(code)))
            .call();

        List<CompileInfo> compileInfos = new ArrayList<>();

        if (collector.getDiagnostics().size() > 0) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                compileInfos.add(new CompileInfo(diagnostic));
            }
        }

        if (!result) {
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
    private SimpleJavaFileObject genCompilationUnitJFO(String content) {
        return new SimpleJavaFileObject(URI.create("string:///" + TEMP_CLASS_NAME.replace('.', '/')
                + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE) {
            @Override
            public String getCharContent(boolean ignoreEncodingErrors) {
                return content;
            }
        };
    }
}
