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


import com.kenai.jffi.MemoryIO;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Optional;
import jnr.ffi.Pointer;
import net.hunterstrategy.libgit2.GitObject.Type;
import net.hunterstrategy.libgit2.Tree.FileMode;

@PeelableType(Type.BLOB)
public class Blob extends AbstractResource implements Peelable, TreeItem {
    private GitOperations ops;
    private Tree.Entry entry;
    private Pointer blob;

    Blob(GitOperations ops, Pointer blob) {
        this.ops = ops;
        this.blob = blob;
    }

    void fromEntry(Tree.Entry entry) {
        this.entry = entry;
    }

    @Override
    public Pointer getPointer() {
        return this.blob;
    }

    @Override
    public void close() throws Exception {
        if (!ops.isClosed()) {
            ops.call_void(git -> git.git_blob_free(this.blob));
        }
    }

    @Override
    public String getId() {
        Pointer oid = ops.call(git -> git.git_blob_id(this.blob));
        return ops.call(git -> git.git_oid_tostr_s(oid));
    }

    @Override
    public String getName() {
        if (this.entry != null) {
            return entry.getName();
        }
        return null;
    }

    @Override
    public boolean isTree() {
        return false;
    }

    @Override
    public FileMode getFileMode() {
        if (entry != null) {
            return entry.getFileMode();
        } else {
            return FileMode.BLOB;
        }
    }

    public boolean isBinary() {
        return ops.call(git -> git.git_blob_is_binary(this.blob));
    }

    public long size() {
        return ops.call(git -> git.git_blob_rawsize(this.blob));
    }

    public ByteBuffer getRawContent() {
        Pointer bufPtr = ops.call(git -> git.git_blob_rawcontent(this.blob));
        return MemoryIO.getInstance().newDirectByteBuffer(bufPtr.address(), (int) size());
    }

    public String getStringContent(Charset charset) {
        return charset.decode(getRawContent()).toString();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends TreeItem> Optional<T> viewAs(Class<T> type) {
        if (Tree.class.equals(type)) {
            return Optional.empty();
        } else if (Tree.Entry.class.equals(type)) {
            return (Optional<T>) Optional.of(this.entry);
        } else if (Blob.class.equals(type)) {
            return (Optional<T>) Optional.of(this);
        }
        throw new UnsupportedOperationException(type.getName());
    }
}
