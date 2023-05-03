package fr.networks.tcp.nonblocking.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record Message(String login, String msg) {
    public ByteBuffer encode() {
        var bb = ByteBuffer.allocate(1024);
        var loginBytes = StandardCharsets.UTF_8.encode(login);
        var msgBytes = StandardCharsets.UTF_8.encode(msg);
        return bb.putInt(loginBytes.remaining()).put(loginBytes)
                .putInt(msgBytes.remaining()).put(msgBytes);
    }

    @Override
    public String toString() {
        return login + ": " + msg;
    }
}
