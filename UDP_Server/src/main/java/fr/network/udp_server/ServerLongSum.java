package fr.network.udp_server;

import java.nio.channels.AsynchronousCloseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class ServerLongSum {

    private static final Logger logger = Logger.getLogger(ServerIdUpperCaseUDP.class.getName());
    private static final int BUFFER_SIZE = 1024;
    private static final int MINIMUM_BYTE = Long.BYTES * 4 + 1;
    private static final int RESPONSE_BUF_SIZE = Long.BYTES * 2 + 1;
    private record Request(long sessionId, long idPosOperand, long totalOperand, long opValue) {}
    private record Acknowledge(long sessionId, long idPosOperand) {
        public ByteBuffer encode() {
            var buffer = ByteBuffer.allocate(RESPONSE_BUF_SIZE);
            buffer.put((byte) 2)
                    .putLong(sessionId)
                    .putLong(idPosOperand);
            return buffer.flip();
        }
    }
    private record Result(long sessionId, long sum) {
        public ByteBuffer encode() {
            var buffer = ByteBuffer.allocate(RESPONSE_BUF_SIZE);
            buffer.put((byte) 3)
                    .putLong(sessionId)
                    .putLong(sum);
            return buffer.flip();
        }
    }

    private static class Sum {
        private final BitSet bitSet;
        private long res = 0;
        private final long totalOps;
        private Sum(long totalOperands) {
            totalOps = totalOperands;
            bitSet = new BitSet(Math.toIntExact(totalOps));
        }

        public void addOperand(long opId, long opValue) {
            var id = Math.toIntExact(opId);
            if (!bitSet.get(id)) {
                res += opValue;
                bitSet.set(id);
            }
        }

        public long result() {
            return res;
        }

        public boolean isComplete() {
            return bitSet.cardinality() == totalOps;
        }
    }

    private final DatagramChannel dc;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private final HashMap<InetSocketAddress, HashMap<Long, Sum>> clientOps = new HashMap<>();

    public ServerLongSum(int port) throws IOException {
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        logger.info("ServerLongSum started on port " + port);
    }

    private Optional<Request> decode(ByteBuffer buffer) {
        buffer.flip();
        if (buffer.remaining() < MINIMUM_BYTE || buffer.get() != 1) {
            logger.info("Packet malformed");
            return Optional.empty();
        }
        return Optional.of(new Request(buffer.getLong(), buffer.getLong(), buffer.getLong(), buffer.getLong()));
    }

    public void serve() throws IOException {
        try {
            while (!Thread.interrupted()) {
                buffer.clear();
                var clientAddress = (InetSocketAddress) dc.receive(buffer);
                var decodeRes = decode(buffer);

                decodeRes.ifPresentOrElse(
                        (req) -> {
                            try {
                                // Send acknowledge
                                dc.send(new Acknowledge(req.sessionId, req.idPosOperand).encode(), clientAddress);

                                // Compute the sum
                                var clientMap = clientOps.computeIfAbsent(clientAddress, k -> new HashMap<>());
                                var clientSum = clientMap.computeIfAbsent(req.sessionId, id -> new Sum(req.totalOperand));
                                clientSum.addOperand(req.idPosOperand, req.opValue);

                                // Send the result if we received all operands
                                if (clientSum.isComplete()) {
                                    dc.send(new Result(req.sessionId, clientSum.result()).encode(), clientAddress);
                                }
                            }catch (AsynchronousCloseException e) {
                                logger.info("Datagram close");
                            } catch (IOException e) {
                                logger.log(Level.SEVERE, e.getMessage());
                                throw new AssertionError(e);
                            }
                        },
                        () -> logger.info("Error because of malformed packet")
                );
            }
        } finally {
            dc.close();
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerIdUpperCaseUDP port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }

        var port = Integer.parseInt(args[0]);

        if (!(port >= 1024) & port <= 65535) {
            logger.severe("The port number must be between 1024 and 65535");
            return;
        }

        try {
            new ServerLongSum(port).serve();
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
    }
}