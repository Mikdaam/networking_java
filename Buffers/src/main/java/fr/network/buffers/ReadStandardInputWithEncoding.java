package fr.network.buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.Charset;

public class ReadStandardInputWithEncoding {
	private static final int BUFFER_SIZE = 1024;
	private static void usage() {
		System.out.println("Usage: ReadStandardInputWithEncoding charset");
	}

	private static ByteBuffer grow(ByteBuffer oldB) {
		var newB = ByteBuffer.allocate(oldB.capacity() * 2);
		oldB.flip();
		newB.put(oldB);
		return newB;
	}

	private static String stringFromStandardInput(Charset cs) throws IOException {
		try (var in = Channels.newChannel(System.in)) {
			var buffer = ByteBuffer.allocate(BUFFER_SIZE);
			while (in.read(buffer) != -1) {
				if (!buffer.hasRemaining()) {
					buffer = grow(buffer);
				}
			}
			buffer.flip();
			return cs.decode(buffer).toString();
		}
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		Charset cs = Charset.forName(args[0]);
		System.out.print(stringFromStandardInput(cs));
	}
}