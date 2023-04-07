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

    /*private static void grow(ByteBuffer buffer) {
        // flip the buffer
        buffer.flip();

        // create the new buffer
        var newBuf = ByteBuffer.allocate(buffer.capacity() * 2);

        // put the old buffer in the new one
        newBuf.put(buffer);

        // the old buffer is the new one
        buffer = newBuf;
    }*/

    private static String requestStringConcatenation(SocketChannel sc, List<String> list) throws IOException {
        var buffer = ByteBuffer.allocate(BUFFER_SIZE);
        var size = list.size();

        // Send a request to the server
        // send the size
        buffer.putInt(size);
        buffer.flip();
        sc.write(buffer);
        buffer.clear();
        // ====================================================
        for (var line : list) {
            var msgBytes = UTF8.encode(line);
            buffer.putInt(msgBytes.remaining());
            buffer.put(msgBytes);
            buffer.flip();
            sc.write(buffer);
            buffer.clear();
        }

        // Receive a response from the server
        buffer.limit(Integer.BYTES);
        readFully(sc, buffer);
        buffer.flip();
        int concatenatedSize = buffer.getInt();
        buffer.clear();
        buffer.limit(concatenatedSize);
        readFully(sc, buffer);

        buffer.flip();
        return UTF8.decode(buffer).toString();
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
                    var concatenatedMessage = requestStringConcatenation(sc, messages);
                    messages.clear();
                    System.out.println(concatenatedMessage);
                } else {
                    messages.add(line);
                    System.out.println(line);
                }
            }
        }
    }
}
