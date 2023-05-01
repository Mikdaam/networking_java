package fr.networks.tcp_blocking.exo5;

import fr.networks.tcp_blocking.utils.Reader;

import java.nio.ByteBuffer;

public class StringReader implements Reader<String> {

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        return null;
    }

    @Override
    public String get() {
        return null;
    }

    @Override
    public void reset() {

    }
}
