package fr.networks.tcpexam.exo1;


import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.logging.Logger;

public class ServerFixedPrestartedPrime {
    private static final Logger logger = Logger.getLogger(ServerFixedPrestartedPrime.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final int nbThreads;
    private final HashMap<Integer, Counter> proposedTimes;

    private static class Counter {
        private int count;
        public Counter(int count) {
            this.count = count;
        }
        public void increment() {
            count++;
        }
        public int count() {
            return count;
        }
    }

    public ServerFixedPrestartedPrime(int port, int nbThreads) throws IOException {
        this.serverSocketChannel = ServerSocketChannel.open();
        this.nbThreads = nbThreads;
        serverSocketChannel.bind(new InetSocketAddress(port));
        proposedTimes = new HashMap<>();
        logger.info(this.getClass().getName()
                + " starts on port " + port);
    }

    private static boolean isPrime(int n) {
        if (n <= 1) return false;
        for (int i = 2; i <= Math.sqrt(n); i++) {
            if (n % i == 0) return false;
        }
        return true;
    }

    public void launch() throws IOException {
        logger.info("Server started");

        for (int i = 0; i < nbThreads; i++) {
            Thread.ofPlatform().start(() -> {
                try {
                    while (!Thread.interrupted()) {
                        var client = serverSocketChannel.accept();
                        logger.info("Connection accepted from " + client.getRemoteAddress());

                        try {
                            serve(client);
                        } catch (IOException e) {
                            logger.info("Connection terminated by client, " + e.getCause());
                        } finally {
                            silentlyClose(client);
                        }
                    }
                } catch (IOException e) {
                    logger.severe("Accept throws an exception");
                }
            });
        }
    }

    private void serve(SocketChannel client) throws IOException {
        var reqBuff = ByteBuffer.allocate(Integer.BYTES);
        var resBuff = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
        while (true) {
            reqBuff.clear();
            if (!readFully(client, reqBuff)) {
                logger.info("Wrong packet format.");
                return;
            }
            reqBuff.flip();
            int number = reqBuff.getInt();

            if (number < 0) {
                logger.info("Wrong packet format.");
                return;
            }

            resBuff.clear();
            if (isPrime(number)) {
                var counter = proposedTimes.computeIfAbsent(number, key -> new Counter(0));
                counter.increment();
                resBuff.put((byte) 1).putInt(counter.count());
            } else {
                resBuff.put((byte) 0);
            }

            resBuff.flip();
            client.write(resBuff);
        }
    }

    private boolean readFully(SocketChannel client, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (client.read(buffer) == -1) {
                return false;
            }
        }
        return true;
    }

    private void silentlyClose(Closeable sc) {
        if (sc != null) {
            try {
                sc.close();
            } catch (IOException e) {
                // Do nothing
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("usage: java fr.upem.net.tcp.exam2122.ServerFixedPrestarted port nbThreads");
            return;
        }
        var server = new ServerFixedPrestartedPrime(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.launch();
    }
}
