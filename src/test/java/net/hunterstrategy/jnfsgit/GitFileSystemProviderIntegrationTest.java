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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import net.hunterstrategy.jnfsgit.ex.FileSystemIsReadOnlyException;
import net.hunterstrategy.jnfsgit.ex.WrongFileTypeException;
import net.hunterstrategy.util.GitRepoTestSupport;
import net.hunterstrategy.util.IntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

@IntegrationTest
public class GitFileSystemProviderIntegrationTest implements GitRepoTestSupport {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    static Path tempDir;

    @Override
    public Path getTempDir() {
        return tempDir;
    }

    @BeforeAll
    public static void setup() throws Exception {
        GitRepoTestSupport.unzipRepo(tempDir, "repo1.zip");
    }

    @Test
    public void write_operations_unsupported() throws Exception {
        FileSystem fs = fs("HEAD");
        FileSystemProvider p = fs.provider();

        Path a = fs.getPath("a");
        Path b = fs.getPath("b");

        Assertions.assertThrows(FileSystemIsReadOnlyException.class, () -> {
            p.copy(b, a);
        });

        Assertions.assertThrows(FileSystemIsReadOnlyException.class, () -> {
            p.move(b, a);
        });

        Assertions.assertThrows(FileSystemIsReadOnlyException.class, () -> {
            p.delete(a);
        });

        Assertions.assertThrows(FileSystemIsReadOnlyException.class, () -> {
            p.createDirectory(a);
        });

        Assertions.assertThrows(FileSystemIsReadOnlyException.class, () -> {
            p.setAttribute(a, "attr", "value");
        });

        Assertions.assertThrows(AccessDeniedException.class, () -> {
            p.checkAccess(b, AccessMode.EXECUTE);
        });
        Assertions.assertThrows(FileSystemIsReadOnlyException.class, () -> {
            p.checkAccess(b, AccessMode.WRITE);
        });
        p.checkAccess(b, AccessMode.READ); // this one's OK
        fs.close();
        IOException cause = Assertions.assertThrows(IOException.class, () -> {
            p.checkAccess(b, AccessMode.READ); // fail now that FS is closed
        });
        Assertions.assertTrue(cause.getMessage().contains("closed"));
    }

    @Test
    public void closed_filesystems_can_be_recreated() throws Exception {
        // create FS
        FileSystem fs = FileSystems.newFileSystem(uri("HEAD"), null);

        // recreate FS, should cause exception
        Assertions.assertThrows(
                FileSystemAlreadyExistsException.class, () -> FileSystems.newFileSystem(uri("HEAD"), null));

        // close the FS
        fs.close();

        // recreate the FS - closed, so it should succeed
        fs = FileSystems.newFileSystem(uri("HEAD"), null);
        Assertions.assertNotNull(fs("HEAD"));
        Assertions.assertTrue(fs("HEAD").isOpen());

        // clean up so other tests don't fail
        fs.close();
    }

    @Test
    public void attempt_bare() throws Exception {
        try (FileSystem fs = FileSystems.newFileSystem(uri("HEAD", ".git"), null)) {
            Assertions.assertNotNull(fs);
            Assertions.assertTrue(Files.exists(fs.getPath("b")));
        }
    }

    @Test
    public void load_filesystem() throws Exception {
        FileSystem fs = fs("HEAD");
        Path buildsh = fs.getPath("b");
        try (InputStream is = Files.newInputStream(buildsh, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.wrap(is.readAllBytes());
            Assertions.assertEquals("bar\n", StandardCharsets.UTF_8.decode(buf).toString());
        }
    }

    @Test
    public void only_allow_git_fs() throws Exception {
        FileSystem fs = fs("HEAD");
        Path notGit = FileSystems.getDefault().getPath("foo");
        FileSystemProvider p = fs.provider();

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.checkAccess(notGit, AccessMode.READ);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.copy(notGit, notGit);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.createDirectory(notGit);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.delete(notGit);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.getFileAttributeView(notGit, BasicFileAttributeView.class);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.getFileStore(notGit);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.isHidden(notGit);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.isSameFile(notGit, notGit);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.move(notGit, notGit);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.newByteChannel(notGit, EnumSet.of(StandardOpenOption.READ));
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.newDirectoryStream(notGit, null);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.readAttributes(notGit, BasicFileAttributes.class);
        });

