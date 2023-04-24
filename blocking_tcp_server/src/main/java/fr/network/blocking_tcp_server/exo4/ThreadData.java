package fr.network.blocking_tcp_server.exo4;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ThreadData {
    private final Object lock = new Object();
    private SocketChannel socketChannel;
    private long lastUpdated;

    public void setSocketChannel(SocketChannel client) {
        synchronized (lock) {
            socketChannel = client;
        }
    }

    public void tick() {
        synchronized (lock) {
            lastUpdated = System.currentTimeMillis();
        }
    }

    public void closeIfInactive(long timeout) throws IOException {
        synchronized (lock) {
            if (System.currentTimeMillis() - lastUpdated > timeout) {
                close();
            }
        }
    }

    public void close() throws IOException {
        synchronized (lock) {
            socketChannel.close();
        }
    }
}
