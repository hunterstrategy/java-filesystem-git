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


import java.util.Optional;

/**
 * This interface represents either a Tree or Tree.Entry.
 *
 * The libgit2 API exposes a Tree as a root element to represent the files
 * of a given commit. The Tree itself contains entries, which themselves
 * can be Trees (directories) or Blobs (files).
 *
 * The root directory of the repository cannot be viewed as a Tree Entry,
 * so this interface provides a unified view of the tree and uses polymorphism.
 *
 * You can viewAs as a Tree.Entry to either iterate children (Tree), or
 * convert it to a Blob.
 */
public interface TreeItem {
    /**
     * The git object ID of this item.
     * @return oid
     */
    String getId();

    /**
     * The name of the item in the tree. "/" is the root entry.
     * @return name
     */
    String getName();

    /**
     * @return true if tree (directory), false if blob (file)
     */
    boolean isTree();

    /**
     * @return git filemode for tree/entry.
     */
    Tree.FileMode getFileMode();

    /**
     * Return the underlying object, if possible.
     *
     * The tree root ("/") cannot be viewed as a Tree.Entry.
     * Only Tree.Entry of TREE type can be viewed as Tree.
     *
     * @param <T>
     * @param type - either Tree or Tree.Entry
     * @return the underlying Tree or Tree.Entry
     */
    <T extends TreeItem> Optional<T> viewAs(Class<T> type);
}
