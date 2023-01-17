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


import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import jnr.ffi.Pointer;
import jnr.ffi.byref.PointerByReference;
import jnr.ffi.util.EnumMapper;

@PeelableType(GitObject.Type.TREE)
public class Tree extends AbstractResource implements Peelable, TreeItem {
    private GitOperations ops;
    private Pointer tree;
    private Repository owner;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<Long, String> entryNames = new ConcurrentHashMap<>();

    private String name = "/";
    private Tree.Entry self = null;

    Tree(GitOperations ops, Pointer tree, Repository owner) {
        this.ops = ops;
        this.tree = tree;
        this.owner = owner;
    }

    void fromEntry(Tree.Entry self) {
        this.name = self.getName();
        this.self = self;
    }

    @Override
    public Pointer getPointer() {
        return this.tree;
    }

    public String getId() {
        Pointer oid = ops.call(git -> git.git_tree_id(this.tree));
        return ops.call(git -> git.git_oid_tostr_s(oid));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isTree() {
        return true;
    }

    @Override
    public Tree.FileMode getFileMode() {
        return Tree.FileMode.TREE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends TreeItem> Optional<T> viewAs(Class<T> type) {
        if (Tree.class.equals(type)) {
            return (Optional<T>) Optional.of(this);
        } else if (Tree.Entry.class.equals(type)) {
            if ("/".equals(getName())) {
                return Optional.empty();
            } else {
                return (Optional<T>) Optional.of(this.self);
            }
        } else if (Blob.class.equals(type)) {
            return Optional.empty();
        }
        throw new UnsupportedOperationException(type.getName());
    }

    public Repository getOwner() {
        return this.owner;
    }

    @Override
    public void close() throws Exception {
        if (!ops.isClosed()) {
            for (Entry e : entries.values()) {
                e.close();
            }
            ops.call_void(git -> git.git_tree_free(this.tree));
        }
    }

    public long getEntryCount() {
        return ops.call(git -> git.git_tree_entrycount(this.tree));
    }

    public Entry getByIndex(long index) {
        if (entryNames.containsKey(index)) {
            return entries.get(entryNames.get(index));
        }

        Pointer entryPtr = ops.call(git -> git.git_tree_entry_byindex(this.tree, index));
        if (entryPtr == null) {
            return null;
        }

        Entry entry = new Entry(entryPtr, false);
        String entryName = entry.getName();
        entries.putIfAbsent(entryName, entry);
        entryNames.putIfAbsent(index, entryName);
        return entry;
    }

    private String strippedPath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1, path.length());
        }
        return path;
    }

    /**
     * Load a path, recursing to find each entry by name.
     * For example, when trying to resolve /foo/bar/file.txt,
     * first load the Tree entry for foo, then load the bar
     * entry under foo's Tree entry, and then load the
     * Blob entry for file.txt under bar.
     *
     * This is different than getByPath which caches the full
     * path to the item under the current Tree.
     *
     * Clients may choose what makes the most sense for their
     * implementation use case - caching everything under the
     * root node, or walking the object/memory tree.
     *
     * This is not part of libgit2.
     */
    public Entry getByNameRecursive(String path) {
        String[] components = strippedPath(path).split("\\/");
        @SuppressWarnings("resource") // will be owned by this Tree
        Tree previous = this;
        Entry finalEntry = null;
        for (int i = 0; i < components.length; i++) {
            String entryName = components[i];
            finalEntry = previous.getByName(entryName);
            if (finalEntry == null) {
                return null;
            }

            Optional<Tree> tree = finalEntry.viewAs(Tree.class);
            if (tree.isPresent()) {
                previous = tree.get();
            } else {
                if (i != components.length - 1) {
                    return null; // can only have blobs as last component
                }
            }
        }
        return finalEntry;
    }

    public Entry getByPath(String path) {
        final String gitPath = strippedPath(path);
        return entries.computeIfAbsent(gitPath, p -> {
            PointerByReference entryPtr = new PointerByReference();
            try {
                ops.callCheck(git -> git.git_tree_entry_bypath(entryPtr, this.tree, gitPath));
                if (entryPtr.getValue() == null) {
                    return null;
                }

                return new Entry(entryPtr.getValue(), true);
            } catch (GitCallException ise) {
                return null;
            }
        });
    }

    public Entry getByName(String filename) {
        String gitPath = strippedPath(filename);
        return entries.computeIfAbsent(gitPath, p -> {
            Pointer entry = ops.call(git -> git.git_tree_entry_byname(this.tree, gitPath));
            return (entry == null) ? null : new Entry(entry, false);
        });
    }

    public class Entry extends AbstractResource implements TreeItem {
        private Pointer entry;
        private boolean must_free;

        private Optional<Tree> treeView = null;
        private Optional<Blob> blob = null;

        Entry(Pointer entry, boolean must_free) {
            this.entry = entry;
            this.must_free = must_free;
        }

        @Override
        public Pointer getPointer() {
            return this.entry;
        }

        public String getId() {
            Pointer oid = ops.call(git -> git.git_tree_entry_id(this.entry));
            return ops.call(git -> git.git_oid_tostr_s(oid));
        }

        public String getName() {
            return ops.call(git -> git.git_tree_entry_name(this.entry));
        }

        @Override
        public boolean isTree() {
            return getType() == GitObject.Type.TREE;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TreeItem> Optional<T> viewAs(Class<T> type) {
            if (Tree.class.equals(type)) {
                return (Optional<T>) toTree();
            } else if (Tree.Entry.class.equals(type)) {
                return (Optional<T>) Optional.of(this);
            } else if (Blob.class.equals(type)) {
                return (Optional<T>) toBlob();
            }
            throw new UnsupportedOperationException(type.getName());
        }

        public FileMode getFileMode() {
            return ops.call(git -> git.git_tree_entry_filemode(this.entry));
        }

        public GitObject.Type getType() {
            return ops.call(git -> git.git_tree_entry_type(this.entry));
        }

        private GitObject toObject() {
            PointerByReference object = new PointerByReference();
            ops.callCheck(git -> git.git_tree_entry_to_object(object, Tree.this.owner.getPointer(), this.entry));
            if (object.getValue() == null) {
                throw new NullPointerException("Unexpectedly unable to resolve tree entry to tree.");
            }
            GitObject obj = new GitObject(ops, object.getValue(), Tree.this.owner);
            Tree.this.owner.addOwnership(obj);
            return obj;
        }

        public Optional<Blob> toBlob() {
            if (this.blob != null) {
                return this.blob;
            }

            FileMode mode = getFileMode();
            if (!EnumSet.of(FileMode.BLOB, FileMode.BLOB_EXECUTABLE).contains(mode)) {
                this.blob = Optional.empty();
                return this.blob;
            }

            /*
             * Going back to git object and then peeling to BLOB doesn't work,
             * but we can just do a direct blob lookup with the entry OID.
             */
            Pointer oid = ops.call(git -> git.git_tree_entry_id(this.entry));
            PointerByReference blobPtr = new PointerByReference();
            ops.callCheck(git -> git.git_blob_lookup(blobPtr, Tree.this.owner.getPointer(), oid));
            if (blobPtr.getValue() == null) {
                return Optional.empty();
            }
            Blob result = new Blob(ops, blobPtr.getValue());
            Tree.this.owner.addOwnership(result);
            result.fromEntry(this);
            return Optional.of(result);
        }

        public Optional<Tree> toTree() {
            if (this.treeView != null) {
                return this.treeView;
            }

            if (!isTree()) {
                this.treeView = Optional.empty();
                return this.treeView;
            }

            GitObject obj = toObject();
            Optional<Tree> result = obj.peel(Tree.class);
            result.ifPresent(t -> t.fromEntry(this));
            this.treeView = result;
            return result;
        }

        @Override
        public void close() throws Exception {
            if (!ops.isClosed() && this.must_free) {
                ops.call_void(git -> git.git_tree_entry_free(entry));
            }
        }

        @Override
        public String toString() {
            return String.format("%s (%s/%s): %s", getName(), getType(), getFileMode(), getId());
        }
    }

    public static enum FileMode implements EnumMapper.IntegerEnum {
        UNREADABLE(0000000),
        TREE(0040000),
        BLOB(0100644),
        BLOB_EXECUTABLE(0100755),
        LINK(0120000),
        COMMIT(0160000);

        private final int value;

        FileMode(int value) {
            this.value = value;
        }

        @Override
        public int intValue() {
            return this.value;
        }
    }
}
