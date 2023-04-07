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
        // !NOTE : about read ...
        while (responseBuffer.hasRemaining()) {
            if (channel.read(responseBuffer) == - 1) {
                logger.info("Connection closed, That's weird");
                break;
            }
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

    private static void grow(ByteBuffer buffer) {
        // flip the buffer
        buffer.flip();

        // create the new buffer
        var newBuf = ByteBuffer.allocate(buffer.capacity() * 2);

        // put the old buffer in the new one
        newBuf.put(buffer);

        // the old buffer is the new one
        buffer = newBuf;
    }

    public static String getUnboundedResponse(String request, SocketAddress server) throws IOException {
        var channel = SocketChannel.open();
        channel.connect(server);
        channel.write(UTF8_CHARSET.encode(request));
        channel.shutdownOutput();

        // ! WARNING: Never decode UTF8 while reading.

        var responseBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        while (readFully(channel, responseBuffer)) {
            if (!responseBuffer.hasRemaining()) {
                grow(responseBuffer);
            }
        }
        responseBuffer.flip();
        return UTF8_CHARSET.decode(responseBuffer).toString();
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
//         System.out.println(getFixedSizeResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google, 512));
         System.out.println(getUnboundedResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google));
    }
}