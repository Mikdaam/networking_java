package fr.network.fault_tolerant_udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientIdUpperCaseUDPOneByOne {

	private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
	private static final Charset UTF8 = StandardCharsets.UTF_8;
	private static final int BUFFER_SIZE = 1024;

	private record Response(long id, String message) { };

	private final String inFilename;
	private final String outFilename;
	private final long timeout;
	private final InetSocketAddress server;
	private final DatagramChannel dc;
	private final SynchronousQueue<Response> queue = new SynchronousQueue<>();

	public static void usage() {
		System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
	}

	public ClientIdUpperCaseUDPOneByOne(String inFilename, String outFilename, long timeout, InetSocketAddress server)
			throws IOException {
		this.inFilename = Objects.requireNonNull(inFilename);
		this.outFilename = Objects.requireNonNull(outFilename);
		this.timeout = timeout;
		this.server = server;
		this.dc = DatagramChannel.open();
		dc.bind(null);
	}

	private Response decode(ByteBuffer bb) {
		bb.flip();
		var id = bb.getLong();
		var msg = UTF8.decode(bb).toString();
		return new Response(id, msg);
	}

	private void listenerThreadRun() {
		var respBuff = ByteBuffer.allocate(BUFFER_SIZE);
		try {
			while (true) {
				respBuff.clear();
				dc.receive(respBuff);
				queue.put(decode(respBuff));
			}
		} catch (InterruptedException e) {
			throw new AssertionError();
		} catch (AsynchronousCloseException e) {
			logger.info("Normal behavior");
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage());
		} finally {
			logger.info("Closing listener");
		}
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

	public void launch() throws IOException, InterruptedException {
		try {

			var listenerThread = Thread.ofPlatform().start(this::listenerThreadRun);
			
			// Read all lines of inFilename opened in UTF-8
			var lines = Files.readAllLines(Path.of(inFilename), UTF8);

			var upperCaseLines = new ArrayList<String>();

			for (int i = 0; i < lines.size(); i++) {
				Response res = null;
				while (res == null || res.id != i) {
					dc.send(encode(i, lines.get(i)), server);
					var start = System.currentTimeMillis();
					res = queue.poll(timeout, TimeUnit.MILLISECONDS);

					while (res != null && res.id != i) {
						var elapsed = System.currentTimeMillis() - start;
						res = queue.poll(timeout - elapsed, TimeUnit.MILLISECONDS);
					}
				}
				upperCaseLines.add(res.message);
			}

			listenerThread.interrupt(); // stopped the thread
			Files.write(Paths.get(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
		} finally {
			dc.close();
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length != 5) {
			usage();
			return;
		}

		var inFilename = args[0];
		var outFilename = args[1];
		var timeout = Long.parseLong(args[2]);
		var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

		// Create client with the parameters and launch it
		new ClientIdUpperCaseUDPOneByOne(inFilename, outFilename, timeout, server).launch();
	}
}