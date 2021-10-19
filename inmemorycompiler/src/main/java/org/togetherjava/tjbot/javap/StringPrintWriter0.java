package org.togetherjava.tjbot.javap;

import java.io.StringWriter;

class StringPrintWriter0 extends StringWriter {
    final StringBuilder stringBuilder = new StringBuilder();

    @Override
    public void write(String str) {
        stringBuilder.append(str);
    }

    @Override
    public void write(int c) {
        stringBuilder.append(c);
    }

    @Override
    public void write(char[] cbuf) {
        stringBuilder.append(cbuf);
    }

    @Override
    public void write(String str, int off, int len) {
        stringBuilder.append(str, off, len);
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        stringBuilder.append(cbuf, off, len);
    }
}
