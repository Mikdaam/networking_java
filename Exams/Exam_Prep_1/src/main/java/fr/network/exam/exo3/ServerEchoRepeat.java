package fr.network.exam.exo3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoRepeat {
    private static final Logger logger = Logger.getLogger(ServerEchoRepeat.class.getName());

    private static final ByteBuffer buffer = ByteBuffer.allocate(1024);

    private final DatagramChannel dc;
    private final Selector selector;
    private final int port;
    private int repeats;
    private int start;
    private int limit;
    private InetSocketAddress client;

    public ServerEchoRepeat(int port) throws IOException {
        this.port = port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
    }

    public void serve() throws IOException {
        logger.info("ServerEchoRepeat started on port " + port);
        while (!Thread.interrupted()) {
            selector.select(this::treatKey);
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
            throw new UncheckedIOException(e);
        }

    }

    private void doRead(SelectionKey key) throws IOException {
        buffer.clear();
        client = (InetSocketAddress) dc.receive(buffer);
        if (client == null) {
            return;
        }
        buffer.flip();
        if (buffer.remaining() < Integer.BYTES) {
            logger.warning("Mal formed packet");
            return;
        }
        repeats = buffer.getInt();
        if (repeats < 0) {
            logger.warning("Mal formed packet");
            return;
        }
        start = buffer.position();
        limit = buffer.limit();
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) throws IOException {
        dc.send(buffer, client);
        if (buffer.hasRemaining()) {
            return;
        }
        repeats--;

        // put the buffer in position again
        buffer.position(start);
        buffer.limit(limit);

        if (repeats == 0) {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerEchoRepeat port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerEchoRepeat(Integer.parseInt(args[0])).serve();
    }
}