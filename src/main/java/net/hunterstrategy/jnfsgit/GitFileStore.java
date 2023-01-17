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
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

public class GitFileStore extends FileStore {
    private GitURI guri;

    GitFileStore(GitURI guri) {
        this.guri = guri;
    }

    @Override
    public String name() {
        return this.guri.toRepoURI().toString();
    }

    @Override
    public String type() {
        return "git";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public long getTotalSpace() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getUsableSpace() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return false;
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        return false;
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        throw new IllegalArgumentException("Unsupported attribute: " + attribute);
    }
}
