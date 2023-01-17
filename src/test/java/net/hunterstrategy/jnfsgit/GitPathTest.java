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


import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent.Kind;
import java.util.ArrayList;
import java.util.List;
import org.easymock.EasyMock;
import org.easymock.EasyMockExtension;
import org.easymock.IAnswer;
import org.easymock.Mock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EasyMockExtension.class)
public class GitPathTest {
    @Mock
    GitFileSystem fs;

    @BeforeEach
    public void setup() {
        IAnswer<GitPath> pathBuilder = new IAnswer<>() {
            @Override
            public GitPath answer() throws Throwable {
                Object[] args = EasyMock.getCurrentArguments();
                List<String> sargs_list = new ArrayList<>(args.length);
                for (Object o : args) {
                    sargs_list.add(o.toString());
                }
                String path = String.join("/", sargs_list);
                return new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD", path)));
            }
        };

        // for some reason, declaring with array for varargs doesn't seem to work
        EasyMock.expect(fs.getPath(EasyMock.anyString())).andStubAnswer(pathBuilder);
        EasyMock.expect(fs.getPath(EasyMock.anyString(), EasyMock.anyString())).andStubAnswer(pathBuilder);
        EasyMock.expect(fs.getPath(EasyMock.anyString(), EasyMock.anyString(), EasyMock.anyString()))
                .andStubAnswer(pathBuilder);
    }

    @Test
    public void equals_hashCode() {
        EasyMock.replay(fs);

        GitPath path1 = new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD")));
        GitPath path2 = GitPath.class.cast(path1.resolve("foo"));

        Assertions.assertNotEquals(path1, path2);
        Assertions.assertNotEquals(path1.hashCode(), path2.hashCode());

        GitFileSystem other = EasyMock.createMock(GitFileSystem.class);
        GitPath path1_alternatefs = new GitPath(other, path1.getGitURI());
        Assertions.assertNotEquals(path1, path1_alternatefs);

        // not the right type
        Assertions.assertFalse(path1.equals(FileSystems.getDefault().getPath("foo")));

        GitPath path3 = path2.getParent();
        Assertions.assertEquals(path1, path3);
        Assertions.assertEquals(path1.hashCode(), path3.hashCode());
    }

    @Test
    public void various_attributes() {
        EasyMock.replay(fs);

        GitPath path1 = new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD", "/path/to/file")));
        GitPath path2 = new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD", "file")));

        // absolute
        Assertions.assertTrue(path1.isAbsolute());
        Assertions.assertFalse(path2.isAbsolute());

        // root
        Assertions.assertFalse(path1.isRoot());
        Assertions.assertTrue(path1.getRoot().isRoot());
        Assertions.assertTrue(new GitPath(fs, new GitURI(GitURI.toURI("/repo", "HEAD"))).isRoot());
        Assertions.assertNull(path1.getRoot().getParent());
        Path not_absolute = fs.getPath("notAbsolute");
        Assertions.assertNull(not_absolute.getRoot());

        // fileName
        Assertions.assertEquals("file", path1.getFileName().toString());
        Assertions.assertEquals(path2, path1.getFileName());

        // absolutePath
        Assertions.assertEquals("/file", path1.getFileName().toAbsolutePath().toString());
        Assertions.assertEquals("/", path1.getRoot().toAbsolutePath().toString());
        Assertions.assertEquals(path1, path1.toAbsolutePath());

        // fileSystem
        Assertions.assertSame(fs, path1.getFileSystem());

        // nameCount
        Assertions.assertEquals(1, path2.getNameCount());
        Assertions.assertEquals(3, path1.getNameCount());

        // name index
        Assertions.assertEquals("to", path1.getName(1).toString());

        // subpath
        Assertions.assertEquals("path/to", path1.subpath(0, 2).toString());

        // endsWith
        Assertions.assertTrue(path1.endsWith(path2));
        Assertions.assertTrue(path1.endsWith(FileSystems.getDefault().getPath("file")));
        Assertions.assertFalse(path1.endsWith(FileSystems.getDefault().getPath("NOPE")));

        // startsWith
        GitPath start = new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD", "/path/to")));
        Assertions.assertTrue(path1.startsWith(FileSystems.getDefault().getPath("/path")));
        Assertions.assertTrue(path1.startsWith(start));
        Assertions.assertFalse(path1.startsWith(FileSystems.getDefault().getPath("NOPE")));

        // compare
        Assertions.assertEquals(-55, path1.compareTo(path2));
        Assertions.assertEquals(55, path2.compareTo(path1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            path1.compareTo(FileSystems.getDefault().getPath("anything"));
        });

        // toUri
        URI guri = GitURI.toURI("/repoPath", "HEAD", "/path/in/repo");
        GitPath gp = new GitPath(fs, new GitURI(guri));
        Assertions.assertEquals(guri, gp.toUri());
    }

    @Test
    public void unsupported_operations() {
        GitPath path1 = new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD")));

        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            path1.register(null);
        });

        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            path1.register(null, new Kind<?>[] {});
        });

        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            path1.toRealPath();
        });
    }

    @Test
    public void parent() {
        EasyMock.replay(fs);

        GitPath path1 = new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD")));
        GitPath path_in_repo = new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD", "/path/in/repo")));

        Assertions.assertNull(path1.getParent());
        Assertions.assertEquals("/path/in", path_in_repo.getParent().toString());
    }

    @Test
    public void resolve() {
        EasyMock.replay(fs);

        GitPath path1 = new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD")));
        GitPath path_in_repo = new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD", "/path/in/repo")));

        GitPath path2 = GitPath.class.cast(path1.resolve("foo"));
        GitPath path2_in_repo = GitPath.class.cast(path_in_repo.resolve("foo"));

        Assertions.assertEquals("/foo", path2.toString());
        Assertions.assertEquals("/path/in/repo/foo", path2_in_repo.toString());

        Assertions.assertSame(path2, path1.resolve(path2));

        Path path3 = fs.getPath("notAbsolute");
        Assertions.assertFalse(path3.isAbsolute());
        Assertions.assertEquals(
                "/path/in/notAbsolute", path_in_repo.resolveSibling(path3).toString());

        Path empty = fs.getPath("");
        Assertions.assertFalse(empty.isAbsolute());
        Assertions.assertSame(path1, path1.resolve(empty));

        Assertions.assertThrows(NullPointerException.class, () -> {
            path1.resolve((Path) null);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            path1.resolve(FileSystems.getDefault().getPath("anything"));
        });
    }

    @Test
    public void relativize() {
        EasyMock.replay(fs);

        GitPath path1 = new GitPath(fs, new GitURI(GitURI.toURI("/path/to/repo", "HEAD", "/a/b/c/d")));
        GitPath path2 = GitPath.class.cast(fs.getPath("/a", "b"));
        GitPath result = path2.relativize(path1);

        Assertions.assertEquals("c/d", result.toString());

        Assertions.assertThrows(NullPointerException.class, () -> {
            path1.relativize((Path) null);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            path1.relativize(FileSystems.getDefault().getPath("anything"));
        });
    }

    @Test
    public void normalize() {
        EasyMock.replay(fs);

        Path relative = fs.getPath("dir", "..", "dir2");
        Assertions.assertEquals("dir2", relative.normalize().toString());
    }
}
