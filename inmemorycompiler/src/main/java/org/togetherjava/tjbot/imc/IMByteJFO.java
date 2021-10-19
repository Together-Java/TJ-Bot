package org.togetherjava.tjbot.imc;

import org.jetbrains.annotations.NotNull;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

class IMByteJFO extends SimpleJavaFileObject {
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    public IMByteJFO(@NotNull String className) {
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
