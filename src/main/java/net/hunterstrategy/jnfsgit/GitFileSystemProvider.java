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


import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import net.hunterstrategy.jnfsgit.ex.FileSystemIsReadOnlyException;
import net.hunterstrategy.jnfsgit.ex.WrongFileTypeException;
import net.hunterstrategy.libgit2.Blob;
import net.hunterstrategy.libgit2.Tree;
import net.hunterstrategy.libgit2.TreeItem;
import net.hunterstrategy.libgit2.util.ByteBufferSeekableByteChannel;

public class GitFileSystemProvider extends FileSystemProvider {
    private static final Function<ByteBuffer, SeekableByteChannel> IOWRAPPER = initWrapper();
    private static final ConcurrentMap<String, GitFileSystem> repositories = new ConcurrentHashMap<>();

    private static Function<ByteBuffer, SeekableByteChannel> initWrapper() {
        String ioWrapper = System.getenv("JNFSGIT_IO_WRAPPER");
        return initIoWrapper(ioWrapper);
    }

    /**
     * Initialize the IO wrapper for reading git byte buffer (pointers).
     *
     * By default, it will use the ByteBufferSeekableByteChannel type defined
     * in this package.
     *
     * You can specify the environment variable JNFSGIT_IO_WRAPPER with a fully-
     * qualified class name. There are two possible options:
     *
     * 1. A java.util.function.Function implementation that takes a ByteBuffer
     *    as its parameter and returns a SeekableByteChannel in its apply() method.
     * 2. An implementation of SeekableByteChannel that has a single-parameter
     *    constructor that takes a ByteBuffer as its parameter.
     */
    @SuppressWarnings("unchecked")
    static Function<ByteBuffer, SeekableByteChannel> initIoWrapper(String ioWrapper) {
        if (ioWrapper == null || ioWrapper.isBlank()) {
            return ByteBufferSeekableByteChannel::new;
        }

        try {
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(ioWrapper);
            if (Function.class.isAssignableFrom(clazz)) {
                try {
                    Method m = clazz.getMethod("apply", ByteBuffer.class);
                    if (SeekableByteChannel.class.isAssignableFrom(m.getReturnType())) {
                        return (Function<ByteBuffer, SeekableByteChannel>)
                                clazz.getConstructor().newInstance();
                    }
                } catch (NoSuchMethodException nsme) {
                }
                throw new IllegalStateException("Must implement Function<ByteBuffer, SeekableByteChannel>");
            }

            if (SeekableByteChannel.class.isAssignableFrom(clazz)) {
                try {
                    final Constructor<?> cons = clazz.getConstructor(ByteBuffer.class);
                    return buf -> {
                        try {
                            return SeekableByteChannel.class.cast(cons.newInstance(buf));
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    };
                } catch (NoSuchMethodException nsme) {
                }
                throw new IllegalStateException("Constructor must take ByteBuffer");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error initializing git memory buffer IO: " + ioWrapper, e);
        }

        throw new IllegalStateException("Error initializing git memory buffer IO: " + ioWrapper);
    }

    @Override
    public String getScheme() {
        return "git";
    }

    private void checkProvider(Path... paths) {
        if (paths == null) {
            return;
        }

        for (Path path : paths) {
            if (!(path instanceof GitPath)) {
                throw new ProviderMismatchException();
            }
        }
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        GitURI guri = new GitURI(uri);
        final GitURI fsgu = new GitURI(guri.toRepoURI());

        String key = fsgu.toRepoURI().toString();
        return repositories.compute(key, (repoString, fs) -> {
            boolean bare = isBare(guri, env);

            if (fs != null && fs.isOpen()) {
                throw new FileSystemAlreadyExistsException(fsgu.toString());
            }
            return new GitFileSystem(this, fsgu, bare);
        });
    }

    private boolean isBare(GitURI guri, Map<String, ?> env) {
        // check if bare is explicitly set in configuration
        Optional<Boolean> bare = getEnv(env, "bare", Boolean.class);
        if (bare.isPresent()) {
            return bare.get(); // always use explicit configuration when present
        }

        // it is a git convention that bare repositories are named with '.git' in
        // the name of the directory
        return guri.getRepositoryDir().endsWith(".git")
                || guri.getRepositoryDir().endsWith(".git/");
    }

    /*
     * Annoying ? capture-of type erasure interferes with Map
     * getOrDefault method.
     */
    private <T> Optional<T> getEnv(Map<String, ?> env, String key, Class<T> type) {
        if (env == null) {
            return Optional.empty();
        }

        Object o = env.get(key);
        if (o != null && type.isAssignableFrom(o.getClass())) {
            return Optional.of(type.cast(o));
        }
        return Optional.empty();
    }

    @Override
    public GitFileSystem getFileSystem(URI uri) {
        GitURI gu = new GitURI(uri);
        GitFileSystem fs = repositories.get(gu.toRepoURI().toString());
        if (fs == null || !fs.isOpen()) {
            throw new FileSystemNotFoundException(uri.toString());
        }
        return fs;
    }

    @Override
    public Path getPath(URI uri) {
        return getFileSystem(uri).createPath(uri);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        checkProvider(path);

        if (!options.isEmpty() && !options.equals(EnumSet.of(StandardOpenOption.READ))) {
            throw new FileSystemIsReadOnlyException();
        }
        GitPath gpath = GitPath.class.cast(path);
        if (gpath.isRoot()) {
            throw new WrongFileTypeException(WrongFileTypeException.Type.DIR_EXPECTED_FILE, gpath.toString());
        }

        GitFileSystem gfs = getFileSystem(gpath.toUri());
        Tree.Entry entry = gfs.withTree(t -> t.getByNameRecursive(gpath.toString()));
        if (entry == null) {
            throw new FileNotFoundException(gpath.toString());
        }
        if (entry.isTree()) {
            throw new WrongFileTypeException(WrongFileTypeException.Type.DIR_EXPECTED_FILE, gpath.toString());
        }
        return IOWRAPPER.apply(entry.viewAs(Blob.class).get().getRawContent());
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
        checkProvider(dir);

        GitPath path = GitPath.class.cast(dir);
        TreeItem pathRoot = path.getFileSystem()
                .withTree(tree -> path.isRoot() ? (TreeItem) tree : tree.getByNameRecursive(path.toString()));
        if (pathRoot == null) {
            throw new FileNotFoundException(dir.toString());
        }
        if (!pathRoot.isTree()) {
            throw new WrongFileTypeException(WrongFileTypeException.Type.FILE_EXPECTED_DIR, dir.toString());
        }
        return new GitDirectoryStream(filter, path, pathRoot.viewAs(Tree.class).get());
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        checkProvider(dir);
        throw new FileSystemIsReadOnlyException();
    }

    @Override
    public void delete(Path path) throws IOException {
        checkProvider(path);
        throw new FileSystemIsReadOnlyException();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        checkProvider(source, target);
        throw new FileSystemIsReadOnlyException();
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        checkProvider(source, target);
        throw new FileSystemIsReadOnlyException();
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        checkProvider(path, path2);
        if (path instanceof GitPath && path2 instanceof GitPath) {
            return path.toUri().equals(path2.toUri());
        }
        return false;
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        checkProvider(path);
        return false;
    }

    @Override
    public GitFileStore getFileStore(Path path) throws IOException {
        checkProvider(path);
        return GitPath.class.cast(path).getFileSystem().getFileStore();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        checkProvider(path);

        GitPath gpath = GitPath.class.cast(path);

        if (!gpath.getFileSystem().isOpen()) {
            throw new IOException("Underlying FileSystem has been closed");
        }

        if (!gpath.getFileSystem().exists(gpath)) {
            throw new FileNotFoundException(path.toString());
        }

        for (AccessMode mode : modes) {
            switch (mode) {
                case READ: // allow read
                    break;
                case EXECUTE:
                    if (!readAttributes(path, GitBasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                            .isExecutable()) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
                default:
                    throw new FileSystemIsReadOnlyException();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        checkProvider(path);

        if (!BasicFileAttributeView.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Only BasicFileAttributeView supported.");
        }

        return (V) new BasicFileAttributeView() {
            @Override
            public String name() {
                return "basic";
            }

            @Override
            public GitBasicFileAttributes readAttributes() throws IOException {
                return GitFileSystemProvider.this.readAttributes(path, GitBasicFileAttributes.class, options);
            }

            @Override
            public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
                    throws IOException {
                throw new FileSystemIsReadOnlyException();
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
            throws IOException {
        checkProvider(path);
        if (!(options != null && Arrays.asList(options).contains(LinkOption.NOFOLLOW_LINKS))) {
            throw new UnsupportedOperationException("Cannot follow symlinks.");
        }

        if (!(BasicFileAttributes.class.equals(type) || GitBasicFileAttributes.class.equals(type))) {
            throw new IllegalArgumentException("Unsupported attribute type: " + type);
        }
        GitPath gpath = GitPath.class.cast(path);
        GitFileSystem fs = gpath.getFileSystem();
        TreeItem item =
                gpath.isRoot() ? fs.lookupPath("/") : fs.withTree(tree -> tree.getByNameRecursive(gpath.toString()));
        if (item == null) {
            throw new FileNotFoundException(path.toUri().toString());
        }
        return (A) new GitBasicFileAttributesImpl(gpath, item);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Use readAttributes(Path, Class, LinkOption...) variant");
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        checkProvider(path);
        throw new FileSystemIsReadOnlyException();
    }
}

/**
 * Currently, the lastAccessed/lastModified/created timestamps are *WRONG*.
 * OK not wrong but simply pinned to the commit author times.
 *
 * Technically to get the correct values, the code should walk the revision history
 * to find when files were introduced or modified. However I'm not sure it's worth
 * the expense to calculate these things, for the time being. Or we can create
 * a separate BasicFileAttributes type, call it "FileHistoryAttributes," and override
 * these methods to provide that information but only when explicitly requested.
 */
class GitBasicFileAttributesImpl implements GitBasicFileAttributes {

    private GitPath path;
    private GitFileSystem fs;

    private TreeItem item;

    GitBasicFileAttributesImpl(GitPath path, TreeItem item) throws IOException {
        this.path = path;
        this.fs = this.path.getFileSystem();
        this.item = item;
    }

    @Override
    public FileTime lastModifiedTime() {
        return FileTime.fromMillis(this.fs.withCommit(c -> c.getAuthorTime()).getTime());
    }

    @Override
    public FileTime lastAccessTime() {
        return FileTime.fromMillis(this.fs.withCommit(c -> c.getAuthorTime()).getTime());
    }

    @Override
    public FileTime creationTime() {
        return FileTime.fromMillis(this.fs.withCommit(c -> c.getAuthorTime()).getTime());
    }

    @Override
    public boolean isRegularFile() {
        if (path.isRoot()) {
            return false;
        }

        return this.item.getFileMode() == Tree.FileMode.BLOB
                || this.item.getFileMode() == Tree.FileMode.BLOB_EXECUTABLE;
    }

    @Override
    public boolean isDirectory() {
        if (path.isRoot()) {
            return true;
        }

        return this.item.getFileMode() == Tree.FileMode.TREE;
    }

    @Override
    public boolean isExecutable() {
        if (path.isRoot()) {
            return false;
        }

        return this.item.getFileMode() == Tree.FileMode.BLOB_EXECUTABLE;
    }

    @Override
    public boolean isSymbolicLink() {
        if (path.isRoot()) {
            return false;
        }

        return this.item.getFileMode() == Tree.FileMode.LINK;
    }

    @Override
    public boolean isOther() {
        if (path.isRoot()) {
            return false;
        }

        return this.item.getFileMode() == Tree.FileMode.COMMIT || this.item.getFileMode() == Tree.FileMode.UNREADABLE;
    }

    @Override
    public long size() {
        if (!isRegularFile()) {
            return 0;
        }

        Optional<Blob> blob = this.item.viewAs(Blob.class);
        return blob.isPresent() ? blob.get().size() : 0;
    }

    @Override
    public Object fileKey() {
        return path.getGitURI().toFullURI();
    }
}
