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

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

/**
 * This class defines a DocBook list that was created when processing the custom
 * injection points defined in a topic XML file
 */
public class InjectionListData {
    /**
     * The elements that are to be inserted into the DocBook XML in place of the
     * comment. There may be more than one element, like in the case where an
     * xref tag is preceded by a emphasis tag.
     */
    public List<List<Element>> listItems = new ArrayList<List<Element>>();
    /**
     * defines the type of list (sequential, itemized etc)
     */
    public int listType = -1;

    /**
     * @param listItems A list that contains the lists of XML elements to
     *                  replace the comment
     * @param listType  The type of list, as defined by the static variables in
     *                  the DocbookBuilder class
     */
    public InjectionListData(final List<List<Element>> listItems, final int listType) {
        this.listItems = listItems;
        this.listType = listType;
    }
}
