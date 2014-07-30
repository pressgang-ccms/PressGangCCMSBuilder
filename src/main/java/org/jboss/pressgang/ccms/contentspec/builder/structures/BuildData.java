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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.buglinks.BaseBugLinkStrategy;
import org.jboss.pressgang.ccms.contentspec.buglinks.BugLinkOptions;
import org.jboss.pressgang.ccms.contentspec.buglinks.BugLinkStrategyFactory;
import org.jboss.pressgang.ccms.contentspec.builder.DocBookXMLPreProcessor;
import org.jboss.pressgang.ccms.contentspec.builder.UTF8ResourceBundleControl;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.entities.InjectionOptions;
import org.jboss.pressgang.ccms.contentspec.enums.BookType;
import org.jboss.pressgang.ccms.contentspec.enums.BugLinkType;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.jboss.pressgang.ccms.provider.ServerSettingsProvider;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.utils.structures.DocBookVersion;
import org.jboss.pressgang.ccms.wrapper.ServerEntitiesWrapper;
import org.jboss.pressgang.ccms.wrapper.ServerSettingsWrapper;
import org.jboss.pressgang.ccms.zanata.ZanataDetails;

public class BuildData {
    public static final Map<String, Locale> LOCALE_MAP = new HashMap<String, Locale>();

    static {
        LOCALE_MAP.put("ja", new Locale("ja", "JP"));
        LOCALE_MAP.put("es", new Locale("es", "ES"));
        LOCALE_MAP.put("zh-Hans", new Locale("zh", "CN"));
        LOCALE_MAP.put("zh-TW", new Locale("zh", "TW"));
        LOCALE_MAP.put("pt-BR", new Locale("pt", "BR"));
        LOCALE_MAP.put("de", new Locale("de", "DE"));
        LOCALE_MAP.put("fr", new Locale("fr", "FR"));
        LOCALE_MAP.put("ko", new Locale("ko", "KR"));
        LOCALE_MAP.put("it", new Locale("it", "IT"));
        LOCALE_MAP.put("ru", new Locale("ru", "RU"));
    }

    private final ServerSettingsWrapper serverSettings;
    /**
     * Holds the SpecTopics and their XML that exist within the content specification.
     */
    private final BuildDatabase buildDatabase = new BuildDatabase();
    /**
     * Holds the compiler errors that form the Errors.xml file in the compiled docbook.
     */
    private final TopicErrorDatabase errorDatabase = new TopicErrorDatabase();
    /**
     * The locale that the book should be saved as.
     */
    private final String outputLocale;
    /**
     * Holds information on file url locations, which will be downloaded and included in the docbook zip file.
     */
    private final List<TopicImageData> imageLocations = new ArrayList<TopicImageData>();

    /**
     * If the build is building a translation or just a normal book
     */
    private final boolean translationBuild;
    /**
     * The date of this build.
     */
    private final Date buildDate;

    /**
     * The escaped version of the books title.
     */
    private final String escapedTitle;
    private final String rootBookFileName;
    /**
     * The locale the book is to be built in.
     */
    private final String locale;
    /**
     * The books original locale.
     */
    private final String defaultLocale;
    /**
     * The Docbook/Formatting Building Options to be used when building.
     */
    private final DocBookBuildingOptions buildOptions;
    /**
     * A mapping of override key's to their files.
     */
    private final Map<String, byte[]> overrideFiles = new HashMap<String, byte[]>();
    /**
     * The options that specify what injections are allowed when building.
     */
    private final InjectionOptions injectionOptions = new InjectionOptions();
    /**
     * The details about the zanata server the translated topics exist on.
     */
    private final ZanataDetails zanataDetails;
    /**
     * The username of the user who requested the build.
     */
    private final String requester;
    /**
     * The set of Constants to use when building
     */
    private ResourceBundle constantsResourceBundle;

    /**
     * The Mapping of file names to file contents to be used to build the ZIP archive.
     */
    private final HashMap<String, byte[]> files = new HashMap<String, byte[]>();
    private final ContentSpec contentSpec;

    private boolean useFixedUrls = false;
    private BaseBugLinkStrategy bugLinkStrategy = null;
    private DocBookXMLPreProcessor xmlPreProcessor = null;

