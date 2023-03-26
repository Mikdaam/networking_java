package fr.network.non_blocking_udp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoMultiPort {
    private static final Logger logger = Logger.getLogger(ServerEchoNonBlocking.class.getName());
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private static class Context {
        private InetSocketAddress client;
        private final ByteBuffer data;
        public Context (InetSocketAddress client, ByteBuffer clientData) {
            this.client = client;
            this.data = clientData;
        }

        public void doRead(SelectionKey key) throws IOException {
            var dc = (DatagramChannel) key.channel();
            data.clear(); // Always put the clear before receive
            this.client = (InetSocketAddress) dc.receive(data);
            if (this.client == null) {
                return; // In case there is an error
            }
            data.flip();
            key.interestOps(SelectionKey.OP_WRITE);
        }

        public void doWrite(SelectionKey key) throws IOException {
            var dc = (DatagramChannel) key.channel();
            dc.send(data, this.client);
            if (data.hasRemaining()) { // ! Never put important code in if statement
                return;
            }
            key.interestOps(SelectionKey.OP_READ);
        }
    }
    private final int portStart;
    private final int portEnd;
    public ServerEchoMultiPort(int portStart, int portEnd) throws IOException {
        this.portStart = portStart;
        this.portEnd = portEnd;
        this.selector = Selector.open();
        for (int port = portStart; port <= portEnd; port++) {
            var dc = DatagramChannel.open();
            dc.configureBlocking(false);
            dc.bind(new InetSocketAddress(port));
            dc.register(selector, SelectionKey.OP_READ, new Context(new InetSocketAddress(port), ByteBuffer.allocate(BUFFER_SIZE)));
        }
    }

    public void serve() throws IOException {
        logger.info("ServerEchoMultiPort started from port " + portStart + " to " + portEnd);
        while (!Thread.interrupted()) {
            try {
                selector.select(this::treatKey);
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            var context = (Context) key.attachment();
            if (key.isValid() && key.isWritable()) {
                context.doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                context.doRead(key);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

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
}
