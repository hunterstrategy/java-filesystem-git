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


import jnr.ffi.Pointer;
import jnr.ffi.annotations.In;
import jnr.ffi.annotations.Out;
import jnr.ffi.byref.PointerByReference;
import jnr.ffi.types.size_t;
import jnr.ffi.types.u_int64_t;

public interface Libgit2
        extends ErrorOps, ObjectOps, RevparseOps, OidOps, RepositoryOps, TreeOps, BlobOps, ConfigOps, CommitOps {
    int git_libgit2_init();

    int git_libgit2_shutdown();
}

interface ErrorOps {
    Structs.GitError git_error_last();
}

interface RevparseOps {
    int git_revparse_single(@Out PointerByReference object, @In Pointer repo, @In String spec);
}

interface CommitOps {
    Structs.GitSignature git_commit_author(@In Pointer commit);

    String git_commit_body(@In Pointer commit);

    Structs.GitSignature git_commit_committer(@In Pointer commit);

    void git_commit_free(@In Pointer commit);

    Pointer git_commit_id(@In Pointer commit);

    String git_commit_message(@In Pointer commit);

    long git_commit_time(@In Pointer commit);

    int git_commit_tree(@Out PointerByReference tree, @In Pointer commit);
}

interface ConfigOps {
    void git_config_free(@Out Pointer config);

    int git_config_set_bool(@In Pointer config, @In String name, @In int value);

    int git_config_set_string(@In Pointer config, @In String name, @In String value);
}

interface OidOps {
    void git_oid_fmt(@Out StringBuilder out, @In Pointer oid);

    int git_oid_fromstr(@Out Pointer oid, @In String str);

    String git_oid_tostr_s(@In Pointer oid);
}

interface RepositoryOps {
    int git_repository_config(@Out PointerByReference config, @In Pointer repo);

    int git_repository_open(@Out PointerByReference repo, @In String path);

    int git_repository_open_bare(@Out PointerByReference repo, @In String path);

    String git_repository_workdir(@In Pointer repo);

    void git_repository_free(@In Pointer repo);
}

interface ObjectOps {
    Pointer git_object_id(@In Pointer object);

    int git_object_free(@In Pointer object);

    int git_object_peel(@Out PointerByReference peeled, @In Pointer object, @In GitObject.Type target);

    GitObject.Type git_object_type(@In Pointer object);
}

interface TreeOps {
    Pointer git_tree_entry_byid(@In Pointer tree, @In Pointer oid);

    Pointer git_tree_entry_byindex(@In Pointer tree, @In @size_t long index);

    Pointer git_tree_entry_byname(@In Pointer tree, @In String filename);

    int git_tree_entry_bypath(@Out PointerByReference entry, @In Pointer tree, @In String path);

    Tree.FileMode git_tree_entry_filemode(@In Pointer treeEntry);

    void git_tree_entry_free(@In Pointer treeEntry);

    Pointer git_tree_entry_id(@In Pointer treeEntry);

    String git_tree_entry_name(@In Pointer treeEntry);

    int git_tree_entry_to_object(@Out PointerByReference object, @In Pointer repo, @In Pointer treeEntry);

    GitObject.Type git_tree_entry_type(@In Pointer treeEntry);

    @size_t
    long git_tree_entrycount(@In Pointer tree);

    void git_tree_free(@In Pointer tree);

    Pointer git_tree_id(@In Pointer tree);
}

interface BlobOps {
    void git_blob_free(@In Pointer blob);

    Pointer git_blob_id(@In Pointer blob);

    boolean git_blob_is_binary(@In Pointer blob);

    int git_blob_lookup(@Out PointerByReference blob, @In Pointer repo, @In Pointer oid);

    Pointer git_blob_rawcontent(@In Pointer blob);

    @u_int64_t
    long git_blob_rawsize(@In Pointer blob);
}
