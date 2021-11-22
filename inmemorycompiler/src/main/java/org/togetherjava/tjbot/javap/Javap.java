package org.togetherjava.tjbot.javap;

import org.jetbrains.annotations.NotNull;

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
 * A javap-interface that uses reflection to access locked-down classes of the jdk.jdeps module and
 * keep everything in-memory
 */
public final class Javap {
    // Hide constructor
    private Javap() {}

    private static final String TEMP_FILE_NAME = "tmp",
            ANNOYING_WARNING_MESSAGE = "Warning: File /%s does not contain class %<s";

    private static final Class<?> javapFileManagerCls;
    private static final Class<?> javapTaskCls;

    private static final Method javapFileManagerCreateMethod;
    private static final Method javapTaskRunMethod;
    private static final Method javapTaskGetDiagnosticListenerForWriterMethod;

    private static final Field javapTaskDefaultFileManagerField;
    private static final Field javapTaskLogField;

    static {
        try {
            javapFileManagerCls = Class.forName("com.sun.tools.javap.JavapFileManager");
            javapFileManagerCreateMethod = javapFileManagerCls.getDeclaredMethod("create",
                    DiagnosticListener.class, PrintWriter.class);

            javapTaskCls = Class.forName("com.sun.tools.javap.JavapTask");
            javapTaskDefaultFileManagerField = javapTaskCls.getDeclaredField("defaultFileManager");
            javapTaskLogField = javapTaskCls.getDeclaredField("log");
            javapTaskRunMethod = javapTaskCls.getDeclaredMethod("run", String[].class);
            javapTaskGetDiagnosticListenerForWriterMethod =
                    javapTaskCls.getDeclaredMethod("getDiagnosticListenerForWriter", Writer.class);

            javapFileManagerCreateMethod.setAccessible(true);
            javapTaskDefaultFileManagerField.setAccessible(true);
            javapTaskLogField.setAccessible(true);
            javapTaskRunMethod.setAccessible(true);
            javapTaskGetDiagnosticListenerForWriterMethod.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException ex) {
            throw new ReflectionException(
                    "A fatal exception has occurred while using reflection in the non-static initializer of org.togetherjava.tjbot.javap.Javap",
                    ex);
        }
    }

    /**
     * Disassembles the given classfile bytecode with given program arguments
     *
     * @param bytes classfile bytecode
     * @param options options to give to javap
     * @return disassembled view
     * @throws ReflectionException if an exception occurs while doing reflection black magic
     */
    public static @NotNull String disassemble(byte @NotNull [] bytes,
            @NotNull JavapOption @NotNull... options) throws ReflectionException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter log = new PrintWriter(stringWriter);

        try {
            Object javapTaskInstance = createJavapTaskObject();

            javapTaskDefaultFileManagerField.set(javapTaskInstance, new IMJavapFileManager(
                    getDefaultFileManager(javapTaskInstance, log), bytes, TEMP_FILE_NAME));

            javapTaskLogField.set(javapTaskInstance, log);

            List<String> invokeOptions = Arrays.stream(options)
                .map(JavapOption::getOption)
                .collect(Collectors.toCollection(ArrayList::new));

            invokeOptions.add(TEMP_FILE_NAME);

            javapTaskRunMethod.invoke(javapTaskInstance,
                    (Object) invokeOptions.toArray(new String[0]));
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException
                | IllegalAccessException ex) {
            throw new ReflectionException("A fatal exception has occurred while using reflection",
                    ex);
        }

        return stringWriter.toString()
            .replace(ANNOYING_WARNING_MESSAGE.formatted(TEMP_FILE_NAME), "");
    }

    /**
     * Gets the default file manager instance from a {@link com.sun.tools.javap.JavapTask} and a
     * {@link PrintWriter} using
     * {@link com.sun.tools.javap.JavapFileManager#create(DiagnosticListener, PrintWriter)}
     */
    private static @NotNull JavaFileManager getDefaultFileManager(@NotNull Object JavapTask,
            @NotNull PrintWriter log) throws InvocationTargetException, IllegalAccessException {
        return (JavaFileManager) javapFileManagerCreateMethod.invoke(null,
                getDiagnosticListenerForWriter(JavapTask, log), log);
    }

    /**
     * Creates a new {@link com.sun.tools.javap.JavapTask} instance
     */
    private static @NotNull Object createJavapTaskObject() throws NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException {
        return javapTaskCls.getConstructor().newInstance();
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
        return (DiagnosticListener<JavaFileObject>) javapTaskGetDiagnosticListenerForWriterMethod
            .invoke(JavapTask, log); // getDiagnosticListenerForWriter's signature:
                                     // DiagnosticListener<JavaFileObject>
                                     // getDiagnosticListenerForWriter(Writer)
    }
}
