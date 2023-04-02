package fr.network.non_blocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientIdUpperCaseUDPOneByOne {

    private static final Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;


    private enum State {
        SENDING, RECEIVING, FINISHED
    }

    private final List<String> lines;
    private final List<String> upperCaseLines = new ArrayList<>();
    private final long timeout;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final Selector selector;
    private final SelectionKey uniqueKey;

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private State state;
    private int currentId = 0;
    private long lastSend;

    private record Packet(long id, String line) {
        public void encode(ByteBuffer buffer) {
            buffer.clear();
            buffer.putLong(id);
            var encodedString = UTF8.encode(line);
            if (buffer.remaining() >= encodedString.remaining()) {
                buffer.put(encodedString);
            }
            buffer.flip();
        }

        public static Optional<Packet> decode(ByteBuffer buffer) {
            buffer.flip();
            if (buffer.remaining() < Long.BYTES) {
                return Optional.empty();
            }
            var id = buffer.getLong();
            var line = UTF8.decode(buffer).toString();
            return Optional.of(new Packet(id, line));
        }
    }

    private static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
    }
    
    private ClientIdUpperCaseUDPOneByOne(List<String> lines, long timeout, InetSocketAddress serverAddress,
            DatagramChannel dc, Selector selector, SelectionKey uniqueKey){
        this.lines = lines;
        this.timeout = timeout;
        this.serverAddress = serverAddress;
        this.dc = dc;
        this.selector = selector;
        this.uniqueKey = uniqueKey;
        this.state = State.SENDING;
    }

    public static ClientIdUpperCaseUDPOneByOne create(String inFilename, long timeout,
            InetSocketAddress serverAddress) throws IOException {
        Objects.requireNonNull(inFilename);
        Objects.requireNonNull(serverAddress);
        Objects.checkIndex(timeout, Long.MAX_VALUE);
        
        // Read all lines of inFilename opened in UTF-8
        var lines = Files.readAllLines(Path.of(inFilename), UTF8);
        var dc = DatagramChannel.open();
        dc.configureBlocking(false);
        dc.bind(null);
        var selector = Selector.open();
        var uniqueKey = dc.register(selector, SelectionKey.OP_WRITE);
        return new ClientIdUpperCaseUDPOneByOne(lines, timeout, serverAddress, dc, selector, uniqueKey);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 5) {
            usage();
            return;
        }

        var inFilename = args[0];
        var outFilename = args[1];
        var timeout = Long.parseLong(args[2]);
        var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

        // Create client with the parameters and launch it
        var upperCaseLines = create(inFilename, timeout, server).launch();
        
        Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
    }

    private List<String> launch() throws IOException {
        try {
            while (!isFinished()) {
                try {
                    selector.select(this::treatKey, updateInterestOps());
                } catch (UncheckedIOException tunneled) {
                    throw tunneled.getCause();
                }
            }
            return upperCaseLines;
        } finally {
            dc.close();
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                doRead();
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Updates the interestOps on key based on state of the context
     *
     * @return the timeout for the next select (0 means no timeout)
     */

    private long updateInterestOps() {
        if (state == State.SENDING) {
            uniqueKey.interestOps(SelectionKey.OP_WRITE);
            return 0;
        } else if (state == State.RECEIVING) {
            var time = System.currentTimeMillis() - lastSend;
            if (time > timeout) {
                state = State.SENDING;
                uniqueKey.interestOps(SelectionKey.OP_WRITE);
                return 0;
            }

            uniqueKey.interestOps(SelectionKey.OP_READ);
            return timeout - time;
        } else {
            return timeout;
        }
    }

    private boolean isFinished() {
        return state == State.FINISHED;
    }

    /**
     * Performs the receptions of packets
     *
     * @throws IOException R
     */

    private void doRead() throws IOException {
        buffer.clear();
        var server = dc.receive(buffer);
        if (server == null) {
            logger.info("Packet not receive, so wait for timeout second");
            return;
        }
        Packet.decode(buffer).ifPresentOrElse(
                packet -> {
                    if (packet.id != currentId) {
                        return;
                    }
                    upperCaseLines.add(packet.line);

                    currentId++;
                    if (currentId >= lines.size()) {
                        state = State.FINISHED;
                    }
                },
                () -> logger.info("Wrong packet format")
        );
    }

    /**
     * Tries to send the packets
     *
     * @throws IOException R
     */

    private void doWrite() throws IOException {
        new Packet(currentId, lines.get(currentId)).encode(buffer);
        dc.send(buffer, serverAddress);
        if (buffer.hasRemaining()) {
            logger.info("Packet not sent, so resent after timeout");
            return;
        }
        state = State.RECEIVING;
        lastSend = System.currentTimeMillis();
    }
}