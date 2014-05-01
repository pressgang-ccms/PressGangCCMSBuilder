package org.jboss.pressgang.ccms.contentspec.builder.structures;

import java.util.HashMap;
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
    private final ResourceBundle translatedConstantsResourceBundle;

    public POBuildData(String requester, ContentSpec contentSpec, DocBookBuildingOptions buildOptions, ZanataDetails zanataDetails,
            DataProviderFactory providerFactory, boolean translationBuild) {
        super(requester, contentSpec, buildOptions, zanataDetails, providerFactory, translationBuild);

        translatedConstantsResourceBundle = ResourceBundle.getBundle("org.jboss.pressgang.ccms.contentspec.builder.Constants",
                BuildData.LOCALE_MAP.get(buildOptions.getLocale()), new UTF8ResourceBundleControl());
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
}
