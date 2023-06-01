package fr.networks.tcpexam.exo1;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.logging.Logger;

public class ServerFixedPrestartedPrime {
    private static final Logger logger = Logger.getLogger(ServerFixedPrestartedPrime.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final int nbThreads;

    public ServerFixedPrestartedPrime(int port, int nbThreads) throws IOException {
        this.serverSocketChannel = ServerSocketChannel.open();
        this.nbThreads = nbThreads;
        serverSocketChannel.bind(new InetSocketAddress(port));
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
        // TODO
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
