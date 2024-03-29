package fr.network.non_blocking_udp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.logging.Logger;

public class ServerEchoPlusNonBlocking {
    private static final Logger logger = Logger.getLogger(ServerEchoNonBlocking.class.getName());

    private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final byte MODULO = 1 % 256;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress sender;
    private int port;

    public ServerEchoPlusNonBlocking(int port) throws IOException {
        this.port = port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        // TODO set dc in non-blocking mode and register it to the selector
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
    }

    public void serve() throws IOException {
        logger.info("ServerEchoPlusNonBlocking started on port " + port);
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
            throw new UncheckedIOException(e);
        }

    }

    private void doRead(SelectionKey key) throws IOException {
        // TODO
        var tempBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        buffer.clear();
        // receive the packet
        sender = dc.receive(tempBuffer);
        tempBuffer.flip();
        if (sender == null) {
            return; // In case there is an error
        }
        // convert the packet
        while (tempBuffer.hasRemaining()) {
            var res = tempBuffer.get();
            buffer.put((byte) (res + MODULO));
        }
        buffer.flip();
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        // TODO
        // Send the packet back
        dc.send(buffer, sender);
        if (buffer.hasRemaining()) { // ! Never put important code in if statement
            return;
        }
        buffer.clear();
        key.interestOps(SelectionKey.OP_READ);
    }

    public static void usage() {
        System.out.println("Usage : ServerEchoPlusNonBlocking port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerEchoPlusNonBlocking(Integer.parseInt(args[0])).serve();
    }
}