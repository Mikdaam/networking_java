package fr.network.exam.exo2;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class ServerPrimer {
    private final DatagramChannel dc;

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(Long.BYTES * 3);
    private final HashMap<InetSocketAddress, ArrayList<Long>> clientData = new HashMap<>();
    private final HashSet<Long> primes = new HashSet<>();

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
                buffer.clear();
                // receive a request
                var client = (InetSocketAddress) dc.receive(buffer);
                buffer.flip();

                // verification
                if (buffer.remaining() < Long.BYTES) {
                    System.out.println("Malformed packet");
                    continue;
                }

                // get number
                long number = buffer.getLong();
                if (number < 0) {
                    System.out.println("Number less than 0");
                    continue;
                }

                var clientPrimes = clientData.computeIfAbsent(client, k -> new ArrayList<>());
                if (isPrime(number)) {
                    clientPrimes.add(number);
                    primes.add(number);
                }

                // send a response
                buffer.clear();
                buffer.putLong(number);
                long clientAvg = (long) clientPrimes.stream().mapToLong(Long::longValue).average().orElse(0);
                buffer.putLong(clientAvg);
                long avg = (long) primes.stream().mapToLong(Long::longValue).average().orElse(0);
                buffer.putLong(avg);
                buffer.flip();
                dc.send(buffer, client);
            }
        } finally {
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
        int port = Integer.valueOf(args[0]);
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