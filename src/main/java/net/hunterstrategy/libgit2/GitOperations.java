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


import java.lang.ref.Cleaner;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import jnr.ffi.LibraryLoader;

public class GitOperations implements ResourceOwner {
    private static final AtomicInteger INITCOUNT = new AtomicInteger(0);
    private static final Cleaner CLEANER = Cleaner.create();

    private final AtomicBoolean CLOSED = new AtomicBoolean(false);
    private final Set<Resource> resources = new HashSet<>();
    private final Libgit2 LIBGIT = initialize();
    private OpsCleaner opsCleaner;

    private Libgit2 initialize() {
        synchronized (INITCOUNT) {
            Libgit2 libgit = LibraryLoader.create(Libgit2.class).load("git2");
            int code = libgit.git_libgit2_init();
            if (code < 0) {
                throw new GitCallException(code, lastError(libgit));
            }
            int javacount = INITCOUNT.incrementAndGet();
            if (code != javacount) {
                System.err.printf(
                        "libgit2/binding initialization count mismatch! java/libgit2 %d/%d%n", javacount, code);
            }

            this.opsCleaner = new OpsCleaner(libgit, resources, CLOSED);
            CLEANER.register(this, this.opsCleaner);
            return libgit;
        }
    }

    /**
     * To use phantom references to clean up resources, the cleanup routine cannot
     * reference the object that has become phantomly-reachable. Hence, we create
     * this cleaner class to share state with the operations class, but only enough
     * to complete the shutdown completely and safely.
     */
    private static class OpsCleaner implements Runnable {
        private final Set<Resource> resources;
        private final Libgit2 git;
        private final AtomicBoolean closed;

        private OpsCleaner(Libgit2 git, Set<Resource> resources, AtomicBoolean closed) {
            this.git = git;
            this.resources = resources;
            this.closed = closed;
        }

        @Override
        public void run() {
            close();
        }

        public void close() {
            synchronized (closed) {
                if (closed.get()) {
                    return;
                }
                try {
                    for (Resource ac : resources) {
                        try {
                            ac.close();
                        } catch (Exception e) {
                            // swallow
                        }
                    }

                    int code = git.git_libgit2_shutdown();
                    if (code < 0) {
                        throw new GitCallException(code, lastError(git));
                    }
                    int javacount = INITCOUNT.decrementAndGet();
                    if (javacount != code) {
                        System.err.println("libgit2/binding shutdown count mismatch!");
                    }
                } finally {
                    closed.compareAndSet(false, true);
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        opsCleaner.close();
    }

    @Override
    public void addOwnership(Resource resource) {
        resources.add(resource);
    }

    boolean isClosed() {
        return CLOSED.get();
    }

    private void checkClosed() {
        if (CLOSED.get()) {
            throw new IllegalStateException("git resource is closed/freed");
        }
    }

    void callCheck(Function<Libgit2, Integer> func) {
        checkClosed();
        int code = func.apply(LIBGIT);
        if (code < 0) {
            throw new GitCallException(code, lastError(LIBGIT));
        }
    }

    void call_void(Consumer<Libgit2> consumer) {
        checkClosed();
        consumer.accept(LIBGIT);
    }

    <T> T call(Function<Libgit2, T> func) {
        checkClosed();
        return func.apply(LIBGIT);
    }

    static String lastError(Libgit2 libgit) {
        Structs.GitError err = libgit.git_error_last();
        return err.message.toString();
    }

    @Override
    public Collection<Resource> getOwnedResources() {
        return Collections.unmodifiableSet(resources);
    }
}
