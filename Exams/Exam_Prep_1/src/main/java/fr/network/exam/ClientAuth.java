package fr.network.exam;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;

public class ClientAuth {

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Charset ISO_8859 = StandardCharsets.ISO_8859_1;
    private static final Logger logger = Logger.getLogger(ClientAuth.class.getName());
    private static final int BUF_SIZE = 1024;
    private static final int MIN_BUF = 8 + 4 * 2;

    private record User(String firstName, String lastName) {
        public User {
            Objects.requireNonNull(firstName);
            Objects.requireNonNull(lastName);
        }

        public static User fromLine(String line) {
            var t = line.split(";");
            if (t.length != 2) {
                throw new IllegalArgumentException("Invalid line : " + line);
            }
            return new User(t[0], t[1]);
        }

        public ByteBuffer encode(long id) {
            var buf = ByteBuffer.allocate(BUF_SIZE);
            var firstNameBuf = UTF8.encode(firstName);
            var lastNameBuf = UTF8.encode(lastName);

            buf.putLong(id);
            // firstname
            buf.putInt(firstNameBuf.remaining());
            buf.put(firstNameBuf);
            // lastName
            buf.putInt(lastNameBuf.remaining());
            buf.put(lastNameBuf);

            return buf;
        }
    }

    private final String inFilename;
    private final String outFilename;
    private final InetSocketAddress server;
    private final DatagramChannel dc;

    public static void usage() {
        System.out.println("Usage : ClientAuth in-filename out-filename host port ");
    }

    public ClientAuth(String inFilename, String outFilename, InetSocketAddress server) throws IOException {
        this.inFilename = Objects.requireNonNull(inFilename);
        this.outFilename = Objects.requireNonNull(outFilename);
        this.server = server;
        this.dc = DatagramChannel.open();
        dc.bind(null);
    }

    public void launch() throws IOException, InterruptedException {
        try {
            // Read all lines of inFilename opened in UTF-8
            var lines = Files.readAllLines(Path.of(inFilename), UTF8);
            // Create the list of all users
            var users = lines.stream().map(User::fromLine).collect(Collectors.toList());
            // List of lines to write to the output file
            var answers = new ArrayList<String>();

            // TODO
            for (int i = 0; i < users.size(); i++) {
                dc.send(users.get(i).encode(i), server);
            }



            Files.write(Paths.get(outFilename), answers, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
        } finally {
            dc.close();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 4) {
            usage();
            return;
        }

        var inFilename = args[0];
        var outFilename = args[1];
        var server = new InetSocketAddress(args[2], Integer.parseInt(args[3]));

        // Create client with the parameters and launch it
        new ClientAuth(inFilename, outFilename, server).launch();
    }
}