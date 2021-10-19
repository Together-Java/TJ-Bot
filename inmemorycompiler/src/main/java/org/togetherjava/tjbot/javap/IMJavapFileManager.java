package org.togetherjava.tjbot.javap;

import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

class IMJavapFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final byte[] bytes;
    private final String fileName;

    protected IMJavapFileManager(JavaFileManager fileManager, byte[] bytes, String fileName) {
        super(fileManager);

        this.bytes = bytes;
        this.fileName = fileName;
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className,
            JavaFileObject.Kind kind) {
        return new SimpleJavaFileObject(URI.create("file:///%s".formatted(fileName)), kind) {
            @Override
            public InputStream openInputStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }
}
