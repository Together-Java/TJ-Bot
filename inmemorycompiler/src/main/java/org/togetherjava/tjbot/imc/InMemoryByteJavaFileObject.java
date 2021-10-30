package org.togetherjava.tjbot.imc;

import org.jetbrains.annotations.NotNull;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * Represents a {@link SimpleJavaFileObject} that exists purely in memory.
 *
 * @see javax.tools.SimpleJavaFileObject
 */
class InMemoryByteJavaFileObject extends SimpleJavaFileObject {
    private final @NotNull ByteArrayOutputStream baos = new ByteArrayOutputStream();

    public InMemoryByteJavaFileObject(@NotNull String className) {
        super(URI.create(className), Kind.CLASS);
    }

    @Override
    public @NotNull OutputStream openOutputStream() {
        return baos;
    }

    public byte @NotNull [] getBytes() {
        return baos.toByteArray();
    }
}
