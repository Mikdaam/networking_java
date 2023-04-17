package fr.network.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HTTPClient {
    private static final Charset ASCII_CHARSET = StandardCharsets.US_ASCII;
    private static void usage() {
        System.out.println("Usage: HTTPClient address resource");
    }

    private static HTTPReader sendRequest(SocketChannel sc, String host, String path, ByteBuffer buffer) throws IOException {
        var request = "GET "+ path +" HTTP/1.1\r\n" + "Host: " + host + "\r\n" + "\r\n";
        sc.connect(new InetSocketAddress(host, 80));
        sc.write(ASCII_CHARSET.encode(request));

        return new HTTPReader(sc, buffer);
    }

    private static HTTPHeader getHeader(HTTPReader reader) throws IOException {
        return reader.readHeader();
    }

    private static String getBody(HTTPHeader header, HTTPReader reader) throws IOException {
        ByteBuffer bodyBuf;
        int contentLength = header.getContentLength();
        if (contentLength == -1) {
            bodyBuf = reader.readChunks();
        } else {
            bodyBuf = reader.readBytes(contentLength);
        }
        bodyBuf.flip();

        return header.getCharset().orElse(StandardCharsets.UTF_8).decode(bodyBuf).toString();
    }

    public static void getResource(String host, String path) throws IOException {
        var buffer = ByteBuffer.allocate(500);
        var sc = SocketChannel.open();

        // Get the response header
        var reader = sendRequest(sc, host, path, buffer);
        var header = getHeader(reader);

        int code = header.getCode();
        if (code == 301 || code == 302) {
            sc.close();
            sc = SocketChannel.open();

            var location = new URL(header.getFields().get("location"));

            reader = sendRequest(sc, location.getHost(), location.getPath(), buffer);
            header = getHeader(reader);
        }

        System.out.println(getBody(header, reader));
        sc.close();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }

        //===============================================
        System.out.println("Using program args");
        getResource(args[0], args[1]);

        // ===============================================
        System.out.println("======================");
        System.out.println("Not using program args");
        getResource("www-igm.univ-mlv.fr","/~carayol/redirect.php");
    }
}
