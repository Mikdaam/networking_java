package fr.network.fault_tolerant_udp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPBurst {

        private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
        private static final Charset UTF8 = StandardCharsets.UTF_8;
        private static final int BUFFER_SIZE = 1024;
        private record Response(long id, String message) { };

        private final List<String> lines;
        private final int nbLines;
        private final String[] upperCaseLines; //
        private final int timeout;
        private final String outFilename;
        private final InetSocketAddress serverAddress;
        private final DatagramChannel dc;
        private final AnswersLog answersLog;         // Thread-safe structure keeping track of missing responses

        public static void usage() {
            System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
        }

        public ClientIdUpperCaseUDPBurst(List<String> lines,int timeout,InetSocketAddress serverAddress,String outFilename) throws IOException {
            this.lines = lines;
            this.nbLines = lines.size();
            this.timeout = timeout;
            this.outFilename = outFilename;
            this.serverAddress = serverAddress;
            this.dc = DatagramChannel.open();
            dc.bind(null);
            this.upperCaseLines = new String[nbLines];
            this.answersLog = new AnswersLog(nbLines); // TODO
        }

        private ByteBuffer encode(long Id, String line) {
            var buffer = ByteBuffer.allocate(BUFFER_SIZE);
            buffer.putLong(Id);
            var encodedString = UTF8.encode(line);
            if (buffer.remaining() >= encodedString.remaining()) {
                buffer.put(encodedString);
            }
            return buffer.flip();
        }

        private Response decode(ByteBuffer bb) {
            bb.flip();
            var id = bb.getLong();
            var msg = UTF8.decode(bb).toString();
            return new Response(id, msg);
        }

        private void senderThreadRun() {
			// TODO : body of the sender thread
            try {
                while (true) {
                    for (int i = 0; i < nbLines; i++) {
                        if (!answersLog.get(i)) {
                            dc.send(encode(i, lines.get(i)), serverAddress);
                            logger.info("Send line (again ?): " + lines.get(i));
                        }
                    }
                    Thread.sleep(timeout);
                }
            } catch (AsynchronousCloseException e) {
                logger.info("Normal behavior");
            }catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage());
                return;
            } catch (InterruptedException e) {
                return;
            }
        }

        public void launch() throws IOException {
            try {
                Thread senderThread = Thread.ofPlatform().start(this::senderThreadRun);

                // TODO : body of the receiver thread

                var bb = ByteBuffer.allocate(BUFFER_SIZE);
                while (answersLog.nbOfReceived() != nbLines) {
                    bb.clear();
                    dc.receive(bb);
                    var res = decode(bb);
                    var id = (int) res.id;
                    answersLog.set(id);
                    upperCaseLines[id] = res.message;
                    logger.info("UpperCase: " + Arrays.toString(upperCaseLines));
                }

                senderThread.interrupt();
                Files.write(Paths.get(outFilename),Arrays.asList(upperCaseLines), UTF8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } finally {
                dc.close();
            }
        }

        public static void main(String[] args) throws IOException, InterruptedException {
            if (args.length !=5) {
                usage();
                return;
            }

            String inFilename = args[0];
            String outFilename = args[1];
            int timeout = Integer.parseInt(args[2]);
            String host=args[3];
            int port = Integer.parseInt(args[4]);
            InetSocketAddress serverAddress = new InetSocketAddress(host,port);

            //Read all lines of inFilename opened in UTF-8
            List<String> lines= Files.readAllLines(Paths.get(inFilename),UTF8);
            //Create client with the parameters and launch it
            ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines,timeout,serverAddress,outFilename);
            client.launch();

        }

        private static class AnswersLog {
            private final Object lock = new Object();
            private final BitSet bitSet;

            private AnswersLog(int nbLines) {
                synchronized (lock) {
                    this.bitSet = new BitSet(nbLines);
                }
            }

            public void set(int index) {
                synchronized (lock) {
                    bitSet.set(index);
                }
            }

            public boolean get(int index) {
                synchronized (lock) {
                    return bitSet.get(index);
                }
            }

            public int nbOfReceived() {
                synchronized (lock) {
                    return bitSet.cardinality();
                }
            }

            @Override
            public String toString() {
                synchronized (lock) {
                    return bitSet.toString();
                }
            }
        }
    }

