package org.togetherjava.tjbot.imc;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

class ByteJFOLoader extends ClassLoader {
    private final Map<String, IMByteJFO> nameToClassJFO = new LinkedHashMap<>();

    public ByteJFOLoader(@NotNull ClassLoader parent) {
        super(parent);
    }

    public @NotNull IMByteJFO registerJFO(@NotNull IMByteJFO jfo) {
        nameToClassJFO.put(jfo.getName(), jfo);

        return jfo;
    }

    @Override
    protected @NotNull Class<?> findClass(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public byte[] getLastBytes() {
        return last(nameToClassJFO.values().toArray(IMByteJFO[]::new)).getBytes();
    }

    private <E> @NotNull E last(@NotNull E[] col) {
        return col[col.length - 1];
    }
}
