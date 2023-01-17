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


import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.hunterstrategy.libgit2.Commit;
import net.hunterstrategy.libgit2.GitObject;
import net.hunterstrategy.libgit2.Repository;
import net.hunterstrategy.libgit2.Tree;
import net.hunterstrategy.libgit2.TreeItem;

public class GitFileSystem extends FileSystem {
    private final GitFileSystemProvider provider;
    private final GitURI guri;

    private final Repository repo;
    private final GitObject revision;
    private final GitFileStore fileStore;

    private Tree rootTree;
    private Commit rootCommit;

    GitFileSystem(GitFileSystemProvider provider, GitURI guri, boolean bare) {
        this.provider = provider;
        this.guri = guri;

        this.repo = bare ? Repository.openBare(guri.getRepositoryDir()) : Repository.open(guri.getRepositoryDir());

        this.revision = this.repo.revparseSingle(guri.getRevisionSpec());

        this.revision.peel(Tree.class).ifPresentOrElse(t -> this.rootTree = t, () -> {
            throw new NullPointerException("Cannot peel tree");
        });

        this.revision.peel(Commit.class).ifPresentOrElse(c -> this.rootCommit = c, () -> {
            throw new NullPointerException("Cannot peel commit");
        });

        this.fileStore = new GitFileStore(guri);
    }

    GitPath createPath(URI uri) {
        return new GitPath(this, new GitURI(uri));
    }

    <T> T withTree(Function<Tree, T> func) {
        return func.apply(this.rootTree);
    }

    <T> T withCommit(Function<Commit, T> func) {
        return func.apply(this.rootCommit);
    }

    TreeItem lookupPath(String path) {
        if ("/".equals(path)) {
            return rootTree;
        }
        return withTree(tree -> tree.getByNameRecursive(path));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GitFileSystem) {
            return guri.equals(((GitFileSystem) obj).guri);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(GitFileSystem.class, guri.toString());
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        try {
            this.repo.close();
        } catch (Exception e) {
            throw new IOException("Error while closing repo", e);
        }
    }

    @Override
    public boolean isOpen() {
        return this.repo.isOpen();
    }

    @Override
    public boolean isReadOnly() {
        return fileStore.isReadOnly();
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        GitPath root = new GitPath(this, new GitURI(this.guri.toRepoPathURI("/")));
        return Arrays.asList(root);
    }

    GitFileStore getFileStore() {
        return fileStore;
    }

    boolean exists(GitPath gpath) {
        if ("/".equals(gpath.toString()) || gpath.toString().isBlank()) {
            return true; // root
        }
        return rootTree.getByNameRecursive(gpath.toString()) != null;
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return Arrays.asList(fileStore);
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return new HashSet<>(Arrays.asList("basic"));
    }

    @Override
    public Path getPath(String first, String... more) {
        List<String> strings = new ArrayList<>();
        strings.add(first);
        strings.addAll(Arrays.asList(more));
        String joined = String.join(getSeparator(), strings);
        return new GitPath(this, new GitURI(this.guri.toRepoPathURI(joined)));
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        Path thePath = new File(guri.getSubtreePath()).toPath();
        PathMatcher matcher = thePath.getFileSystem().getPathMatcher(syntaxAndPattern);
        return matcher;
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return null;
    }

    @Override
    public WatchService newWatchService() throws IOException {
        return null;
    }
}
