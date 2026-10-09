package com.elfmcys.ysm.format.container;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;

public final class ByteArraySeekableChannel implements SeekableByteChannel {
    private final byte[] data;
    private final int offset;
    private final int size;
    private int position;
    private boolean open = true;

    public ByteArraySeekableChannel(byte[] data) {
        this(data, 0, data.length);
    }

    public ByteArraySeekableChannel(byte[] data, int offset, int size) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (offset < 0 || size < 0 || size > data.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        this.data = data;
        this.offset = offset;
        this.size = size;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkOpen();
        if (position == size) {
            return -1;
        }
        var length = Math.min(dst.remaining(), size - position);
        dst.put(data, offset + position, length);
        position += length;
        return length;
    }

    @Override
    public int write(ByteBuffer src) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public long position() throws IOException {
        checkOpen();
        return position;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        checkOpen();
        if (newPosition < 0 || newPosition > size) {
            throw new IllegalArgumentException("Invalid position: " + newPosition);
        }
        position = Math.toIntExact(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        checkOpen();
        return size;
    }

    @Override
    public SeekableByteChannel truncate(long size) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
    }

    private void checkOpen() throws ClosedChannelException {
        if (!open) {
            throw new ClosedChannelException();
        }
    }
}
