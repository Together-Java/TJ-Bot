package org.togetherjava.tjbot.javap;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In-memory disassembler of raw Java bytecode (for example, as received by
 * {@link org.togetherjava.tjbot.imc.InMemoryCompiler}, or by reading a {@code .class} file).
 */
/*
 * Sonar does not like the heavy use of reflection to access locked down internal JDK tools, but the
 * class is designed based on this.
 */
@SuppressWarnings("java:S3011")
public final class Javap {
    // FIXME Make this a non-static class
    private Javap() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final String TEMP_FILE_NAME = "tmp";
    // FIXME Odd name
    private static final String ANNOYING_WARNING_MESSAGE =
            "Warning: File /%s does not contain class %<s";

    private static final Class<?> javapFileManagerType;
    private static final Class<?> javapTaskType;

    private static final Method javapFileManagerCreate;
    private static final Method javapTaskRun;
    private static final Method javapTaskGetDiagnosticListenerForWriter;

    private static final Field javapTaskDefaultFileManager;
    private static final Field javapTaskLog;

    static {
        try {
            javapFileManagerType = Class.forName("com.sun.tools.javap.JavapFileManager");
            javapFileManagerCreate = javapFileManagerType.getDeclaredMethod("create",
                    DiagnosticListener.class, PrintWriter.class);

            javapTaskType = Class.forName("com.sun.tools.javap.JavapTask");
            javapTaskDefaultFileManager = javapTaskType.getDeclaredField("defaultFileManager");
            javapTaskLog = javapTaskType.getDeclaredField("log");
            javapTaskRun = javapTaskType.getDeclaredMethod("run", String[].class);
            javapTaskGetDiagnosticListenerForWriter =
                    javapTaskType.getDeclaredMethod("getDiagnosticListenerForWriter", Writer.class);

            javapFileManagerCreate.setAccessible(true);
            javapTaskDefaultFileManager.setAccessible(true);
            javapTaskLog.setAccessible(true);
            javapTaskRun.setAccessible(true);
            javapTaskGetDiagnosticListenerForWriter.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
            throw new IllegalStateException(
                    "Error while attempting to access JDK internal Javap classes for the disassembler.",
                    e);
        }
    }

    /**
     * Disassembles the given bytecode with given options.
     *
     * @param rawBytecode the raw bytecode to disassemble
     * @param options selectable options
     * @return disassembled bytecode
     * @throws RuntimeException If the given bytecode could not be disassembled, for example,
     *         because it was invalid
     */
    public static String disassemble(byte[] rawBytecode, JavapOption... options) {
        // FIXME Proper exception usage
        StringWriter result = new StringWriter();
        PrintWriter resultWriter = new PrintWriter(result);

        try {
            Object javapTask = newJavapTask();

            var fileManager = new InMemoryJavaFileManager(
                    getDefaultFileManager(javapTask, resultWriter), rawBytecode, TEMP_FILE_NAME);
            javapTaskDefaultFileManager.set(javapTask, fileManager);

            javapTaskLog.set(javapTask, resultWriter);

            javapTaskRun.invoke(javapTask, optionsToInvokeArgs(options));
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException
                | IllegalAccessException e) {
            throw new IllegalArgumentException("Failed to disassemble the given bytecode.", e);
        }

        return result.toString().replace(ANNOYING_WARNING_MESSAGE.formatted(TEMP_FILE_NAME), "");
    }

    /**
     * Gets the default file manager instance from a {@code com.sun.tools.javap.JavapTask} and a
     * {@link PrintWriter} using
     * {@code com.sun.tools.javap.JavapFileManager#create(DiagnosticListener, PrintWriter)}
     */
    private static JavaFileManager getDefaultFileManager(Object javapTask, PrintWriter writer)
            throws InvocationTargetException, IllegalAccessException {
        DiagnosticListener<JavaFileObject> diagnosticListener =
                getDiagnosticListenerForWriter(javapTask, writer);
        return (JavaFileManager) javapFileManagerCreate.invoke(null, diagnosticListener, writer);
    }

    /**
     * Generates a {@link DiagnosticListener} instance from a {@code com.sun.tools.javap.JavapTask}
     * and a {@link PrintWriter} using
     * {@code com.sun.tools.javap.JavapTask#getDiagnosticListenerForWriter(Writer)}
     */
    @SuppressWarnings("unchecked")
    private static DiagnosticListener<JavaFileObject> getDiagnosticListenerForWriter(
            Object javapTask, PrintWriter writer)
            throws InvocationTargetException, IllegalAccessException {
        return (DiagnosticListener<JavaFileObject>) javapTaskGetDiagnosticListenerForWriter
            .invoke(javapTask, writer);
    }

    /**
     * Creates a new {@code com.sun.tools.javap.JavapTask} instance
     */
    private static Object newJavapTask() throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        return javapTaskType.getConstructor().newInstance();
    }

    private static Object[] optionsToInvokeArgs(JavapOption... options) {
        List<String> invokeArgs = Arrays.stream(options)
            .map(JavapOption::getLabel)
            .collect(Collectors.toCollection(ArrayList::new));

        invokeArgs.add(TEMP_FILE_NAME);

        return invokeArgs.toArray(Object[]::new);
    }
}
