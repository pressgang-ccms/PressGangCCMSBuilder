package org.jboss.pressgang.ccms.contentspec.builder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuilderCreationException;
import org.jboss.pressgang.ccms.contentspec.builder.structures.CSDocbookBuildingOptions;
import org.jboss.pressgang.ccms.contentspec.builder.structures.SpecDatabase;
import org.jboss.pressgang.ccms.contentspec.builder.utils.DocbookBuildUtilities;
import org.jboss.pressgang.ccms.contentspec.builder.utils.ReportUtilities;
import org.jboss.pressgang.ccms.contentspec.builder.utils.SAXXMLValidator;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.contentspec.entities.AuthorInformation;
import org.jboss.pressgang.ccms.contentspec.entities.InjectionOptions;
import org.jboss.pressgang.ccms.contentspec.enums.BookType;
import org.jboss.pressgang.ccms.contentspec.enums.LevelType;
import org.jboss.pressgang.ccms.contentspec.interfaces.ShutdownAbleApp;
import org.jboss.pressgang.ccms.contentspec.sort.AuthorInformationComparator;
import org.jboss.pressgang.ccms.contentspec.utils.EntityUtilities;
import org.jboss.pressgang.ccms.docbook.constants.DocbookBuilderConstants;
import org.jboss.pressgang.ccms.docbook.processing.DocbookXMLPreProcessor;
import org.jboss.pressgang.ccms.docbook.structures.TocTopicDatabase;
import org.jboss.pressgang.ccms.docbook.structures.TopicErrorData;
import org.jboss.pressgang.ccms.docbook.structures.TopicErrorDatabase;
import org.jboss.pressgang.ccms.docbook.structures.TopicErrorDatabase.ErrorLevel;
import org.jboss.pressgang.ccms.docbook.structures.TopicErrorDatabase.ErrorType;
import org.jboss.pressgang.ccms.docbook.structures.TopicImageData;
import org.jboss.pressgang.ccms.provider.BlobConstantProvider;
import org.jboss.pressgang.ccms.provider.ContentSpecProvider;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.jboss.pressgang.ccms.provider.ImageProvider;
import org.jboss.pressgang.ccms.provider.PropertyTagProvider;
import org.jboss.pressgang.ccms.provider.StringConstantProvider;
import org.jboss.pressgang.ccms.provider.TagProvider;
import org.jboss.pressgang.ccms.provider.TopicProvider;
import org.jboss.pressgang.ccms.provider.TranslatedTopicProvider;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.utils.common.ExceptionUtilities;
import org.jboss.pressgang.ccms.utils.common.FileUtilities;
import org.jboss.pressgang.ccms.utils.common.StringUtilities;
import org.jboss.pressgang.ccms.utils.common.XMLUtilities;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.utils.structures.Pair;
import org.jboss.pressgang.ccms.wrapper.BlobConstantWrapper;
import org.jboss.pressgang.ccms.wrapper.ImageWrapper;
import org.jboss.pressgang.ccms.wrapper.LanguageImageWrapper;
import org.jboss.pressgang.ccms.wrapper.PropertyTagInTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.StringConstantWrapper;
import org.jboss.pressgang.ccms.wrapper.TagWrapper;
import org.jboss.pressgang.ccms.wrapper.TopicWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.UserWrapper;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.collection.CollectionWrapper;
import org.jboss.pressgang.ccms.wrapper.collection.UpdateableCollectionWrapper;
import org.jboss.pressgang.ccms.zanata.ZanataDetails;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DocbookBuilder implements ShutdownAbleApp {
    protected static final Logger log = Logger.getLogger(DocbookBuilder.class);
    protected static final List<Integer> validKeywordCategoryIds = CollectionUtilities.toArrayList(CSConstants.TECHNOLOGY_CATEGORY_ID,
            CSConstants.RELEASE_CATEGORY_ID, CSConstants.SEO_METADATA_CATEGORY_ID, CSConstants.COMMON_NAME_CATEGORY_ID,
            CSConstants.CONCERN_CATEGORY_ID, CSConstants.CONTENT_TYPE_CATEGORY_ID, CSConstants.PROGRAMMING_LANGUAGE_CATEGORY_ID);
    private static final Integer MAX_URL_LENGTH = 4000;
    private static final String ENCODING = "UTF-8";

    protected final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    protected final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final List<String> verbatimElements;
    private final List<String> inlineElements;
    private final List<String> contentsInlineElements;

    private final DataProviderFactory providerFactory;
    private final ContentSpecProvider contentSpecProvider;
    private final TopicProvider topicProvider;
    private final TranslatedTopicProvider translatedTopicProvider;
    private final TagProvider tagProvider;
    private final PropertyTagProvider propertyTagProvider;
    private final StringConstantProvider stringConstantProvider;
    private final BlobConstantProvider blobConstantProvider;
    private final ImageProvider imageProvider;

    private final BlobConstantWrapper rocbookdtd;
    /**
     * The set of Translation Constants to use when building
     */
    private final Properties constantTranslatedStrings;

    /**
     * The details about the zanata server the translated topics exist on.
     */
    private ZanataDetails zanataDetails;

    /**
     * The StringConstant that holds the error template for a topic with no content.
     */
    private final StringConstantWrapper errorEmptyTopicTemplate;
    /**
     * The StringConstant that holds the error template for a topic with invalid injection references.
     */
    private final StringConstantWrapper errorInvalidInjectionTopicTemplate;
    /**
     * The StringConstant that holds the error template for a topic that failed validation.
     */
    private final StringConstantWrapper errorInvalidValidationTopicTemplate;

    /**
     * The Docbook/Formatting Building Options to be used when building.
     */
    private CSDocbookBuildingOptions buildOptions;
    /**
     * A mapping of override key's to their files.
     */
    private Map<String, byte[]> overrideFiles;
    /**
     * The options that specify what injections are allowed when building.
     */
    private InjectionOptions injectionOptions;
    /**
     * The date of this build.
     */
    private Date buildDate;

    /**
     * The escaped version of the books title.
     */
    private String escapedTitle;
    /**
     * The locale the book is to be built in.
     */
    private String locale;
    /**
     * The default locale to build a book as if it isn't specified.
     */
    private final String defaultLocale;
    /**
     * The locale that the book should be saved as.
     */
    private String outputLocale;
    /**
     * The original title of the book before any modifications are done.
     */
    private String originalTitle;
    /**
     * The original product of the book before any modifications are done.
     */
    private String originalProduct;

    /**
     * Holds the compiler errors that form the Errors.xml file in the compiled docbook.
     */
    private TopicErrorDatabase errorDatabase;

    /**
     * Holds the SpecTopics and their XML that exist within the content specification.
     */
    private SpecDatabase specDatabase;

    /**
     * Holds information on file url locations, which will be downloaded and included in the docbook zip file.
     */
    private List<TopicImageData> imageLocations = new ArrayList<TopicImageData>();

    /**
     * If the build is building a translation or just a normal book
     */
    private boolean translationBuild;

    public DocbookBuilder(final DataProviderFactory providerFactory, final BlobConstantWrapper rocbookDtd,
            final String defaultLocale) throws BuilderCreationException {
        this.providerFactory = providerFactory;

        contentSpecProvider = providerFactory.getProvider(ContentSpecProvider.class);
        topicProvider = providerFactory.getProvider(TopicProvider.class);
        translatedTopicProvider = providerFactory.getProvider(TranslatedTopicProvider.class);
        propertyTagProvider = providerFactory.getProvider(PropertyTagProvider.class);
        stringConstantProvider = providerFactory.getProvider(StringConstantProvider.class);
        blobConstantProvider = providerFactory.getProvider(BlobConstantProvider.class);
        tagProvider = providerFactory.getProvider(TagProvider.class);
        imageProvider = providerFactory.getProvider(ImageProvider.class);

        rocbookdtd = rocbookDtd;
        errorEmptyTopicTemplate = stringConstantProvider.getStringConstant(DocbookBuilderConstants.CSP_EMPTY_TOPIC_ERROR_XML_ID);
        errorInvalidInjectionTopicTemplate = stringConstantProvider.getStringConstant(
                DocbookBuilderConstants.CSP_INVALID_INJECTION_TOPIC_ERROR_XML_ID);
        errorInvalidValidationTopicTemplate = stringConstantProvider.getStringConstant(
                DocbookBuilderConstants.CSP_INVALID_VALIDATION_TOPIC_ERROR_XML_ID);
        final StringConstantWrapper xmlElementsProperties = stringConstantProvider.getStringConstant(
                CommonConstants.XML_ELEMENTS_STRING_CONSTANT_ID);

        this.defaultLocale = defaultLocale;

        /*
         * Get the XML formatting details. These are used to pretty-print the XML when it is converted into a String.
         */
        final Properties prop = new Properties();
        try {
            prop.load(new StringReader(xmlElementsProperties.getValue()));
        } catch (IOException e) {
            log.error("Failed to read the XML Elements Property file");
            throw new BuilderCreationException("Failed to read the XML Elements Property file");
        }

        final String verbatimElementsString = prop.getProperty(CommonConstants.VERBATIM_XML_ELEMENTS_PROPERTY_KEY);
        final String inlineElementsString = prop.getProperty(CommonConstants.INLINE_XML_ELEMENTS_PROPERTY_KEY);
        final String contentsInlineElementsString = prop.getProperty(CommonConstants.CONTENTS_INLINE_XML_ELEMENTS_PROPERTY_KEY);

        verbatimElements = CollectionUtilities.toArrayList(verbatimElementsString.split("[\\s]*,[\\s]*"));
        inlineElements = CollectionUtilities.toArrayList(inlineElementsString.split("[\\s]*,[\\s]*"));
        contentsInlineElements = CollectionUtilities.toArrayList(contentsInlineElementsString.split("[\\s]*,[\\s]*"));

        // Load the default constant translation strings
        final Properties defaultProperties = new Properties();
        final URL defaultUrl = ClassLoader.getSystemResource("Constants.properties");
        if (defaultUrl != null) {
            try {
                defaultProperties.load(new InputStreamReader(defaultUrl.openStream(), ENCODING));
            } catch (IOException ex) {
                log.debug(ex);
            }
        }
        constantTranslatedStrings = new Properties(defaultProperties);
    }

    protected StringConstantWrapper getErrorEmptyTopicTemplate() {
        return errorEmptyTopicTemplate;
    }

    protected StringConstantWrapper getErrorInvalidInjectionTopicTemplate() {
        return errorInvalidInjectionTopicTemplate;
    }

    protected StringConstantWrapper getErrorInvalidValidationTopicTemplate() {
        return errorInvalidValidationTopicTemplate;
    }

    protected String getDefaultBuildLocale() {
        return defaultLocale;
    }

    protected Properties getConstantTranslatedStrings() {
        return constantTranslatedStrings;
    }

    protected CSDocbookBuildingOptions getBuildOptions() {
        return buildOptions;
    }

    protected void setBuildOptions(final CSDocbookBuildingOptions buildOptions) {
        this.buildOptions = buildOptions;
    }

    protected Map<String, byte[]> getOverrideFiles() {
        return overrideFiles;
    }

    protected void setOverrideFiles(final Map<String, byte[]> overrideFiles) {
        this.overrideFiles = overrideFiles;
    }

    protected InjectionOptions getInjectionOptions() {
        return injectionOptions;
    }

    protected void setInjectionOptions(final InjectionOptions injectionOptions) {
        this.injectionOptions = injectionOptions;
    }

    protected Date getBuildDate() {
        return buildDate;
    }

    protected void setBuildDate(final Date buildDate) {
        this.buildDate = buildDate;
    }

    protected String getEscapedBookTitle() {
        return escapedTitle;
    }

    protected void setEscapedBookTitle(final String escapedTitle) {
        this.escapedTitle = escapedTitle;
    }

    protected String getOriginalBookTitle() {
        return originalTitle;
    }

    protected void setOriginalBookTitle(final String originalTitle) {
        this.originalTitle = originalTitle;
    }

    protected String getOriginalBookProduct() {
        return originalProduct;
    }

    protected void setOriginalBookProduct(final String originalProduct) {
        this.originalProduct = originalProduct;
    }

    protected String getBuildLocale() {
        return locale;
    }

    protected void setBuildLocale(final String locale) {
        this.locale = locale;
    }

    protected String getOutputLocale() {
        return outputLocale;
    }

    protected void setOutputLocale(final String outputLocale) {
        this.outputLocale = outputLocale;
    }

    protected TopicErrorDatabase getTopicErrorDatabase() {
        return errorDatabase;
    }

    protected void setTopicErrorDatabase(final TopicErrorDatabase errorDatabase) {
        this.errorDatabase = errorDatabase;
    }

    protected SpecDatabase getSpecDatabase() {
        return specDatabase;
    }

    protected void setSpecDatabase(final SpecDatabase specDatabase) {
        this.specDatabase = specDatabase;
    }

    protected List<TopicImageData> getImageLocations() {
        return imageLocations;
    }

    protected void setImageLocations(final List<TopicImageData> imageLocations) {
        this.imageLocations = imageLocations;
    }

    protected boolean isTranslationBuild() {
        return translationBuild;
    }

    protected void setTranslationBuild(boolean translationBuild) {
        this.translationBuild = translationBuild;
    }

    protected ZanataDetails getZanataDetails() {
        return zanataDetails;
    }

    protected void setZanataDetails(final ZanataDetails zanataDetails) {
        this.zanataDetails = zanataDetails;
    }

    /**
     * Get the root path for the books storage.
     */
    protected String getRootBookFolder() {
        return getEscapedBookTitle() + "/";
    }

    /**
     * Get the locale path for the books storage. eg. /{@code<TITLE>}/en-US/
     */
    protected String getBookLocaleFolder() {
        return getRootBookFolder() + getOutputLocale() + "/";
    }

    /**
     * Get the path where topics are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/topics/
     */
    protected String getBookTopicsFolder() {
        return getBookLocaleFolder() + "topics/";
    }

    /**
     * Get the path where images are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/images/
     */
    protected String getBookImagesFolder() {
        return getBookLocaleFolder() + "images/";
    }

    /**
     * Get the path where generic files are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/files/
     */
    protected String getBookFilesFolder() {
        return getBookLocaleFolder() + "files/";
    }

    @Override
    public void shutdown() {
        isShuttingDown.set(true);
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    /**
     * Gets the number of warnings that occurred during the last build.
     *
     * @return The number of warnings that occurred.
     */
    public int getNumWarnings() {
        int numWarnings = 0;
        if (getTopicErrorDatabase() != null && getTopicErrorDatabase().getErrors(getBuildLocale()) != null) {
            for (final TopicErrorData errorData : getTopicErrorDatabase().getErrors(getBuildLocale())) {
                numWarnings += errorData.getItemsOfType(ErrorLevel.WARNING).size();
            }
        }
        return numWarnings;
    }

    /**
     * Gets the number of errors that occurred during the last build.
     *
     * @return The number of errors that occurred.
     */
    public int getNumErrors() {
        int numErrors = 0;
        if (getTopicErrorDatabase() != null && getTopicErrorDatabase().getErrors(getBuildLocale()) != null) {
            for (final TopicErrorData errorData : getTopicErrorDatabase().getErrors(getBuildLocale())) {
                numErrors += errorData.getItemsOfType(ErrorLevel.ERROR).size();
            }
        }
        return numErrors;
    }

    /**
     * Reset the builder so that it can build from a clean state.
     */
    protected void resetBuilder() {
        setTopicErrorDatabase(new TopicErrorDatabase());
        setSpecDatabase(new SpecDatabase());
        setImageLocations(new ArrayList<TopicImageData>());

        setEscapedBookTitle(null);
        setOriginalBookTitle(null);
        setOriginalBookProduct(null);
        setOutputLocale(null);
        setBuildLocale(null);

        setBuildDate(new Date());
        setBuildOptions(null);
        setInjectionOptions(null);
    }

    /**
     * Builds a Docbook Formatted Book using a Content Specification to define the structure and contents of the book.
     *
     * @param contentSpec     The content specification to build from.
     * @param requester       The user who requested the build.
     * @param buildingOptions The options to be used when building.
     * @return Returns a mapping of file names/locations to files. This HashMap can be used to build a ZIP archive.
     * @throws BuilderCreationException Thrown if the builder is unable to start due to incorrect passed variables.
     * @throws BuildProcessingException Any build issue that should not occur under normal circumstances. Ie a Template can't be
     *                                  converted to a DOM Document.
     */
    public HashMap<String, byte[]> buildBook(final ContentSpec contentSpec, final UserWrapper requester,
            final CSDocbookBuildingOptions buildingOptions) throws BuilderCreationException, BuildProcessingException {
        return buildBook(contentSpec, requester, buildingOptions, new ZanataDetails());
    }

    /**
     * Builds a Docbook Formatted Book using a Content Specification to define the structure and contents of the book.
     *
     * @param contentSpec     The content specification to build from.
     * @param requester       The user who requested the build.
     * @param buildingOptions The options to be used when building.
     * @param overrideFiles
     * @return Returns a mapping of file names/locations to files. This HashMap can be used to build a ZIP archive.
     * @throws BuilderCreationException Thrown if the builder is unable to start due to incorrect passed variables.
     * @throws BuildProcessingException Any build issue that should not occur under normal circumstances. Ie a Template can't be
     *                                  converted to a DOM Document.
     */
    public HashMap<String, byte[]> buildBook(final ContentSpec contentSpec, final UserWrapper requester,
            final CSDocbookBuildingOptions buildingOptions,
            final Map<String, byte[]> overrideFiles) throws BuilderCreationException, BuildProcessingException {
        return buildBook(contentSpec, requester, buildingOptions, overrideFiles, new ZanataDetails());
    }

    /**
     * Builds a Docbook Formatted Book using a Content Specification to define the structure and contents of the book.
     *
     * @param contentSpec     The content specification to build from.
     * @param requester       The user who requested the build.
     * @param buildingOptions The options to be used when building.
     * @param zanataDetails   The Zanata server details to be used when populating links
     * @return Returns a mapping of file names/locations to files. This HashMap can be used to build a ZIP archive.
     * @throws BuilderCreationException Thrown if the builder is unable to start due to incorrect passed variables.
     * @throws BuildProcessingException Any build issue that should not occur under normal circumstances. Ie a Template can't be
     *                                  converted to a DOM Document.
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, byte[]> buildBook(final ContentSpec contentSpec, final UserWrapper requester,
            final CSDocbookBuildingOptions buildingOptions,
            final ZanataDetails zanataDetails) throws BuilderCreationException, BuildProcessingException {
        return buildBook(contentSpec, requester, buildingOptions, new HashMap<String, byte[]>(), zanataDetails);
    }

    /**
     * Builds a Docbook Formatted Book using a Content Specification to define the structure and contents of the book.
     *
     * @param contentSpec     The content specification to build from.
     * @param requester       The user who requested the build.
     * @param buildingOptions The options to be used when building.
     * @param overrideFiles
     * @param zanataDetails   The Zanata server details to be used when populating links
     * @return Returns a mapping of file names/locations to files. This HashMap can be used to build a ZIP archive.
     * @throws BuilderCreationException Thrown if the builder is unable to start due to incorrect passed variables.
     * @throws BuildProcessingException Any build issue that should not occur under normal circumstances. Ie a Template can't be
     *                                  converted to a DOM Document.
     */
    @SuppressWarnings("unchecked")
    public HashMap<String, byte[]> buildBook(final ContentSpec contentSpec, final UserWrapper requester,
            final CSDocbookBuildingOptions buildingOptions, final Map<String, byte[]> overrideFiles,
            final ZanataDetails zanataDetails) throws BuilderCreationException, BuildProcessingException {
        if (contentSpec == null) {
            throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
        }

        // Reset the builder
        resetBuilder();

        setOverrideFiles(overrideFiles == null ? new HashMap<String, byte[]>() : overrideFiles);
        setZanataDetails(zanataDetails);

        if (contentSpec.getLocale() == null || contentSpec.getLocale().equals(getDefaultBuildLocale())) {
            setTranslationBuild(false);
        } else {
            setTranslationBuild(true);
        }

        // Setup the constants
        setEscapedBookTitle(DocBookUtilities.escapeTitle(contentSpec.getTitle()));
        setBuildLocale(buildingOptions.getLocale() == null ? getDefaultBuildLocale() : buildingOptions.getLocale());
        setOutputLocale(buildingOptions.getOutputLocale() == null ? getBuildLocale() : buildingOptions.getOutputLocale());

        setOriginalBookTitle(contentSpec.getTitle());
        setOriginalBookProduct(contentSpec.getProduct());

        /*
         * Apply the build options from the content spec only if the build options are true. We do this so that if the options
         * are turned off earlier then we don't re-enable them.
         */
        setBuildOptions(buildingOptions);
        if (getBuildOptions().getInsertSurveyLink()) {
            getBuildOptions().setInsertSurveyLink(contentSpec.isInjectSurveyLinks());
        }
        if (getBuildOptions().getInsertBugzillaLinks()) {
            getBuildOptions().setInsertBugzillaLinks(contentSpec.isInjectBugLinks());
        }
        if (getBuildOptions().getBuildName() == null || getBuildOptions().getBuildName().isEmpty()) {
            getBuildOptions().setBuildName(
                    (contentSpec.getId() != null ? (contentSpec.getId() + ", ") : "") + contentSpec.getTitle() + "-" + contentSpec
                            .getVersion() + "-" + contentSpec.getEdition());
        }
        if (!getBuildOptions().getDraft()) {
            if (contentSpec.getBookType() == BookType.ARTICLE_DRAFT || contentSpec.getBookType() == BookType.BOOK_DRAFT) {
                getBuildOptions().setDraft(true);
            }
        }

        // Add the options that were passed to the builder
        final InjectionOptions injectionOptions = new InjectionOptions();

        // Get the injection mode
        InjectionOptions.UserType injectionType = InjectionOptions.UserType.NONE;
        final Boolean injection = buildingOptions.getInjection();
        if (injection != null && !injection) {
            injectionType = InjectionOptions.UserType.OFF;
        } else if (injection != null && injection) {
            injectionType = InjectionOptions.UserType.ON;
        }

        // Add the strict injection types
        if (buildingOptions.getInjectionTypes() != null) {
            for (final String injectType : buildingOptions.getInjectionTypes()) {
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

        // Set the injection options
        setInjectionOptions(injectionOptions);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        // Get the translations
        if (buildingOptions.getLocale() != null) {
            pullTranslations(contentSpec, buildingOptions.getLocale());
        }
        loadConstantTranslations(getBuildLocale());

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        final Map<Integer, Set<String>> usedIdAttributes = new HashMap<Integer, Set<String>>();
        final boolean fixedUrlsSuccess = doPopulateDatabasePass(contentSpec, usedIdAttributes);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        /*
         * We need to create a list of all id's in the book to check if links are valid. So generate the id attribute that are
         * used by topics, section and chapters. Then add any id's that were found in the topics.
         */
        final Set<String> bookIdAttributes = getSpecDatabase().getIdAttributes(fixedUrlsSuccess);
        for (final Entry<Integer, Set<String>> entry : usedIdAttributes.entrySet()) {
            bookIdAttributes.addAll(entry.getValue());
        }
        validateTopicLinks(bookIdAttributes, fixedUrlsSuccess);

        // second topic pass to set the ids and process injections
        doSpecTopicPass(contentSpec, usedIdAttributes, fixedUrlsSuccess, BuilderConstants.BUILD_NAME);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        /* Process the images in the topics */
        processImageLocations();

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        return doBuildZipPass(contentSpec, requester, fixedUrlsSuccess);
    }

    /**
     * Load the Constant Translated Strings from the local properties file.
     *
     * @param locale The locale the book is being built in.
     */
    protected void loadConstantTranslations(final String locale) {
        // Load the translated properties
        final URL url = ClassLoader.getSystemResource("Constants-" + locale + ".properties");
        if (url != null) {
            try {
                getConstantTranslatedStrings().load(new InputStreamReader(url.openStream(), ENCODING));
            } catch (IOException ex) {
                log.debug(ex);
            }
        }
    }

    /**
     * Get the translations from the REST API and replace the original strings with the values downloaded.
     *
     * @param contentSpec The Content Spec to get and replace the translations for.
     * @param locale      The locale to pull the translations for.
     */
    protected void pullTranslations(final ContentSpec contentSpec, final String locale) {
        // TODO
        /*final CollectionWrapper<TranslatedTopicStringWrapper> translatedStrings = EntityUtilities.getTranslatedTopicStringsByTopicId(
                providerFactory, contentSpec.getId(), contentSpec.getRevision(), locale);

        if (translatedStrings != null && translatedStrings.getItems() != null) {
            final Map<String, String> translations = new HashMap<String, String>();

            final List<TranslatedTopicStringWrapper> translatedTopicStringItems = translatedStrings.getItems();
            for (final TranslatedTopicStringWrapper translatedTopicString : translatedTopicStringItems) {
                if (translatedTopicString.getOriginalString() != null && translatedTopicString.getTranslatedString() != null)
                    translations.put(translatedTopicString.getOriginalString(), translatedTopicString.getTranslatedString());
            }

            ContentSpecUtilities.replaceTranslatedStrings(contentSpec, translations);
        }*/
    }

    /**
     * Validate all the book links in the each topic to ensure that they exist somewhere in the book. If they don't then the
     * topic XML is replaced with a generic error template.
     *
     * @param bookIdAttributes A set of all the id's that exist in the book.
     * @param useFixedUrls     Whether or not the fixed urls should be used for topic ID's.
     * @throws BuildProcessingException TODO
     */
    @SuppressWarnings("unchecked")
    protected void validateTopicLinks(final Set<String> bookIdAttributes, final boolean useFixedUrls) throws BuildProcessingException {
        log.info("Doing " + getBuildLocale() + " Topic Link Pass");

        final List<SpecTopic> topics = getSpecDatabase().getAllSpecTopics();
        final Set<Integer> processedTopics = new HashSet<Integer>();
        for (final SpecTopic specTopic : topics) {
            final BaseTopicWrapper<?> topic = specTopic.getTopic();
            final Document doc = specTopic.getXmlDocument();

            /*
             * We only to to process topics at this point and not spec topics. So check to see if the topic has all ready been
             * processed.
             */
            if (!processedTopics.contains(topic.getId())) {
                processedTopics.add(topic.getId());

                /* Get the XRef links in the topic document */
                final Set<String> linkIds = new HashSet<String>();
                DocbookBuildUtilities.getTopicLinkIds(doc, linkIds);

                final List<String> invalidLinks = new ArrayList<String>();

                for (final String linkId : linkIds) {
                    /*
                     * Check if the xref linkend id exists in the book. If the Tag Starts with our error syntax then we can
                     * ignore it
                     */
                    if (!bookIdAttributes.contains(linkId) && !linkId.startsWith(CommonConstants.ERROR_XREF_ID_PREFIX)) {
                        invalidLinks.add("\"" + linkId + "\"");
                    }
                }

                // If there were any invalid links then replace the XML with an error template and add an error message.
                if (!invalidLinks.isEmpty()) {
                    final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                            getErrorInvalidValidationTopicTemplate().getValue(), getBuildOptions());

                    final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(
                            XMLUtilities.convertNodeToString(doc, verbatimElements, inlineElements, contentsInlineElements, true));
                    getTopicErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                            "The following link(s) " + CollectionUtilities.toSeperatedString(invalidLinks,
                                    ",") + " don't exist. The processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");

                    /* Find the Topic ID */
                    final Integer topicId = topic.getTopicId();

                    final List<SpecTopic> specTopics = getSpecDatabase().getSpecTopicsForTopicID(topicId);
                    for (final SpecTopic spec : specTopics) {
                        setSpecTopicXMLForError(spec, topicXMLErrorTemplate, useFixedUrls);
                    }
                }
            }
        }
    }

    /**
     * Populates the SpecTopicDatabase with the SpecTopics inside the content specification. It also adds the equivalent real
     * topics to each SpecTopic.
     *
     * @param contentSpec      The content spec to populate the database from.
     * @param usedIdAttributes The set of Used ID Attributes that should be added to.
     * @return True if the database was populated successfully otherwise false.
     * @throws BuildProcessingException TODO
     */
    @SuppressWarnings("unchecked")
    private boolean doPopulateDatabasePass(final ContentSpec contentSpec,
            final Map<Integer, Set<String>> usedIdAttributes) throws BuildProcessingException {
        log.info("Doing " + getBuildLocale() + " Populate Database Pass");

        /* Calculate the ids of all the topics to get */
        final Set<Pair<Integer, Integer>> topicToRevisions = getTopicIdsFromLevel(contentSpec.getBaseLevel());

        /*
         * Determine which topics we need to fetch the latest topics for and which topics we need to fetch revisions for.
         */
        final Set<Integer> topicIds = new HashSet<Integer>();
        final Set<Pair<Integer, Integer>> topicRevisions = new HashSet<Pair<Integer, Integer>>();
        for (final Pair<Integer, Integer> topicToRevision : topicToRevisions) {
            if (topicToRevision.getSecond() != null && !getBuildOptions().getUseLatestVersions()) {
                topicRevisions.add(topicToRevision);
            } else {
                topicIds.add(topicToRevision.getFirst());
            }
        }

        final CollectionWrapper topics;
        final boolean fixedUrlsSuccess;
        if (contentSpec.getLocale() == null || contentSpec.getLocale().equals(getDefaultBuildLocale())) {
            final CollectionWrapper<TopicWrapper> allTopics = topicProvider.newTopicCollection();
            final CollectionWrapper<TopicWrapper> revisionTopics = topicProvider.newTopicCollection();

            /* Ensure that the collection doesn't equal null */
            final CollectionWrapper<TopicWrapper> latestTopics = topicProvider.getTopics(CollectionUtilities.toArrayList(topicIds));

            /* Add any latest topics to the "all" topic collection */
            if (latestTopics != null) {
                final List<TopicWrapper> topicItems = latestTopics.getItems();
                for (final TopicWrapper topic : topicItems) {
                    allTopics.addItem(topic);
                }
            }

            /*
             * Fetch each topic that is a revision separately since this functionality isn't offered by the REST API.
             */
            for (final Pair<Integer, Integer> topicToRevision : topicRevisions) {
                final TopicWrapper topicRevision = topicProvider.getTopic(topicToRevision.getFirst(), topicToRevision.getSecond());
                if (topicRevision != null) {
                    allTopics.addItem(topicRevision);
                    revisionTopics.addItem(topicRevision);
                }
            }

            final Set<String> processedFileNames = new HashSet<String>();
            if (latestTopics != null && latestTopics.getItems() != null) {
                /*
                 * assign fixed urls property tags to the topics. If fixedUrlsSuccess is true, the id of the topic sections,
                 * xref injection points and file names in the zip file will be taken from the fixed url property tag,
                 * defaulting back to the TopicID## format if for some reason that property tag does not exist.
                 */
                fixedUrlsSuccess = setFixedURLsPass(latestTopics, processedFileNames);
            } else {
                fixedUrlsSuccess = true;
            }

            /* Ensure that our revision topics FixedURLs are still valid */
            setFixedURLsForRevisionsPass(revisionTopics, processedFileNames);

            topics = allTopics;
        } else {
            /*
             * Translations should reference an existing historical topic with the fixed urls set, so we assume this to be the
             * case
             */
            fixedUrlsSuccess = true;

            /* set the topics variable now all initialisation is done */
            topics = getTranslatedTopics(topicIds, topicRevisions);

            /* Ensure that our translated topics FixedURLs are still valid */
            final Set<String> processedFileNames = new HashSet<String>();
            setFixedURLsForRevisionsPass(topics, processedFileNames);
        }

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return false;
        }

        /* Add all the levels and topics to the database first */
        addLevelAndTopicsToDatabase(contentSpec.getBaseLevel(), fixedUrlsSuccess);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return false;
        }

        /* Pass the topics to make sure they are valid */
        doTopicPass(topics, fixedUrlsSuccess, usedIdAttributes);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return false;
        }

        /* Set the duplicate id's for each topic */
        getSpecDatabase().setDatabaseDulicateIds(usedIdAttributes);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return false;
        }

        return fixedUrlsSuccess;
    }

    /**
     * Gets the translated topics from the REST Interface and also creates any dummy translations for topics that have yet to be
     * translated.
     *
     * @param topicIds       A Set of topic id's that are to be used to get the latest translations.
     * @param topicRevisions A Set of topic id's to revisions, used to get translations closest to specific revisions.
     * @return A collection of TranslatedTopics or null if a shutdown was requested.
     */
    private CollectionWrapper<TranslatedTopicWrapper> getTranslatedTopics(final Set<Integer> topicIds,
            final Set<Pair<Integer, Integer>> topicRevisions) {
        final CollectionWrapper<TranslatedTopicWrapper> translatedTopics = translatedTopicProvider.newTranslatedTopicCollection();

        /* Ensure that the collection doesn't equal null */
        final CollectionWrapper<TopicWrapper> topicCollection = topicProvider.getTopics(CollectionUtilities.toArrayList(topicIds));

        /*
         * Populate the dummy topic ids using the latest topics. We will remove the topics that exist a little later.
         */
        final Set<Integer> dummyTopicIds = new HashSet<Integer>(topicIds);

        /* Remove any topic ids for translated topics that were found */
        if (topicCollection != null && topicCollection.getItems() != null) {
            final List<TopicWrapper> topics = topicCollection.getItems();
            for (final TopicWrapper topic : topics) {
                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return null;
                }

                // Get the matching latest translated topic and pushed translated topics
                final Pair<TranslatedTopicWrapper, TranslatedTopicWrapper> lastestTranslations = getLatestTranslations(topic, null);
                final TranslatedTopicWrapper latestTranslatedTopic = lastestTranslations.getFirst();
                final TranslatedTopicWrapper latestPushedTranslatedTopic = lastestTranslations.getSecond();

                // If the latest translation and latest pushed topic matches, then use that if not a dummy topic should be
                // created
                if (latestTranslatedTopic != null && latestPushedTranslatedTopic != null && latestPushedTranslatedTopic.getTopicRevision
                        ().equals(
                        latestTranslatedTopic.getTopicRevision())) {
                    final TranslatedTopicWrapper translatedTopic = translatedTopicProvider.getTranslatedTopic(
                            latestTranslatedTopic.getId());
                    if (translatedTopic != null) {
                        dummyTopicIds.remove(topic.getId());
                        translatedTopics.addItem(translatedTopic);
                    }
                }
            }
        }

        final Set<Pair<Integer, Integer>> dummyTopicRevisionIds = new HashSet<Pair<Integer, Integer>>();
        if (topicRevisions != null) {
            /*
             * Fetch each topic that is a revision separately since this functionality isn't offered by the REST API.
             */
            for (final Pair<Integer, Integer> topicToRevision : topicRevisions) {
                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return null;
                }

                final TopicWrapper topic = topicProvider.getTopic(topicToRevision.getFirst(), topicToRevision.getSecond());

                if (topic != null) {
                    // Get the matching latest translated topic and pushed translated topics
                    final Pair<TranslatedTopicWrapper, TranslatedTopicWrapper> lastestTranslations = getLatestTranslations(topic,
                            topicToRevision.getSecond());
                    final TranslatedTopicWrapper latestTranslatedTopic = lastestTranslations.getFirst();
                    final TranslatedTopicWrapper latestPushedTranslatedTopic = lastestTranslations.getSecond();

                    // If the latest translation and latest pushed topic matches, then use that if not a dummy topic should be
                    // created
                    if (latestTranslatedTopic != null && latestPushedTranslatedTopic != null && latestPushedTranslatedTopic
                            .getTopicRevision().equals(
                            latestTranslatedTopic.getTopicRevision())) {
                        final TranslatedTopicWrapper translatedTopic = translatedTopicProvider.getTranslatedTopic(
                                latestTranslatedTopic.getId());
                        if (translatedTopic != null) {
                            translatedTopics.addItem(translatedTopic);
                        } else {
                            dummyTopicRevisionIds.add(topicToRevision);
                        }
                    } else {
                        dummyTopicRevisionIds.add(topicToRevision);
                    }
                } else {
                    dummyTopicRevisionIds.add(topicToRevision);
                }
            }
        }

        /* Create the dummy translated topics */
        if (!dummyTopicIds.isEmpty() || !dummyTopicRevisionIds.isEmpty()) {
            populateDummyTranslatedTopicsPass(translatedTopics, dummyTopicIds, dummyTopicRevisionIds);
        }

        return translatedTopics;
    }

    /**
     * Find the latest pushed and translated topics for a topic. We need to do this since translations are only added when some
     * content is added in Zanata. So if the latest translated topic doesn't match the topic revision of the latest pushed then
     * we will need to create a dummy topic for the latest pushed topic.
     * 
     * @param topic The topic to find the latest translated topic and pushed translation.
     * @param rev The revision for the topic as specified in the ContentSpec.
     * @return A Pair whose first element is the Latest Translated Topic and second element is the Latest Pushed Translation.
     */
    private Pair<TranslatedTopicWrapper, TranslatedTopicWrapper> getLatestTranslations(final TopicWrapper topic, final Integer rev) {
        TranslatedTopicWrapper latestTranslatedTopic = null;
        TranslatedTopicWrapper latestPushedTranslatedTopic = null;
        if (topic.getTranslatedTopics() != null && topic.getTranslatedTopics().getItems() != null) {
            final List<TranslatedTopicWrapper> topics = topic.getTranslatedTopics().getItems();
            for (final TranslatedTopicWrapper tempTopic : topics) {
                // Find the Latest Translated Topic
                if (getBuildLocale().equals(
                        tempTopic.getLocale()) && (latestTranslatedTopic == null || latestTranslatedTopic.getTopicRevision() < tempTopic
                        .getTopicRevision()) && (rev == null || tempTopic.getTopicRevision() <= rev)) {
                    latestTranslatedTopic = tempTopic;
                }

                // Find the Latest Pushed Topic
                if (topic.getLocale().equals(
                        tempTopic.getLocale()) && (latestPushedTranslatedTopic == null || latestPushedTranslatedTopic.getTopicRevision()
                        < tempTopic.getTopicRevision()) && (rev == null || tempTopic.getTopicRevision() <= rev)) {
                    latestPushedTranslatedTopic = tempTopic;
                }
            }
        }

        return new Pair<TranslatedTopicWrapper, TranslatedTopicWrapper>(latestTranslatedTopic, latestPushedTranslatedTopic);
    }

    /**
     * Adds the levels and topics in the provided Level object to the local content spec database.
     *
     * @param level        The content spec level to be added to the database.
     * @param useFixedUrls Whether fixed URL's are to be used for the level ID attributes.
     */
    private void addLevelAndTopicsToDatabase(final Level level, final boolean useFixedUrls) {
        /* Add the level to the database */
        getSpecDatabase().add(level, DocbookBuildUtilities.createURLTitle(level.getTitle()));

        /* Add the topics at this level to the database */
        for (final SpecTopic specTopic : level.getSpecTopics()) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            getSpecDatabase().add(specTopic, specTopic.getUniqueLinkId(useFixedUrls));
        }

        /* Add the child levels to the database */
        for (final Level childLevel : level.getChildLevels()) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            addLevelAndTopicsToDatabase(childLevel, useFixedUrls);
        }
    }

    /**
     * Gets a Set of Topic ID's to Revisions from the content specification level for each Spec Topic.
     *
     * @param level The level to scan for topics.
     * @return A Set of Topic ID/Revision Pairs that represent the topics in the level.
     */
    private Set<Pair<Integer, Integer>> getTopicIdsFromLevel(final Level level) {
        /* Add the topics at this level to the database */
        final Set<Pair<Integer, Integer>> topicIds = new HashSet<Pair<Integer, Integer>>();
        for (final SpecTopic specTopic : level.getSpecTopics()) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return topicIds;
            }

            if (specTopic.getDBId() != 0) {
                topicIds.add(new Pair<Integer, Integer>(specTopic.getDBId(), specTopic.getRevision()));
            }
        }

        /* Add the child levels to the database */
        for (final Level childLevel : level.getChildLevels()) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return topicIds;
            }

            topicIds.addAll(getTopicIdsFromLevel(childLevel));
        }

        return topicIds;
    }

    /**
     * Populates the topics with a set of dummy topics as specified by the dummyTopicIds list.
     *
     * @param translatedTopics      The set of topics to add the dummy translated topics to.
     * @param dummyTopicIds         The list of topics to be added as dummy translated topics.
     * @param dummyRevisionTopicIds The list of revision topics to be added as dummy translated topics.
     */
    private void populateDummyTranslatedTopicsPass(final CollectionWrapper<TranslatedTopicWrapper> translatedTopics,
            final Set<Integer> dummyTopicIds, Set<Pair<Integer, Integer>> dummyRevisionTopicIds) {
        log.info("\tDoing dummy Translated Topic pass");

        final CollectionWrapper<TopicWrapper> dummyTopics;

        CollectionWrapper<TopicWrapper> tempCollection = topicProvider.getTopics(CollectionUtilities.toArrayList(dummyTopicIds));
        if (tempCollection == null) {
            dummyTopics = topicProvider.newTopicCollection();
        } else {
            dummyTopics = tempCollection;
        }

        /* Add any revision topics */
        for (final Pair<Integer, Integer> topicToRevision : dummyRevisionTopicIds) {
            final TopicWrapper topic = topicProvider.getTopic(topicToRevision.getFirst(), topicToRevision.getSecond());
            if (topic != null) {
                dummyTopics.addItem(topic);
            }
        }

        /* Only continue if we found dummy topics */
        if (dummyTopics == null || dummyTopics.getItems() == null || dummyTopics.getItems().isEmpty()) {
            return;
        }

        /* Split the topics up into their different locales */
        final Map<Integer, TranslatedTopicWrapper> translatedTopicsMap = new HashMap<Integer, TranslatedTopicWrapper>();

        if (translatedTopics != null && translatedTopics.getItems() != null) {
            final List<TranslatedTopicWrapper> translatedTopicsList = translatedTopics.getItems();
            for (final TranslatedTopicWrapper topic : translatedTopicsList) {
                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return;
                }

                translatedTopicsMap.put(topic.getTopicId(), topic);
            }
        }

        /* create and add the dummy topics */
        final List<TopicWrapper> topicItems = dummyTopics.getItems();
        for (final TopicWrapper topic : topicItems) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            if (!translatedTopicsMap.containsKey(topic.getId())) {
                final TranslatedTopicWrapper dummyTopic = createDummyTranslatedTopic(translatedTopicsMap, topic, getBuildLocale());

                translatedTopics.addItem(dummyTopic);
            }
        }
    }

    /**
     * Creates a dummy translated topic so that a book can be built using the same relationships as a normal build.
     *
     * @param translatedTopicsMap A map of topic ids to translated topics.
     * @param topic               The topic to create the dummy topic from.
     * @param locale              The locale to build the dummy translations for.
     * @return The dummy translated topic.
     */
    private TranslatedTopicWrapper createDummyTranslatedTopic(final Map<Integer, TranslatedTopicWrapper> translatedTopicsMap,
            final TopicWrapper topic, final String locale) {
        final TranslatedTopicWrapper translatedTopic = translatedTopicProvider.newTranslatedTopic();
        translatedTopic.setTopic(topic);
        translatedTopic.setId(topic.getId() * -1);

        final TranslatedTopicWrapper pushedTranslatedTopic = EntityUtilities.returnPushedTranslatedTopic(translatedTopic);

        /*
         * Try and use the untranslated default locale translated topic as the base for the dummy topic. If that fails then
         * create a dummy topic from the passed RESTTopicV1.
         */
        if (pushedTranslatedTopic != null) {
            final TranslatedTopicWrapper defaultLocaleTranslatedTopic = translatedTopicProvider.getTranslatedTopic(
                    pushedTranslatedTopic.getId());

            if (defaultLocaleTranslatedTopic != null) {
                /* Negate the ID to show it isn't a proper translated topic */
                defaultLocaleTranslatedTopic.setId(topic.getId() * -1);

                /* prefix the locale to show that it is missing the related translated topic */
                defaultLocaleTranslatedTopic.setTitle(
                        "[" + defaultLocaleTranslatedTopic.getLocale() + "] " + defaultLocaleTranslatedTopic.getTitle());

                /* Change the locale since the default locale translation is being transformed into a dummy translation */
                defaultLocaleTranslatedTopic.setLocale(locale);

                return defaultLocaleTranslatedTopic;
            }
        }

        /*
         * If we get to this point then no translation exists or the default locale translation failed to be downloaded.
         */
        translatedTopic.setTopicId(topic.getId());
        translatedTopic.setTopicRevision(topic.getRevision());
        translatedTopic.setTranslationPercentage(100);
        translatedTopic.setXml(topic.getXml());
        translatedTopic.setTags(topic.getTags());
        translatedTopic.setSourceURLs(topic.getSourceURLs());
        translatedTopic.setProperties(topic.getProperties());
        translatedTopic.setLocale(locale);

        /* prefix the locale to show that it is missing the related translated topic */
        translatedTopic.setTitle("[" + topic.getLocale() + "] " + topic.getTitle());

        /* Add the dummy outgoing relationships */
        if (topic.getOutgoingRelationships() != null && topic.getOutgoingRelationships().getItems() != null) {
            final CollectionWrapper<TranslatedTopicWrapper> outgoingRelationships = translatedTopicProvider.newTranslatedTopicCollection();
            final List<TopicWrapper> relatedTopics = topic.getOutgoingRelationships().getItems();
            for (final TopicWrapper relatedTopic : relatedTopics) {
                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return translatedTopic;
                }

                /* check to see if the translated topic already exists */
                if (translatedTopicsMap.containsKey(relatedTopic.getId())) {
                    outgoingRelationships.addItem(translatedTopicsMap.get(relatedTopic.getId()));
                } else {
                    outgoingRelationships.addItem(createDummyTranslatedTopic(translatedTopicsMap, relatedTopic, locale));
                }
            }
            translatedTopic.setOutgoingRelationships(outgoingRelationships);
        }

        /* Add the dummy incoming relationships */
        if (topic.getIncomingRelationships() != null && topic.getIncomingRelationships().getItems() != null) {
            final CollectionWrapper<TranslatedTopicWrapper> incomingRelationships = translatedTopicProvider.newTranslatedTopicCollection();
            final List<TopicWrapper> relatedTopics = topic.getIncomingRelationships().getItems();
            for (final TopicWrapper relatedTopic : relatedTopics) {
                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return translatedTopic;
                }

                /* check to see if the translated topic already exists */
                if (translatedTopicsMap.containsKey(relatedTopic.getId())) {
                    incomingRelationships.addItem(translatedTopicsMap.get(relatedTopic.getId()));
                } else {
                    incomingRelationships.addItem(createDummyTranslatedTopic(translatedTopicsMap, relatedTopic, locale));
                }
            }
            translatedTopic.setIncomingRelationships(incomingRelationships);
        }

        return translatedTopic;
    }

    /**
     * Do the first topic pass on the database and check if the base XML is valid and set the Document Object's for each spec
     * topic. Also collect the ID Attributes that are used within the topics.
     *
     * @param topics           The list of topics to be checked and added to the database.
     * @param useFixedUrls     Whether the Fixed URL Properties should be used for the topic ID attributes.
     * @param usedIdAttributes The set of Used ID Attributes that should be added to.
     * @throws BuildProcessingException
     */
    private <T extends BaseTopicWrapper<T>> void doTopicPass(final CollectionWrapper<T> topics, final boolean useFixedUrls,
            final Map<Integer, Set<String>> usedIdAttributes) throws BuildProcessingException {
        log.info("Doing " + getBuildLocale() + " First topic pass");

        /* Check that we have some topics to process */
        if (topics != null && topics.getItems() != null) {
            log.info("\tProcessing " + topics.getItems().size() + " Topics");

            final int showPercent = 5;
            final float total = topics.getItems().size();
            float current = 0;
            int lastPercent = 0;

            /* Process each topic */
            final List<T> topicItems = topics.getItems();
            for (final T topic : topicItems) {
                ++current;
                final int percent = Math.round(current / total * 100);
                if (percent - lastPercent >= showPercent) {
                    lastPercent = percent;
                    log.info("\tFirst topic Pass " + percent + "% Done");
                }

                /* Get the Topic ID */
                final Integer topicId = topic.getTopicId();
                final Integer topicRevision = topic.getTopicRevision();

                Document topicDoc = null;
                final String topicXML = topic.getXml();

                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return;
                }

                boolean xmlValid = true;

                // Check that the Topic XML exists and isn't empty
                if (topicXML == null || topicXML.equals("")) {
                    // Create an empty topic with the topic title from the resource file
                    final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                            getErrorEmptyTopicTemplate().getValue(), getBuildOptions());

                    getTopicErrorDatabase().addWarning(topic, ErrorType.NO_CONTENT, BuilderConstants.WARNING_EMPTY_TOPIC_XML);
                    topicDoc = setTopicXMLForError(topic, topicXMLErrorTemplate, useFixedUrls);
                    xmlValid = false;
                }

                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return;
                }

                /* make sure we have valid XML */
                if (xmlValid) {
                    try {
                        topicDoc = XMLUtilities.convertStringToDocument(topic.getXml());

                        if (topicDoc != null) {
                            /* Ensure the topic is wrapped in a section and the title matches the topic */
                            DocBookUtilities.wrapDocumentInSection(topicDoc);
                            DocBookUtilities.setSectionTitle(topic.getTitle(), topicDoc);
                        } else {
                            final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                                    getErrorInvalidValidationTopicTemplate().getValue(), getBuildOptions());
                            final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(topic.getXml());
                            getTopicErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                                    BuilderConstants.ERROR_INVALID_XML_CONTENT + " The processed XML is <programlisting>" +
                                            xmlStringInCDATA + "</programlisting>");
                            topicDoc = setTopicXMLForError(topic, topicXMLErrorTemplate, useFixedUrls);
                        }
                    } catch (SAXException ex) {
                        final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                                getErrorInvalidValidationTopicTemplate().getValue(), getBuildOptions());
                        final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(topic.getXml());
                        getTopicErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                                BuilderConstants.ERROR_BAD_XML_STRUCTURE + " " + StringUtilities.escapeForXML(
                                        ex.getMessage()) + " The processed XML is <programlisting>" + xmlStringInCDATA +
                                        "</programlisting>");
                        topicDoc = setTopicXMLForError(topic, topicXMLErrorTemplate, useFixedUrls);
                    }
                }

                /*
                 * Extract the id attributes used in this topic. We'll use this data in the second pass to make sure that
                 * individual topics don't repeat id attributes.
                 */
                collectIdAttributes(topicId, topicDoc, usedIdAttributes);

                processTopicSectionInfo(topic, topicDoc);

                processTopicID(topic, topicDoc, useFixedUrls);

                /* Add the document & topic to the database spec topics */
                final List<SpecTopic> specTopics = getSpecDatabase().getSpecTopicsForTopicID(topicId);
                for (final SpecTopic specTopic : specTopics) {
                    // Check if the app should be shutdown
                    if (isShuttingDown.get()) {
                        return;
                    }

                    if (getBuildOptions().getUseLatestVersions()) {
                        specTopic.setTopic(topic.clone(false));
                        specTopic.setXmlDocument((Document) topicDoc.cloneNode(true));
                    } else {
                        /*
                         * Only set the topic for the spec topic if it matches the spec topic revision. If the Spec Topic
                         * Revision is null then we need to ensure that we get the latest version of the topic that was
                         * downloaded.
                         */
                        if ((specTopic.getRevision() == null && (specTopic.getTopic() == null || specTopic.getTopic().getRevision() >=
                                topicRevision)) || (specTopic.getRevision() != null && specTopic.getRevision() >= topicRevision)) {
                            specTopic.setTopic(topic.clone(false));
                            specTopic.setXmlDocument((Document) topicDoc.cloneNode(true));
                        }
                    }
                }

            }
        } else {
            log.info("\tProcessing 0 Topics");
        }
    }

    /**
     * Loops through each of the spec topics in the database and sets the injections and unique ids for each id attribute in the
     * Topics XML.
     *
     * @param contentSpec      The content specification used to build the book.
     * @param usedIdAttributes The set of ids that have been used in the set of topics in the content spec.
     * @param useFixedUrls     If during processing the fixed urls should be used.
     * @param buildName        A specific name for the build to be used in bug links.
     * @throws BuildProcessingException
     */
    @SuppressWarnings("unchecked")
    private <T extends BaseTopicWrapper<T>> void doSpecTopicPass(final ContentSpec contentSpec,
            final Map<Integer, Set<String>> usedIdAttributes, final boolean useFixedUrls,
            final String buildName) throws BuildProcessingException {
        log.info("Doing " + getBuildLocale() + " Spec Topic Pass");
        final List<SpecTopic> specTopics = getSpecDatabase().getAllSpecTopics();

        log.info("\tProcessing " + specTopics.size() + " Spec Topics");

        final int showPercent = 5;
        final float total = specTopics.size();
        float current = 0;
        int lastPercent = 0;

        /* Create the related topics database to be used for CSP builds */
        final TocTopicDatabase relatedTopicsDatabase = new TocTopicDatabase();
        final List<T> topics = getSpecDatabase().getAllTopics(true);
        relatedTopicsDatabase.setTopics(topics);

        for (final SpecTopic specTopic : specTopics) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            if (log.isDebugEnabled())
                log.debug("\tProcessing SpecTopic " + specTopic.getId()
                        + (specTopic.getRevision() != null ? (", Revision " + specTopic.getRevision()) : ""));

            ++current;
            final int percent = Math.round(current / total * 100);
            if (percent - lastPercent >= showPercent) {
                lastPercent = percent;
                log.info("\tProcessing Pass " + percent + "% Done");
            }

            final BaseTopicWrapper<?> topic = specTopic.getTopic();
            final Document doc = specTopic.getXmlDocument();

            assert doc != null;
            assert topic != null;

            final DocbookXMLPreProcessor xmlPreProcessor = new DocbookXMLPreProcessor(getConstantTranslatedStrings());

            if (doc != null) {
                /* process the conditional statements */
                if (!contentSpec.getPublicanCfg().contains("condition:")) {
                    final String condition = specTopic.getConditionStatement(true);
                    DocBookUtilities.processConditions(condition, doc, BuilderConstants.DEFAULT_CONDITION);
                }

                final boolean valid = processSpecTopicInjections(contentSpec, specTopic, xmlPreProcessor,
                        relatedTopicsDatabase, useFixedUrls);

                /*
                 * If the topic is a translated topic then check to see if the translated topic hasn't been pushed for
                 * translation, is untranslated, has incomplete translations or contains fuzzy text.
                 */
                if (topic instanceof TranslatedTopicWrapper) {
                    /* Check the topic itself isn't a dummy topic */
                    if (EntityUtilities.isDummyTopic(topic) && EntityUtilities.hasBeenPushedForTranslation(
                            (TranslatedTopicWrapper) topic)) {
                        getTopicErrorDatabase().addWarning(topic, ErrorType.UNTRANSLATED, BuilderConstants.WARNING_UNTRANSLATED_TOPIC);
                    } else if (EntityUtilities.isDummyTopic(topic)) {
                        getTopicErrorDatabase().addWarning(topic, ErrorType.NOT_PUSHED_FOR_TRANSLATION,
                                BuilderConstants.WARNING_NONPUSHED_TOPIC);
                    } else {
                        /* Check if the topic's content isn't fully translated */
                        if (((TranslatedTopicWrapper) topic).getTranslationPercentage() < 100) {
                            getTopicErrorDatabase().addWarning(topic, ErrorType.INCOMPLETE_TRANSLATION,
                                    BuilderConstants.WARNING_INCOMPLETE_TRANSLATION);
                        }

                        if (((TranslatedTopicWrapper) topic).getContainsFuzzyTranslations()) {
                            getTopicErrorDatabase().addWarning(topic, ErrorType.FUZZY_TRANSLATION,
                                    BuilderConstants.WARNING_FUZZY_TRANSLATION);
                        }
                    }
                }

                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return;
                }

                if (!valid) {
                    final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                            getErrorInvalidInjectionTopicTemplate().getValue(), getBuildOptions());

                    final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(
                            XMLUtilities.convertNodeToString(doc, verbatimElements, inlineElements, contentsInlineElements, true));
                    getTopicErrorDatabase().addError(topic,
                            BuilderConstants.ERROR_INVALID_INJECTIONS + " The processed XML is <programlisting>" + xmlStringInCDATA +
                                    "</programlisting>");

                    setSpecTopicXMLForError(specTopic, topicXMLErrorTemplate, useFixedUrls);
                } else {
                    /* add the standard boilerplate xml */
                    xmlPreProcessor.processTopicAdditionalInfo(specTopic, doc, contentSpec.getBugzillaOptions(), getBuildOptions(),
                            buildName, getBuildDate(), getZanataDetails());

                    /*
                     * make sure the XML is valid docbook after the standard processing has been done
                     */
                    validateTopicXML(specTopic, doc, useFixedUrls);
                }

                /*
                 * Check to see if the translated topic revision is an older topic than the topic revision specified in the map
                 */
                if (topic instanceof TranslatedTopicWrapper) {
                    final TranslatedTopicWrapper pushedTranslatedTopic = EntityUtilities.returnPushedTranslatedTopic(
                            (TranslatedTopicWrapper) topic);
                    if (pushedTranslatedTopic != null && specTopic.getRevision() != null && !pushedTranslatedTopic.getTopicRevision()
                            .equals(specTopic.getRevision())) {
                        if (EntityUtilities.isDummyTopic(topic)) {
                            getTopicErrorDatabase().addWarning((T) topic, ErrorType.OLD_UNTRANSLATED,
                                    BuilderConstants.WARNING_OLD_UNTRANSLATED_TOPIC);
                        } else {
                            getTopicErrorDatabase().addWarning((T) topic, ErrorType.OLD_TRANSLATION,
                                    BuilderConstants.WARNING_OLD_TRANSLATED_TOPIC);
                        }
                    }
                }

                /*
                 * Ensure that all of the id attributes are valid by setting any duplicates with a post fixed number.
                 */
                DocbookBuildUtilities.setUniqueIds(specTopic, specTopic.getXmlDocument(), specTopic.getXmlDocument(), usedIdAttributes);
            }
        }
    }

    /**
     * Process the Injections for a SpecTopic and add any errors to the error database.
     *
     * @param contentSpec           The Content Spec being used to build the book.
     * @param specTopic             The Spec Topic to do injection processing on.
     * @param xmlPreProcessor       The XML Processor to use for Injections.
     * @param relatedTopicsDatabase The Database of Related Topics.
     * @param useFixedUrls          If during processing the fixed urls should be used.
     * @return True if no errors occurred or if the build is set to ignore missing injections, otherwise false.
     */
    @SuppressWarnings("unchecked")
    protected boolean processSpecTopicInjections(final ContentSpec contentSpec, final SpecTopic specTopic,
            final DocbookXMLPreProcessor xmlPreProcessor, final TocTopicDatabase relatedTopicsDatabase, final boolean useFixedUrls) {
        final BaseTopicWrapper<?> topic = specTopic.getTopic();
        final Document doc = specTopic.getXmlDocument();
        final Level baseLevel = contentSpec.getBaseLevel();
        boolean valid = true;

        /* process the injection points */
        if (getInjectionOptions().isInjectionAllowed()) {

            final ArrayList<Integer> customInjectionIds = new ArrayList<Integer>();
            final List<Integer> genericInjectionErrors;
            final List<Integer> customInjectionErrors;

            if (contentSpec.getOutputStyle().equalsIgnoreCase(CSConstants.PRESSGANG_OUTPUT_FORMAT)) {
                /*
                 * create a collection of the tags that make up the topics types that will be included in generic injection
                 * points
                 */
                final List<Pair<Integer, String>> topicTypeTagDetails = new ArrayList<Pair<Integer, String>>();
                topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.TASK_TAG_ID, DocbookBuilderConstants.TASK_TAG_NAME));
                topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.REFERENCE_TAG_ID, DocbookBuilderConstants.REFERENCE_TAG_NAME));
                topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.CONCEPT_TAG_ID, DocbookBuilderConstants.CONCEPT_TAG_NAME));
                topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.CONCEPTUALOVERVIEW_TAG_ID,
                        DocbookBuilderConstants.CONCEPTUALOVERVIEW_TAG_NAME));

                customInjectionErrors = xmlPreProcessor.processInjections(baseLevel, specTopic, customInjectionIds, doc, getBuildOptions(),
                        null, useFixedUrls);

                genericInjectionErrors = xmlPreProcessor.processGenericInjections(baseLevel, specTopic, doc, customInjectionIds,
                        topicTypeTagDetails, getBuildOptions(), useFixedUrls);
            } else {
                xmlPreProcessor.processPrerequisiteInjections(specTopic, doc, useFixedUrls);
                xmlPreProcessor.processPrevRelationshipInjections(specTopic, doc, useFixedUrls);
                xmlPreProcessor.processLinkListRelationshipInjections(specTopic, doc, useFixedUrls);
                xmlPreProcessor.processNextRelationshipInjections(specTopic, doc, useFixedUrls);
                xmlPreProcessor.processSeeAlsoInjections(specTopic, doc, useFixedUrls);

                customInjectionErrors = xmlPreProcessor.processInjections(baseLevel, specTopic, customInjectionIds, doc, getBuildOptions(),
                        relatedTopicsDatabase, useFixedUrls);

                genericInjectionErrors = new ArrayList<Integer>();
            }

            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return false;
            }

            valid = processSpecTopicInjectionErrors(topic, genericInjectionErrors, customInjectionErrors);

            /* check for dummy topics */
            if (topic instanceof TranslatedTopicWrapper) {
                /* Add the warning for the topics relationships that haven't been translated */
                if (topic.getOutgoingRelationships() != null && topic.getOutgoingRelationships().getItems() != null) {
                    final List<? extends BaseTopicWrapper<?>> relatedTopics = topic.getOutgoingRelationships().getItems();
                    for (final BaseTopicWrapper<?> relatedTopic : relatedTopics) {
                        // Check if the app should be shutdown
                        if (isShuttingDown.get()) {
                            return false;
                        }

                        final TranslatedTopicWrapper relatedTranslatedTopic = (TranslatedTopicWrapper) relatedTopic;

                        /* Only show errors for topics that weren't included in the injections */
                        if (!customInjectionErrors.contains(relatedTranslatedTopic.getTopicId()) && !genericInjectionErrors.contains(
                                relatedTopic.getId())) {
                            if ((!baseLevel.isSpecTopicInLevelByTopicID(
                                    relatedTranslatedTopic.getTopicId()) && !getBuildOptions().getIgnoreMissingCustomInjections()) ||
                                    baseLevel.isSpecTopicInLevelByTopicID(
                                    relatedTranslatedTopic.getTopicId())) {
                                if (EntityUtilities.isDummyTopic(relatedTopic) && EntityUtilities.hasBeenPushedForTranslation(
                                        relatedTranslatedTopic)) {
                                    getTopicErrorDatabase().addWarning(topic, "Topic ID " + relatedTranslatedTopic.getTopicId() + ", " +
                                            "Revision " + relatedTranslatedTopic.getTopicRevision() + ", " +
                                            "Title \"" + relatedTopic.getTitle() + "\" is an untranslated topic.");
                                } else if (EntityUtilities.isDummyTopic(relatedTopic)) {
                                    getTopicErrorDatabase().addWarning(topic, "Topic ID " + relatedTranslatedTopic.getTopicId() + ", " +
                                            "Revision " + relatedTranslatedTopic.getTopicRevision() + ", " +
                                            "Title \"" + relatedTopic.getTitle() + "\" hasn't been pushed for translation.");
                                }
                            }
                        }
                    }
                }
            }
        }

        return valid;
    }

    /**
     * Process the Injection Errors and add them to the Error Database.
     *
     * @param topic                  The topic that the errors occurred for.
     * @param genericInjectionErrors The List of Generic Injection Errors.
     * @param customInjectionErrors  The List of Custom Injection Errors.
     * @return True if no errors were processed or if the build is set to ignore missing injections, otherwise false.
     */
    protected boolean processSpecTopicInjectionErrors(final BaseTopicWrapper<?> topic, final List<Integer> genericInjectionErrors,
            final List<Integer> customInjectionErrors) {
        boolean valid = true;

        if (!customInjectionErrors.isEmpty()) {
            final String message = "Topic has referenced Topic(s) " + CollectionUtilities.toSeperatedString(
                    customInjectionErrors) + " in a custom injection point that was either not related, " +
                    "or not included in the filter used to build this book.";
            if (getBuildOptions().getIgnoreMissingCustomInjections()) {
                getTopicErrorDatabase().addWarning(topic, ErrorType.INVALID_INJECTION, message);
            } else {
                getTopicErrorDatabase().addError(topic, ErrorType.INVALID_INJECTION, message);
                valid = false;
            }
        }

        if (!genericInjectionErrors.isEmpty()) {
            final String message = "Topic has related Topic(s) " + CollectionUtilities.toSeperatedString(
                    CollectionUtilities.toAbsIntegerList(
                            genericInjectionErrors)) + " that were not included in the filter used to build this book.";
            if (getBuildOptions().getIgnoreMissingCustomInjections()) {
                getTopicErrorDatabase().addWarning(topic, ErrorType.INVALID_INJECTION, message);
            } else {
                getTopicErrorDatabase().addError(topic, ErrorType.INVALID_INJECTION, message);
                valid = false;
            }
        }

        return valid;
    }

    /**
     * This function scans the supplied XML node and it's children for id attributes, collecting them in the usedIdAttributes
     * parameter.
     *
     * @param node             The current node being processed (will be the document root to start with, and then all the children as this
     *                         function is recursively called)
     * @param topicId          The ID of the topic that we are collecting attribute ID's for.
     * @param usedIdAttributes The set of Used ID Attributes that should be added to.
     */
    private void collectIdAttributes(final Integer topicId, final Node node, final Map<Integer, Set<String>> usedIdAttributes) {
        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            final Node idAttribute = attributes.getNamedItem("id");
            if (idAttribute != null) {
                final String idAttributeValue = idAttribute.getNodeValue();
                if (!usedIdAttributes.containsKey(topicId)) {
                    usedIdAttributes.put(topicId, new HashSet<String>());
                }
                usedIdAttributes.get(topicId).add(idAttributeValue);
            }
        }

        final NodeList elements = node.getChildNodes();
        for (int i = 0; i < elements.getLength(); ++i) {
            collectIdAttributes(topicId, elements.item(i), usedIdAttributes);
        }
    }

    /**
     * Wrap all of the topics, images, common content, etc... files into a ZIP Archive.
     *
     * @param contentSpec  The content specification object to be built.
     * @param requester    The User who requested the book be built.
     * @param useFixedUrls If during processing the fixed urls should be used.
     * @return A ZIP Archive containing all the information to build the book.
     * @throws BuildProcessingException Any build issue that should not occur under normal circumstances. Ie a Template can't be
     *                                  converted to a DOM Document.
     */
    private HashMap<String, byte[]> doBuildZipPass(final ContentSpec contentSpec, final UserWrapper requester,
            final boolean useFixedUrls) throws BuildProcessingException {
        log.info("Building the ZIP file");

        final StringBuffer bookXIncludes = new StringBuffer();

        /* Add the base book information */
        final HashMap<String, byte[]> files = new HashMap<String, byte[]>();
        final String bookBase = buildBookBase(contentSpec, requester, files);

        /* add the images to the book */
        addImagesToBook(files, getBuildLocale());

        final LinkedList<org.jboss.pressgang.ccms.contentspec.Node> levelData = contentSpec.getBaseLevel().getChildNodes();

        // Loop through and create each chapter and the topics inside those chapters
        for (final org.jboss.pressgang.ccms.contentspec.Node node : levelData) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return null;
            }

            if (node instanceof Level) {
                final Level level = (Level) node;

                if (level.hasSpecTopics()) {
                    createRootElementXML(files, bookXIncludes, level, useFixedUrls);
                } else if (getBuildOptions().isAllowEmptySections()) {
                    bookXIncludes.append(DocBookUtilities.wrapInPara("No Content"));
                }
            } else if (node instanceof SpecTopic) {
                final SpecTopic specTopic = (SpecTopic) node;
                final String topicFileName = createTopicXMLFile(files, specTopic, getBookTopicsFolder(), useFixedUrls);

                if (topicFileName != null) {
                    bookXIncludes.append(
                            "\t<xi:include href=\"topics/" + topicFileName + "\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
                }
            }
        }

        if (getBuildOptions().getInsertEditorLinks() && isTranslationBuild()) {
            final String translateLinkChapter = DocBookUtilities.addDocbook45XMLDoctype(
                    buildTranslateCSChapter(contentSpec, getBuildLocale()), getEscapedBookTitle() + ".ent", "chapter");
            files.put(getBookLocaleFolder() + "Translate.xml", StringUtilities.getStringBytes(
                    StringUtilities.cleanTextForXML(translateLinkChapter == null ? "" : translateLinkChapter)));
            bookXIncludes.append("\t<xi:include href=\"Translate.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
        }

        /* add any compiler errors */
        if (!getBuildOptions().getSuppressErrorsPage() && getTopicErrorDatabase().hasItems(getBuildLocale())) {
            final String compilerOutput = DocBookUtilities.addDocbook45XMLDoctype(buildErrorChapter(contentSpec, getBuildLocale()),
                    getEscapedBookTitle() + ".ent", "chapter");
            files.put(getBookLocaleFolder() + "Errors.xml",
                    StringUtilities.getStringBytes(StringUtilities.cleanTextForXML(compilerOutput == null ? "" : compilerOutput)));
            bookXIncludes.append("\t<xi:include href=\"Errors.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
        }

        /* add the report chapter */
        if (getBuildOptions().getShowReportPage()) {
            final String compilerOutput = DocBookUtilities.addDocbook45XMLDoctype(buildReportChapter(contentSpec, getBuildLocale()),
                    getEscapedBookTitle() + ".ent", "chapter");
            files.put(getBookLocaleFolder() + "Report.xml",
                    StringUtilities.getStringBytes(StringUtilities.cleanTextForXML(compilerOutput == null ? "" : compilerOutput)));
            bookXIncludes.append("\t<xi:include href=\"Report.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
        }

        /* build the content specification page */
        if (!getBuildOptions().getSuppressContentSpecPage()) {
            try {
                files.put(getBookLocaleFolder() + "Build_Content_Specification.xml", DocBookUtilities.buildAppendix(
                        DocBookUtilities.wrapInPara(
                                "<programlisting>" + XMLUtilities.wrapStringInCDATA(contentSpec.toString()) + "</programlisting>"),
                        "Build Content Specification").getBytes(ENCODING));
            } catch (UnsupportedEncodingException e) {
                /* UTF-8 is a valid format so this should exception should never get thrown */
                log.error(e.getMessage());
            }
            bookXIncludes.append(
                    "	<xi:include href=\"Build_Content_Specification.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
        }

        final String book = bookBase.replace(BuilderConstants.XIINCLUDES_INJECTION_STRING, bookXIncludes);
        try {
            files.put(getBookLocaleFolder() + getEscapedBookTitle() + ".xml", book.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            /* UTF-8 is a valid format so this exception should never get thrown */
            log.error(e.getMessage());
        }

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return null;
        }

        return files;
    }

    /**
     * Builds the basics of a Docbook from the resource files for a specific content specification.
     *
     * @param contentSpec The content specification object to be built.
     * @param requester   The User who requested the book be built.
     * @param files       The mapping of file names/locations to files that will be packaged into the ZIP archive.
     * @return A Document object to be used in generating the book.xml
     * @throws BuildProcessingException TODO
     */
    protected String buildBookBase(final ContentSpec contentSpec, final UserWrapper requester,
            final Map<String, byte[]> files) throws BuildProcessingException {
        log.info("\tAdding standard files to Publican ZIP file");

        final Map<String, String> overrides = getBuildOptions().getOverrides();

        // Load the templates from the server
        final String publicanCfg = stringConstantProvider.getStringConstant(DocbookBuilderConstants.PUBLICAN_CFG_ID).getValue();
        final String bookEntityTemplate = stringConstantProvider.getStringConstant(DocbookBuilderConstants.BOOK_ENT_ID).getValue();
        final String prefaceXmlTemplate = stringConstantProvider.getStringConstant(DocbookBuilderConstants.CSP_PREFACE_XML_ID).getValue();

        final String bookInfoTemplate;
        if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
            bookInfoTemplate = stringConstantProvider.getStringConstant(DocbookBuilderConstants.ARTICLE_INFO_XML_ID).getValue();
        } else {
            bookInfoTemplate = stringConstantProvider.getStringConstant(DocbookBuilderConstants.BOOK_INFO_XML_ID).getValue();
        }

        final String bookXmlTemplate;
        if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
            bookXmlTemplate = stringConstantProvider.getStringConstant(DocbookBuilderConstants.ARTICLE_XML_ID).getValue();
        } else {
            bookXmlTemplate = stringConstantProvider.getStringConstant(DocbookBuilderConstants.BOOK_XML_ID).getValue();
        }

        // Setup the basic book.xml
        String basicBook = bookXmlTemplate.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, getEscapedBookTitle());
        basicBook = basicBook.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
        basicBook = basicBook.replaceAll(BuilderConstants.VERSION_REGEX, contentSpec.getVersion());
        basicBook = basicBook.replaceAll(BuilderConstants.DRAFT_REGEX, getBuildOptions().getDraft() ? "status=\"draft\""
                : "");

        if (!contentSpec.getOutputStyle().equals(CSConstants.PRESSGANG_OUTPUT_FORMAT)) {
            // Add the preface to the book.xml
            basicBook = basicBook.replaceAll(BuilderConstants.PREFACE_REGEX,
                    "<xi:include href=\"Preface.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />");

            // Add the revision history to the book.xml
            basicBook = basicBook.replaceAll(BuilderConstants.REV_HISTORY_REGEX,
                    "<xi:include href=\"Revision_History.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />");
        }

        // Setup publican.cfg
        final String fixedPublicanCfg = buildPublicanCfgFile(publicanCfg, contentSpec);
        try {
            files.put(getRootBookFolder() + "publican.cfg", fixedPublicanCfg.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            /* UTF-8 is a valid format so this should exception should never get thrown */
            log.error(e.getMessage());
        }

        // Setup Book_Info.xml
        final String fixedBookInfo = buildBookInfoFile(bookInfoTemplate, contentSpec);
        try {
            if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
                files.put(getBookLocaleFolder() + "Article_Info.xml", fixedBookInfo.getBytes(ENCODING));
            } else {
                files.put(getBookLocaleFolder() + "Book_Info.xml", fixedBookInfo.getBytes(ENCODING));
            }
        } catch (UnsupportedEncodingException e) {
            /* UTF-8 is a valid format so this should exception should never get thrown */
            log.error(e.getMessage());
        }

        // Setup Author_Group.xml
        if (overrides.containsKey(CSConstants.AUTHOR_GROUP_OVERRIDE) && getOverrideFiles().containsKey(CSConstants.AUTHOR_GROUP_OVERRIDE)) {
            // Add the override Author_Group.xml file to the book
            files.put(getBookLocaleFolder() + "Author_Group.xml", getOverrideFiles().get(CSConstants.AUTHOR_GROUP_OVERRIDE));
        } else {
            buildAuthorGroup(contentSpec, files);
        }

        // Add the Feedback.xml if the override exists
        if (overrides.containsKey(CSConstants.FEEDBACK_OVERRIDE) && getOverrideFiles().containsKey(CSConstants.FEEDBACK_OVERRIDE)) {
            // Add the override Feedback.xml file to the book
            files.put(getBookLocaleFolder() + "Feedback.xml", getOverrideFiles().get(CSConstants.FEEDBACK_OVERRIDE));
        }

        // Setup Preface.xml
        if (!contentSpec.getOutputStyle().equals(CSConstants.PRESSGANG_OUTPUT_FORMAT)) {
            String fixedPrefaceXml = prefaceXmlTemplate.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, getEscapedBookTitle());

            final String prefaceTitleTranslation = getConstantTranslatedStrings().getProperty("PREFACE");
            if (prefaceTitleTranslation != null) {
                fixedPrefaceXml = fixedPrefaceXml.replace("<title>Preface</title>", "<title>" + prefaceTitleTranslation + "</title>");
            }

            try {
                files.put(getBookLocaleFolder() + "Preface.xml", fixedPrefaceXml.getBytes(ENCODING));
            } catch (UnsupportedEncodingException e) {
                /* UTF-8 is a valid format so this should exception should never get thrown */
                log.error(e.getMessage());
            }
        }

        // Add any common content files that need to be included locally
        if (getBuildOptions().getCommonContentLocale() != null && getBuildOptions().getCommonContentDirectory() != null) {
            addPublicanCommonContentToBook(contentSpec, getBuildOptions().getCommonContentLocale(),
                    getBuildOptions().getCommonContentDirectory(), files);
        }

        // Replace the basic injection data inside the revision history
        final String revisionHistoryXml = stringConstantProvider.getStringConstant(
                DocbookBuilderConstants.REVISION_HISTORY_XML_ID).getValue();
        String fixedRevisionHistoryXml = revisionHistoryXml.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, getEscapedBookTitle());

        // Setup Revision_History.xml
        if (overrides.containsKey(CSConstants.REVISION_HISTORY_OVERRIDE) && getOverrideFiles().containsKey(
                CSConstants.REVISION_HISTORY_OVERRIDE)) {
            byte[] revHistory = getOverrideFiles().get(CSConstants.REVISION_HISTORY_OVERRIDE);
            if (getBuildOptions().getRevisionMessages() != null && !getBuildOptions().getRevisionMessages().isEmpty()) {
                try {
                    // Parse the Revision History and add the new entry/entries
                    final ByteArrayInputStream bais = new ByteArrayInputStream(revHistory);
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(bais));
                    final StringBuilder buffer = new StringBuilder();
                    String line = "";
                    while ((line = reader.readLine()) != null) {
                        buffer.append(line + "\n");
                    }

                    // Add a revision message to the Revision_History.xml
                    final String revHistoryOverride = buffer.toString();
                    final String docType = XMLUtilities.findDocumentType(revHistoryOverride);
                    if (docType != null) {
                        buildRevisionHistory(contentSpec, revHistoryOverride.replace(docType, ""), requester, files);
                    } else {
                        buildRevisionHistory(contentSpec, revHistoryOverride, requester, files);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage());
                    buildRevisionHistory(contentSpec, fixedRevisionHistoryXml, requester, files);
                }
            } else {
                // Add the revision history directly to the book
                files.put(getBookLocaleFolder() + "Revision_History.xml", revHistory);
            }
        } else {
            buildRevisionHistory(contentSpec, fixedRevisionHistoryXml, requester, files);
        }

        // Build the book .ent file
        final String entFile = buildBookEntityFile(bookEntityTemplate, contentSpec);
        try {
            files.put(getBookLocaleFolder() + getEscapedBookTitle() + ".ent", entFile.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            /* UTF-8 is a valid format so this should exception should never get thrown */
            log.error(e.getMessage());
        }

        // Setup the images and files folders
        addBookBaseFilesAndImages(contentSpec, files);

        return basicBook;
    }

    /**
     * Adds the basic Images and Files to the book that are the minimum requirements to build it.
     *
     * @param contentSpec The content specification object to be built.
     * @param files       The mapping of file names/locations to files that will be packaged into the ZIP archive.
     */
    protected void addBookBaseFilesAndImages(final ContentSpec contentSpec, final Map<String, byte[]> files) {
        final String iconSvg = stringConstantProvider.getStringConstant(DocbookBuilderConstants.ICON_SVG_ID).getValue();
        try {
            files.put(getBookImagesFolder() + "icon.svg", iconSvg.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            /* UTF-8 is a valid format so this should exception should never get thrown */
            log.error(e.getMessage());
        }

        if (contentSpec.getOutputStyle() != null && contentSpec.getOutputStyle().equals(CSConstants.PRESSGANG_OUTPUT_FORMAT)) {
            final String jbossSvg = stringConstantProvider.getStringConstant(DocbookBuilderConstants.JBOSS_SVG_ID).getValue();

            final String yahooDomEventJs = stringConstantProvider.getStringConstant(
                    DocbookBuilderConstants.YAHOO_DOM_EVENT_JS_ID).getValue();
            final String treeviewMinJs = stringConstantProvider.getStringConstant(DocbookBuilderConstants.TREEVIEW_MIN_JS_ID).getValue();
            final String treeviewCss = stringConstantProvider.getStringConstant(DocbookBuilderConstants.TREEVIEW_CSS_ID).getValue();
            final String jqueryMinJs = stringConstantProvider.getStringConstant(DocbookBuilderConstants.JQUERY_MIN_JS_ID).getValue();

            final byte[] treeviewSpriteGif = blobConstantProvider.getBlobConstant(
                    DocbookBuilderConstants.TREEVIEW_SPRITE_GIF_ID).getValue();
            final byte[] treeviewLoadingGif = blobConstantProvider.getBlobConstant(
                    DocbookBuilderConstants.TREEVIEW_LOADING_GIF_ID).getValue();
            final byte[] check1Gif = blobConstantProvider.getBlobConstant(DocbookBuilderConstants.CHECK1_GIF_ID).getValue();
            final byte[] check2Gif = blobConstantProvider.getBlobConstant(DocbookBuilderConstants.CHECK2_GIF_ID).getValue();

            final String bookFilesFolder = getBookFilesFolder();
            // these files are used by the YUI treeview
            files.put(bookFilesFolder + "yahoo-dom-event.js", StringUtilities.getStringBytes(yahooDomEventJs));
            files.put(bookFilesFolder + "treeview-min.js", StringUtilities.getStringBytes(treeviewMinJs));
            files.put(bookFilesFolder + "treeview.css", StringUtilities.getStringBytes(treeviewCss));
            files.put(bookFilesFolder + "jquery.min.js", StringUtilities.getStringBytes(jqueryMinJs));

            // these are the images that are referenced in the treeview.css file
            files.put(bookFilesFolder + "treeview-sprite.gif", treeviewSpriteGif);
            files.put(bookFilesFolder + "treeview-loading.gif", treeviewLoadingGif);
            files.put(bookFilesFolder + "check1.gif", check1Gif);
            files.put(bookFilesFolder + "check2.gif", check2Gif);

            files.put(getBookImagesFolder() + "jboss.svg", StringUtilities.getStringBytes(jbossSvg));
        }
    }

    /**
     * Builds the Book_Info.xml file that is a basic requirement to build the book.
     *
     * @param bookInfoTemplate The Book_Info.xml template to add content to.
     * @param contentSpec      The content specification object to be built.
     * @return The Book_Info.xml file filled with content from the Content Spec.
     */
    protected String buildBookInfoFile(final String bookInfoTemplate, final ContentSpec contentSpec) {
        final Map<String, String> overrides = getBuildOptions().getOverrides();

        String bookInfo = bookInfoTemplate.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, getEscapedBookTitle());
        bookInfo = bookInfo.replaceAll(BuilderConstants.TITLE_REGEX, contentSpec.getTitle());
        bookInfo = bookInfo.replaceAll(BuilderConstants.SUBTITLE_REGEX,
                contentSpec.getSubtitle() == null ? BuilderConstants.SUBTITLE_DEFAULT : contentSpec.getSubtitle());
        bookInfo = bookInfo.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
        bookInfo = bookInfo.replaceAll(BuilderConstants.VERSION_REGEX, contentSpec.getVersion());
        bookInfo = bookInfo.replaceAll(BuilderConstants.EDITION_REGEX,
                contentSpec.getEdition() == null ? BuilderConstants.DEFAULT_EDITION : contentSpec.getEdition());
        final String pubsNumber = overrides.containsKey(CSConstants.PUBSNUMBER_OVERRIDE) ? overrides.get(
                CSConstants.PUBSNUMBER_OVERRIDE) : (contentSpec.getPubsNumber() == null ? BuilderConstants.DEFAULT_PUBSNUMBER :
                contentSpec.getPubsNumber().toString());
        bookInfo = bookInfo.replaceAll(BuilderConstants.PUBSNUMBER_REGEX, "<pubsnumber>" + pubsNumber + "</pubsnumber>");

        if (!contentSpec.getOutputStyle().equals(CSConstants.PRESSGANG_OUTPUT_FORMAT)) {
            bookInfo = bookInfo.replaceAll(BuilderConstants.ABSTRACT_REGEX,
                    contentSpec.getAbstract() == null ? BuilderConstants.DEFAULT_ABSTRACT : ("<abstract>\n\t\t<para>\n\t\t\t" +
                            contentSpec.getAbstract() + "\n\t\t</para>\n\t</abstract>\n"));
            bookInfo = bookInfo.replaceAll(BuilderConstants.LEGAL_NOTICE_REGEX, BuilderConstants.LEGAL_NOTICE_XML);
        }

        return bookInfo;
    }

    /**
     * Builds the publican.cfg file that is a basic requirement to build the publican book.
     *
     * @param publicanCfgTemplate The publican.cfg template to add content to.
     * @param contentSpec         The content specification object to be built.
     * @return The publican.cfg file filled with content from the Content Spec.
     */
    protected String buildPublicanCfgFile(final String publicanCfgTemplate, final ContentSpec contentSpec) {
        final Map<String, String> overrides = getBuildOptions().getOverrides();

        final String brand = overrides.containsKey(CSConstants.BRAND_OVERRIDE) ? overrides.get(
                CSConstants.BRAND_OVERRIDE) : (contentSpec.getBrand() == null ? BuilderConstants.DEFAULT_BRAND : contentSpec.getBrand());

        // Setup publican.cfg
        String publicanCfg = publicanCfgTemplate.replaceAll(BuilderConstants.BRAND_REGEX, brand);
        publicanCfg = publicanCfg.replaceFirst("type\\:\\s*.*($|\\r\\n|\\n)",
                "type: " + contentSpec.getBookType().toString().replaceAll("-Draft", "") + "\n");
        publicanCfg = publicanCfg.replaceAll("xml_lang\\:\\s*.*?($|\\r\\n|\\n)", "xml_lang: " + getOutputLocale() + "\n");
        if (!publicanCfg.matches(".*\n$")) {
            publicanCfg += "\n";
        }

        // Remove the image width for CSP output
        if (!contentSpec.getOutputStyle().equals(CSConstants.PRESSGANG_OUTPUT_FORMAT)) {
            publicanCfg = publicanCfg.replaceFirst("max_image_width:\\s*\\d+\\s*(\\r)?\\n", "");
            publicanCfg = publicanCfg.replaceFirst("toc_section_depth:\\s*\\d+\\s*(\\r)?\\n", "");
        }

        if (contentSpec.getPublicanCfg() != null) {
            /* Remove the git_branch if the content spec contains a git_branch */
            if (contentSpec.getPublicanCfg().contains("git_branch")) {
                publicanCfg = publicanCfg.replaceFirst("git_branch:\\s*.*(\\r)?(\\n)?", "");
            }
            publicanCfg += DocbookBuildUtilities.cleanUserPublicanCfg(contentSpec.getPublicanCfg());

            if (!publicanCfg.matches(".*\n$")) {
                publicanCfg += "\n";
            }
        }

        if (getBuildOptions().getPublicanShowRemarks()) {
            /* Remove any current show_remarks definitions */
            if (publicanCfg.contains("show_remarks")) {
                publicanCfg = publicanCfg.replaceAll("show_remarks:\\s*\\d+\\s*(\\r)?(\\n)?", "");
            }
            publicanCfg += "show_remarks: 1\n";
        }

        publicanCfg += "docname: " + getEscapedBookTitle().replaceAll("_", " ") + "\n";
        publicanCfg += "product: " + getOriginalBookProduct() + "\n";

        if (getBuildOptions().getCvsPkgOption() != null) {
            publicanCfg += "cvs_pkg: " + getBuildOptions().getCvsPkgOption() + "\n";
        }

        return publicanCfg;
    }

    /**
     * Builds the book .ent file that is a basic requirement to build the book.
     *
     * @param entityFileTemplate The entity file template to add content to.
     * @param contentSpec        The content specification object to be built.
     * @return The book .ent file filled with content from the Content Spec.
     */
    protected String buildBookEntityFile(final String entityFileTemplate, final ContentSpec contentSpec) {
        // Setup the <<contentSpec.title>>.ent file
        String entFile = entityFileTemplate.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, getEscapedBookTitle());
        entFile = entFile.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
        entFile = entFile.replaceAll(BuilderConstants.TITLE_REGEX, getOriginalBookTitle());
        entFile = entFile.replaceAll(BuilderConstants.YEAR_FORMAT_REGEX, Integer.toString(Calendar.getInstance().get(Calendar.YEAR)));
        entFile = entFile.replaceAll(BuilderConstants.CONTENT_SPEC_COPYRIGHT_REGEX, contentSpec.getCopyrightHolder());
        entFile = entFile.replaceAll(BuilderConstants.BZPRODUCT_REGEX,
                contentSpec.getBugzillaProduct() == null ? getOriginalBookProduct() : contentSpec.getBugzillaProduct());
        entFile = entFile.replaceAll(BuilderConstants.BZCOMPONENT_REGEX,
                contentSpec.getBugzillaComponent() == null ? BuilderConstants.DEFAULT_BZCOMPONENT : contentSpec.getBugzillaComponent());
        entFile = entFile.replaceAll(BuilderConstants.CONTENT_SPEC_BUGZILLA_URL_REGEX,
                contentSpec.getBugzillaURL() == null ? BuilderConstants.DEFAULT_BUGZILLA_URL : contentSpec.getBugzillaURL());

        return entFile;
    }

    /**
     * Adds the Publican Common_Content files specified by the brand, locale and directory location build options . If the
     * Common_Content files don't exist at the directory, brand and locale specified then the "common" brand will be used
     * instead. If the file still don't exist then the files are skipped and will rely on XML XI Include Fallbacks.
     *
     * @param contentSpec            The Content Spec that is used to build the book.
     * @param commonContentLocale    The Common_Content Locale to be used.
     * @param commonContentDirectory The Common_Content directory.
     * @param files                  The Mapping of file names to file contents to be used to build the ZIP archive.
     */
    protected void addPublicanCommonContentToBook(final ContentSpec contentSpec, final String commonContentLocale,
            final String commonContentDirectory, final Map<String, byte[]> files) {
        final String brand = contentSpec.getBrand() == null ? BuilderConstants.DEFAULT_BRAND : contentSpec.getBrand();

        final String brandDir = commonContentDirectory + (commonContentDirectory.endsWith(
                "/") ? "" : "/") + brand + File.separator + commonContentLocale + File.separator;
        final String commonBrandDir = commonContentDirectory + (commonContentDirectory.endsWith(
                "/") ? "" : "/") + BuilderConstants.DEFAULT_BRAND + File.separator + commonContentLocale + File.separator;

        /*
         * We need to pull the Conventions.xml, Feedback.xml & Legal_Notice.xml from the publican Common_Content directory.
         * First we need to check if the files exist for the brand, if they don't then we need to check the common directory.
         */

        for (final String fileName : BuilderConstants.COMMON_CONTENT_FILES) {
            final File brandFile = new File(brandDir + fileName);

            try {
                if (brandFile.exists() && brandFile.isFile()) {
                    final String file = FileUtilities.readFileContents(brandFile);
                    if (!file.isEmpty()) {
                        files.put(getBookLocaleFolder() + fileName, file.getBytes(ENCODING));
                    }
                } else {
                    final File commonBrandFile = new File(commonBrandDir + fileName);
                    if (commonBrandFile.exists() && commonBrandFile.isFile()) {
                        final String file = FileUtilities.readFileContents(commonBrandFile);
                        if (!file.isEmpty()) {
                            files.put(getBookLocaleFolder() + fileName, file.getBytes(ENCODING));
                        }
                    }
                }
            } catch (UnsupportedEncodingException e) {
                log.debug(e.getMessage());
            }
        }

    }

    /**
     * Creates all the chapters/appendixes for a book and generates the section/topic data inside of each chapter.
     *
     * @param files         The mapping of File Names/Locations to actual file content.
     * @param bookXIncludes The string based list of XIncludes to be used in the book.xml
     * @param level         The level to build the chapter from.
     * @param useFixedUrls  If Fixed URL Properties should be used for topic ID attributes.
     * @throws BuildProcessingException TODO
     */
    protected void createRootElementXML(final Map<String, byte[]> files, final StringBuffer bookXIncludes, final Level level,
            final boolean useFixedUrls) throws BuildProcessingException {
        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        /* Get the name of the element based on the type */
        final String elementName = level.getType() == LevelType.PROCESS ? "chapter" : level.getType().getTitle().toLowerCase();

        Document chapter = null;
        try {
            chapter = XMLUtilities.convertStringToDocument("<" + elementName + "></" + elementName + ">");
        } catch (SAXException ex) {
            /* Exit since we shouldn't fail at converting a basic chapter */
            log.debug(ExceptionUtilities.getStackTrace(ex));
            throw new BuildProcessingException("Failed to create a basic XML document");
        }

        // Create the title
        final String chapterName = level.getUniqueLinkId(useFixedUrls);
        final String chapterXMLName = chapterName + ".xml";

        // Add to the list of XIncludes that will get set in the book.xml
        bookXIncludes.append("\t<xi:include href=\"" + chapterXMLName + "\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");

        // Create the chapter.xml
        final Element titleNode = chapter.createElement("title");
        if (isTranslationBuild() && level.getTranslatedTitle() != null && !level.getTranslatedTitle().isEmpty()) {
            titleNode.setTextContent(level.getTranslatedTitle());
        } else {
            titleNode.setTextContent(level.getTitle());
        }
        chapter.getDocumentElement().appendChild(titleNode);
        chapter.getDocumentElement().setAttribute("id", level.getUniqueLinkId(useFixedUrls));
        createSectionXML(files, level, chapter, chapter.getDocumentElement(), getBookTopicsFolder() + chapterName + "/", useFixedUrls);

        // Add the boiler plate text and add the chapter to the book
        final String chapterString = DocBookUtilities.addDocbook45XMLDoctype(
                XMLUtilities.convertNodeToString(chapter, verbatimElements, inlineElements, contentsInlineElements, true),
                getEscapedBookTitle() + ".ent", elementName);
        try {
            files.put(getBookLocaleFolder() + chapterXMLName, chapterString.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            /* UTF-8 is a valid format so this should exception should never get thrown */
            log.error(e.getMessage());
        }
    }

    /**
     * Creates all the chapters/appendixes for a book that are contained within another part/chapter/appendix and generates the
     * section/topic data inside of each chapter.
     *
     * @param files        The mapping of File Names/Locations to actual file content.
     * @param doc          The document object to add the child level content to.
     * @param level        The level to build the chapter from.
     * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
     * @return The Element that specifies the XiInclude for the chapter/appendix in the files.
     * @throws BuildProcessingException TODO
     */
    protected Element createSubRootElementXML(final Map<String, byte[]> files, final Document doc, final Level level,
            final String parentFileDirectory, final boolean useFixedUrls) throws BuildProcessingException {
        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return null;
        }

        /* Get the name of the element based on the type */
        final String elementName = level.getType() == LevelType.PROCESS ? "chapter" : level.getType().getTitle().toLowerCase();

        Document chapter = null;
        try {
            chapter = XMLUtilities.convertStringToDocument("<" + elementName + "></" + elementName + ">");
        } catch (SAXException ex) {
            /* Exit since we shouldn't fail at converting a basic chapter */
            log.debug(ExceptionUtilities.getStackTrace(ex));
            throw new BuildProcessingException("Failed to create a basic XML document");
        }

        // Create the title
        final String chapterName = level.getUniqueLinkId(useFixedUrls);
        final String chapterXMLName = chapterName + ".xml";

        // Create the chapter.xml
        final Element titleNode = chapter.createElement("title");
        if (isTranslationBuild() && level.getTranslatedTitle() != null && !level.getTranslatedTitle().isEmpty()) {
            titleNode.setTextContent(level.getTranslatedTitle());
        } else {
            titleNode.setTextContent(level.getTitle());
        }
        chapter.getDocumentElement().appendChild(titleNode);
        chapter.getDocumentElement().setAttribute("id", level.getUniqueLinkId(useFixedUrls));
        createSectionXML(files, level, chapter, chapter.getDocumentElement(), parentFileDirectory + chapterName + "/", useFixedUrls);

        // Add the boiler plate text and add the chapter to the book
        final String chapterString = DocBookUtilities.addDocbook45XMLDoctype(
                XMLUtilities.convertNodeToString(chapter, verbatimElements, inlineElements, contentsInlineElements, true),
                getEscapedBookTitle() + ".ent", elementName);
        try {
            files.put(getBookLocaleFolder() + chapterXMLName, chapterString.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            /* UTF-8 is a valid format so this should exception should never get thrown */
            log.error(e.getMessage());
        }

        // Create the XIncludes that will get set in the book.xml
        final Element xiInclude = doc.createElement("xi:include");
        xiInclude.setAttribute("href", chapterXMLName);
        xiInclude.setAttribute("xmlns:xi", "http://www.w3.org/2001/XInclude");

        return xiInclude;
    }

    /**
     * Creates the section component of a chapter.xml for a specific ContentLevel.
     *
     * @param files        The mapping of File Names/Locations to actual file content.
     * @param level        The section level object to get content from.
     * @param chapter      The chapter document object that this section is to be added to.
     * @param parentNode   The parent XML node of this section.
     * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
     * @throws BuildProcessingException TODO
     */
    protected void createSectionXML(final Map<String, byte[]> files, final Level level, final Document chapter, final Element parentNode,
            final String parentFileLocation, final boolean useFixedUrls) throws BuildProcessingException {
        final LinkedList<org.jboss.pressgang.ccms.contentspec.Node> levelData = level.getChildNodes();

        /* Get the name of the element based on the type */
        final String elementName = level.getType() == LevelType.PROCESS ? "chapter" : level.getType().getTitle().toLowerCase();
        final Element intro = chapter.createElement(elementName + "intro");

        /* Storage container to hold the levels so they can be added in proper order with the intro */
        final LinkedList<Node> childNodes = new LinkedList<Node>();

        // Add the section and topics for this level to the chapter.xml
        for (final org.jboss.pressgang.ccms.contentspec.Node node : levelData) {

            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            if (node instanceof Level && node.getParent() != null && (((Level) node).getParent().getType() == LevelType.BASE || ((Level)
                    node).getParent().getType() == LevelType.PART)) {
                final Level childLevel = (Level) node;

                // Create a new file for the Chapter/Appendix
                final Element xiInclude = createSubRootElementXML(files, chapter, childLevel, parentFileLocation, useFixedUrls);
                if (xiInclude != null) {
                    childNodes.add(xiInclude);
                }
            } else if (node instanceof Level) {
                final Level childLevel = (Level) node;

                // Create the section and its title
                final Element sectionNode = chapter.createElement("section");
                final Element sectionTitleNode = chapter.createElement("title");
                if (isTranslationBuild() && childLevel.getTranslatedTitle() != null && !childLevel.getTranslatedTitle().isEmpty())
                    sectionTitleNode.setTextContent(childLevel.getTranslatedTitle());
                else sectionTitleNode.setTextContent(childLevel.getTitle());
                sectionNode.appendChild(sectionTitleNode);
                sectionNode.setAttribute("id", childLevel.getUniqueLinkId(useFixedUrls));

                // Ignore sections that have no spec topics
                if (!childLevel.hasSpecTopics()) {
                    if (getBuildOptions().isAllowEmptySections()) {
                        Element warning = chapter.createElement("warning");
                        warning.setTextContent("No Content");
                        sectionNode.appendChild(warning);
                    } else {
                        continue;
                    }
                } else {
                    // Add this sections child sections/topics
                    createSectionXML(files, childLevel, chapter, sectionNode, parentFileLocation, useFixedUrls);
                }

                childNodes.add(sectionNode);
            } else if (node instanceof SpecTopic) {
                final SpecTopic specTopic = (SpecTopic) node;

                final String topicFileName = createTopicXMLFile(files, specTopic, parentFileLocation, useFixedUrls);

                if (topicFileName != null) {
                    // Remove the initial file location as we only where it lives in the topics directory
                    final String fixedParentFileLocation = getBuildOptions().getFlattenTopics() ? "topics/" : parentFileLocation.replace(
                            getBookLocaleFolder(), "");

                    final Element topicNode = chapter.createElement("xi:include");
                    topicNode.setAttribute("href", fixedParentFileLocation + topicFileName);
                    topicNode.setAttribute("xmlns:xi", "http://www.w3.org/2001/XInclude");

                    if (specTopic.getParent() != null && specTopic.getParent().getType() == LevelType.PART) {
                        intro.appendChild(topicNode);
                    } else {
                        childNodes.add(topicNode);
                    }
                }
            }
        }

        /* Add the child nodes and intro to the parent */
        if (intro.hasChildNodes()) {
            parentNode.appendChild(intro);
        }

        for (final Node node : childNodes) {
            parentNode.appendChild(node);
        }
    }

    /**
     * Creates the Topic component of a chapter.xml for a specific SpecTopic.
     *
     * @param files              The mapping of File Names/Locations to actual file content.
     * @param specTopic          The SpecTopic object to get content from.
     * @param parentFileLocation
     * @param useFixedUrls       If Fixed URL Properties should be used for topic ID attributes.  @return The filename of the new topic
     *                           XML file.
     */
    protected String createTopicXMLFile(final Map<String, byte[]> files, final SpecTopic specTopic, final String parentFileLocation,
            final boolean useFixedUrls) {
        String topicFileName;
        final BaseTopicWrapper<?> topic = specTopic.getTopic();

        if (topic != null) {
            if (useFixedUrls) {
                topicFileName = topic.getXRefPropertyOrId(CommonConstants.FIXED_URL_PROP_TAG_ID);
            } else {
                topicFileName = topic.getXRefId();
            }

            if (specTopic.getDuplicateId() != null) {
                topicFileName += "-" + specTopic.getDuplicateId();
            }

            topicFileName += ".xml";

            final String fixedParentFileLocation = getBuildOptions().getFlattenTopics() ? getBookTopicsFolder() : parentFileLocation;

            final String topicXML = DocBookUtilities.addDocbook45XMLDoctype(
                    XMLUtilities.convertNodeToString(specTopic.getXmlDocument(), verbatimElements, inlineElements, contentsInlineElements,
                            true), getEscapedBookTitle() + ".ent", DocBookUtilities.TOPIC_ROOT_NODE_NAME);
            try {
                files.put(fixedParentFileLocation + topicFileName, topicXML.getBytes(ENCODING));
            } catch (UnsupportedEncodingException e) {
                /* UTF-8 is a valid format so this should exception should never get thrown */
                log.error(e.getMessage());
            }

            return topicFileName;
        }

        return null;
    }

    /**
     * Adds all the images found using the {@link #processImageLocations()} method to the files map that will alter be turned
     * into a ZIP archive.
     *
     * @param files  The mapping of File Names/Locations to actual file content.
     * @param locale The locale for the book.
     */
    private void addImagesToBook(final HashMap<String, byte[]> files, final String locale) {
        /* Load the database constants */
        final byte[] failpenguinPng = blobConstantProvider.getBlobConstant(DocbookBuilderConstants.FAILPENGUIN_PNG_ID).getValue();

        /* download the image files that were identified in the processing stage */
        float imageProgress = 0;
        final float imageTotal = getImageLocations().size();
        final int showPercent = 5;
        int lastPercent = 0;

        for (final TopicImageData imageLocation : getImageLocations()) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            boolean success = false;

            final int extensionIndex = imageLocation.getImageName().lastIndexOf(".");
            final int pathIndex = imageLocation.getImageName().lastIndexOf("/");
            final int hypenIndex = imageLocation.getImageName().lastIndexOf("-");

            if (/* characters were found */
                    extensionIndex != -1 && pathIndex != -1
            /* the path character was found before the extension */ && extensionIndex > pathIndex) {
                try {
                    /*
                     * The file name minus the extension should be an integer that references an ImageFile record ID.
                     */
                    final String imageID;
                    if (hypenIndex != -1) {
                        imageID = imageLocation.getImageName().substring(pathIndex + 1, Math.min(extensionIndex, hypenIndex));
                    } else {
                        imageID = imageLocation.getImageName().substring(pathIndex + 1, extensionIndex);
                    }

                    /*
                     * If the image is the failpenguin the that means that an error has already occurred most likely from not
                     * specifying an image file at all.
                     */
                    if (imageID.equals(BuilderConstants.FAILPENGUIN_PNG_NAME)) {
                        success = false;
                        getTopicErrorDatabase().addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES,
                                "No image filename specified. Must be in the format [ImageFileID].extension e.g. 123.png, " +
                                        "" + "or images/321.jpg");
                    } else {
                        final ImageWrapper imageFile = imageProvider.getImage(Integer.parseInt(imageID));
                        // TODO Uncomment this once Image Revisions are fixed.
//                        if (imageLocation.getRevision() == null) {
//                            imageFile = imageProvider.getImage(Integer.parseInt(imageID));
//                        } else {
//                            imageFile = imageProvider.getImage(Integer.parseInt(imageID), imageLocation.getRevision());
//                        }

                        /* Find the image that matches this locale. If the locale isn't found then use the default locale */
                        LanguageImageWrapper languageImageFile = null;
                        if (imageFile.getLanguageImages() != null && imageFile.getLanguageImages().getItems() != null) {
                            final List<LanguageImageWrapper> languageImages = imageFile.getLanguageImages().getItems();
                            for (final LanguageImageWrapper image : languageImages) {
                                if (image.getLocale().equals(locale)) {
                                    languageImageFile = image;
                                } else if (image.getLocale().equals(getDefaultBuildLocale()) && languageImageFile == null) {
                                    languageImageFile = image;
                                }
                            }
                        }

                        if (languageImageFile != null && languageImageFile.getImageData() != null) {
                            success = true;
                            files.put(getBookLocaleFolder() + imageLocation.getImageName(), languageImageFile.getImageData());
                        } else {
                            getTopicErrorDatabase().addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES,
                                    "ImageFile ID " + imageID + " from image location " + imageLocation.getImageName() + " was not found!");
                        }
                    }
                } catch (final NumberFormatException ex) {
                    success = false;
                    getTopicErrorDatabase().addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES,
                            imageLocation.getImageName() + " is not a valid image. Must be in the format [ImageFileID].extension e.g. 123" +
                                    ".png, or images/321.jpg");
                    log.debug("", ex);
                } catch (final Exception ex) {
                    success = false;
                    getTopicErrorDatabase().addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES,
                            imageLocation.getImageName() + " is not a valid image. Must be in the format [ImageFileID].extension e.g. 123" +
                                    ".png, or images/321.jpg");
                    log.debug("", ex);
                }
            }

            /* put in a place holder */
            if (!success) {
                files.put(getBookLocaleFolder() + imageLocation.getImageName(), failpenguinPng);
            }

            final int progress = Math.round(imageProgress / imageTotal * 100);
            if (progress - lastPercent >= showPercent) {
                lastPercent = progress;
                log.info("\tDownloading Images " + progress + "% done");
            }

            ++imageProgress;
        }
    }

    /**
     * Builds the Author_Group.xml using the assigned writers for topics inside of the content specification.
     *
     * @param contentSpec The content spec used to build the book.
     * @param files       The mapping of File Names/Locations to actual file content.
     * @throws BuildProcessingException
     */
    private void buildAuthorGroup(final ContentSpec contentSpec, final Map<String, byte[]> files) throws BuildProcessingException {
        log.info("\tBuilding Author_Group.xml");

        // Setup Author_Group.xml
        final String authorGroupXml = stringConstantProvider.getStringConstant(DocbookBuilderConstants.AUTHOR_GROUP_XML_ID).getValue();
        String fixedAuthorGroupXml = authorGroupXml;
        Document authorDoc = null;
        try {
            authorDoc = XMLUtilities.convertStringToDocument(fixedAuthorGroupXml);
        } catch (SAXException ex) {
            /* Exit since we shouldn't fail at converting the basic author group */
            log.debug("", ex);
            throw new BuildProcessingException("Failed to convert the Author_Group.xml template into a DOM document");
        }
        final LinkedHashMap<Integer, AuthorInformation> authorIDtoAuthor = new LinkedHashMap<Integer, AuthorInformation>();

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        // Get the mapping of authors using the topics inside the content spec
        for (final Integer topicId : getSpecDatabase().getTopicIds()) {
            final BaseTopicWrapper<?> topic = getSpecDatabase().getSpecTopicsForTopicID(topicId).get(0).getTopic();
            final List<TagWrapper> authorTags = topic.getTagsInCategories(CollectionUtilities.toArrayList(CSConstants.WRITER_CATEGORY_ID));

            if (authorTags.size() > 0) {
                for (final TagWrapper author : authorTags) {
                    if (!authorIDtoAuthor.containsKey(author.getId())) {
                        final AuthorInformation authorInfo = EntityUtilities.getAuthorInformation(providerFactory, author.getId());
                        if (authorInfo != null) {
                            authorIDtoAuthor.put(author.getId(), authorInfo);
                        }
                    }
                }
            }
        }

        /* Sort and make sure duplicate authors don't exist */
        final Set<AuthorInformation> authors = new TreeSet<AuthorInformation>(new AuthorInformationComparator());
        for (final Entry<Integer, AuthorInformation> authorEntry : authorIDtoAuthor.entrySet()) {
            final AuthorInformation authorInfo = authorEntry.getValue();
            if (authorInfo != null) {
                authors.add(authorInfo);
            }
        }

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        // If one or more authors were found then remove the default and attempt to add them
        if (!authors.isEmpty()) {
            // Clear the template data
            XMLUtilities.emptyNode(authorDoc.getDocumentElement());
            boolean insertedAuthor = false;

            // For each author attempt to find the author information records and populate Author_Group.xml.
            for (final AuthorInformation authorInfo : authors) {
                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    shutdown.set(true);
                    return;
                }

                final Element authorEle = authorDoc.createElement("author");
                final Element firstNameEle = authorDoc.createElement("firstname");
                firstNameEle.setTextContent(authorInfo.getFirstName());
                authorEle.appendChild(firstNameEle);
                final Element lastNameEle = authorDoc.createElement("surname");
                lastNameEle.setTextContent(authorInfo.getLastName());
                authorEle.appendChild(lastNameEle);

                // Add the affiliation information
                if (authorInfo.getOrganization() != null) {
                    final Element affiliationEle = authorDoc.createElement("affiliation");
                    final Element orgEle = authorDoc.createElement("orgname");
                    orgEle.setTextContent(authorInfo.getOrganization());
                    affiliationEle.appendChild(orgEle);
                    if (authorInfo.getOrgDivision() != null) {
                        final Element orgDivisionEle = authorDoc.createElement("orgdiv");
                        orgDivisionEle.setTextContent(authorInfo.getOrgDivision());
                        affiliationEle.appendChild(orgDivisionEle);
                    }
                    authorEle.appendChild(affiliationEle);
                }

                // Add an email if one exists
                if (authorInfo.getEmail() != null) {
                    Element emailEle = authorDoc.createElement("email");
                    emailEle.setTextContent(authorInfo.getEmail());
                    authorEle.appendChild(emailEle);
                }
                authorDoc.getDocumentElement().appendChild(authorEle);
                insertedAuthor = true;
            }

            // If no authors were inserted then use a default value
            // Note: This should never happen but is used as a safety measure
            if (!insertedAuthor && contentSpec.getOutputStyle().equals(CSConstants.PRESSGANG_OUTPUT_FORMAT)) {
                // Use the author "Skynet Alpha Build System"
                final Element authorEle = authorDoc.createElement("author");
                final Element firstNameEle = authorDoc.createElement("firstname");
                firstNameEle.setTextContent("Skynet");
                authorEle.appendChild(firstNameEle);
                final Element lastNameEle = authorDoc.createElement("surname");
                lastNameEle.setTextContent("Alpha Build System");
                authorEle.appendChild(lastNameEle);
                authorDoc.getDocumentElement().appendChild(authorEle);

                // Add the affiliation
                final Element affiliationEle = authorDoc.createElement("affiliation");
                final Element orgEle = authorDoc.createElement("orgname");
                orgEle.setTextContent("Red Hat");
                affiliationEle.appendChild(orgEle);
                final Element orgDivisionEle = authorDoc.createElement("orgdiv");
                orgDivisionEle.setTextContent("Enigineering Content Services");
                affiliationEle.appendChild(orgDivisionEle);
                authorEle.appendChild(affiliationEle);
            } else if (!insertedAuthor) {
                // Use the author "Staff Writer"
                final Element authorEle = authorDoc.createElement("author");
                final Element firstNameEle = authorDoc.createElement("firstname");
                firstNameEle.setTextContent("Staff");
                authorEle.appendChild(firstNameEle);
                final Element lastNameEle = authorDoc.createElement("surname");
                lastNameEle.setTextContent("Writer");
                authorEle.appendChild(lastNameEle);
                authorDoc.getDocumentElement().appendChild(authorEle);
            }
        }

        // Add the Author_Group.xml to the book
        fixedAuthorGroupXml = DocBookUtilities.addDocbook45XMLDoctype(
                XMLUtilities.convertNodeToString(authorDoc, verbatimElements, inlineElements, contentsInlineElements, true),
                getEscapedBookTitle() + ".ent", "authorgroup");
        try {
            files.put(getBookLocaleFolder() + "Author_Group.xml", fixedAuthorGroupXml.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            /* UTF-8 is a valid format so this should exception should never get thrown */
            log.error(e.getMessage());
        }
    }

    /**
     * Builds the revision history using the requester of the build.
     *
     * @param requester          The user who requested the build action.
     * @param revisionHistoryXml The Revision_History.xml file/template to add revision information to.
     * @param contentSpec        The content spec object used to build the book.
     * @param files              The mapping of File Names/Locations to actual file content.
     * @throws BuildProcessingException TODO
     */
    protected void buildRevisionHistory(final ContentSpec contentSpec, final String revisionHistoryXml, final UserWrapper requester,
            final Map<String, byte[]> files) throws BuildProcessingException {
        log.info("\tBuilding Revision_History.xml");

        Document revHistoryDoc;
        try {
            revHistoryDoc = XMLUtilities.convertStringToDocument(revisionHistoryXml);
        } catch (SAXException ex) {
            /* Exit since we shouldn't fail at converting the basic revision history */
            log.debug(ExceptionUtilities.getStackTrace(ex));
            throw new BuildProcessingException("Failed to convert the Revision_History.xml template into a DOM document");
        }

        if (revHistoryDoc == null) {
            throw new BuildProcessingException("Failed to convert the Revision_History.xml template into a DOM document");
        }

        revHistoryDoc.getDocumentElement().setAttribute("id", "appe-" + getEscapedBookTitle() + "-Revision_History");

        final String reportHistoryTitleTranslation = getConstantTranslatedStrings().getProperty("REVISION_HISTORY");
        if (reportHistoryTitleTranslation != null) {
            DocBookUtilities.setRootElementTitle(reportHistoryTitleTranslation, revHistoryDoc);
        }

        /* Find the revhistory node */
        final Element revHistory;
        final NodeList revHistories = revHistoryDoc.getElementsByTagName("revhistory");
        if (revHistories.getLength() > 0) {
            revHistory = (Element) revHistories.item(0);
        } else {
            revHistory = null;
            throw new BuildProcessingException("Revision_History.xml Template has no revhistory block to add revisions to.");
        }

        final CollectionWrapper<TagWrapper> authorList = requester == null ? tagProvider.newTagCollection() : tagProvider.getTagsByName(
                requester.getUsername());

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        // An assigned writer tag exists for the User so check if there is an AuthorInformation tuple for that writer
        if (authorList.size() == 1) {
            AuthorInformation authorInfo = EntityUtilities.getAuthorInformation(providerFactory, authorList.getItems().get(0).getId());
            if (authorInfo != null) {
                final Element revision = generateRevision(contentSpec, revHistoryDoc, authorInfo, requester);

                addRevisionToRevHistory(revHistory, revision);
            } else {
                // No AuthorInformation so Use the default value
                authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME, BuilderConstants.DEFAULT_AUTHOR_LASTNAME,
                        BuilderConstants.DEFAULT_EMAIL);
                final Element revision = generateRevision(contentSpec, revHistoryDoc, authorInfo, requester);

                addRevisionToRevHistory(revHistory, revision);
            }
        }
        // No assigned writer exists for the uploader so use default values
        else {
            final AuthorInformation authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME,
                    BuilderConstants.DEFAULT_AUTHOR_LASTNAME, BuilderConstants.DEFAULT_EMAIL);
            final Element revision = generateRevision(contentSpec, revHistoryDoc, authorInfo, requester);

            addRevisionToRevHistory(revHistory, revision);
        }

        // Add the revision history to the book
        final String fixedRevisionHistoryXml = DocBookUtilities.addDocbook45XMLDoctype(
                XMLUtilities.convertNodeToString(revHistoryDoc, verbatimElements, inlineElements, contentsInlineElements, true),
                getEscapedBookTitle() + ".ent", "appendix");
        try {
            files.put(getBookLocaleFolder() + "Revision_History.xml", fixedRevisionHistoryXml.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            /* UTF-8 is a valid format so this should exception should never get thrown */
            log.error(e.getMessage());
        }
    }

    /**
     * Adds a revision element to the list of revisions in a revhistory element. This method ensures that the new revision is at
     * the top of the revhistory list.
     *
     * @param revHistory The revhistory element to add the revision to.
     * @param revision   The revision element to be added into the revisionhistory element.
     */
    private void addRevisionToRevHistory(final Node revHistory, final Node revision) {
        if (revHistory.hasChildNodes()) {
            revHistory.insertBefore(revision, revHistory.getFirstChild());
        } else {
            revHistory.appendChild(revision);
        }
    }

    /**
     * Fills in the information required inside of a revision tag, for the Revision_History.xml file.
     *
     * @param contentSpec The content spec to generate the revisions for.
     * @param xmlDoc      An XML DOM document that contains key regex expressions.
     * @param authorInfo  An AuthorInformation entity object containing the details for who requested the build.
     * @param requester   The user object for the build request.
     * @return Returns an XML element that represents a {@code <revision>} element initialised with the authors information.
     * @throws BuildProcessingException TODO
     */
    protected Element generateRevision(final ContentSpec contentSpec, final Document xmlDoc, final AuthorInformation authorInfo,
            final UserWrapper requester) throws BuildProcessingException {
        if (authorInfo == null) {
            return null;
        }

        // Build up the revision
        final Element revision = xmlDoc.createElement("revision");

        final Element revnumberEle = xmlDoc.createElement("revnumber");
        revision.appendChild(revnumberEle);

        final Element revDateEle = xmlDoc.createElement("date");
        final DateFormat dateFormatter = new SimpleDateFormat(BuilderConstants.REV_DATE_STRING_FORMAT, Locale.ENGLISH);
        revDateEle.setTextContent(dateFormatter.format(getBuildDate()));
        revision.appendChild(revDateEle);

        /*
         * Determine the revnumber to use. If we have an override specified then use that directly. If not then build up the
         * revision number using the Book Edition and Publication Number. The format to build it in is: <EDITION>-<PUBSNUMBER>.
         * If Edition only specifies a x or x.y version (eg 5 or 5.1) then postfix the version so it matches the x.y.z format
         * (eg 5.0.0).
         */
        final String overrideRevnumber = getBuildOptions().getOverrides().get(CSConstants.REVNUMBER_OVERRIDE);
        final String revnumber;
        if (overrideRevnumber == null) {
            revnumber = DocbookBuildUtilities.generateRevisionNumber(contentSpec);
        } else {
            revnumber = overrideRevnumber;
        }

        // Set the revision number in Revision_History.xml
        revnumberEle.setTextContent(revnumber);

        // Create the Author node
        final Element author = xmlDoc.createElement("author");
        revision.appendChild(author);

        final Element firstName = xmlDoc.createElement("firstname");
        firstName.setTextContent(authorInfo.getFirstName());
        author.appendChild(firstName);

        final Element lastName = xmlDoc.createElement("surname");
        lastName.setTextContent(authorInfo.getLastName());
        author.appendChild(lastName);

        final Element email = xmlDoc.createElement("email");
        email.setTextContent(authorInfo.getEmail() == null ? BuilderConstants.DEFAULT_EMAIL : authorInfo.getEmail());
        author.appendChild(email);

        // Create the Revision Messages
        final Element revDescription = xmlDoc.createElement("revdescription");
        revision.appendChild(revDescription);

        final Element simplelist = xmlDoc.createElement("simplelist");
        revDescription.appendChild(simplelist);

        // Add the custom revision messages if one or more exists.
        if (getBuildOptions().getRevisionMessages() != null && !getBuildOptions().getRevisionMessages().isEmpty()) {
            for (final String revMessage : getBuildOptions().getRevisionMessages()) {
                final Element revMemberEle = xmlDoc.createElement("member");
                revMemberEle.setTextContent(revMessage);
                simplelist.appendChild(revMemberEle);
            }
        }

        // Add the revision information
        final Element listMemberEle = xmlDoc.createElement("member");

        if (contentSpec.getId() != null && contentSpec.getId() > 0) {
            if (contentSpec.getRevision() == null) {
                listMemberEle.setTextContent(String.format(BuilderConstants.BUILT_MSG, contentSpec.getId(),
                        contentSpecProvider.getContentSpec(
                                contentSpec.getId()).getRevision()) + (authorInfo.getAuthorId() > 0 ? (" by " + requester.getUsername())
                        : ""));
            } else {
                listMemberEle.setTextContent(String.format(BuilderConstants.BUILT_MSG, contentSpec.getId(),
                        contentSpec.getRevision()) + (authorInfo.getAuthorId() > 0 ? (" by " + requester.getUsername()) : ""));
            }
        } else {
            listMemberEle.setTextContent(
                    BuilderConstants.BUILT_FILE_MSG + (authorInfo.getAuthorId() > 0 ? (" by " + requester.getUsername()) : ""));
        }

        simplelist.appendChild(listMemberEle);

        return revision;
    }

    /**
     * Builds a Chapter with a single paragraph, that contains a link to translate the Content Specification.
     *
     * @param contentSpec The content spec that was used to build the book.
     * @param locale      The locale the book was built in.
     * @return The Chapter represented as Docbook markup.
     */
    // TODO Fix this to work with translations
    private String buildTranslateCSChapter(final ContentSpec contentSpec, final String locale) {

        final CollectionWrapper<TranslatedTopicWrapper> translatedTopics = getTranslatedTopics(
                new HashSet<Integer>(CollectionUtilities.toArrayList(contentSpec.getId())), null);

        final String para;
        if (translatedTopics != null && translatedTopics.getItems() != null && !translatedTopics.getItems().isEmpty()) {
            // TODO fix this to get the right locale
            final TranslatedTopicWrapper translatedContentSpec = translatedTopics.getItems().get(0);
            final String url = translatedContentSpec.getEditorURL(getZanataDetails());

            if (url != null) {
                para = DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(url, "Translate this Content Spec"));
            } else {
                para = DocBookUtilities.wrapInPara(
                        "No editor link available as this Content Specification hasn't been pushed for Translation.");
            }
        } else {
            para = DocBookUtilities.wrapInPara(
                    "No editor link available as this Content Specification hasn't been pushed for Translation.");
        }

        if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
            return DocBookUtilities.buildSection(para, "Content Specification");
        } else {
            return DocBookUtilities.buildChapter(para, "Content Specification");
        }
    }

    /**
     * Builds the Error Chapter that contains all warnings and errors. It also builds a glossary to define most of the error
     * messages.
     *
     * @param contentSpec TODO
     * @param locale      The locale of the book.
     * @return A docbook formatted string representation of the error chapter.
     */
    private String buildErrorChapter(final ContentSpec contentSpec, final String locale) {
        log.info("\tBuilding Error Chapter");

        String errorItemizedLists = "";

        if (getTopicErrorDatabase().hasItems(locale)) {
            for (final TopicErrorData topicErrorData : getTopicErrorDatabase().getErrors(locale)) {
                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return null;
                }

                final BaseTopicWrapper<?> topic = topicErrorData.getTopic();

                final List<String> topicErrorItems = new ArrayList<String>();

                final String tags = EntityUtilities.getCommaSeparatedTagList(topic);
                final String url = topic.getPressGangURL();

                topicErrorItems.add(DocBookUtilities.buildListItem("INFO: " + tags));
                topicErrorItems.add(DocBookUtilities.buildListItem("INFO: <ulink url=\"" + url + "\">Topic URL</ulink>"));

                for (final String error : topicErrorData.getItemsOfType(ErrorLevel.ERROR)) {
                    topicErrorItems.add(DocBookUtilities.buildListItem("ERROR: " + error));
                }

                for (final String warning : topicErrorData.getItemsOfType(ErrorLevel.WARNING)) {
                    topicErrorItems.add(DocBookUtilities.buildListItem("WARNING: " + warning));
                }

                /*
                 * this should never be false, because a topic will only be listed in the errors collection once a error or
                 * warning has been added. The count of 2 comes from the standard list items we added above for the tags and
                 * url.
                 */
                if (topicErrorItems.size() > 2) {
                    final String title;
                    if (topic instanceof TranslatedTopicWrapper) {
                        title = "Topic ID " + topic.getTopicId() + ", Revision " + topic.getTopicRevision();
                    } else {
                        title = "Topic ID " + topic.getTopicId();
                    }
                    final String id = topic.getErrorXRefId();

                    errorItemizedLists += DocBookUtilities.wrapListItems(topicErrorItems, title, id);
                }
            }

            // Create the glossary
            final String errorGlossary = buildErrorChapterGlossary("Compiler Glossary");
            if (errorGlossary != null) {
                errorItemizedLists += errorGlossary;
            }
        } else {
            errorItemizedLists = "<para>No Errors Found</para>";
        }

        if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
            return DocBookUtilities.buildSection(errorItemizedLists, "Compiler Output");
        } else {
            return DocBookUtilities.buildChapter(errorItemizedLists, "Compiler Output");
        }
    }

    /**
     * Builds the Glossary used in the Error Chapter.
     *
     * @param title The title for the glossary.
     * @return A docbook formatted string representation of the glossary.
     */
    private String buildErrorChapterGlossary(final String title) {
        final StringBuilder glossary = new StringBuilder("<glossary>");

        // Add the title of the glossary
        glossary.append("<title>");
        if (title != null) {
            glossary.append(title);
        }
        glossary.append("</title>");

        // Add generic error messages

        // No Content Warning
        glossary.append(
                DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_EMPTY_TOPIC_XML + "\""),
                        DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_NO_CONTENT_TOPIC_DEFINTIION)));

        // Invalid XML entity or element
        glossary.append(DocBookUtilities.wrapInGlossEntry(
                DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_INVALID_XML_CONTENT + "\""),
                DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_INVALID_XML_CONTENT_DEFINTIION)));

        // No Content Error
        glossary.append(
                DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_BAD_XML_STRUCTURE + "\""),
                        DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_BAD_XML_STRUCTURE_DEFINTIION)));

        // Invalid Docbook XML Error
        glossary.append(
                DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_INVALID_TOPIC_XML + "\""),
                        DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_INVALID_TOPIC_XML_DEFINTIION)));

        // Invalid Injections Error
        glossary.append(
                DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_INVALID_INJECTIONS + "\""),
                        DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_INVALID_INJECTIONS_DEFINTIION)));

        // Add the glossary terms and definitions
        if (isTranslationBuild()) {
            // Incomplete translation warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_INCOMPLETE_TRANSLATION + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_INCOMPLETE_TRANSLATED_TOPIC_DEFINTIION)));

            // Fuzzy translation warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_FUZZY_TRANSLATION + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_FUZZY_TRANSLATED_TOPIC_DEFINTIION)));

            // Untranslated Content warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_UNTRANSLATED_TOPIC + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_UNTRANSLATED_TOPIC_DEFINTIION)));

            // Non Pushed Translation Content warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_NONPUSHED_TOPIC + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_NONPUSHED_TOPIC_DEFINTIION)));

            // Old Translation Content warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_OLD_TRANSLATED_TOPIC + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_OLD_TRANSLATED_TOPIC_DEFINTIION)));

            // Old Untranslated Content warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_OLD_UNTRANSLATED_TOPIC + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_OLD_UNTRANSLATED_TOPIC_DEFINTIION)));
        }

        glossary.append("</glossary>");

        return glossary.toString();
    }

    /**
     * Builds a Report Chapter to be included in the book that displays a count of different types of errors and then a table to
     * list the errors, providing links and basic topic data.
     *
     * @param locale      The locale to build the report chapter for.
     * @param contentSpec The content spec object used to build the book.
     * @return The Docbook Report Chapter formatted as a String.
     */
    private String buildReportChapter(final ContentSpec contentSpec, final String locale) {
        log.info("\tBuilding Report Chapter");

        String reportChapter = "";

        final List<TopicErrorData> noContentTopics = getTopicErrorDatabase().getErrorsOfType(locale, ErrorType.NO_CONTENT);
        final List<TopicErrorData> invalidInjectionTopics = getTopicErrorDatabase().getErrorsOfType(locale, ErrorType.INVALID_INJECTION);
        final List<TopicErrorData> invalidContentTopics = getTopicErrorDatabase().getErrorsOfType(locale, ErrorType.INVALID_CONTENT);
        final List<TopicErrorData> invalidImageTopics = getTopicErrorDatabase().getErrorsOfType(locale, ErrorType.INVALID_IMAGES);
        final List<TopicErrorData> untranslatedTopics = getTopicErrorDatabase().getErrorsOfType(locale, ErrorType.UNTRANSLATED);
        final List<TopicErrorData> incompleteTranslatedTopics = getTopicErrorDatabase().getErrorsOfType(locale,
                ErrorType.INCOMPLETE_TRANSLATION);
        final List<TopicErrorData> fuzzyTranslatedTopics = getTopicErrorDatabase().getErrorsOfType(locale, ErrorType.FUZZY_TRANSLATION);
        final List<TopicErrorData> notPushedTranslatedTopics = getTopicErrorDatabase().getErrorsOfType(locale,
                ErrorType.NOT_PUSHED_FOR_TRANSLATION);
        final List<TopicErrorData> oldTranslatedTopics = getTopicErrorDatabase().getErrorsOfType(locale, ErrorType.OLD_TRANSLATION);
        final List<TopicErrorData> oldUntranslatedTopics = getTopicErrorDatabase().getErrorsOfType(locale, ErrorType.OLD_UNTRANSLATED);

        final List<String> list = new LinkedList<String>();
        list.add(DocBookUtilities.buildListItem("Total Number of Errors: " + getNumErrors()));
        list.add(DocBookUtilities.buildListItem("Total Number of Warnings: " + getNumWarnings()));
        list.add(DocBookUtilities.buildListItem("Number of Topics with No Content: " + noContentTopics.size()));
        list.add(DocBookUtilities.buildListItem("Number of Topics with Invalid Injection points: " + invalidInjectionTopics.size()));
        list.add(DocBookUtilities.buildListItem("Number of Topics with Invalid Content: " + invalidContentTopics.size()));
        list.add(DocBookUtilities.buildListItem("Number of Topics with Invalid Image references: " + invalidImageTopics.size()));

        if (isTranslationBuild()) {
            list.add(DocBookUtilities.buildListItem(
                    "Number of Topics that haven't been pushed for Translation: " + notPushedTranslatedTopics.size()));
            list.add(DocBookUtilities.buildListItem("Number of Topics that haven't been Translated: " + untranslatedTopics.size()));
            list.add(DocBookUtilities.buildListItem(
                    "Number of Topics that have incomplete Translations: " + incompleteTranslatedTopics.size()));
            list.add(DocBookUtilities.buildListItem("Number of Topics that have fuzzy Translations: " + fuzzyTranslatedTopics.size()));
            list.add(DocBookUtilities.buildListItem(
                    "Number of Topics that haven't been Translated but are using previous revisions: " + oldUntranslatedTopics.size()));
            list.add(DocBookUtilities.buildListItem(
                    "Number of Topics that have been Translated using a previous revision: " + oldTranslatedTopics.size()));
        }

        reportChapter += DocBookUtilities.wrapListItems(list, "Build Statistics");

        // Add a link to show the zanata statistics
        if (isTranslationBuild()) {
            reportChapter += generateAllTopicZanataUrl(contentSpec);
        }

        final boolean showEditorLinks = getBuildOptions().getInsertEditorLinks();

        /* Create the Report Tables */
        reportChapter += ReportUtilities.buildReportTable(noContentTopics, "Topics that have no Content", showEditorLinks,
                getZanataDetails());

        reportChapter += ReportUtilities.buildReportTable(invalidContentTopics, "Topics that have Invalid XML Content", showEditorLinks,
                getZanataDetails());

        reportChapter += ReportUtilities.buildReportTable(invalidInjectionTopics, "Topics that have Invalid Injection points in the XML",
                showEditorLinks, getZanataDetails());

        reportChapter += ReportUtilities.buildReportTable(invalidImageTopics, "Topics that have Invalid Image references in the XML",
                showEditorLinks, getZanataDetails());

        if (isTranslationBuild()) {
            reportChapter += ReportUtilities.buildReportTable(notPushedTranslatedTopics, "Topics that haven't been pushed for Translation",
                    showEditorLinks, getZanataDetails());

            reportChapter += ReportUtilities.buildReportTable(untranslatedTopics, "Topics that haven't been Translated", showEditorLinks,
                    getZanataDetails());

            reportChapter += ReportUtilities.buildReportTable(incompleteTranslatedTopics, "Topics that have Incomplete Translations",
                    showEditorLinks, getZanataDetails());

            reportChapter += ReportUtilities.buildReportTable(fuzzyTranslatedTopics, "Topics that have fuzzy Translations", showEditorLinks,
                    getZanataDetails());

            reportChapter += ReportUtilities.buildReportTable(oldUntranslatedTopics,
                    "Topics that haven't been Translated but are using previous revisions", showEditorLinks, getZanataDetails());

            reportChapter += ReportUtilities.buildReportTable(oldTranslatedTopics,
                    "Topics that have been Translated using a previous revision", showEditorLinks, getZanataDetails());
        }

        if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
            return DocBookUtilities.buildSection(reportChapter, "Status Report");
        } else {
            return DocBookUtilities.buildChapter(reportChapter, "Status Report");
        }
    }

    /**
     * Generates a set of docbook paragraphs containing links to all the Topics in Zanata.
     *
     * @param contentSpec The content spec object used to build the book.
     * @return The docbook generated content.
     */
    protected String generateAllTopicZanataUrl(final ContentSpec contentSpec) {
        final String zanataServerUrl = getZanataDetails() == null ? null : getZanataDetails().getServer();
        final String zanataProject = getZanataDetails() == null ? null : getZanataDetails().getProject();
        final String zanataVersion = getZanataDetails() == null ? null : getZanataDetails().getVersion();

        String reportChapter = "";
        if (zanataServerUrl != null && !zanataServerUrl.isEmpty() && zanataProject != null && !zanataProject.isEmpty() && zanataVersion
                != null && !zanataVersion.isEmpty()) {

            final List<StringBuilder> zanataUrls = new ArrayList<StringBuilder>();
            StringBuilder zanataUrl = new StringBuilder(zanataServerUrl);
            zanataUrls.add(zanataUrl);

            zanataUrl.append("webtrans/Application.html?project=" + zanataProject);
            zanataUrl.append("&amp;");
            zanataUrl.append("iteration=" + zanataVersion);
            zanataUrl.append("&amp;");
            zanataUrl.append("localeId=" + getBuildLocale());

            // Add all the Topic Zanata Ids
            final List<TranslatedTopicWrapper> topics = getSpecDatabase().getAllTopics();
            int andCount = 0;
            for (final TranslatedTopicWrapper topic : topics) {
                // Check to make sure the topic has been pushed for translation
                if (!EntityUtilities.isDummyTopic(topic) || EntityUtilities.hasBeenPushedForTranslation(topic)) {
                    zanataUrl.append("&amp;");
                    andCount++;
                    zanataUrl.append("doc=" + ((TranslatedTopicWrapper) topic).getZanataId());
                }

                // If the URL gets too big create a second, third, etc... URL.
                if (zanataUrl.length() > (MAX_URL_LENGTH + andCount * 4)) {
                    zanataUrl = new StringBuilder(zanataServerUrl);
                    zanataUrls.add(zanataUrl);

                    zanataUrl.append("webtrans/Application.html?project=" + zanataProject);
                    zanataUrl.append("&amp;");
                    zanataUrl.append("iteration=" + zanataVersion);
                    zanataUrl.append("&amp;");
                    zanataUrl.append("localeId=" + getBuildLocale());
                }
            }

            // Add the CSP Zanata ID
            final CollectionWrapper<TranslatedTopicWrapper> translatedTopics = getTranslatedTopics(
                    new HashSet<Integer>(CollectionUtilities.toArrayList(contentSpec.getId())), null);

            if (translatedTopics != null && translatedTopics.getItems() != null && !translatedTopics.getItems().isEmpty()) {
                final TranslatedTopicWrapper translatedContentSpec = translatedTopics.getItems().get(0);

                // Check to make sure the Content Spec has been pushed for translation
                if (!EntityUtilities.isDummyTopic(translatedContentSpec) || EntityUtilities.hasBeenPushedForTranslation(
                        translatedContentSpec)) {
                    zanataUrl.append("&amp;");
                    zanataUrl.append("doc=" + translatedContentSpec.getZanataId());
                }
            }

            // Generate the docbook elements for the links
            for (int i = 1; i <= zanataUrls.size(); i++) {
                final String para;

                if (zanataUrls.size() > 1) {
                    para = DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(zanataUrls.get(i - 1).toString(),
                            "View Topics and Statistics in Zanata (" + i + "/" + zanataUrls.size() + ")"));
                } else {
                    para = DocBookUtilities.wrapInPara(
                            DocBookUtilities.buildULink(zanataUrl.toString(), "View Topics and Statistics in Zanata"));
                }

                reportChapter += para;
            }
        }

        return reportChapter;
    }

    /**
     * Processes the Topics in the SpecDatabase and builds up the images found within the topics XML. If the image reference is
     * blank or invalid it is replaced by the fail penguin image.
     */
    @SuppressWarnings("unchecked")
    private void processImageLocations() {
        final List<Integer> topicIds = getSpecDatabase().getTopicIds();
        for (final Integer topicId : topicIds) {
            // TODO Deal with different spec topic revisions in this method once images are fixed
            final SpecTopic specTopic = getSpecDatabase().getSpecTopicsForTopicID(topicId).get(0);
            final BaseTopicWrapper<?> topic = specTopic.getTopic();

            if (log.isDebugEnabled()) log.debug("\tProcessing SpecTopic " + specTopic.getId() + (specTopic.getRevision() != null ? (", " +
                    "Revision " + specTopic.getRevision()) : ""));

            /*
             * Images have to be in the image folder in Publican. Here we loop through all the imagedata elements and fix up any
             * reference to an image that is not in the images folder.
             */
            final List<Node> images = XMLUtilities.getChildNodes(specTopic.getXmlDocument(), "imagedata", "inlinegraphic");

            for (final Node imageNode : images) {
                final NamedNodeMap attributes = imageNode.getAttributes();
                if (attributes != null) {
                    final Node fileRefAttribute = attributes.getNamedItem("fileref");

                    if (fileRefAttribute != null && (fileRefAttribute.getNodeValue() == null || fileRefAttribute.getNodeValue().isEmpty()
                    )) {
                        fileRefAttribute.setNodeValue("images/" + BuilderConstants.FAILPENGUIN_PNG_NAME + ".jpg");
                        getImageLocations().add(new TopicImageData(topic, fileRefAttribute.getNodeValue()));
                    } else if (fileRefAttribute != null) {
                        // TODO Uncomment once image processing is fixed.
//                        if (specTopic.getRevision() == null)
//                        {
                        if (fileRefAttribute != null && !fileRefAttribute.getNodeValue().startsWith("images/")) {
                            fileRefAttribute.setNodeValue("images/" + fileRefAttribute.getNodeValue());
                        }

                        getImageLocations().add(new TopicImageData(topic, fileRefAttribute.getNodeValue()));

//                        } else {
//                            if (fileRefAttribute != null && !fileRefAttribute.getNodeValue().startsWith("images/")) {
//                                fileRefAttribute.setNodeValue("images/" + fileRefAttribute.getNodeValue());
//                            }
//
//                            // Add the revision number to the name
//                            final String imageFileRef = fileRefAttribute.getNodeValue();
//                            final int extensionIndex = imageFileRef.lastIndexOf(".");
//                            final String fixedImageFileRef;
//                            if (extensionIndex != -1) {
//                                fixedImageFileRef = imageFileRef.substring(0, extensionIndex) + "-" + specTopic.getRevision() +
// imageFileRef
//                                        .substring(extensionIndex);
//                            } else {
//                                fixedImageFileRef = imageFileRef + "-" + specTopic.getRevision();
//                            }
//
//                            fileRefAttribute.setNodeValue(fixedImageFileRef);
//
//                            getImageLocations().add(new TopicImageData(topic, fileRefAttribute.getNodeValue(), specTopic.getRevision()));
//                        }
                    }
                }
            }
        }
    }

    /**
     * Validates the XML after the first set of injections have been processed.
     *
     * @param specTopic    The topic that is being validated.
     * @param topicDoc     A Document object that holds the Topic's XML
     * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
     * @return The validate document or a template if it failed validation.
     * @throws BuildProcessingException
     */
    @SuppressWarnings("unchecked")
    private boolean validateTopicXML(final SpecTopic specTopic, final Document topicDoc,
            final boolean useFixedUrls) throws BuildProcessingException {
        final BaseTopicWrapper<?> topic = specTopic.getTopic();

        // Validate the topic against its DTD/Schema
        final SAXXMLValidator validator = new SAXXMLValidator();
        if (!validator.validateXML(topicDoc, BuilderConstants.ROCBOOK_45_DTD, rocbookdtd.getValue())) {
            final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                    getErrorInvalidValidationTopicTemplate().getValue(), getBuildOptions());

            final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(
                    XMLUtilities.convertNodeToString(topicDoc, verbatimElements, inlineElements, contentsInlineElements, true));
            getTopicErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                    BuilderConstants.ERROR_INVALID_TOPIC_XML + " The error is <emphasis>" + validator.getErrorText() + "</emphasis>. The " +
                            "processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");
            setSpecTopicXMLForError(specTopic, topicXMLErrorTemplate, useFixedUrls);

            return false;
        }
        /* Check to ensure that if the topic has a table, that the table isn't missing any entries */
        else if (!DocbookBuildUtilities.validateTopicTables(topicDoc)) {
            final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                    getErrorInvalidValidationTopicTemplate().getValue(), getBuildOptions());

            final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(
                    XMLUtilities.convertNodeToString(topicDoc, verbatimElements, inlineElements, contentsInlineElements, true));
            getTopicErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                    BuilderConstants.ERROR_INVALID_TOPIC_XML + " Table column declaration doesn't match the number of entry elements. The" +
                            " processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");
            setSpecTopicXMLForError(specTopic, topicXMLErrorTemplate, useFixedUrls);

            return false;
        }

        return true;
    }

    /**
     * Sets the XML of the topic to the specified error template.
     *
     * @param topic        The topic to be updated as having an error.
     * @param template     The template for the Error Message.
     * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
     * @return The Document Object that is initialised using the topic and error template.
     * @throws BuildProcessingException TODO
     */
    protected <T extends BaseTopicWrapper<T>> Document setTopicXMLForError(final T topic, final String template,
            final boolean useFixedUrls) throws BuildProcessingException {
        Document doc = null;
        try {
            doc = XMLUtilities.convertStringToDocument(template);
        } catch (SAXException ex) {
            /* Exit since we shouldn't fail at converting a basic template */
            log.debug(ExceptionUtilities.getStackTrace(ex));
            throw new BuildProcessingException("Failed to convert the Topic Error template into a DOM document");
        }
        DocBookUtilities.setSectionTitle(topic.getTitle(), doc);
        processTopicID(topic, doc, useFixedUrls);
        return doc;
    }

    /**
     * Sets the XML of the topic in the content spec to the error template provided.
     *
     * @param specTopic    The spec topic to be updated as having an error.
     * @param template     The template for the Error Message.
     * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
     * @throws BuildProcessingException TODO
     */
    protected void setSpecTopicXMLForError(final SpecTopic specTopic, final String template,
            final boolean useFixedUrls) throws BuildProcessingException {
        final BaseTopicWrapper<?> topic = specTopic.getTopic();

        Document doc = null;
        try {
            doc = XMLUtilities.convertStringToDocument(template);
        } catch (SAXException ex) {
            /* Exit since we shouldn't fail at converting a basic template */
            log.debug(ExceptionUtilities.getStackTrace(ex));
            throw new BuildProcessingException("Failed to convert the Topic Error template into a DOM document");
        }
        specTopic.setXmlDocument(doc);
        DocBookUtilities.setSectionTitle(topic.getTitle(), doc);
        processTopicID(topic, doc, useFixedUrls);
    }

    /**
     * Sets the topic xref id to the topic database id.
     *
     * @param topic        The topic to be used to set the id attribute.
     * @param doc          The document object for the topics XML.
     * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
     */
    protected void processTopicID(final BaseTopicWrapper<?> topic, final Document doc, final boolean useFixedUrls) {
        if (useFixedUrls) {
            final String errorXRefID = topic.getXRefPropertyOrId(CommonConstants.FIXED_URL_PROP_TAG_ID);
            doc.getDocumentElement().setAttribute("id", errorXRefID);
        } else {
            final String errorXRefID = topic.getXRefId();
            doc.getDocumentElement().setAttribute("id", errorXRefID);
        }

        final Integer topicId = topic.getTopicId();
        doc.getDocumentElement().setAttribute("remap", "TID_" + topicId);
    }

    /**
     * Process a topic and add the section info information. This information consists of the keywordset information. The
     * keywords are populated using the tags assigned to the topic.
     *
     * @param topic The Topic to create the sectioninfo for.
     * @param doc   The XML Document DOM oject for the topics XML.
     */
    protected <T extends BaseTopicWrapper<T>> void processTopicSectionInfo(final T topic, final Document doc) {
        if (doc == null || topic == null) return;

        final CollectionWrapper<TagWrapper> tags = topic.getTags();

        if (tags != null && tags.getItems() != null && tags.getItems().size() > 0) {
            /* Find the sectioninfo node in the document, or create one if it doesn't exist */
            final Element sectionInfo;
            final List<Node> sectionInfoNodes = XMLUtilities.getDirectChildNodes(doc.getDocumentElement(), "sectioninfo");
            if (sectionInfoNodes.size() == 1) {
                sectionInfo = (Element) sectionInfoNodes.get(0);
            } else {
                sectionInfo = doc.createElement("sectioninfo");
            }

            /* Build up the keywordset */
            final Element keywordSet = doc.createElement("keywordset");

            final List<TagWrapper> tagItems = tags.getItems();
            for (final TagWrapper tag : tagItems) {
                if (tag.getName() == null || tag.getName().isEmpty()) continue;

                if (tag.containedInCategories(validKeywordCategoryIds)) {
                    final Element keyword = doc.createElement("keyword");
                    keyword.setTextContent(tag.getName());

                    keywordSet.appendChild(keyword);
                }
            }

            /* Only update the section info if we've added data */
            if (keywordSet.hasChildNodes()) {
                sectionInfo.appendChild(keywordSet);

                DocBookUtilities.setSectionInfo(sectionInfo, doc);
            }
        }
    }

    /**
     * This method does a pass over all the topics returned by the query and attempts to create unique Fixed URL if one does not
     * already exist.
     *
     * @param topics             The list of topics to set the Fixed URL's for.
     * @param processedFileNames TODO
     * @return True if the fixed url property tags were able to be created for all topics, and false otherwise.
     */
    protected boolean setFixedURLsPass(final CollectionWrapper<TopicWrapper> topics, final Set<String> processedFileNames) {
        log.info("Doing Fixed URL Pass");

        int tries = 0;
        boolean success = false;

        try {
            // This first pass will update or correct the fixed url property tags on the current revision
            while (tries < BuilderConstants.MAXIMUM_SET_PROP_TAGS_RETRY && !success) {
                ++tries;
                final CollectionWrapper<TopicWrapper> updateTopics = topicProvider.newTopicCollection();

                final List<TopicWrapper> topicItems = topics.getItems();
                for (final TopicWrapper topic : topicItems) {

                    // Check if the app should be shutdown
                    if (isShuttingDown.get()) {
                        return false;
                    }

                    // Create the PropertyTagCollection to be used to update any data
                    final UpdateableCollectionWrapper<PropertyTagInTopicWrapper> updatePropertyTags = propertyTagProvider
                            .newPropertyTagInTopicCollection(
                            topic);

                    // Get a list of all property tag items that exist for the current topic
                    /*final List<RESTAssignedPropertyTagCollectionItemV1> existingUniqueURLs = ComponentTopicV1.returnPropertyItems(topic,
                            CommonConstants.FIXED_URL_PROP_TAG_ID);*/

                    PropertyTagInTopicWrapper existingUniqueURL = topic.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);

                    // Remove any Duplicate Fixed URL's
                    // TODO
                    /*for (int i = 0; i < existingUniqueURLs.size(); i++) {
                        final RESTAssignedPropertyTagCollectionItemV1 propertyTag = existingUniqueURLs.get(i);
                        if (propertyTag.getItem() == null) continue;

                        if (i == 0) {
                            existingUniqueURL = propertyTag.getItem();
                        } else {
                            updatePropertyTags.addRemoveItem(propertyTag.getItem());
                            topic.getProperties().getItems().remove(propertyTag);
                        }
                    }*/

                    if (existingUniqueURL == null || !existingUniqueURL.isValid()) {
                        // generate the base url
                        String baseUrlName = DocbookBuildUtilities.createURLTitle(topic.getTitle());

                        // generate a unique fixed url
                        String postFix = "";

                        for (int uniqueCount = 1; uniqueCount <= BuilderConstants.MAXIMUM_SET_PROP_TAG_NAME_RETRY; ++uniqueCount) {
                            final String query = "query;propertyTag" + CommonConstants.FIXED_URL_PROP_TAG_ID + "=" + URLEncoder.encode(
                                    baseUrlName + postFix, ENCODING);
                            final CollectionWrapper<TopicWrapper> queryTopics = topicProvider.getTopicsWithQuery(query);

                            if (queryTopics.size() != 0 || processedFileNames.contains(baseUrlName + postFix)) {
                                postFix = uniqueCount + "";
                            } else {
                                break;
                            }
                        }

                        // Check if the app should be shutdown
                        if (isShuttingDown.get()) {
                            return false;
                        }

                        // persist the new fixed url, as long as we are not looking at a landing page topic
                        if (topic.getId() >= 0) {

                            // update any old fixed url property tags
                            boolean found = false;
                            if (topic.getProperties() != null && topic.getProperties().getItems() != null) {
                                final List<PropertyTagInTopicWrapper> propertyTags = topic.getProperties().getItems();
                                for (final PropertyTagInTopicWrapper existing : propertyTags) {
                                    if (existing.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID)) {
                                        if (found) {
                                            // If we've already found one then we need to remove any duplicates
                                            updatePropertyTags.addRemoveItem(existing);
                                        } else {
                                            found = true;
                                            existing.setValue(baseUrlName + postFix);

                                            updatePropertyTags.addUpdateItem(existing);
                                        }
                                    }
                                }
                            }

                            // If we didn't find any tags then add a new one
                            if (!found) {
                                final PropertyTagInTopicWrapper propertyTag = propertyTagProvider.newPropertyTagInTopic(topic);
                                propertyTag.setId(CommonConstants.FIXED_URL_PROP_TAG_ID);
                                propertyTag.setValue(baseUrlName + postFix);

                                updatePropertyTags.addNewItem(propertyTag);
                            }
                            processedFileNames.add(baseUrlName + postFix);
                        }
                    } else {
                        processedFileNames.add(existingUniqueURL.getValue());
                    }

                    // If we have changes then create a basic topic so that the property tags can be updated.
                    if (!updatePropertyTags.getItems().isEmpty()) {
                        final TopicWrapper updateTopic = topicProvider.newTopic();
                        updateTopic.setId(topic.getId());

                        updateTopic.setProperties(updatePropertyTags);
                        updateTopics.addItem(updateTopic);
                    }
                }

                if (updateTopics.getItems() != null && updateTopics.getItems().size() != 0) {
                    topicProvider.updateTopics(updateTopics);
                }

                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return false;
                }

                // If we got here, then the REST update went ok
                success = true;

                updateFixedURLsForTopics(updateTopics, topicItems);
            }
        } catch (final Exception ex) {
            // Dump the exception to the command prompt, and restart the loop
            log.error(ExceptionUtilities.getStackTrace(ex));
        }

        // did we blow the try count?
        return success;
    }

    /**
     * Ensure that the FixedURL Properties for revision topics are still valid inside the book. Revision topics can either be
     * Normal Topics or Translated Topics (which are actually a saved normal revision).
     *
     * @param topics             The list of revision topics.
     * @param processedFileNames A List of file names that has already been processed. (ie in the setFixedURLsPass() method)
     */
    protected <T extends BaseTopicWrapper<T>> void setFixedURLsForRevisionsPass(final CollectionWrapper<T> topics,
            final Set<String> processedFileNames) {
        log.info("Doing Revisions Fixed URL Pass");

        /*
         * Now loop over the revision topics, and make sure their fixed url property tags are unique. They only have to be
         * unique within the book.
         */
        final List<T> topicItems = topics.getItems();
        for (final BaseTopicWrapper<?> topic : topicItems) {

            /* Get the existing property tag */
            PropertyTagInTopicWrapper existingUniqueURL = topic.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);

            /* Create a property tag if none exists */
            if (existingUniqueURL == null) {
                existingUniqueURL = propertyTagProvider.newPropertyTagInTopic(topic);
                existingUniqueURL.setId(CommonConstants.FIXED_URL_PROP_TAG_ID);
                topic.getProperties().addItem(existingUniqueURL);
            }

            if (existingUniqueURL.getValue() == null || existingUniqueURL.getValue().isEmpty() || processedFileNames.contains(
                    existingUniqueURL.getValue())) {

                final String baseUrlName;
                if (topic instanceof TranslatedTopicWrapper) {
                    baseUrlName = DocbookBuildUtilities.createURLTitle(((TranslatedTopicWrapper) topic).getTopic().getTitle());
                } else {
                    baseUrlName = DocbookBuildUtilities.createURLTitle(topic.getTitle());
                }
                String postFix = "";
                for (int uniqueCount = 1; ; ++uniqueCount) {
                    if (!processedFileNames.contains(baseUrlName + postFix)) {
                        break;
                    } else {
                        postFix = uniqueCount + "";
                    }
                }

                /* Update the fixed url */
                existingUniqueURL.setValue(baseUrlName + postFix);
            }

            processedFileNames.add(existingUniqueURL.getValue());
        }
    }

    /**
     * Update the Fixed URL Property Tags from a collection of updated topics.
     *
     * @param updatedTopics  The collection of updated topics.
     * @param originalTopics The collection of original topics.
     */
    protected void updateFixedURLsForTopics(final CollectionWrapper<TopicWrapper> updatedTopics, final List<TopicWrapper> originalTopics) {
        /* copy the topics fixed url properties to our local collection */
        if (updatedTopics.getItems() != null && updatedTopics.getItems().size() != 0) {
            final List<TopicWrapper> updateItems = updatedTopics.getItems();
            for (final TopicWrapper topicWithFixedUrl : updateItems) {
                for (final TopicWrapper topic : originalTopics) {
                    final PropertyTagInTopicWrapper fixedUrlProp = topicWithFixedUrl.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);

                    if (topic != null && topicWithFixedUrl.getId().equals(topic.getId())) {
                        CollectionWrapper<PropertyTagInTopicWrapper> properties = topic.getProperties();
                        if (properties == null) {
                            properties = propertyTagProvider.newPropertyTagInTopicCollection(topic);
                        } else if (properties.getItems() != null) {
                            // remove any current url's
                            final List<PropertyTagInTopicWrapper> propertyTags = new ArrayList<PropertyTagInTopicWrapper>(
                                    properties.getItems());
                            for (final PropertyTagInTopicWrapper prop : propertyTags) {
                                if (prop.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID)) {
                                    properties.remove(prop);
                                }
                            }
                        }

                        if (fixedUrlProp != null) {
                            properties.addItem(fixedUrlProp);
                        }
                    }

                    /*
                     * we also have to copy the fixed urls into the related topics
                     */
                    if (topic != null && topic.getOutgoingRelationships() != null && topic.getOutgoingRelationships().getItems() != null) {
                        final List<TopicWrapper> relatedTopics = topic.getOutgoingRelationships().getItems();
                        for (final TopicWrapper relatedTopic : relatedTopics) {
                            if (topicWithFixedUrl.getId().equals(relatedTopic.getId())) {
                                CollectionWrapper<PropertyTagInTopicWrapper> relatedTopicProperties = relatedTopic.getProperties();
                                if (relatedTopicProperties == null) {
                                    relatedTopicProperties = propertyTagProvider.newPropertyTagInTopicCollection(relatedTopic);
                                } else if (relatedTopicProperties.getItems() != null) {
                                    // remove any current url's
                                    final List<PropertyTagInTopicWrapper> relatedTopicPropertyTags = new
                                            ArrayList<PropertyTagInTopicWrapper>(
                                            relatedTopicProperties.getItems());
                                    for (final PropertyTagInTopicWrapper prop : relatedTopicPropertyTags) {
                                        if (prop.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID)) {
                                            relatedTopicProperties.remove(prop);
                                        }
                                    }
                                }

                                if (fixedUrlProp != null) {
                                    relatedTopicProperties.addItem(fixedUrlProp);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
