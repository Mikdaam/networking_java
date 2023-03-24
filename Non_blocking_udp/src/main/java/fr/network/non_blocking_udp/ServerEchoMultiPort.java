package fr.network.non_blocking_udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

public class ServerEchoMultiPort {
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private record Context(InetSocketAddress client, ByteBuffer clientData) {}
    private int portStart;
    private int portEnd;
    public ServerEchoMultiPort(int portStart, int portEnd) throws IOException {
        this.portStart = portStart;
        this.portEnd = portEnd;
        this.selector = Selector.open();
    }

    public static void usage() {
        System.out.println("Usage : ServerEchoPlusNonBlocking portStart portEnd");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        new ServerEchoMultiPort(Integer.parseInt(args[0]), Integer.parseInt(args[1])).serve();
    }

    private void serve() {
    }
}
