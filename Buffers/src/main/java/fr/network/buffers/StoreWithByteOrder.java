package fr.network.buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import static java.nio.file.StandardOpenOption.*;

public class StoreWithByteOrder {
	public static void usage() {
		System.out.println("StoreWithByteOrder [LE|BE] filename");
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			usage();
			return;
		}
		var path = Path.of(args[1]);
		var buffer = ByteBuffer.allocate(100);
		switch (args[0].toUpperCase()) {
			case "LE" -> buffer.order(ByteOrder.LITTLE_ENDIAN);
			case "BE" -> buffer.order(ByteOrder.BIG_ENDIAN);
			default -> {
				System.out.println("Unrecognized option : " + args[0]);
				usage();
				return;
			}
		}

		try (var outChannel = FileChannel.open(path, WRITE, CREATE, TRUNCATE_EXISTING); var scanner = new Scanner(System.in)) {
			while (scanner.hasNextLong()) {
				var l = scanner.nextLong();
				buffer.putLong(l);
				buffer.flip();
				outChannel.write(buffer);
				buffer.compact();
			}
		}
	}
}