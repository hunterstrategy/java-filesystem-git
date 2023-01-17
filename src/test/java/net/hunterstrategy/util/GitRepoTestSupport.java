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
package net.hunterstrategy.util;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.hunterstrategy.jnfsgit.GitURI;

public interface GitRepoTestSupport {
    Path getTempDir();

    default URI uri(String revision, String... paths) {
        String path = getTempDir().toString();
        if (paths != null && paths.length > 0) {
            List<String> pathList = new ArrayList<>();
            pathList.add(path);
            pathList.addAll(Arrays.asList(paths));
            path = String.join("/", pathList);
        }
        return GitURI.toURI(path, revision);
    }

    default FileSystem fs(String revision, String... paths) throws Exception {
        try {
            Map<String, FileSystem> revisions = GitRepoTestSupportHolder.revisionsFor(getClass());

            return revisions.compute(revision, (rev, fs) -> {
                if (fs != null && fs.isOpen()) {
                    return fs;
                }

                URI uri = uri(rev, paths);
                try {
                    return FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException fsnfe) {
                    // control flow via exceptions, good job Java
                    // yes this is what the Javadoc for FileSystemProvider imply
                    // is the intended way these two methods work together
                }
                try {
                    return FileSystems.newFileSystem(uri, Collections.emptyMap());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (IllegalStateException e) {
            if (e.getCause() != null) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    public static void unzipRepo(Path dest, String archive) throws Exception {
        System.out.printf("==> Using dir '%s' for archive '%s'%n", dest, archive);
        try (FileInputStream fis = new FileInputStream("src/test/resources/" + archive);
                ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                File zipFile = new File(dest.toFile(), ze.getName());
                if (ze.isDirectory()) {
                    zipFile.mkdirs();
                } else {
                    zipFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(zipFile)) {
                        zis.transferTo(fos);
                        fos.flush();
                    }
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
        }
    }
}

final class GitRepoTestSupportHolder {
    private GitRepoTestSupportHolder() {}

    static Map<String, FileSystem> revisionsFor(Class<?> clazz) {
        return revisions.computeIfAbsent(clazz, (key) -> {
            return new ConcurrentHashMap<String, FileSystem>();
        });
    }

    static final ConcurrentMap<Class<?>, Map<String, FileSystem>> revisions = new ConcurrentHashMap<>();
}