        Assertions.assertThrows(ProviderMismatchException.class, () -> {
            p.setAttribute(notGit, "attr", "value");
        });
    }

    @Test
    public void files_deleted_between_revisions() throws Exception {
        FileSystem fs = fs("HEAD");
        FileSystem parent = fs("HEAD^");

        // browse repo1 at HEAD revision
        Path b = fs.getPath("b");
        Assertions.assertTrue(Files.exists(b));
        Path a = fs.getPath("a");
        Assertions.assertFalse(Files.exists(a));

        // browse repo1 at HEAD - 1 revision
        Path b_parent = parent.getPath("b");
        Assertions.assertTrue(Files.exists(b_parent));
        Path a_parent = parent.getPath("a");
        Assertions.assertTrue(Files.exists(a_parent));
    }

    @Test
    public void root_directory() throws Exception {
        FileSystem fs = fs("HEAD");
        Path root1 = fs.getRootDirectories().iterator().next();
        Path root2 = root1.getRoot();
        Path root3 = fs.getPath("/");

        Assertions.assertEquals(root1, root2);
        Assertions.assertEquals(root2, root3);
        Assertions.assertEquals(root1, root3); // commutative

        Assertions.assertTrue(Files.isDirectory(root1, LinkOption.NOFOLLOW_LINKS));
        Assertions.assertTrue(Files.isDirectory(root2, LinkOption.NOFOLLOW_LINKS));
        Assertions.assertTrue(Files.isDirectory(root3, LinkOption.NOFOLLOW_LINKS));

        FileSystemProvider p = fs.provider();
        Assertions.assertTrue(p.isSameFile(root1, root2));
        Assertions.assertTrue(p.isSameFile(root2, root3));
        Assertions.assertTrue(p.isSameFile(root1, root3));
    }

    @Test
    public void file_open_options() throws Exception {
        FileSystem fs = fs("HEAD");
        Path b = fs.getPath("b");
        FileSystemProvider provider = fs.provider();
        SeekableByteChannel chan = provider.newByteChannel(b, EnumSet.noneOf(StandardOpenOption.class));
        Assertions.assertNotNull(chan);

        // can only READ files
        Assertions.assertThrows(FileSystemIsReadOnlyException.class, () -> {
            provider.newByteChannel(b, EnumSet.of(StandardOpenOption.APPEND));
        });

        // cannot open byte streams on directories
        Path root = fs.getPath("/");
        Assertions.assertThrows(WrongFileTypeException.class, () -> {
            provider.newByteChannel(root, EnumSet.of(StandardOpenOption.READ));
        });
    }

    // for now these are all the same as the time-of-commit.
    // maybe in the future we'll have a way to walk the rev
    // tree and get history-aware timestamps
    @Test
    public void file_timestamps() throws Exception {
        @SuppressWarnings("serial")
        Map<String, Long> expectedTimes = new TreeMap<>() {
            {
                put("HEAD@{1}", 1668026855000L);
                put("HEAD@{2}", 1668026838000L);
                put("HEAD@{3}", 1668026817000L);
                put("HEAD@{4}", 1668026801000L);
            }
        };

        for (Entry<String, Long> e : expectedTimes.entrySet()) {
            FileSystem fs = fs(e.getKey());
            Path a = fs.getPath("a");

            // what a verbose way to do a simple thing... good job Java
            BasicFileAttributeView view =
                    Files.getFileAttributeView(a, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);

            // test some other aspects of the view impl
            Assertions.assertEquals("basic", view.name());
            Assertions.assertThrows(FileSystemIsReadOnlyException.class, () -> {
                view.setTimes(FileTime.fromMillis(0), FileTime.fromMillis(0), FileTime.fromMillis(0));
            });

            BasicFileAttributes attr = view.readAttributes();
            Assertions.assertEquals(e.getValue(), attr.creationTime().toMillis());
            Assertions.assertEquals(e.getValue(), attr.lastAccessTime().toMillis());
            Assertions.assertEquals(e.getValue(), attr.lastModifiedTime().toMillis());

            // test alternate codepath to getting lastModifiedTime
            Assertions.assertEquals(
                    e.getValue(),
                    Files.getLastModifiedTime(a, LinkOption.NOFOLLOW_LINKS).toMillis());
        }
    }

    @Test
    public void file_contents_over_history() throws Exception {
        // a deleted
        FileSystem fs = fs("HEAD");
        Path a = fs.getPath("a");
        Path b = fs.getPath("b");
        Assertions.assertFalse(Files.exists(a));
        Assertions.assertTrue(Files.exists(b));
        byte[] b_bytes = Files.readAllBytes(b);
        String b_line = Files.readAllLines(b).get(0);
        Assertions.assertEquals(4, b_bytes.length);
        Assertions.assertEquals("bar", b_line);
        fs.close();

        // b modified to contain 'bar'
        fs = fs("HEAD@{1}");
        a = fs.getPath("a");
        b = fs.getPath("b");
        Assertions.assertTrue(Files.exists(a));
        Assertions.assertTrue(Files.exists(b));
        byte[] a_bytes = Files.readAllBytes(a);
        b_bytes = Files.readAllBytes(b);
        Assertions.assertEquals(4, a_bytes.length);
        Assertions.assertEquals(4, b_bytes.length);
        String a_line = Files.readAllLines(a).get(0);
        b_line = Files.readAllLines(b).get(0);
        Assertions.assertEquals("foo", a_line);
        Assertions.assertEquals("bar", b_line);
        fs.close();

        // a modified to contain 'foo'
        fs = fs("HEAD@{2}");
        a = fs.getPath("a");
        b = fs.getPath("b");
        Assertions.assertTrue(Files.exists(a));
        Assertions.assertTrue(Files.exists(b));
        a_bytes = Files.readAllBytes(a);
        b_bytes = Files.readAllBytes(b);
        Assertions.assertEquals(4, a_bytes.length);
        Assertions.assertEquals(0, b_bytes.length);
        a_line = Files.readAllLines(a).get(0);
        Assertions.assertEquals("foo", a_line);
        fs.close();

        // a and b exist, both empty
        fs = fs("HEAD@{3}");
        a = fs.getPath("a");
        b = fs.getPath("b");
        Assertions.assertTrue(Files.exists(a));
        Assertions.assertTrue(Files.exists(b));
        a_bytes = Files.readAllBytes(a);
        b_bytes = Files.readAllBytes(b);
        Assertions.assertEquals(0, a_bytes.length);
        Assertions.assertEquals(0, b_bytes.length);
        fs.close();

        // only a exists, empty
        fs = fs("HEAD@{4}");
        a = fs.getPath("a");
        b = fs.getPath("b");
        Assertions.assertTrue(Files.exists(a));
        Assertions.assertFalse(Files.exists(b));
        a_bytes = Files.readAllBytes(a);
        Assertions.assertEquals(0, a_bytes.length);
        fs.close();
    }
}
