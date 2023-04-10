package fr.network.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;

public class HTTPReader {

    private final Charset ASCII_CHARSET = Charset.forName("ASCII");
    private final int BUF_SIZE = 1024;
    private final SocketChannel sc;
    private final ByteBuffer buffer;

    public HTTPReader(SocketChannel sc, ByteBuffer buffer) {
        this.sc = sc;
        this.buffer = buffer;
    }

    /**
     * @return The ASCII string terminated by CRLF without the CRLF
     *         <p>
     *         The method assume that buffer is in write mode and leaves it in
     *         write mode The method process the data from the buffer and if necessary
     *         will read more data from the socket.
     * @throws IOException HTTPException if the connection is closed before a line
     *                     could be read
     */
    public String readLineCRLF() throws IOException {
        var builder = new StringBuilder();
        boolean hasCR = false;

        buffer.flip();
        while (true) {
            if (!buffer.hasRemaining()) {
                buffer.clear();
                if (sc.read(buffer) == -1) {
                    throw new HTTPException("Connection is closed before read.");
                }
                buffer.flip();
            }

            char c = (char) buffer.get();
            builder.append(c);

            if (c == '\n' && hasCR) {
                buffer.compact();
                break;
            }

            hasCR = c == '\r';
        }

        builder.setLength(builder.length() - 2);
        return builder.toString();
    }

    /**
     * @return The HTTPHeader object corresponding to the header read
     * @throws IOException HTTPException if the connection is closed before a header
     *                     could be read or if the header is ill-formed
     */
    public HTTPHeader readHeader() throws IOException {
        var responseStatus = readLineCRLF();
        var emptyLine = "";
        var fields = new HashMap<String, String>();
        String line;
        while (!(line = readLineCRLF()).equals(emptyLine)) {
            var cols = line.split(": ");
            fields.put(cols[0], cols[1]);
        }
        return HTTPHeader.create(responseStatus, fields);
    }

    /**
     * @param size 
     * @return a ByteBuffer in write mode containing size bytes read on the socket
     *         <p>
     *         The method assume that buffer is in write mode and leaves it in
     *         write mode The method process the data from the buffer and if necessary
     *         will read more data from the socket.
     * @throws IOException HTTPException is the connection is closed before all
     *                     bytes could be read
     */
    public ByteBuffer readBytes(int size) throws IOException {
        var readBuffer = ByteBuffer.allocate(size);

        buffer.flip();
        if (buffer.hasRemaining()) {
            while (buffer.hasRemaining() && readBuffer.hasRemaining()) {
                readBuffer.put(buffer.get());
            }
            buffer.compact();
        }

        while (readBuffer.hasRemaining()) {
            if (sc.read(readBuffer) == -1) {
                throw new HTTPException("Connection is closed before read.");
            }
        }

        return readBuffer;
    }

    private ByteBuffer grow(ByteBuffer oldBuf) {
        var newBuf = ByteBuffer.allocate(oldBuf.capacity() * 2);
        oldBuf.flip();
        newBuf.put(oldBuf);
        return newBuf;
    }

    /**
     * @return a ByteBuffer in write-mode containing a content read in chunks mode
     * @throws IOException HTTPException if the connection is closed before the end
     *                     of the chunks if chunks are ill-formed
     */

    public ByteBuffer readChunks() throws IOException {
        var chunksBuf = ByteBuffer.allocate(BUF_SIZE);

        // read a chunk
        // read the bytes size
        int size;
        while ((size = Integer.parseInt(readLineCRLF(), 16)) != 0) {

            // grow the main buffer
            if (!chunksBuf.hasRemaining()) {
                chunksBuf = grow(chunksBuf);
            }

            var chunkBuf = ByteBuffer.allocate(size);
            // read the chunk data
            while (chunkBuf.hasRemaining()) {
                chunkBuf.put(ByteBuffer.wrap(readLineCRLF().getBytes(ASCII_CHARSET)));
            }

            chunksBuf.put(chunkBuf);
        }
        return chunksBuf;
    }

    public static void main(String[] args) throws IOException {
        var charsetASCII = Charset.forName("ASCII");
        var request = "GET / HTTP/1.1\r\n" + "Host: www.w3.org\r\n" + "\r\n";
        var sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        sc.write(charsetASCII.encode(request));
        var buffer = ByteBuffer.allocate(50);
        var reader = new HTTPReader(sc, buffer);
        System.out.println(reader.readLineCRLF());
        System.out.println(reader.readLineCRLF());
        System.out.println(reader.readLineCRLF());
        sc.close();

        buffer = ByteBuffer.allocate(50);
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        reader = new HTTPReader(sc, buffer);
        sc.write(charsetASCII.encode(request));
        System.out.println(reader.readHeader());
        sc.close();

        buffer = ByteBuffer.allocate(50);
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("igm.univ-mlv.fr", 80));
        request = "GET /coursprogreseau/ HTTP/1.1\r\n" + "Host: igm.univ-mlv.fr\r\n" + "\r\n";
        reader = new HTTPReader(sc, buffer);
        sc.write(charsetASCII.encode(request));
        var header = reader.readHeader();
        System.out.println(header);
        var content = reader.readBytes(header.getContentLength());
        content.flip();
        System.out.println(header.getCharset().orElse(Charset.forName("UTF8")).decode(content));
        sc.close();

        buffer = ByteBuffer.allocate(50);
        request = "GET / HTTP/1.1\r\n" + "Host: www.u-pem.fr\r\n" + "\r\n";
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.u-pem.fr", 80));
        reader = new HTTPReader(sc, buffer);
        sc.write(charsetASCII.encode(request));
        header = reader.readHeader();
        System.out.println(header);
        content = reader.readChunks();
        content.flip();
        System.out.println(header.getCharset().orElse(Charset.forName("UTF8")).decode(content));
        sc.close();
    }
}