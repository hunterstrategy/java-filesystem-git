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


import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import jnr.ffi.Pointer;
import jnr.ffi.byref.PointerByReference;

public class Repository extends AbstractResource implements ResourceOwner {
    private GitOperations ops;
    private Pointer repo;
    private Set<Resource> ownedObjects = new HashSet<>();

    public static Repository open(String repoPath) {
        GitOperations ops = new GitOperations();
        PointerByReference repoPointer = new PointerByReference();
        ops.callCheck(git -> git.git_repository_open(repoPointer, repoPath));
        return new Repository(ops, repoPointer.getValue());
    }

    public static Repository openBare(String repoPath) {
        GitOperations ops = new GitOperations();
        PointerByReference repoPointer = new PointerByReference();
        ops.callCheck(git -> git.git_repository_open_bare(repoPointer, repoPath));
        return new Repository(ops, repoPointer.getValue());
    }

    private Repository(GitOperations ops, Pointer repo) {
        this.ops = ops;
        this.repo = repo;
    }

    public Config getConfig() {
        PointerByReference configPtr = new PointerByReference();
        ops.callCheck(git -> git.git_repository_config(configPtr, repo));
        if (configPtr.getValue() == null) {
            throw new NullPointerException();
        }
        Config config = new Config(ops, configPtr.getValue());
        addOwnership(config);
        return config;
    }

    public String getWorkdir() {
        return ops.call(git -> git.git_repository_workdir(repo));
    }

    public GitObject revparseSingle(String spec) {
        PointerByReference objectPointer = new PointerByReference();
        ops.callCheck(git -> git.git_revparse_single(objectPointer, repo, spec));
        if (objectPointer.getValue() == null) {
            throw new NullPointerException("Invalid revision: " + spec);
        }
        GitObject obj = new GitObject(ops, objectPointer.getValue(), this);
        addOwnership(obj);
        return obj;
    }

    @Override
    public void addOwnership(Resource obj) {
        ownedObjects.add(obj);
    }

    @Override
    public Pointer getPointer() {
        return this.repo;
    }

    @Override
    public Collection<Resource> getOwnedResources() {
        return Collections.unmodifiableSet(ownedObjects);
    }

    public boolean isOpen() {
        return !ops.isClosed();
    }

    @Override
    public void close() throws Exception {
        if (ops.isClosed()) {
            return;
        }

        try {
            closeOwnedResources();
        } catch (Exception e) {
            // swallow while closing
        }

        try {
            ops.call_void(git -> git.git_repository_free(repo));
        } finally {
            ops.close();
        }
    }
}
