package org.togetherjava.tjbot.imc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

/**
 * File manager that tries to keep every java file output in memory
 *
 * @see JavaFileManager
 * @see ByteJavaFileObjectLoader
 */
class InMemoryJavacFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final @NotNull ByteJavaFileObjectLoader classLoader;

    protected InMemoryJavacFileManager(@NotNull JavaFileManager fileManager,
            @NotNull ByteJavaFileObjectLoader classLoader) {
        super(fileManager);

        this.classLoader = classLoader;
    }

    @Override
    public @NotNull JavaFileObject getJavaFileForOutput(@Nullable Location location,
            @NotNull String className, @Nullable JavaFileObject.Kind kind,
            @Nullable FileObject sibling) {
        return classLoader.registerJFO(new InMemoryByteJavaFileObject(className));
    }

    public @NotNull ByteJavaFileObjectLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public @NotNull ByteJavaFileObjectLoader getClassLoader(@Nullable Location location) {
        return getClassLoader();
    }
}
