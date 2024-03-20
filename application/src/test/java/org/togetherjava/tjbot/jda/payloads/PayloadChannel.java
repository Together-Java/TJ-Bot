package org.togetherjava.tjbot.jda.payloads;

public final class PayloadChannel {

    private String id;
    private int type;

    public PayloadChannel(String id, int type) {
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
