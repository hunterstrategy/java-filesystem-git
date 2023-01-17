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


import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import jnr.ffi.Pointer;
import jnr.ffi.byref.PointerByReference;
import jnr.ffi.util.EnumMapper;

public class GitObject extends AbstractResource {
    private GitOperations ops;
    private Pointer object;
    private Repository owner;
    private final Map<Class<? extends Peelable>, Function<Pointer, Peelable>> peelables = initFunctionMap();

    GitObject(GitOperations ops, Pointer object, Repository owner) {
        this.ops = ops;
        this.object = object;
        this.owner = owner;
    }

    Map<Class<? extends Peelable>, Function<Pointer, Peelable>> initFunctionMap() {
        Map<Class<? extends Peelable>, Function<Pointer, Peelable>> map = new ConcurrentHashMap<>();
        map.put(Tree.class, this::peelToTree);
        map.put(Commit.class, this::peelToCommit);
        return Collections.unmodifiableMap(map);
    }

    @Override
    public Pointer getPointer() {
        return this.object;
    }

    public String getId() {
        Pointer oid = ops.call(git -> git.git_object_id(this.object));
        return ops.call(git -> git.git_oid_tostr_s(oid));
    }

    public Repository getOwner() {
        return this.owner;
    }

    public Type getType() {
        return ops.call(git -> git.git_object_type(this.object));
    }

    @SuppressWarnings("unchecked")
    public <T extends Peelable> Optional<T> peel(Class<T> clazz) {
        Type typeToPeel = clazz.getAnnotation(PeelableType.class).value();

        if (peelables.containsKey(clazz)) {
            PointerByReference pointer = new PointerByReference();
            try {
                ops.callCheck(git -> git.git_object_peel(pointer, this.object, typeToPeel));
            } catch (GitCallException ise) {
                return Optional.empty();
            }

            if (pointer.getValue() == null) {
                return Optional.empty();
            }

            Peelable p = peelables.get(clazz).apply(pointer.getValue());
            this.owner.addOwnership(p);
            return (Optional<T>) Optional.of(p);
        }
        return Optional.empty();
    }

    private Commit peelToCommit(Pointer pointer) {
        return new Commit(this.ops, pointer, this.owner);
    }

    private Tree peelToTree(Pointer pointer) {
        return new Tree(this.ops, pointer, this.owner);
    }

    @Override
    public void close() throws Exception {
        if (!ops.isClosed()) {
            ops.call_void(git -> git.git_object_free(this.object));
        }
    }

    public static enum Type implements EnumMapper.IntegerEnum {
        ANY(-2),
        INVALID(-1),
        COMMIT(1),
        TREE(2),
        BLOB(3),
        TAG(4),
        OFS_DELTA(6),
        REF_DELTA(7);

        private final int value;

        Type(int value) {
            this.value = value;
        }

        @Override
        public int intValue() {
            return this.value;
        }
    }
}
