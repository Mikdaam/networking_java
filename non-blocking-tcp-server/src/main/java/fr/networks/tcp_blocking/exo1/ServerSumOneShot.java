package fr.networks.tcp_blocking.exo1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.logging.Logger;

public class ServerSumOneShot {

	private static final int BUFFER_SIZE = 2 * Integer.BYTES;
	private static final Logger logger = Logger.getLogger(ServerSumOneShot.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;

	public ServerSumOneShot(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		while (!Thread.interrupted()) {
			Helpers.printKeys(selector); // for debug
			System.out.println("Starting select");
			try {
				selector.select(this::treatKey);
			} catch (IOException e) {
				e.getCause();
			}
			System.out.println("Select finished");
		}
	}

	private void treatKey(SelectionKey key) {
		Helpers.printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				doWrite(key);
			}
			if (key.isValid() && key.isReadable()) {
				doRead(key);
			}
		} catch (IOException e) {
			logger.info("Connection closed with client due to IOException");
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		var server = (ServerSocketChannel) key.channel();
		var client = server.accept();
		if (client == null) {
			logger.warning("The selector give a bad hint");
			return; // selector gave a bad hint
		}
		client.configureBlocking(false);
		client.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(BUFFER_SIZE));
	}

	private void doRead(SelectionKey key) throws IOException {
		var client = (SocketChannel) key.channel();
		var buffer = (ByteBuffer) key.attachment();

		if (client.read(buffer) == -1) { // kick the client when he closes the connection first
			logger.info("Connection closed with client");
			silentlyClose(key);
			return;
		}

		if (buffer.hasRemaining()) { // read while there is space in my buffer
			return;
		}

		buffer.flip();
		int res = buffer.getInt() + buffer.getInt();
		buffer.clear();
		buffer.putInt(res);
		key.interestOps(SelectionKey.OP_WRITE);
	}

	private void doWrite(SelectionKey key) throws IOException {
		var client = (SocketChannel) key.channel();
		var buffer = (ByteBuffer) key.attachment();
		buffer.flip();

		try {
			client.write(buffer);
			if (buffer.hasRemaining()) {
				return;
			}

			silentlyClose(key);
		} finally {
			buffer.compact();
		}
	}

	private void silentlyClose(SelectionKey key) {
		var sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		new ServerSumOneShot(Integer.parseInt(args[0])).launch();
	}

	private static void usage() {
		System.out.println("Usage : ServerSumOneShot port");
	}
}