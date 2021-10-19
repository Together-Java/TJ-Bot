package org.togetherjava.tjbot.javap;

import java.io.PrintWriter;

class StringPrintWriter extends PrintWriter {
    public StringPrintWriter() {
        super(new StringPrintWriter0());
    }

    public String getString() {
        return ((StringPrintWriter0) super.out).stringBuilder.toString();
    }

    @Override
    public String toString() {
        return getString();
    }
}
