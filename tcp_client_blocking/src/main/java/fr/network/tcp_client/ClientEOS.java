package fr.network.tcp_client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class ClientEOS {

    public static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;
    public static final int BUFFER_SIZE = 1024;
    public static final Logger logger = Logger.getLogger(ClientEOS.class.getName());

    /**
     * This method: 
     * - connect to server 
     * - writes the bytes corresponding to request in UTF8 
     * - closes the write-channel to the server 
     * - stores the bufferSize first bytes of server response 
     * - return the corresponding string in UTF8
     *
     * @param request The request sent to the server
     * @param server The server address
     * @param bufferSize The bufferSize
     * @return the UTF8 string corresponding to bufferSize first bytes of server
     *         response
     * @throws IOException when there is an error
     */

    public static String getFixedSizeResponse(String request, SocketAddress server, int bufferSize) throws IOException {
        var channel = SocketChannel.open();
        channel.connect(server);
        channel.write(UTF8_CHARSET.encode(request));
        channel.shutdownOutput();

        var responseBuffer = ByteBuffer.allocate(bufferSize);
        int read = channel.read(responseBuffer);
        if (read == - 1) {
            logger.warning("Connection closed, That's weird");
            return "null";
        }
        responseBuffer.flip();
        return UTF8_CHARSET.decode(responseBuffer).toString();
    }

    /**
     * This method: 
     * - connect to server 
     * - writes the bytes corresponding to request in UTF8 
     * - closes the write-channel to the server 
     * - reads and stores all bytes from server until read-channel is closed 
     * - return the corresponding string in UTF8
     *
     * @param request The request sent to the server
     * @param server The server address
     * @return the UTF8 string corresponding the full response of the server
     * @throws IOException when there is error
     */

    public static String getUnboundedResponse(String request, SocketAddress server) throws IOException {
        var channel = SocketChannel.open();
        channel.connect(server);
        channel.write(UTF8_CHARSET.encode(request));
        channel.shutdownOutput();

        var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        var builder = new StringBuilder();
        while (readFully(channel, responseBuffer)) {
            responseBuffer.flip();
            builder.append(UTF8_CHARSET.decode(responseBuffer));
            responseBuffer.clear();
        }

        return builder.toString();

        /* int read = channel.read(responseBuffer);
        if (read == - 1) {
            logger.warning("Connection closed, That's weird");
            return "null";
        }
        responseBuffer.flip();
        return UTF8_CHARSET.decode(responseBuffer).toString();*/
    }

    /**
     * Fill the workspace of the Bytebuffer with bytes read from sc.
     *
     * @param sc
     * @param buffer
     * @return false if read returned -1 at some point and true otherwise
     * @throws IOException
     */
    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        return sc.read(buffer) != - 1;
    }

    public static void main(String[] args) throws IOException {
        var google = new InetSocketAddress("www.google.fr", 80);
        // System.out.println(getFixedSizeResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google, 512));
         System.out.println(getUnboundedResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google));
    }
}