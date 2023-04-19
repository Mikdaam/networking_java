package fr.network.fault_tolerant_udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ClientUpperCaseUDPRetry {
    private static final Logger logger = Logger.getLogger(ClientUpperCaseUDPRetry.class.getName());
    public static final int BUFFER_SIZE = 1024;

    private static void usage() {
        System.out.println("Usage : NetcatUDP host port charset");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 3) {
            usage();
            return;
        }

        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        var cs = Charset.forName(args[2]);
        var buffer = ByteBuffer.allocate(BUFFER_SIZE);
        var messageQueue = new ArrayBlockingQueue<String>(10);

        try(var dataChan = DatagramChannel.open()) {
            dataChan.bind(null); // Choose a random port.

            Thread.ofPlatform().start(() -> {
                while (true) {
                    try {
                        var exp = dataChan.receive(buffer);
                        buffer.flip();
                        System.out.println("Received " + buffer.remaining() + " bytes from " + exp);
                        messageQueue.put(cs.decode(buffer).toString());
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

            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    var line = scanner.nextLine();
                    dataChan.send(cs.encode(line), server);
                    String msg;
                    while ((msg = messageQueue.poll(1_000, TimeUnit.MILLISECONDS)) == null) {
                        dataChan.send(cs.encode(line), server);
                    }
                    System.out.println("String: " +  msg);
                }
            }
        }
        logger.info("End Of Program");
    }
}