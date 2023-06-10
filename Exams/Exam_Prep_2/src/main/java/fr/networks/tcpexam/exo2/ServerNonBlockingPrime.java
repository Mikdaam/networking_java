package fr.networks.tcpexam.exo2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Logger;

import static java.nio.channels.SelectionKey.*;

public class ServerNonBlockingPrime {

	private class Context {
		final private SelectionKey key;
		final private SocketChannel sc;
		final private ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
		final private ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
		boolean closed;

		Context(SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
		}

		private void process() {

		}

		private void updateInterestOps() {
			int newInterestOps = 0;

			if (!closed && bufferIn.hasRemaining()) {
				newInterestOps |= OP_READ;
			}

			if (bufferOut.position() != 0) {
				newInterestOps |= OP_WRITE;
			}

			if (newInterestOps == 0) {
				silentlyClose();
				return;
			}
			key.interestOps(newInterestOps);
		}

		private void doRead() throws IOException {
			// Read
			// Process
			// update InterestOps
			if (sc.read(bufferIn) == -1) {
				closed = true;
				return;
			}
			process();
		}

		private void doWrite() throws IOException {
			// Flip
			// Writes
			// Process
			// Update InterestOps
			bufferOut.flip();
			sc.write(bufferOut);
			bufferOut.compact();
			process();
		}

		void silentlyClose() {
			try {
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}
	}

	final static int BUFFER_SIZE = 1024;
	final static private Logger logger = Logger.getLogger(ServerNonBlockingPrime.class.getName());
	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	// TODO

	public ServerNonBlockingPrime(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
	}

	private static void usage() {
		System.out.println("Usage : ServerNonBlockingPrime port");
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		new ServerNonBlockingPrime(Integer.parseInt(args[0])).launch();
	}

	private static boolean isPrime(int n) {
		if (n <= 1)
			return false;
		for (int i = 2; i <= Math.sqrt(n); i++) {
			if (n % i == 0)
				return false;
		}
		return true;
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, OP_ACCEPT);
		while (!Thread.interrupted()) {
			printKeys(); // for debug
			System.out.println("Starting select");
			selector.select(this::treatKey);
			System.out.println("Select finished");
		}
	}

	private void treatKey(SelectionKey key) {
		printSelectedKey(key); // for debug
		// TODO Handle exceptions
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException tunneled) {
			throw new UncheckedIOException(tunneled);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.info("Connection closed with client");
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		var client = serverSocketChannel.accept();
		if (client == null) {
			logger.info("The selector gave a bad hint");
			return;
		}
		client.configureBlocking(false);
		var clientKey = client.register(selector, OP_ACCEPT);
		clientKey.attach(new Context(clientKey));
	}

	private void silentlyClose(SelectionKey key) {
		silentlyClose(key.channel());
	}

	private void silentlyClose(Channel sc) {
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	/***
	 * Theses methods are here to help understanding the behavior of the selector
	 ***/

	private String interestOpsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps & OP_ACCEPT) != 0)
			list.add("OP_ACCEPT");
		if ((interestOps & SelectionKey.OP_READ) != 0)
			list.add("OP_READ");
		if ((interestOps & SelectionKey.OP_WRITE) != 0)
			list.add("OP_WRITE");
		return String.join("|", list);
	}

	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
			}
		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e) {
			return "???";
		}
	}

	public void printSelectedKey(SelectionKey key) {
		SelectableChannel channel = key.channel();
		if (channel instanceof ServerSocketChannel) {
			System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
		} else {
			SocketChannel sc = (SocketChannel) channel;
			System.out.println(
					"\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
		}
	}

	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable())
			list.add("ACCEPT");
		if (key.isReadable())
			list.add("READ");
		if (key.isWritable())
			list.add("WRITE");
		return String.join(" and ", list);
	}
}