package org.togetherjava.tjbot.imc;

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
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    public InMemoryByteJavaFileObject(String className) {
        super(URI.create(className), Kind.CLASS);
    }

    @Override
    public OutputStream openOutputStream() {
        return baos;
    }

    public byte[] getBytes() {
        return baos.toByteArray();
    }
}
