package org.togetherjava.tjbot.imc;

import java.util.LinkedHashMap;
import java.util.Map;

class ByteJavaFileObjectLoader extends ClassLoader {
    private final Map<String, InMemoryByteJavaFileObject> nameToClassJFO = new LinkedHashMap<>();

    public ByteJavaFileObjectLoader(ClassLoader parent) {
        super(parent);
    }

    public InMemoryByteJavaFileObject registerJFO(
            InMemoryByteJavaFileObject jfo) {
        nameToClassJFO.put(jfo.getName(), jfo);

        return jfo;
    }

    /**
     * Only meant to be used for registering and getting, not finding. There are no plans to
     * implement this, and it is also not needed.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    protected Class<?> findClass(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public byte[] getLastBytes() {
        return last(nameToClassJFO.values().toArray(InMemoryByteJavaFileObject[]::new)).getBytes();
    }

    private <E> E last(E[] col) {
        return col[col.length - 1];
    }
}
