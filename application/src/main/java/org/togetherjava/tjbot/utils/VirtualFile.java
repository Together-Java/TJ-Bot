package org.togetherjava.tjbot.utils;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class VirtualFile {
    private final String name;
    private final String content;

    /**
     *
     * @param name the name of the file, example: {@code "foo.md"}
     * @param content the content of the file.
     */
    public VirtualFile(@NotNull String name, @NotNull String content) {
        this.name = name;
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public InputStream getAsInputStream() {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
