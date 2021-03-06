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

package org.jboss.pressgang.ccms.contentspec.builder.structures;

/**
 * This class represents a topic that was included in a custom injection point.
 */
public class InjectionData {
    /**
     * The topic/target ID
     */
    public String id;
    /**
     * whether this topic was marked as optional
     */
    public boolean optional;

    public InjectionData(final String id, final boolean optional) {
        this.id = id;
        this.optional = optional;
    }
}
