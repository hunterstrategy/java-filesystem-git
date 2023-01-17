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


import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Objects;

public class GitURI {
    private URI uri;

    public static URI toURI(String repoDir, String revisionSpec) {
        return toURI(repoDir, revisionSpec, "/");
    }

    public static URI toURI(String repoDir, String revisionSpec, String subtreePath) {
        try {
            return URI.create(
                    String.format("git://%s?%s#%s", repoDir, subtreePath, URLEncoder.encode(revisionSpec, "UTF-8")));
        } catch (UnsupportedEncodingException uee) {
            throw new IllegalStateException(uee);
        }
    }

    GitURI(URI uri) {
        String fragment = uri.getFragment();
        if (fragment == null || fragment.isBlank()) {
            this.uri = toURI(uri.getPath(), "HEAD", uri.getQuery());
        }
        this.uri = uri;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GitURI) {
            return this.uri.equals(((GitURI) obj).uri);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(GitURI.class, uri);
    }

    public String getRevisionSpec() {
        return uri.getFragment();
    }

    public String getRepositoryDir() {
        return uri.getPath();
    }

    public String getSubtreePath() {
        String subtree = uri.getQuery();
        if (subtree == null || subtree.isBlank()) {
            return "";
        }
        return subtree;
    }

    public boolean isRoot() {
        return "/".equals(getSubtreePath());
    }

    public URI toFullURI() {
        return toURI(getRepositoryDir(), getRevisionSpec(), getSubtreePath());
    }

    public URI toRepoURI() {
        return toURI(getRepositoryDir(), getRevisionSpec());
    }

    public URI toRepoPathURI(String path) {
        return toURI(getRepositoryDir(), getRevisionSpec(), path);
    }

    @Override
    public String toString() {
        return toFullURI().toString();
    }
}
