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


import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import net.hunterstrategy.util.GitRepoTestSupport;
import net.hunterstrategy.util.IntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@IntegrationTest
public class AttributesIntegrationTest implements GitRepoTestSupport {
    @TempDir
    static Path tempDir;

    @Override
    public Path getTempDir() {
        return tempDir;
    }

    @BeforeAll
    public static void setup() throws Exception {
        GitRepoTestSupport.unzipRepo(tempDir, "attributes.zip");
    }

    @Test
    public void read_basic_attributes() throws Exception {
        try (FileSystem fs = fs("HEAD", "attributes")) {
            BasicFileAttributes attr = Files.readAttributes(
                    fs.getPath("regular_file"), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Assertions.assertNotNull(attr);
            Assertions.assertTrue(attr.isRegularFile());
        }
    }

    @Test
    public void cannot_follow_symlinks() throws Exception {
        try (FileSystem fs = fs("HEAD", "attributes")) {
            Throwable cause = Assertions.assertThrows(UnsupportedOperationException.class, () -> {
                Files.readAttributes(fs.getPath("regular_file"), BasicFileAttributes.class, new LinkOption[0]);
            });
            assertLinkFollowException(cause);

            cause = Assertions.assertThrows(UnsupportedOperationException.class, () -> {
                Files.readAttributes(fs.getPath("regular_file"), BasicFileAttributes.class, (LinkOption[]) null);
            });
            assertLinkFollowException(cause);
        }
    }

    void assertLinkFollowException(Throwable cause) {
        Assertions.assertEquals("Cannot follow symlinks.", cause.getMessage());
    }

    @Test
    public void read_unsupported_attributes() throws Exception {
        try (FileSystem fs = fs("HEAD", "attributes")) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                Files.readAttributes(fs.getPath("regular_file"), PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            });
        }
    }

    @Test
    public void read_attribute_by_string_unsupported() throws Exception {
        try (FileSystem fs = fs("HEAD", "attributes")) {
            Assertions.assertThrows(UnsupportedOperationException.class, () -> {
                Files.readAttributes(fs.getPath("regular_file"), "size");
            });
        }
    }

    @Test
    public void read_git_attributes() throws Exception {
        try (FileSystem fs = fs("HEAD", "attributes")) {
            assertDirectory(fs.getPath("/"));
            assertRegularFile(fs.getPath("regular_file"), 0);
            assertDirectory(fs.getPath("bin"));
            assertExecutable(fs.getPath("bin", "executable.sh"), 0);
            assertDirectory(fs.getPath("dir"));
            assertRegularFile(fs.getPath("dir", "child"), 0);
            assertSymlink(fs.getPath("symlink"));
            Assertions.assertThrows(FileNotFoundException.class, () -> {
                assertRegularFile(fs.getPath("does-not-exist"), 0);
            });
        }
    }

    void assertExecutable(Path p, long expectedSize) throws IOException {
        GitBasicFileAttributes attr = Files.readAttributes(p, GitBasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Assertions.assertEquals(expectedSize, attr.size());
        Assertions.assertFalse(attr.isDirectory());
        Assertions.assertTrue(attr.isRegularFile());
        Assertions.assertFalse(attr.isSymbolicLink());
        Assertions.assertTrue(attr.isExecutable());
        Assertions.assertFalse(attr.isOther());
        Assertions.assertEquals(p.toUri(), attr.fileKey());
    }

    void assertSymlink(Path p) throws IOException {
        GitBasicFileAttributes attr = Files.readAttributes(p, GitBasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Assertions.assertEquals(0, attr.size());
        Assertions.assertFalse(attr.isDirectory());
        Assertions.assertFalse(attr.isRegularFile());
        Assertions.assertTrue(attr.isSymbolicLink());
        Assertions.assertFalse(attr.isExecutable());
        Assertions.assertFalse(attr.isOther());
        Assertions.assertEquals(p.toUri(), attr.fileKey());
    }

    void assertDirectory(Path p) throws IOException {
        GitBasicFileAttributes attr = Files.readAttributes(p, GitBasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Assertions.assertEquals(0, attr.size());
        Assertions.assertTrue(attr.isDirectory());
        Assertions.assertFalse(attr.isRegularFile());
        Assertions.assertFalse(attr.isSymbolicLink());
        Assertions.assertFalse(attr.isExecutable());
        Assertions.assertFalse(attr.isOther());
        Assertions.assertEquals(p.toUri(), attr.fileKey());
    }

    void assertRegularFile(Path p, long expectedSize) throws IOException {
        GitBasicFileAttributes attr = Files.readAttributes(p, GitBasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Assertions.assertEquals(expectedSize, attr.size());
        Assertions.assertFalse(attr.isDirectory());
        Assertions.assertTrue(attr.isRegularFile());
        Assertions.assertFalse(attr.isSymbolicLink());
        Assertions.assertFalse(attr.isExecutable());
        Assertions.assertFalse(attr.isOther());
        Assertions.assertEquals(p.toUri(), attr.fileKey());
    }
}
