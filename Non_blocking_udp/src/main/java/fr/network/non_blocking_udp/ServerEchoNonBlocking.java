package fr.network.non_blocking_udp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerEchoNonBlocking {
    private static final Logger logger = Logger.getLogger(ServerEchoNonBlocking.class.getName());

    private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress sender;
    private int port;

    public ServerEchoNonBlocking(int port) throws IOException {
        this.port = port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        // TODO set dc in non-blocking mode and register it to the selector
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE);
    }

    public void serve() throws IOException {
        logger.info("ServerEchoNonBlocking started on port " + port);
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
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
        } catch (IOException e) {
            // TODO
            logger.log(Level.SEVERE, "Severe IO problem, ", e);
        }

    }

    private void doRead(SelectionKey key) throws IOException {
        // TODO
        System.out.println("The key is: " + key);
        var chan = (DatagramChannel) key.channel();
        System.out.println("The chan is: " + chan);
        var sender = chan.receive(buffer);
        if (sender == null) {
            selector.select();
        } else {
            buffer.flip();
        }
    }

    private void doWrite(SelectionKey key) throws IOException {
        // TODO
        System.out.println("The key is: " + key);
        var chan = (DatagramChannel) key.channel();
        System.out.println("The chan is: " + chan);
        chan.send(buffer, sender);
        if (buffer.hasRemaining()) {
            selector.select();
        } else {
            buffer.clear();
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerEchoNonBlocking port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerEchoNonBlocking(Integer.parseInt(args[0])).serve();
    }
}