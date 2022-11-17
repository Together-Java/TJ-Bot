package org.togetherjava.tjbot.javap;

import javax.annotation.Nullable;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

/**
 * File manager that always returns given predefined data for any file request.
 *
 * @see JavaFileManager
 */
final class InMemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final byte[] fileData;
    private final String fileName;

    InMemoryJavaFileManager(JavaFileManager fileManager, byte[] fileData, String fileName) {
        super(fileManager);

        this.fileData = fileData.clone();
        this.fileName = fileName;
    }

    @Override
    public JavaFileObject getJavaFileForInput(@Nullable Location location,
            @Nullable String className, JavaFileObject.Kind kind) {
        return new InMemoryJavaFile(kind, fileData, fileName);
    }

    private static final class InMemoryJavaFile extends SimpleJavaFileObject {
        private final byte[] fileData;

        private InMemoryJavaFile(Kind kind, byte[] fileData, String fileName) {
            super(URI.create("file:///" + fileName), kind);

            this.fileData = fileData;
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(fileData);
        }
    }
}
