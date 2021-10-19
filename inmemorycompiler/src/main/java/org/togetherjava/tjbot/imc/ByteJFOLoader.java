package org.togetherjava.tjbot.imc;

import java.util.LinkedHashMap;
import java.util.Map;

class ByteJFOLoader extends ClassLoader {
    private final Map<String, IMByteJFO> classJFO = new LinkedHashMap<>();

    public ByteJFOLoader(ClassLoader parent) {
        super(parent);
    }

    public IMByteJFO registerJFO(IMByteJFO jfo) {
        classJFO.put(jfo.getName(), jfo);

        return jfo;
    }

    @Override
    protected Class<?> findClass(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public byte[] getLastBytes() {
        return ((IMByteJFO) last(classJFO.values().toArray())).getBytes();
    }

    private <E> E last(E[] col) {
        return col[col.length - 1];
    }
}
