/*
  Copyright 2011-2014 Red Hat

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

public class TranslationDetails {
    private final String translation;
    private final boolean fuzzy;
    private String tagName;

    public TranslationDetails(final String translation, final boolean fuzzy) {
        this.translation = translation;
        this.fuzzy = fuzzy;
        tagName = null;
    }

    public TranslationDetails(final String translation, final boolean fuzzy, final String tagName) {
        this.translation = translation;
        this.fuzzy = fuzzy;
        this.tagName = tagName;
    }

    public boolean isFuzzy() {
        return fuzzy;
    }

    public String getTranslation() {
        return translation;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(final String tagName) {
        this.tagName = tagName;
    }
}
