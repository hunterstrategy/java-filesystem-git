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


import java.util.Objects;
import jnr.ffi.Pointer;

interface Resource extends AutoCloseable {
    Pointer getPointer();
}

abstract class AbstractResource implements Resource {
    @Override
    public boolean equals(Object obj) {
        if (obj != null && getClass().isAssignableFrom(obj.getClass())) {
            Resource other = Resource.class.cast(obj);
            return getPointer().address() == other.getPointer().address();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), getPointer().address());
    }
}
