package fr.networks.tcp.nonblocking.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class StringReader implements Reader<String> {

    private enum State {
        DONE, WAITING, ERROR
    }

    private StringReader.State state = State.WAITING;
    private final ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES); // write-mode
    private final ByteBuffer stringBuffer = ByteBuffer.allocate(1_024 - Integer.BYTES); // write-mode
    private String value;

    private void fillBuffer(ByteBuffer buffer, ByteBuffer internalBuffer) {
        buffer.flip();
        try {
            if (buffer.remaining() <= internalBuffer.remaining()) {
                internalBuffer.put(buffer);
            } else {
                var oldLimit = buffer.limit();
                buffer.limit(internalBuffer.remaining());
                internalBuffer.put(buffer);
                buffer.limit(oldLimit);
            }
        } finally {
            buffer.compact();
        }
    }

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        while (sizeBuffer.hasRemaining()) {
            fillBuffer(buffer, sizeBuffer);
            if (sizeBuffer.hasRemaining()) {
                return ProcessStatus.REFILL;
            }
        }

        int size = sizeBuffer.flip().getInt();
        if (size < 0 || size > 1020) {
            return ProcessStatus.ERROR;
        }

        stringBuffer.limit(size);
        fillBuffer(buffer, stringBuffer);
        if (stringBuffer.hasRemaining()) {
            return ProcessStatus.REFILL;
        }
        state = State.DONE;
        stringBuffer.flip();
        value = StandardCharsets.UTF_8.decode(stringBuffer).toString();
        return ProcessStatus.DONE;
    }

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING;
        sizeBuffer.clear();
        stringBuffer.clear();
    }
}