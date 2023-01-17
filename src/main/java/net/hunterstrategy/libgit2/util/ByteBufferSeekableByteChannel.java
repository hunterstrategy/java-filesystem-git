/*-
 * #%L
 * java.nio FileSystem - git
 * %%
 * Copyright (C) 2022 - 2023 Hunter Strategy LLC
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package net.hunterstrategy.libgit2.util;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;

public class ByteBufferSeekableByteChannel implements SeekableByteChannel {
    private volatile ByteBuffer buf;

    public ByteBufferSeekableByteChannel(ByteBuffer buf) {
        this.buf = buf;
    }

    @Override
    public boolean isOpen() {
        return buf != null;
    }

    @Override
    public void close() throws IOException {
        buf = null;
    }

    private void checkClosed() throws IOException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkClosed();
        if (buf.remaining() <= 0) {
            return -1;
        }

        int destinationRemaining = dst.remaining();
        int remaining = buf.remaining();
        int sizeToCopy = Math.min(destinationRemaining, remaining);

        byte[] chunk = new byte[sizeToCopy];
        buf.get(chunk);
        int dstPos = dst.position();
        dst.put(chunk);
        dst.position(dstPos);
        return sizeToCopy;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkClosed();
        // read only!
        return 0;
    }

    @Override
    public long position() throws IOException {
        checkClosed();
        return buf.position();
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        checkClosed();
        buf.position((int) newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        checkClosed();
        return buf.capacity();
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        checkClosed();
        return this;
    }
}
