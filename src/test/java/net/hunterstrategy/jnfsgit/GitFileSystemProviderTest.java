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
package net.hunterstrategy.jnfsgit;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.function.Function;
import net.hunterstrategy.libgit2.util.ByteBufferSeekableByteChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GitFileSystemProviderTest {

    @Test
    public void wrap_function() throws Exception {
        Function<ByteBuffer, SeekableByteChannel> func =
                GitFileSystemProvider.initIoWrapper(ProperlyFormedWrapper.class.getName());
        Assertions.assertNotNull(func);
        Assertions.assertThrows(RuntimeException.class, () -> func.apply(null));
    }

    @Test
    public void wrap_seekable_byte_channel() throws Exception {
        Function<ByteBuffer, SeekableByteChannel> func =
                GitFileSystemProvider.initIoWrapper(ByteBufferSeekableByteChannel.class.getName());
        Assertions.assertNotNull(func);
        SeekableByteChannel chan = func.apply(null);
        Assertions.assertFalse(chan.isOpen());
    }

    @Test
    public void wrap_junk() throws Exception {
        Assertions.assertThrows(
                IllegalStateException.class, () -> GitFileSystemProvider.initIoWrapper(String.class.getName()));
    }

    @Test
    public void wrap_bad_SeekableByteChannel() {
        IllegalStateException ise = Assertions.assertThrows(
                IllegalStateException.class,
                () -> GitFileSystemProvider.initIoWrapper(FakeByteChannel.class.getName()));
        Throwable cause = ise.getCause();
        Assertions.assertNotNull(cause);
        Assertions.assertEquals("Constructor must take ByteBuffer", cause.getMessage());
    }

    @Test
    public void wrap_bad_function() throws Exception {
        IllegalStateException ise = Assertions.assertThrows(
                IllegalStateException.class, () -> GitFileSystemProvider.initIoWrapper(Function.class.getName()));
        Throwable cause = ise.getCause();
        Assertions.assertNotNull(cause);
        Assertions.assertEquals("Must implement Function<ByteBuffer, SeekableByteChannel>", cause.getMessage());

        // check return type as well
        ise = Assertions.assertThrows(
                IllegalStateException.class,
                () -> GitFileSystemProvider.initIoWrapper(MalformedWrapper.class.getName()));
        cause = ise.getCause();
        Assertions.assertNotNull(cause);
        Assertions.assertEquals("Must implement Function<ByteBuffer, SeekableByteChannel>", cause.getMessage());
    }

    public static class ProperlyFormedWrapper implements Function<ByteBuffer, SeekableByteChannel> {

        @Override
        public SeekableByteChannel apply(ByteBuffer t) {
            throw new RuntimeException("broken!");
        }
    }

    public static class MalformedWrapper implements Function<ByteBuffer, Long> {
        @Override
        public Long apply(ByteBuffer t) {
            throw new RuntimeException("also broken!");
        }
    }

    public static class FakeByteChannel implements SeekableByteChannel {
        // missing constructor for ByteBuffer on purpose

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() throws IOException {}

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return 0;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return 0;
        }

        @Override
        public long position() throws IOException {
            return 0;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            return null;
        }

        @Override
        public long size() throws IOException {
            return 0;
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            return null;
        }
    }
}
