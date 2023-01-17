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


import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jnr.ffi.Pointer;
import jnr.ffi.byref.PointerByReference;

@PeelableType(GitObject.Type.COMMIT)
public class Commit extends AbstractResource implements Peelable {
    private static enum SigType {
        COMMITTER,
        AUTHOR;
    }

    private Pointer commit;
    private Repository owner;
    private GitOperations ops;
    private Map<SigType, Structs.GitSignature> signatures = new ConcurrentHashMap<>();

    Commit(GitOperations ops, Pointer commit, Repository owner) {
        this.ops = ops;
        this.commit = commit;
        this.owner = owner;
    }

    @Override
    public Pointer getPointer() {
        return this.commit;
    }

    @Override
    public void close() throws Exception {
        if (!ops.isClosed()) {
            ops.call_void(git -> git.git_commit_free(this.commit));
        }
    }

    Structs.GitSignature getAuthor() {
        return signatures.computeIfAbsent(SigType.AUTHOR, sig -> ops.call(git -> git.git_commit_author(this.commit)));
    }

    Structs.GitSignature getCommitter() {
        return signatures.computeIfAbsent(
                SigType.COMMITTER, sig -> ops.call(git -> git.git_commit_committer(this.commit)));
    }

    public String getCommitterName() {
        return getCommitter().name.toString();
    }

    public String getCommitterEmail() {
        return getCommitter().email.toString();
    }

    public Date getCommitTime() {
        return new Date(getCommitter().when.time.get() * 1000); // git is in seconds
    }

    public String getAuthorName() {
        return getAuthor().name.toString();
    }

    public String getAuthorEmail() {
        return getAuthor().email.toString();
    }

    public Date getAuthorTime() {
        return new Date(getAuthor().when.time.get() * 1000);
    }

    public String getMessage() {
        return ops.call(git -> git.git_commit_message(this.commit));
    }

    public Repository getOwner() {
        return this.owner;
    }

    public Tree getTree() {
        PointerByReference treePtr = new PointerByReference();
        ops.callCheck(git -> git.git_commit_tree(treePtr, this.commit));
        if (treePtr.getValue() == null) {
            throw new NullPointerException();
        }
        Tree tree = new Tree(this.ops, treePtr.getValue(), this.owner);
        this.owner.addOwnership(tree);
        return tree;
    }
}
