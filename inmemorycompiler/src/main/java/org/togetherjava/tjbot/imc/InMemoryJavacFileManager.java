package org.togetherjava.tjbot.imc;

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
    private final ByteJavaFileObjectLoader classLoader;

    protected InMemoryJavacFileManager(JavaFileManager fileManager,
            ByteJavaFileObjectLoader classLoader) {
        super(fileManager);

        this.classLoader = classLoader;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(@Nullable Location location,
            String className, @Nullable JavaFileObject.Kind kind,
            @Nullable FileObject sibling) {
        return classLoader.registerJFO(new InMemoryByteJavaFileObject(className));
    }

    public ByteJavaFileObjectLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public ByteJavaFileObjectLoader getClassLoader(@Nullable Location location) {
        return getClassLoader();
    }
}
