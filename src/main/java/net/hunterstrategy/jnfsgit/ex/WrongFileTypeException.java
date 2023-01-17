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
package net.hunterstrategy.jnfsgit.ex;


import java.io.FileNotFoundException;

public class WrongFileTypeException extends FileNotFoundException {
    public static enum Type {
        DIR_EXPECTED_FILE("Expected file, but path was directory: %s"),
        FILE_EXPECTED_DIR("Expected directory, but path was file: %s");

        private String message;

        Type(String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
    }

    private static final long serialVersionUID = 1L;

    public WrongFileTypeException(Type type, String path) {
        super(String.format(type.message(), path));
    }
}
