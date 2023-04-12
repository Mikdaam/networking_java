package fr.network.exam.exo1;

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

import static java.nio.file.StandardOpenOption.*;

public class ClientAuth {

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Charset ISO_8859 = StandardCharsets.ISO_8859_1;
    private static final Logger logger = Logger.getLogger(ClientAuth.class.getName());

    private static final ByteBuffer buffer = ByteBuffer.allocate(1024);
    private static final int MIN_BUF = Long.BYTES + Integer.BYTES * 2;

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

    public void launch() throws IOException {
        try {
            // Read all lines of inFilename opened in UTF-8
            var lines = Files.readAllLines(Path.of(inFilename), UTF8);
            // Create the list of all users
            var users = lines.stream().map(User::fromLine).toList();
            // List of lines to write to the output file
            var answers = new ArrayList<String>();

            // Send request
            for (int id = 0; id < users.size(); id++) {
                var user = users.get(id);
                var firstnameBytes = UTF8.encode(user.firstName);
                var lastnameBytes = UTF8.encode(user.lastName);

                buffer.clear(); // clearing the buffer

                buffer.putLong(id); // id
                buffer.putInt(firstnameBytes.remaining()); // size of firstname bytes
                buffer.put(firstnameBytes); // firstname bytes
                buffer.putInt(lastnameBytes.remaining()); // size of lastname bytes
                buffer.put(lastnameBytes); // lastname bytes
                buffer.flip();

                dc.send(buffer, server);
            }

            // Receive response
            for (var user : users) {
                buffer.clear();
                dc.receive(buffer);

                buffer.flip();
                // size verification
                if (buffer.remaining() < MIN_BUF) {
                    logger.warning("Ill formed packet.");
                    continue;
                }
                buffer.getLong(); // get id
                int usernameSize = buffer.getInt(); // username Bytes size
                if (buffer.remaining() < usernameSize) {
                    logger.warning("Mal formed packet");
                    continue;
                }
                var usernameBytes = buffer.slice(buffer.position(), usernameSize);
                buffer.position(buffer.position() + usernameSize); // put the position in right place
                // ============================
                int passwdSize = buffer.getInt(); // passwd Bytes size
                if (buffer.remaining() < passwdSize) {
                    logger.warning("Mal formed packet");
                    continue;
                }
                var passwdBytes = buffer.slice(buffer.position(), passwdSize);
                buffer.position(buffer.position() + passwdSize); // put the position in right place
                // ================================

                var answer = String.format("%s;%s;%s;%s", user.firstName, user.lastName, ISO_8859.decode(usernameBytes), ISO_8859.decode(passwdBytes));
                answers.add(answer);
            }

            Files.write(Paths.get(outFilename), answers, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
        } finally {
            dc.close();
        }
    }

    public static void main(String[] args) throws IOException {
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