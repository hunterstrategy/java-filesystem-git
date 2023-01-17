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
package net.hunterstrategy.libgit2;


import jnr.ffi.Runtime;
import jnr.ffi.Struct;

/**
 * Structures for libgit2 bindings.
 *
 * Collected here so they don't spill out of the package
 * (top level class is package-protected).
 */
class Structs {
    public static class GitBuf extends Struct {
        public final Struct.Pointer ptr = new Struct.Pointer();
        public final Struct.size_t reserved = new Struct.size_t();
        public final Struct.size_t size = new Struct.size_t();

        public GitBuf(jnr.ffi.Runtime runtime) {
            super(runtime);
            useMemory(ptr.get());
        }
    }

    public static class GitError extends Struct {
        public final Struct.AsciiStringRef message = new Struct.AsciiStringRef();
        public final Struct.SignedLong klass = new Struct.SignedLong();

        public GitError(Runtime runtime) {
            super(runtime);
        }
    }

    public static class GitSignature extends Struct {
        public final Struct.AsciiStringRef name = new Struct.AsciiStringRef();
        public final Struct.AsciiStringRef email = new Struct.AsciiStringRef();
        public final GitTime when;

        public GitSignature(jnr.ffi.Runtime runtime) {
            super(runtime);
            this.when = inner(new GitTime(runtime));
        }
    }

    public static class GitTime extends Struct {
        public Struct.Signed64 time = new Struct.Signed64();
        public Struct.Signed32 offset = new Struct.Signed32();
        public Struct.Unsigned8 sign = new Struct.Unsigned8();

        public GitTime(jnr.ffi.Runtime runtime) {
            super(runtime);
        }
    }
}
