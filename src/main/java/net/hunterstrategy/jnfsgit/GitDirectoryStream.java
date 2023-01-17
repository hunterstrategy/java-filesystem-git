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
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.hunterstrategy.libgit2.Tree;
import net.hunterstrategy.libgit2.TreeItem;

public class GitDirectoryStream implements DirectoryStream<Path> {
    private final DirectoryStream.Filter<? super Path> filter;
    private final Tree tree;
    private final GitPath path;

    GitDirectoryStream(DirectoryStream.Filter<? super Path> filter, GitPath path, Tree tree) {
        this.filter = filter;
        this.tree = tree;
        this.path = path;
    }

    @Override
    public void close() throws IOException {}

    @Override
    public Iterator<Path> iterator() {
        int entryCount = (int) tree.getEntryCount();
        List<Path> items = new ArrayList<>(entryCount);
        for (int pos = 0; pos < entryCount; pos++) {
            TreeItem item = tree.getByIndex(pos);
            GitPath itemPath = GitPath.class.cast(path.resolve(item.getName()));
            try {
                if (filter.accept(itemPath)) {
                    items.add(itemPath);
                }
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }
        return items.iterator();
    }
}
