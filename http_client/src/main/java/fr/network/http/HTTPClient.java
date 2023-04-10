package fr.network.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HTTPClient {
    private static final Charset ASCII_CHARSET = StandardCharsets.US_ASCII;
    private static void usage() {
        System.out.println("Usage: HTTPClient address resource");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }

        var request = "GET "+ args[1] +" HTTP/1.1\r\n" + "Host: " + args[0] + "\r\n" + "\r\n";
        var buffer = ByteBuffer.allocate(500);
        var sc = SocketChannel.open();

        sc.connect(new InetSocketAddress(args[0], 80));
        sc.write(ASCII_CHARSET.encode(request));
        var reader = new HTTPReader(sc, buffer);
        var header = reader.readHeader();
        System.out.println(header);
        var content = reader.readBytes(header.getContentLength());
        content.flip();
        System.out.println(header.getCharset().orElse(StandardCharsets.UTF_8).decode(content));
        sc.close();
    }
}
