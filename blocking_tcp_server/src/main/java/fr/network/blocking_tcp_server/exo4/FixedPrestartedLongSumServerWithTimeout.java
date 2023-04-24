package fr.network.blocking_tcp_server.exo4;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FixedPrestartedLongSumServerWithTimeout {
    private static final Logger logger = Logger.getLogger(FixedPrestartedLongSumServerWithTimeout.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private final ServerSocketChannel serverSocketChannel;
    private final long timeout;
    private final static int MAX_CLIENT = 5;
    private final ThreadData[] threadsData = new ThreadData[MAX_CLIENT];


    public FixedPrestartedLongSumServerWithTimeout(int port, long timeout) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        logger.info(this.getClass().getName() + " starts on port " + port);
        this.timeout = timeout;
    }

    /**
     * Iterative server main loop
     *
     */

    public void launch() {
        logger.info("Server started");
        // Manager thread which check every 5000 milliseconds
        Thread.ofPlatform().start(() -> {
            try {
                for (var threadData: threadsData) {
                    threadData.closeIfInactive(timeout);
                }
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                logger.info("IOException");
            }
        });

        for (int i = 0; i < MAX_CLIENT; i++) {
            threadsData[i] = new ThreadData();
            int finalI = i;
            Thread.ofPlatform().start(() -> {
                try {
                    while (!Thread.interrupted()) {
                        SocketChannel client = serverSocketChannel.accept();
                        try {
                            logger.info("Connection accepted from " + client.getRemoteAddress());
                            serve(client, threadsData[finalI]);
                        } catch (IOException ioe) {
                            logger.log(Level.INFO, "Connection terminated with client by IOException", ioe.getCause());
                        } finally {
                            silentlyClose(client);
                        }
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Accept() can accept anymore");
                }
            });
        }
    }

    /**
     * Treat the connection sc applying the protocol. All IOException are thrown
     *
     * @param sc param
     * @throws IOException exception
     */
    private void serve(SocketChannel sc, ThreadData event) throws IOException {
        var bufferNbOps = ByteBuffer.allocate(Integer.BYTES);
        long sum = 0;

        while (true) {
            bufferNbOps.clear();
            if (!readFully(sc, bufferNbOps)) {
                logger.info("Wrong packet format. Close connection");
                return;
            }
            event.tick();
            bufferNbOps.flip();
            int nbOps = bufferNbOps.getInt();
            if (nbOps < 0) {
                logger.info("Wrong packet format. Close connection");
                return;
            }

            var longs = ByteBuffer.allocate(nbOps * Long.BYTES);
            if (!readFully(sc, longs)) {
                logger.info("Wrong packet format. Close connection");
                return;
            }
            event.tick();
            while (longs.hasRemaining()) {
                sum += longs.getLong();
            }

            longs.clear();
            longs.putLong(sum);
            longs.flip();

            sc.write(longs);
            event.tick();
        }
    }

    /**
     * Close a SocketChannel while ignoring IOExecption
     *
     * @param sc param
     */

    private void silentlyClose(Closeable sc) {
        if (sc != null) {
            try {
                sc.close();
            } catch (IOException e) {
                // Do nothing
            }
        }
    }

    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (sc.read(buffer) == -1) {
                logger.info("Input stream closed");
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        var server = new FixedPrestartedLongSumServerWithTimeout(Integer.parseInt(args[0]), Long.parseLong(args[1]));
        server.launch();
    }
}
