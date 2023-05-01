package fr.networks.tcp_blocking.exo5;

import fr.networks.tcp_blocking.utils.Reader;
import fr.networks.tcp_blocking.utils.StringReader;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message> {
    private enum State {
        DONE, WAITING, ERROR
    }

    private State state = State.WAITING;
    private final StringReader loginReader = new StringReader();
    private final StringReader msgReader = new StringReader();
    private Message message;
    private boolean loginIsRead;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        ProcessStatus loginStatus;
        if (!loginIsRead) {
            loginStatus = loginReader.process(buffer);
        } else {
            loginStatus = ProcessStatus.DONE;
        }

        if (loginStatus != ProcessStatus.DONE) {
            return loginStatus;
        } else {
            var msgStatus = msgReader.process(buffer);
            if (msgStatus != ProcessStatus.DONE) {
                return msgStatus;
            } else {
                state = State.DONE;
                message = new Message(loginReader.get(), msgReader.get());
                return ProcessStatus.DONE;
            }
        }
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
        state = State.WAITING;
        loginIsRead = false;
        loginReader.reset();
        msgReader.reset();
    }
}
