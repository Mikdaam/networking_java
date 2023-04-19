package fr.network.fault_tolerant_udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientUpperCaseUDPFile {
    private final static Charset UTF8 = StandardCharsets.UTF_8;
    private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPFile.class.getName());
    private final static int BUFFER_SIZE = 1024;

    private static void usage() {
        System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 5) {
            usage();
            return;
        }

        var inFilename = args[0];
        var outFilename = args[1];
        var timeout = Integer.parseInt(args[2]);
        var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));
        var messageQueue = new ArrayBlockingQueue<String>(10);

        // Read all lines of inFilename opened in UTF-8
        var lines = Files.readAllLines(Path.of(inFilename), UTF8);
        var upperCaseLines = new ArrayList<String>();

        // turn every line in uppercase using the ServerUpperCaseUDP whose address is given as a parameter
        try (var dataChan = DatagramChannel.open()) {
            var buffer = ByteBuffer.allocate(BUFFER_SIZE);

            // ! Should start the thread first (before doing anything)
            Thread.ofPlatform().start(() -> {
                while (true) {
                    try {
                        var exp = dataChan.receive(buffer);
                        buffer.flip();
                        logger.info("Received " + buffer.remaining() + " bytes from " + exp);
                        messageQueue.put(UTF8.decode(buffer).toString());
                        buffer.clear();
                    } catch (InterruptedException e) {
                        throw new AssertionError();
                    } catch (AsynchronousCloseException e) {
                        logger.info("Closing listener");
                        break;
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }
                }
            });

            for (var line: lines) {
                String upperLine;
                try {
                    dataChan.send(UTF8.encode(line), server);
                    while (((upperLine = messageQueue.poll(timeout, TimeUnit.MILLISECONDS)) == null)) {
                        dataChan.send(UTF8.encode(line), server);
                    }
                    upperCaseLines.add(upperLine);
                } catch (InterruptedException | IOException e) {
                    throw new AssertionError(e);
                }
            }
        }

        // Write upperCaseLines to outFilename in UTF-8
        Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
    }
}