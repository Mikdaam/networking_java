package fr.network.tcp_client;

import java.util.Scanner;

public class ClientConcatenation {
    public static void main(String[] args) {
        try (var scanner = new Scanner(System.in)) {
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
