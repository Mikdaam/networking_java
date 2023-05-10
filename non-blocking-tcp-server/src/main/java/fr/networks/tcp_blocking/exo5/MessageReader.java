package fr.networks.tcp_blocking.exo5;

import fr.networks.tcp_blocking.utils.Reader;
import fr.networks.tcp_blocking.utils.StringReader;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message> {
    private enum State {
        DONE, WAITING_LOGIN, WAITING_MSG, ERROR
    }

    private State state = State.WAITING_LOGIN;
    private final StringReader stringReader = new StringReader();
    private Message message;
    private String login;
    private String content;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        if (state == State.WAITING_LOGIN) {
            var status = stringReader.process(buffer);
            if (status == ProcessStatus.DONE) {
                state = State.WAITING_MSG;
                login = stringReader.get();
                stringReader.reset();
            } else {
                return status;
            }
        }

        if (state == State.WAITING_MSG) {
            var status = stringReader.process(buffer);
            if (status != ProcessStatus.DONE) {
                return status;
            } else {
                state = State.DONE;
                content = stringReader.get();
                message = new Message(login, content);
                return ProcessStatus.DONE;
            }
        }

        throw new AssertionError();
    }

    @Override
    public Message get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return message;
    }

    @Override
    public void reset() {
        state = State.WAITING_LOGIN;
        stringReader.reset();
    }
}