    public BuildData(final String requester, final ContentSpec contentSpec, final DocBookBuildingOptions buildOptions,
            final ZanataDetails zanataDetails, final DataProviderFactory providerFactory, boolean translationBuild) {
        this.contentSpec = contentSpec;
        this.requester = requester;
        this.buildOptions = buildOptions;
        this.zanataDetails = zanataDetails;
        escapedTitle = DocBookUtilities.escapeTitle(contentSpec.getTitleNode().getValue());
        if (BuilderConstants.VALID_PUBLICAN_DOCNAME_PATTERN.matcher(escapedTitle).matches()) {
            rootBookFileName = escapedTitle;
        } else {
            rootBookFileName = contentSpec.getBookType().toString().replace("-Draft", "");
        }
        this.translationBuild = translationBuild;
        buildDate = new Date();

        serverSettings = providerFactory.getProvider(ServerSettingsProvider.class).getServerSettings();

        defaultLocale = contentSpec.getLocale() == null ? serverSettings.getDefaultLocale() : contentSpec.getLocale();
        if (translationBuild) {
            locale = buildOptions.getLocale() == null ? defaultLocale : buildOptions.getLocale();
            outputLocale = buildOptions.getOutputLocale() == null ? locale : buildOptions.getOutputLocale();
        } else {
            locale = defaultLocale;
            outputLocale = buildOptions.getOutputLocale() == null ? locale : buildOptions.getOutputLocale();
        }

        applyBuildOptionsFromSpec(contentSpec, buildOptions);
        applyInjectionOptionsFromSpec(contentSpec, buildOptions);

        if (getBuildLocale().equals("en-US") || !LOCALE_MAP.containsKey(getBuildLocale())) {
            constantsResourceBundle = ResourceBundle.getBundle("org.jboss.pressgang.ccms.contentspec.builder.Constants",
                    new UTF8ResourceBundleControl());
        } else {
            constantsResourceBundle = ResourceBundle.getBundle("org.jboss.pressgang.ccms.contentspec.builder.Constants",
                    LOCALE_MAP.get(getBuildLocale()), new UTF8ResourceBundleControl());
        }
    }

    public ZanataDetails getZanataDetails() {
        return zanataDetails;
    }

    public BuildDatabase getBuildDatabase() {
        return buildDatabase;
    }

    public TopicErrorDatabase getErrorDatabase() {
        return errorDatabase;
    }

    public String getOutputLocale() {
        return outputLocale;
    }

    public String getOriginalBookTitle() {
        return contentSpec.getTitleNode().getValue();
    }

    public String getOriginalBookProduct() {
        return contentSpec.getProductNode().getValue();
    }

    public List<TopicImageData> getImageLocations() {
        return imageLocations;
    }

    public boolean isTranslationBuild() {
        return translationBuild;
    }

    public Date getBuildDate() {
        return buildDate;
    }

    public String getEscapedBookTitle() {
        return escapedTitle;
    }

    public String getRootBookFileName() {
        return rootBookFileName;
    }

