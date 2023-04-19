package fr.network.blocking_tcp_server.exo1;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IterativeLongSumServer {

    private static final Logger logger = Logger.getLogger(IterativeLongSumServer.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private final ServerSocketChannel serverSocketChannel;


    public IterativeLongSumServer(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        logger.info(this.getClass().getName() + " starts on port " + port);
    }

    /**
     * Iterative server main loop
     *
     * @throws IOException
     */

    public void launch() throws IOException {
        logger.info("Server started");
        while (!Thread.interrupted()) {
            SocketChannel client = serverSocketChannel.accept();
            try {
                logger.info("Connection accepted from " + client.getRemoteAddress());
                serve(client);
            } catch (IOException ioe) {
                logger.log(Level.SEVERE, "Connection terminated with client by IOException", ioe.getCause());
            } finally {
                silentlyClose(client);
            }
        }
    }

    /**
     * Treat the connection sc applying the protocol. All IOException are thrown
     *
     * @param sc
     * @throws IOException
     */
    private void serve(SocketChannel sc) throws IOException {
        var bufferNbOps = ByteBuffer.allocate(Integer.BYTES);
        long sum = 0;

        while (true) {
            bufferNbOps.clear();
            if (!readFully(sc, bufferNbOps)) {
                logger.info("Wrong packet format. Close connection");
                return;
            }
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

            while (longs.hasRemaining()) {
                sum += longs.getLong();
            }

            longs.clear();
            longs.putLong(sum);
            longs.flip();

            sc.write(longs);
        }
    }

    /**
     * Close a SocketChannel while ignoring IOExecption
     *
     * @param sc
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
        var server = new IterativeLongSumServer(Integer.parseInt(args[0]));
        server.launch();
    }
}