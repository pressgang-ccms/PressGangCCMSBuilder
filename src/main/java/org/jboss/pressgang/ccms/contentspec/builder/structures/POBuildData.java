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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.builder.UTF8ResourceBundleControl;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.jboss.pressgang.ccms.zanata.ZanataDetails;

public class POBuildData extends BuildData {
    private final Map<String, Map<String, TranslationDetails>> translationMap = new HashMap<String, Map<String, TranslationDetails>>();
    private String translatedRevisionHistory;
    private String translatedAuthorGroup;
    private final String poLocale;
    private final String poOutputLocale;
    private final ResourceBundle translatedConstantsResourceBundle;

    public POBuildData(String requester, ContentSpec contentSpec, DocBookBuildingOptions buildOptions, ZanataDetails zanataDetails,
            DataProviderFactory providerFactory, boolean translationBuild) {
        super(requester, contentSpec, buildOptions, zanataDetails, providerFactory, translationBuild);

        // Setup the locales
        poLocale = getBuildOptions().getLocale() == null ? getBuildLocale() : getBuildOptions().getLocale();
        if (getBuildOptions().getOutputLocale() != null) {
            poOutputLocale = getBuildOptions().getOutputLocale();
        } else if (getLocaleMap().containsKey(poLocale)) {
            poOutputLocale = getLocaleMap().get(poLocale).getValue();
        } else {
            poOutputLocale = poLocale;
        }

        if (getBuildLocale().equals(getPOBuildLocale())) {
            translatedConstantsResourceBundle = getConstants();
        } else {
            final Locale buildLocale = Locale.forLanguageTag(getLocaleMap().get(getBuildLocale()).getBuildValue());
            translatedConstantsResourceBundle = ResourceBundle.getBundle("org.jboss.pressgang.ccms.contentspec.builder.Constants",
                    buildLocale, new UTF8ResourceBundleControl());
        }
    }

    public Map<String, Map<String, TranslationDetails>> getTranslationMap() {
        return translationMap;
    }

    public String getTranslatedRevisionHistory() {
        return translatedRevisionHistory;
    }

    public void setTranslatedRevisionHistory(final String translatedRevisionHistory) {
        this.translatedRevisionHistory = translatedRevisionHistory;
    }

    public String getTranslatedAuthorGroup() {
        return translatedAuthorGroup;
    }

    public void setTranslatedAuthorGroup(final String translatedAuthorGroup) {
        this.translatedAuthorGroup = translatedAuthorGroup;
    }

    public ResourceBundle getTranslationConstants() {
        return translatedConstantsResourceBundle;
    }

    public String getPOBuildLocale() {
        return poLocale;
    }

    public String getPOOutputLocale() {
        return poOutputLocale;
    }
}