    public String getBuildLocale() {
        return locale;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public DocBookBuildingOptions getBuildOptions() {
        return buildOptions;
    }

    public Map<String, byte[]> getOverrideFiles() {
        return overrideFiles;
    }

    public InjectionOptions getInjectionOptions() {
        return injectionOptions;
    }

    /**
     * Get the root path for the books storage.
     */
    public String getRootBookFolder() {
        return getEscapedBookTitle() + "/";
    }

    /**
     * Get the locale path for the books storage. eg. /{@code<TITLE>}/en-US/
     */
    public String getBookLocaleFolder() {
        return getRootBookFolder() + getOutputLocale() + "/";
    }

    /**
     * Get the path where topics are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/topics/
     */
    public String getBookTopicsFolder() {
        return getBookLocaleFolder() + "topics/";
    }

    /**
     * Get the path where images are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/images/
     */
    public String getBookImagesFolder() {
        return getBookLocaleFolder() + "images/";
    }

    /**
     * Get the path where generic files are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/files/
     */
    public String getBookFilesFolder() {
        return getBookLocaleFolder() + "files/";
    }

    public String getEntityFileName() {
        return getRootBookFileName() + ".ent";
    }

    public String getBuildName() {
        return getBuildOptions().getBuildName();
    }

    public ContentSpec getContentSpec() {
        return contentSpec;
    }

    protected void applyBuildOptionsFromSpec(final ContentSpec contentSpec, final DocBookBuildingOptions buildOptions) {
        /*
         * Apply the build options from the content spec only if the build options are true. We do this so that if the options
         * are turned off earlier then we don't re-enable them.
         */
        if (buildOptions.getForceInjectBugLinks()) {
            buildOptions.setInsertBugLinks(true);
        } else {
            if (buildOptions.getInsertBugLinks()) {
                buildOptions.setInsertBugLinks(contentSpec.isInjectBugLinks());
            }
        }
        if (buildOptions.getBuildName() == null || buildOptions.getBuildName().isEmpty()) {
            buildOptions.setBuildName(
                    (contentSpec.getId() != null ? (contentSpec.getId() + ", ") : "") + contentSpec.getTitle() + "-" + contentSpec
                            .getVersion() + (contentSpec.getEdition() == null ? "" : ("-" + contentSpec.getEdition())));
        }
        if (!buildOptions.getDraft()) {
            if (contentSpec.getBookType() == BookType.ARTICLE_DRAFT || contentSpec.getBookType() == BookType.BOOK_DRAFT) {
                buildOptions.setDraft(true);
            }
        }
    }

    protected void applyInjectionOptionsFromSpec(final ContentSpec contentSpec, final DocBookBuildingOptions buildOptions) {
        // Get the injection mode
        InjectionOptions.UserType injectionType = InjectionOptions.UserType.NONE;
        final Boolean injection = buildOptions.getInjection();
        if (injection != null && !injection) {
            injectionType = InjectionOptions.UserType.OFF;
        } else if (injection != null && injection) {
            injectionType = InjectionOptions.UserType.ON;
        }

        // Add the strict injection types
        if (buildOptions.getInjectionTypes() != null) {
            for (final String injectType : buildOptions.getInjectionTypes()) {
                injectionOptions.addStrictTopicType(injectType.trim());
            }
            if (injection != null && injection) {
                injectionType = InjectionOptions.UserType.STRICT;
            }
        }

        // Set the injection mode
        injectionOptions.setClientType(injectionType);

        // Set the injection options for the content spec
        if (contentSpec.getInjectionOptions() != null) {
            injectionOptions.setContentSpecType(contentSpec.getInjectionOptions().getContentSpecType());
            injectionOptions.addStrictTopicTypes(contentSpec.getInjectionOptions().getStrictTopicTypes());
        }
    }

    /**
     * @return
     */
    public HashMap<String, byte[]> getOutputFiles() {
        return files;
    }

    public String getRequester() {
        return requester;
    }

    /**
     * Get whether the Fixed URL Properties should be used for the topic ID attributes.
     *
     * @return Whether the Fixed URL Properties should be used for the topic ID attributes.
     */
    public boolean isUseFixedUrls() {
        return useFixedUrls;
    }

    /**
     * Set whether the Fixed URL Properties should be used for the topic ID attributes.
     *
     * @param useFixedUrls True if the fixed urls should be used, otherwise false.
     */
    public void setUseFixedUrls(boolean useFixedUrls) {
        this.useFixedUrls = useFixedUrls;
    }

    public ServerSettingsWrapper getServerSettings() {
        return serverSettings;
    }

    public ServerEntitiesWrapper getServerEntities() {
        return getServerSettings().getEntities();
    }

    public BugLinkOptions getBugLinkOptions() {
        final BugLinkOptions bugLinkOptions;
        if (getContentSpec().getBugLinks().equals(BugLinkType.JIRA)) {
            bugLinkOptions = getContentSpec().getJIRABugLinkOptions();
        } else if (getContentSpec().getBugLinks().equals(BugLinkType.BUGZILLA)) {
            bugLinkOptions = getContentSpec().getBugzillaBugLinkOptions();
        } else {
            bugLinkOptions = null;
        }

        if (bugLinkOptions != null) {
            bugLinkOptions.setUseEntities(!buildOptions.getUseOldBugLinks());
        }

        return bugLinkOptions;
    }

    public BaseBugLinkStrategy getBugLinkStrategy() {
        if (bugLinkStrategy == null) {
            final BugLinkType bugLinkType = getContentSpec().getBugLinks();
            final BugLinkOptions bugOptions = getBugLinkOptions();
            bugLinkStrategy = BugLinkStrategyFactory.getInstance().create(bugLinkType, bugOptions == null ? null : bugOptions.getBaseUrl());
        }

        return bugLinkStrategy;
    }

    public DocBookVersion getDocBookVersion() {
        if (contentSpec.getFormat().equalsIgnoreCase(CommonConstants.DOCBOOK_50_TITLE)) {
            return DocBookVersion.DOCBOOK_50;
        } else {
            return DocBookVersion.DOCBOOK_45;
        }
    }

    public ResourceBundle getConstants() {
        return constantsResourceBundle;
    }

    public DocBookXMLPreProcessor getXMLPreProcessor() {
        if (xmlPreProcessor == null) {
            xmlPreProcessor = new DocBookXMLPreProcessor(getConstants(), getBugLinkStrategy());
        }
        return xmlPreProcessor;
    }
}
