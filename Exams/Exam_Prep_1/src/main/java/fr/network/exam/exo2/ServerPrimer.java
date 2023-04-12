package fr.network.exam.exo2;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

public class ServerPrimer {
    private final DatagramChannel dc;
    private final HashMap<InetSocketAddress, HashSet<Long>> data = new HashMap<>();
    private final HashSet<Long> primeNumbers = new HashSet<>();
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(Long.BYTES * 3);

    private static final Logger logger = Logger.getLogger(ServerPrimer.class.getName());

    public ServerPrimer(int port) throws IOException {
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        System.out.println("ServerPrimer started on port " + port);
    }

    private static boolean isPrime(long n) {
        if (n <= 1)
            return false;
        for (int i = 2; i <= Math.sqrt(n); i++) {
            if (n % i == 0)
                return false;
        }
        return true;
    }

    public void serve() throws IOException {
        try {
            while (!Thread.interrupted()) {
                buffer.clear(); // clearing buffer
                var client = (InetSocketAddress) dc.receive(buffer);
                buffer.flip();

                if (buffer.remaining() < Long.BYTES) {
                    logger.warning("Mal formed packet");
                    continue;
                }
                long number = buffer.getLong();
                if (number < 0) {
                    logger.warning("Not a positive number");
                    continue;
                }
                buffer.clear();

                var clientNumbers = data.computeIfAbsent(client, __ -> new HashSet<>());
                if (isPrime(number)) {
                    clientNumbers.add(number);
                    primeNumbers.add(number);
                }

                long clientAvg = (long) clientNumbers.stream().mapToLong(Long::longValue).average().orElse(0);
                long avg = (long) primeNumbers.stream().mapToLong(Long::longValue).average().orElse(0);

                buffer.putLong(number); // put the number
                buffer.putLong(clientAvg); // put the client avg
                buffer.putLong(avg); // put the total avg

                buffer.flip();

                dc.send(buffer, client); // send response to the client
            }
        } finally {
            data.clear();
            primeNumbers.clear();
            dc.close();
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerPrimer port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        ServerPrimer server;
        int port = Integer.parseInt(args[0]);
        if (!(port >= 1024) & port <= 65535) {
            System.out.println("The port number must be between 1024 and 65535");
            return;
        }
        try {
            server = new ServerPrimer(port);
        } catch (BindException e) {
            System.out
                    .println("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
        server.serve();
    }
}