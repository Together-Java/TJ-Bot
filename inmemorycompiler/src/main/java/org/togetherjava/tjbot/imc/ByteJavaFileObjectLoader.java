package org.togetherjava.tjbot.imc;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

class ByteJavaFileObjectLoader extends ClassLoader {
    private final Map<String, InMemoryByteJavaFileObject> nameToClassJFO = new LinkedHashMap<>();

    public ByteJavaFileObjectLoader(@NotNull ClassLoader parent) {
        super(parent);
    }

    public @NotNull InMemoryByteJavaFileObject registerJFO(@NotNull InMemoryByteJavaFileObject jfo) {
        nameToClassJFO.put(jfo.getName(), jfo);

        return jfo;
    }

    /**
     * Only meant to be used for registering and getting, not finding.
     * There are no plans to implement this, and it is also not needed.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    protected @NotNull Class<?> findClass(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public byte @NotNull [] getLastBytes() {
        return last(nameToClassJFO.values().toArray(InMemoryByteJavaFileObject[]::new)).getBytes();
    }

    private <E> @NotNull E last(@NotNull E[] col) {
        return col[col.length - 1];
    }
}
