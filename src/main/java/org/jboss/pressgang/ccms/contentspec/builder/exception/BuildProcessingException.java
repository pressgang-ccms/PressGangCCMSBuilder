/*
  Copyright 2011-2014 Red Hat, Inc

  This file is part of PressGang CCMS.

  PressGang CCMS is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  PressGang CCMS is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with PressGang CCMS.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.jboss.pressgang.ccms.contentspec.builder.exception;

public class BuildProcessingException extends Exception {
    private static final long serialVersionUID = 7574806371186221384L;

    public BuildProcessingException(final String message) {
        super(message);
    }

    public BuildProcessingException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public BuildProcessingException(final Throwable cause) {
        super(cause);
    }
}
