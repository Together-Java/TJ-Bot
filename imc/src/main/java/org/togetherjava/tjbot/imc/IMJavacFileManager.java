package org.togetherjava.tjbot.imc;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

class IMJavacFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final ByteJFOLoader cl;

    protected IMJavacFileManager(JavaFileManager fileManager, ByteJFOLoader cl) {
        super(fileManager);

        this.cl = cl;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className,
            JavaFileObject.Kind kind, FileObject sibling) {
        return cl.registerJFO(new IMByteJFO(className));
    }

    public ByteJFOLoader getClassLoader() {
        return cl;
    }

    @Override
    public ByteJFOLoader getClassLoader(Location location) {
        return getClassLoader();
    }
}
