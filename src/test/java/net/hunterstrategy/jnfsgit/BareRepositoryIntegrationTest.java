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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.hunterstrategy.util.GitRepoTestSupport;
import net.hunterstrategy.util.IntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

@IntegrationTest
public class BareRepositoryIntegrationTest implements GitRepoTestSupport {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    static Path tempDir;

    @Override
    public Path getTempDir() {
        return tempDir;
    }

    @BeforeAll
    public static void setup() throws Exception {
        GitRepoTestSupport.unzipRepo(tempDir, "repo1-bare.git.zip");
    }

    @Test
    public void open_bare_implicit() throws Exception {
        URI uri = uri("HEAD", "repo1-bare.git");
        try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            Assertions.assertNotNull(fs);
            Assertions.assertTrue(Files.exists(fs.getPath("b")));
            Assertions.assertFalse(Files.exists(fs.getPath("a")));
        }
    }

    @Test
    public void open_bare_explicit() throws Exception {
        URI uri = uri("HEAD", "repo1-bare.git");
        Map<String, Object> env = new HashMap<>();
        env.put("bare", true);
        try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
            Assertions.assertNotNull(fs);
            Assertions.assertTrue(Files.exists(fs.getPath("b")));
            Assertions.assertFalse(Files.exists(fs.getPath("a")));
        }
    }

    @Test
    public void open_bare_explicit_notbare() throws Exception {
        URI uri = uri("HEAD", "repo1-bare");
        Map<String, Object> env = new HashMap<>();
        env.put("bare", false);
        /*
         * Initially I expected this to fail, but it seems libgit2 is
         * detecting that the repository is bare and opening it as such
         * even though this is forcing repository_open and not the use
         * of repository_open_bare.
         */
        try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
            Assertions.assertTrue(Files.exists(fs.getPath("b")));
            Assertions.assertFalse(Files.exists(fs.getPath("a")));
        }
    }
}
