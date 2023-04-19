package fr.network.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;

public class NetcatUDP {
    public static final int BUFFER_SIZE = 1024;

    private static void usage() {
        System.out.println("Usage : NetcatUDP host port charset");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            usage();
            return;
        }

        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        var cs = Charset.forName(args[2]);
        var buffer = ByteBuffer.allocate(BUFFER_SIZE);

        try (var scanner = new Scanner(System.in);) {
            try(var dataChan = DatagramChannel.open()) {
                while (scanner.hasNextLine()) {
                    var line = scanner.nextLine();
                        dataChan.bind(null); // Choose a random port
                        dataChan.send(cs.encode(line), server); // Send the message

                        var exp = dataChan.receive(buffer); // Receive the response
                        buffer.flip();
                        System.out.println("Received " + buffer.remaining() + " bytes from " + exp);
                        System.out.println("String: " + cs.decode(buffer));
                }
                buffer.compact();
            }
        }
    }
}