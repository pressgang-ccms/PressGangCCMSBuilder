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
import org.jboss.pressgang.ccms.contentspec.builder.UTF8ResourceBundleControl;
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
    protected static final Map<String, Locale> LOCALE_MAP = new HashMap<String, Locale>();
    static {
        LOCALE_MAP.put("ja", new Locale("ja", "JP"));
        LOCALE_MAP.put("es", new Locale("es", "ES"));
        LOCALE_MAP.put("zh-Hans", new Locale("zh", "CH"));
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
     * The original title of the book before any modifications are done.
     */
    private final String originalTitle;
    /**
     * The original product of the book before any modifications are done.
     */
    private final String originalProduct;
    /**
     * Holds information on file url locations, which will be downloaded and included in the docbook zip file.
     */
    private final List<TopicImageData> imageLocations = new ArrayList<TopicImageData>();

    /**
     * If the build is building a translation or just a normal book
     */
    private boolean translationBuild;
    /**
     * The date of this build.
     */
    private final Date buildDate;

    /**
     * The escaped version of the books title.
     */
    private final String escapedTitle;
    /**
     * The locale the book is to be built in.
     */
    private final String locale;
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

    public BuildData(final String requester, final ContentSpec contentSpec, final DocBookBuildingOptions buildOptions, final DataProviderFactory providerFactory) {
        this(requester, contentSpec, buildOptions, new ZanataDetails(), providerFactory);
    }

    public BuildData(final String requester, final ContentSpec contentSpec, final DocBookBuildingOptions buildOptions, final ZanataDetails zanataDetails, final DataProviderFactory providerFactory) {
        this.contentSpec = contentSpec;
        this.requester = requester;
        this.buildOptions = buildOptions;
        this.zanataDetails = zanataDetails;
        originalProduct = contentSpec.getProduct();
        originalTitle = contentSpec.getTitle();
        escapedTitle = DocBookUtilities.escapeTitle(originalTitle);
        buildDate = new Date();

        serverSettings = providerFactory.getProvider(ServerSettingsProvider.class).getServerSettings();

        locale = buildOptions.getLocale() == null ? serverSettings.getDefaultLocale() : buildOptions.getLocale();
        outputLocale = buildOptions.getOutputLocale() == null ? locale : buildOptions.getOutputLocale();

        applyBuildOptionsFromSpec(contentSpec, buildOptions);
        applyInjectionOptionsFromSpec(contentSpec, buildOptions);

        final String defaultBuildLocale = serverSettings.getDefaultLocale();
        if (getBuildLocale().equals(defaultBuildLocale) || !LOCALE_MAP.containsKey(getBuildLocale())) {
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
        return originalTitle;
    }

    public String getOriginalBookProduct() {
        return originalProduct;
    }

    public List<TopicImageData> getImageLocations() {
        return imageLocations;
    }

    public boolean isTranslationBuild() {
        return translationBuild;
    }

    public void setTranslationBuild(boolean translationBuild) {
        this.translationBuild = translationBuild;
    }

    public Date getBuildDate() {
        return buildDate;
    }

    public String getEscapedBookTitle() {
        return escapedTitle;
    }

    public String getBuildLocale() {
        return locale;
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
        return getEscapedBookTitle() + ".ent";
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
     *
     *
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
        if (getContentSpec().getBugLinks().equals(BugLinkType.JIRA)) {
            return getContentSpec().getJIRABugLinkOptions();
        } else if (getContentSpec().getBugLinks().equals(BugLinkType.BUGZILLA)) {
            return getContentSpec().getBugzillaBugLinkOptions();
        } else {
            return null;
        }
    }

    public BaseBugLinkStrategy getBugLinkStrategy() {
        if (bugLinkStrategy == null) {
            final BugLinkType bugLinkType = getContentSpec().getBugLinks();
            final BugLinkOptions bugOptions = getBugLinkOptions();
            bugLinkStrategy = BugLinkStrategyFactory.getInstance().create(bugLinkType,
                bugOptions == null ? null : bugOptions.getBaseUrl());
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
}
