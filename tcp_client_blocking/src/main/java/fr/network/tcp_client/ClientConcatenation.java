package fr.network.tcp_client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

public class ClientConcatenation {
    private static final Logger logger = Logger.getLogger(ClientConcatenation.class.getName());
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int BUFFER_SIZE = 1024;

    public static void usage() {
        System.out.println("Usage : ClientConcatenation host port");
    }

    private static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (sc.read(buffer) == -1) {
                return false;
            }
        }
        return true;
    }

    private static String requestStringConcatenation(SocketChannel sc, List<String> list) throws IOException {
        var buffer = ByteBuffer.allocate(50);
        var nbOfString = list.size();

        // Send a request to the server
        // send the size
        buffer.putInt(nbOfString);
        buffer.flip();
        sc.write(buffer);
        buffer.clear();
        // ====================================================
        for (var line : list) {
            var msgBytes = UTF8.encode(line);
            var msgSize = msgBytes.remaining();

            if (Integer.BYTES + msgSize > buffer.remaining()) {
                buffer.flip();
                sc.write(buffer);
                buffer.clear();
            }

            buffer.putInt(msgSize);
            buffer.put(msgBytes);
        }
        // Send the rest
        buffer.flip();
        sc.write(buffer);
        buffer.clear();


        // Receive a response from the server
        var responseBuf = ByteBuffer.allocate(BUFFER_SIZE);
        // read the size
        responseBuf.limit(Integer.BYTES);
        readFully(sc, responseBuf);
        responseBuf.flip();
        int concatenatedSize = responseBuf.getInt();
        responseBuf.clear();

        // read the strings
        responseBuf.limit(concatenatedSize);
        readFully(sc, responseBuf);
        responseBuf.flip();

        return UTF8.decode(responseBuf).toString();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        var host = args[0];
        var port = Integer.parseInt(args[1]);

        var server = new InetSocketAddress(host, port);
        var messages = new ArrayList<String>();

        try (var sc = SocketChannel.open(server); var scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                var line = scanner.nextLine();

                if (line.equals("")) {
                    if (!messages.isEmpty()) {
                        var concatenatedMessage = requestStringConcatenation(sc, messages);
                        messages.clear();
                        System.out.println(concatenatedMessage);
                    }
                } else {
                    messages.add(line);
                    System.out.println(line);
                }
            }
        }
    }
}
