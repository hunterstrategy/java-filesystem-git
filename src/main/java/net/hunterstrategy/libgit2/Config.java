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


import jnr.ffi.Pointer;

// TODO this is woefully incomplete.
// eventually want to have a set(Map<String, Object>) method
// that uses reflection to call all the appropriate config set
// methods. may need to have some sort of parameter <-> type
// map in the code to simplify getting the right type for
// various values
public class Config extends AbstractResource {
    Pointer config;
    GitOperations ops;

    Config(GitOperations ops, Pointer config) {
        this.ops = ops;
        this.config = config;
    }

    @Override
    public Pointer getPointer() {
        return this.config;
    }

    public void setParameter(String name, boolean value) {
        ops.callCheck(git -> git.git_config_set_bool(this.config, name, value ? 1 : 0));
    }

    public void setParameter(String name, String value) {
        ops.callCheck(git -> git.git_config_set_string(this.config, name, value));
    }

    @Override
    public void close() throws Exception {
        if (!ops.isClosed()) {
            ops.call_void(git -> git.git_config_free(this.config));
        }
    }
}
