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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;

public class GitPath implements Path {
    private GitFileSystem fs;
    private GitURI guri;

    GitPath(GitFileSystem fs, GitURI uri) {
        this.fs = fs;
        this.guri = uri;
    }

    @Override
    public GitFileSystem getFileSystem() {
        return this.fs;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GitPath) {
            GitPath other = GitPath.class.cast(obj);
            return this.guri.getSubtreePath().equals(other.guri.getSubtreePath()) && this.fs.equals(other.fs);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(GitPath.class, this.guri.getSubtreePath(), this.fs);
    }

    @Override
    public boolean isAbsolute() {
        return new File(this.guri.getSubtreePath()).isAbsolute();
    }

    public boolean isRoot() {
        return guri.isRoot();
    }

    @Override
    public GitPath getRoot() {
        if (!isAbsolute()) {
            return null;
        }
        GitURI rootGuri = new GitURI(this.guri.toRepoPathURI("/"));
        return new GitPath(this.fs, rootGuri);
    }

    @Override
    public GitPath getFileName() {
        String name = new File(this.guri.getSubtreePath()).getName();
        return new GitPath(this.fs, new GitURI(this.guri.toRepoPathURI(name)));
    }

    @Override
    public GitPath getParent() {
        String parent = new File(this.guri.getSubtreePath()).getParent();
        return parent == null ? null : new GitPath(this.fs, new GitURI(this.guri.toRepoPathURI(parent)));
    }

    @Override
    public int getNameCount() {
        return new File(this.guri.getSubtreePath()).toPath().getNameCount();
    }

    @Override
    public GitPath getName(int index) {
        Path newPath = new File(this.guri.getSubtreePath()).toPath().getName(index);
        return new GitPath(this.fs, new GitURI(this.guri.toRepoPathURI(newPath.toString())));
    }

    @Override
    public GitPath subpath(int beginIndex, int endIndex) {
        Path newPath = new File(this.guri.getSubtreePath()).toPath().subpath(beginIndex, endIndex);
        return new GitPath(this.fs, new GitURI(this.guri.toRepoPathURI(newPath.toString())));
    }

    @Override
    public boolean startsWith(Path other) {
        if (other instanceof GitPath) {
            return guri.getSubtreePath().startsWith(((GitPath) other).guri.getSubtreePath());
        }
        return guri.getSubtreePath().startsWith(other.toString());
    }

    @Override
    public boolean endsWith(Path other) {
        if (other instanceof GitPath) {
            return guri.getSubtreePath().endsWith(((GitPath) other).guri.getSubtreePath());
        }
        return guri.getSubtreePath().endsWith(other.toString());
    }

    @Override
    public GitPath normalize() {
        Path normalized = new File(guri.getSubtreePath()).toPath().normalize();
        return new GitPath(this.fs, new GitURI(this.guri.toRepoPathURI(normalized.toString())));
    }

    @Override
    public GitPath resolve(Path other) {
        if (other == null) {
            throw new NullPointerException();
        }
        if (!(other instanceof GitPath)) {
            throw new ProviderMismatchException();
        }
        GitPath gother = GitPath.class.cast(other);
        if (other.isAbsolute()) {
            return gother;
        }
        if (other.toString().isBlank()) {
            return this;
        }
        File f = new File(toString(), other.toString());
        return new GitPath(this.fs, new GitURI(this.guri.toRepoPathURI(f.getPath())));
    }

    @Override
    public GitPath relativize(Path other) {
        if (other == null) {
            throw new NullPointerException();
        }
        if (!(other instanceof GitPath)) {
            throw new ProviderMismatchException();
        }
        Path thisPath = new File(this.guri.getSubtreePath()).toPath();
        Path otherPath = new File(other.toString()).toPath();
        Path relativized = thisPath.relativize(otherPath);
        return new GitPath(this.fs, new GitURI(this.guri.toRepoPathURI(relativized.toString())));
    }

    @Override
    public URI toUri() {
        return this.guri.toFullURI();
    }

    GitURI getGitURI() {
        return this.guri;
    }

    @Override
    public GitPath toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }

        return new GitPath(this.fs, new GitURI(this.guri.toRepoPathURI("/" + this.toString())));
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        // potentially could be implemented be resolving to git object file TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Path other) {
        if (!(other instanceof GitPath)) {
            throw new IllegalArgumentException("Cannot be used to compare paths across providers.");
        }
        return this.guri.getSubtreePath().compareTo(other.toString());
    }

    @Override
    public String toString() {
        return this.guri.getSubtreePath();
    }
}
