package org.togetherjava.tjbot.javap;

import org.jetbrains.annotations.NotNull;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A javap-interface that uses reflection to access locked-down classes of the jdk.jdeps module and
 * keep everything in-memory
 */
public final class Javap {
    private static final String TEMP_FILE_NAME = "tmp";
    private static final String ANNOYING_WARNING_MESSAGE = "Warning: File /%s does not contain class %<s";

    // ======== reflection

    // ---- classes
    private static final Class<?> R_C_JavapFileManager;
    private static final Class<?> R_C_JavapTask;

    // ---- methods
    private static final Method R_C_JavapFileManager_M_create;
    private static final Method R_C_JavapTask_M_run;
    private static final Method R_C_JavapTask_M_getDiagnosticListenerForWriter;

    // ---- fields
    private static final Field R_C_JavapTask_F_defaultFileManager;
    private static final Field R_C_JavapTask_F_log;

    static {
        try {
            R_C_JavapFileManager = Class.forName("com.sun.tools.javap.JavapFileManager");
            R_C_JavapFileManager_M_create = R_C_JavapFileManager.getDeclaredMethod("create",
                    DiagnosticListener.class, PrintWriter.class);

            R_C_JavapTask = Class.forName("com.sun.tools.javap.JavapTask");
            R_C_JavapTask_F_defaultFileManager =
                    R_C_JavapTask.getDeclaredField("defaultFileManager");
            R_C_JavapTask_F_log = R_C_JavapTask.getDeclaredField("log");
            R_C_JavapTask_M_run = R_C_JavapTask.getDeclaredMethod("run", String[].class);
            R_C_JavapTask_M_getDiagnosticListenerForWriter =
                    R_C_JavapTask.getDeclaredMethod("getDiagnosticListenerForWriter", Writer.class);

            // ------- make things accessible

            R_C_JavapFileManager_M_create.setAccessible(true);
            R_C_JavapTask_F_defaultFileManager.setAccessible(true);
            R_C_JavapTask_F_log.setAccessible(true);
            R_C_JavapTask_M_run.setAccessible(true);
            R_C_JavapTask_M_getDiagnosticListenerForWriter.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException ex) {
            throw new ReflectionException(
                    "A fatal exception has occurred while using reflection in the non-static initializer of org.togetherjava.tjbot.javap.Javap",
                    ex);
        }
    }

    // ========

    /**
     * Disassembles the given classfile bytecode with given program arguments
     *
     * @param bytes classfile bytecode
     * @param options options to give to javap
     * @return disassembled view
     * @throws ReflectionException if an exception occurs while doing reflection black magic
     */
    public static @NotNull String disassemble(byte @NotNull [] bytes, @NotNull JavapOption @NotNull ... options) throws ReflectionException {
        PrintWriter log = new StringPrintWriter();

        try {
            Object JavapTask = R_I_JavapTask();

            R_C_JavapTask_F_defaultFileManager.set(JavapTask, new IMJavapFileManager(
                    getDefaultFileManager(JavapTask, log), bytes, TEMP_FILE_NAME));

            R_C_JavapTask_F_log.set(JavapTask, log);

            List<String> invokeOptions = Arrays.stream(options)
                .map(JavapOption::getOption)
                .collect(Collectors.toCollection(ArrayList::new));

            invokeOptions.add(TEMP_FILE_NAME);

            R_C_JavapTask_M_run.invoke(JavapTask,
                    new Object[] {invokeOptions.toArray(new String[0])});
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException
                | IllegalAccessException ex) {
            throw new ReflectionException("A fatal exception has occurred while using reflection",
                    ex);
        }

        return log.toString().replace(ANNOYING_WARNING_MESSAGE.formatted(TEMP_FILE_NAME), "");
    }

    /**
     * Gets the default file manager instance from a {@link com.sun.tools.javap.JavapTask} and a
     * {@link PrintWriter} using
     * {@link com.sun.tools.javap.JavapFileManager#create(DiagnosticListener, PrintWriter)}
     */
    private static @NotNull JavaFileManager getDefaultFileManager(@NotNull Object JavapTask, @NotNull PrintWriter log)
            throws InvocationTargetException, IllegalAccessException {
        return (JavaFileManager) R_C_JavapFileManager_M_create.invoke(null,
                getDiagnosticListenerForWriter(JavapTask, log), log);
    }

    /**
     * Creates a new {@link com.sun.tools.javap.JavapTask} instance
     */
    private static @NotNull Object R_I_JavapTask() throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        return R_C_JavapTask.getConstructor().newInstance();
    }

    /**
     * Generates a {@link DiagnosticListener} instance from a {@link com.sun.tools.javap.JavapTask}
     * and a {@link PrintWriter} using
     * {@link com.sun.tools.javap.JavapTask#getDiagnosticListenerForWriter(Writer)}
     */
    @SuppressWarnings("unchecked")
    private static @NotNull DiagnosticListener<JavaFileObject> getDiagnosticListenerForWriter(
            @NotNull Object JavapTask, @NotNull PrintWriter log)
            throws InvocationTargetException, IllegalAccessException {
        return (DiagnosticListener<JavaFileObject>) R_C_JavapTask_M_getDiagnosticListenerForWriter
            .invoke(JavapTask, log); // getDiagnosticListenerForWriter's signature: DiagnosticListener<JavaFileObject> getDiagnosticListenerForWriter(Writer)
    }
}
