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
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.CSDocbookBuildingOptions;
import org.jboss.pressgang.ccms.contentspec.builder.structures.XMLFormatProperties;
import org.jboss.pressgang.ccms.contentspec.builder.utils.DocbookBuildUtilities;
import org.jboss.pressgang.ccms.contentspec.builder.utils.ReportUtilities;
import org.jboss.pressgang.ccms.contentspec.builder.utils.SAXXMLValidator;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.contentspec.entities.AuthorInformation;
import org.jboss.pressgang.ccms.contentspec.enums.BookType;
import org.jboss.pressgang.ccms.contentspec.enums.LevelType;
import org.jboss.pressgang.ccms.contentspec.interfaces.ShutdownAbleApp;
import org.jboss.pressgang.ccms.contentspec.sort.AuthorInformationComparator;
import org.jboss.pressgang.ccms.contentspec.utils.ContentSpecUtilities;
import org.jboss.pressgang.ccms.contentspec.utils.EntityUtilities;
import org.jboss.pressgang.ccms.docbook.constants.DocbookBuilderConstants;
import org.jboss.pressgang.ccms.docbook.processing.DocbookXMLPreProcessor;
import org.jboss.pressgang.ccms.docbook.structures.TocTopicDatabase;
import org.jboss.pressgang.ccms.docbook.structures.TopicErrorData;
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
import org.jboss.pressgang.ccms.provider.TranslatedCSNodeProvider;
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
import org.jboss.pressgang.ccms.wrapper.TranslatedCSNodeStringWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedCSNodeWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedContentSpecWrapper;
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

public class DocbookBuilder implements ShutdownAbleApp {
    protected static final Logger log = Logger.getLogger(DocbookBuilder.class);
    protected static final List<Integer> validKeywordCategoryIds = CollectionUtilities.toArrayList(CSConstants.TECHNOLOGY_CATEGORY_ID,
            CSConstants.RELEASE_CATEGORY_ID, CSConstants.SEO_METADATA_CATEGORY_ID, CSConstants.COMMON_NAME_CATEGORY_ID,
            CSConstants.CONCERN_CATEGORY_ID, CSConstants.CONTENT_TYPE_CATEGORY_ID, CSConstants.PROGRAMMING_LANGUAGE_CATEGORY_ID);
    private static final Integer MAX_URL_LENGTH = 4000;
    private static final String ENCODING = "UTF-8";
    protected static final String REVISION_HISTORY_FILE_NAME = "Revision_History.xml";
    protected static final String FEEDBACK_FILE_NAME = "Feedback.xml";
    protected static final String AUTHOR_GROUP_FILE_NAME = "Author_Group.xml";
    protected static final String PREFACE_FILE_NAME = "Preface.xml";
    protected static final String LEGAL_NOTICE_FILE_NAME = "Legal_Notice.xml";

    protected final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    protected final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final XMLFormatProperties xmlFormatProperties = new XMLFormatProperties();
    private final DataProviderFactory providerFactory;
    private final ContentSpecProvider contentSpecProvider;
    private final TranslatedCSNodeProvider translatedCSNodeProvider;
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
     * The default locale to build a book as if it isn't specified.
     */
    private final String defaultLocale;

    /**
     * All data associated with the build.
     */
    private BuildData buildData;

    public DocbookBuilder(final DataProviderFactory providerFactory, final BlobConstantWrapper rocbookDtd,
            final String defaultLocale) throws BuilderCreationException {
        this.providerFactory = providerFactory;

        contentSpecProvider = providerFactory.getProvider(ContentSpecProvider.class);
        translatedCSNodeProvider = providerFactory.getProvider(TranslatedCSNodeProvider.class);
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

        xmlFormatProperties.setVerbatimElements(CollectionUtilities.toArrayList(verbatimElementsString.split("[\\s]*,[\\s]*")));
        xmlFormatProperties.setInlineElements(CollectionUtilities.toArrayList(inlineElementsString.split("[\\s]*,[\\s]*")));
        xmlFormatProperties.setContentsInlineElements(CollectionUtilities.toArrayList(contentsInlineElementsString.split("[\\s]*,[\\s]*")));

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

    public XMLFormatProperties getXMLFormatProperties() {
        return xmlFormatProperties;
    }

    protected BuildData getBuildData() {
        return buildData;
    }

    protected void setBuildData(BuildData buildData) {
        this.buildData = buildData;
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
        if (getBuildData().getErrorDatabase() != null && getBuildData().getErrorDatabase().getErrors(
                getBuildData().getBuildLocale()) != null) {
            for (final TopicErrorData errorData : getBuildData().getErrorDatabase().getErrors(getBuildData().getBuildLocale())) {
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
        if (getBuildData().getErrorDatabase() != null && getBuildData().getErrorDatabase().getErrors(
                getBuildData().getBuildLocale()) != null) {
            for (final TopicErrorData errorData : getBuildData().getErrorDatabase().getErrors(getBuildData().getBuildLocale())) {
                numErrors += errorData.getItemsOfType(ErrorLevel.ERROR).size();
            }
        }
        return numErrors;
    }

    /**
     * Reset the builder so that it can build from a clean state.
     */
    protected void resetBuilder() {
        setBuildData(null);
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
    public Map<String, byte[]> buildBook(final ContentSpec contentSpec, final UserWrapper requester,
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

        // Get the Build Locale
        final String buildLocale = buildingOptions.getLocale() == null ? getDefaultBuildLocale() : buildingOptions.getLocale();

        // Create the build data
        final BuildData buildData = new BuildData(BuilderConstants.BUILD_NAME, contentSpec, buildLocale, buildingOptions, zanataDetails);
        setBuildData(buildData);

        // Set the override files if any were passed
        if (overrideFiles != null) {
            buildData.getOverrideFiles().putAll(overrideFiles);
        }

        // Determine if this is a translation build
        if (contentSpec.getLocale() == null || contentSpec.getLocale().equals(getDefaultBuildLocale())) {
            buildData.setTranslationBuild(false);
        } else {
            buildData.setTranslationBuild(true);
        }

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        // Get the translations
        if (buildingOptions.getLocale() != null) {
            pullTranslations(contentSpec, buildingOptions.getLocale());
        }
        loadConstantTranslations(buildData.getBuildLocale());

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        final Map<Integer, Set<String>> usedIdAttributes = new HashMap<Integer, Set<String>>();
        final boolean fixedUrlsSuccess = doPopulateDatabasePass(buildData, contentSpec, usedIdAttributes);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        /*
         * We need to create a list of all id's in the book to check if links are valid. So generate the id attribute that are
         * used by topics, section and chapters. Then add any id's that were found in the topics.
         */
        final Set<String> bookIdAttributes = buildData.getBuildDatabase().getIdAttributes(fixedUrlsSuccess);
        for (final Entry<Integer, Set<String>> entry : usedIdAttributes.entrySet()) {
            bookIdAttributes.addAll(entry.getValue());
        }
        validateTopicLinks(buildData, bookIdAttributes, fixedUrlsSuccess);

        // second topic pass to set the ids and process injections
        doSpecTopicPass(buildData, usedIdAttributes, fixedUrlsSuccess);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        // Process the images in the topics
        processImageLocations(buildData);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        return doBuildZipPass(buildData, requester, fixedUrlsSuccess);
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
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected void pullTranslations(final ContentSpec contentSpec, final String locale) throws BuildProcessingException {
        final TranslatedContentSpecWrapper translatedContentSpec = EntityUtilities.getTranslatedContentSpecById(providerFactory,
                contentSpec.getId(), contentSpec.getRevision());

        // Ensure that the passed content spec has a translation
        if (translatedContentSpec == null) {
            throw new BuildProcessingException(
                    "Unable to find any translations for Content Spec " + contentSpec.getId() + (contentSpec.getRevision() == null ? "" :
                            (", Revision" + contentSpec.getRevision())));
        }

        if (translatedContentSpec.getTranslatedNodes() != null) {
            final Map<String, String> translations = new HashMap<String, String>();

            // Iterate over each translated node and build up the list of translated strings for the content spec.
            final List<TranslatedCSNodeWrapper> translatedCSNodes = translatedContentSpec.getTranslatedNodes().getItems();
            for (final TranslatedCSNodeWrapper translatedCSNode : translatedCSNodes) {
                if (translatedCSNode.getTranslatedStrings() != null) {
                    final List<TranslatedCSNodeStringWrapper> translatedCSNodeStrings = translatedCSNode.getTranslatedStrings().getItems();
                    for (final TranslatedCSNodeStringWrapper translatedCSNodeString : translatedCSNodeStrings) {
                        if (translatedCSNodeString.getLocale().equals(locale)) {
                            translations.put(translatedCSNode.getOriginalString(), translatedCSNodeString.getTranslatedString());
                        }
                    }
                }
            }

            ContentSpecUtilities.replaceTranslatedStrings(translatedContentSpec.getContentSpec(), contentSpec, translations);
        }
    }

    /**
     * Sets the Translation Unique Ids on all the content spec nodes, that have a matching translated node.
     *
     * @param contentSpec           The content spec that contains all the nodes to set the translation unique ids for.
     * @param translatedContentSpec The Translated Content Spec object, that holds details on the translated nodes.
     */
    protected void setTranslationUniqueIds(final ContentSpec contentSpec, final TranslatedContentSpecWrapper translatedContentSpec) {
        final List<TranslatedCSNodeWrapper> translatedCSNodes = translatedContentSpec.getTranslatedNodes().getItems();
        for (final TranslatedCSNodeWrapper translatedCSNode : translatedCSNodes) {
            final org.jboss.pressgang.ccms.contentspec.Node node = ContentSpecUtilities.findMatchingContentSpecNode(contentSpec,
                    translatedCSNode.getNodeId());
            node.setTranslationUniqueId(translatedCSNode.getId().toString());
        }
    }

    /**
     * Validate all the book links in the each topic to ensure that they exist somewhere in the book. If they don't then the
     * topic XML is replaced with a generic error template.
     *
     * @param buildData        Information and data structures for the build.
     * @param bookIdAttributes A set of all the id's that exist in the book.
     * @param useFixedUrls     Whether or not the fixed urls should be used for topic ID's.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    @SuppressWarnings("unchecked")
    protected void validateTopicLinks(final BuildData buildData, final Set<String> bookIdAttributes,
            final boolean useFixedUrls) throws BuildProcessingException {
        log.info("Doing " + buildData.getBuildLocale() + " Topic Link Pass");

        final List<SpecTopic> topics = buildData.getBuildDatabase().getAllSpecTopics();
        final Set<Integer> processedTopics = new HashSet<Integer>();
        for (final SpecTopic specTopic : topics) {
            final BaseTopicWrapper<?> topic = specTopic.getTopic();
            final Document doc = specTopic.getXMLDocument();

            /*
             * We only to to process topics at this point and not spec topics. So check to see if the topic has all ready been
             * processed.
             */
            if (!processedTopics.contains(topic.getId())) {
                processedTopics.add(topic.getId());

                // Get the XRef links in the topic document
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
                            getErrorInvalidValidationTopicTemplate().getValue(), buildData.getBuildOptions());

                    final String xmlStringInCDATA = DocbookBuildUtilities.convertDocumentToCDATAFormattedString(doc,
                            getXMLFormatProperties());
                    buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                            "The following link(s) " + CollectionUtilities.toSeperatedString(invalidLinks,
                                    ",") + " don't exist. The processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");

                    // Find the Topic ID
                    final Integer topicId = topic.getTopicId();

                    final List<SpecTopic> buildTopics = buildData.getBuildDatabase().getSpecTopicsForTopicID(topicId);
                    for (final SpecTopic spec : buildTopics) {
                        DocbookBuildUtilities.setSpecTopicXMLForError(spec, topicXMLErrorTemplate, useFixedUrls);
                    }
                }
            }
        }
    }

    /**
     * Populates the SpecTopicDatabase with the SpecTopics inside the content specification. It also adds the equivalent real
     * topics to each SpecTopic.
     *
     * @param buildData        Information and data structures for the build.
     * @param contentSpec      The content spec to populate the database from.
     * @param usedIdAttributes The set of Used ID Attributes that should be added to.
     * @return True if the database was populated successfully otherwise false.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    @SuppressWarnings("unchecked")
    private boolean doPopulateDatabasePass(final BuildData buildData, final ContentSpec contentSpec,
            final Map<Integer, Set<String>> usedIdAttributes) throws BuildProcessingException {
        log.info("Doing " + buildData.getBuildLocale() + " Populate Database Pass");

        final Map<String, BaseTopicWrapper<?>> topics = new HashMap<String, BaseTopicWrapper<?>>();
        final boolean fixedUrlsSuccess;
        if (buildData.getBuildLocale() == null || buildData.getBuildLocale().equals(getDefaultBuildLocale())) {
            fixedUrlsSuccess = populateDatabaseTopics(buildData, topics);
        } else {
            //Translations should reference an existing historical topic with the fixed urls set, so we assume this to be the case
            fixedUrlsSuccess = true;
            populateTranslatedTopicDatabase(buildData, topics);
        }

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return false;
        }

        // Add all the levels to the database
        DocbookBuildUtilities.addLevelsToDatabase(buildData.getBuildDatabase(), contentSpec.getBaseLevel());

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return false;
        }

        // Set the duplicate id's for each spec topic
        buildData.getBuildDatabase().setDatabaseDuplicateIds(usedIdAttributes, fixedUrlsSuccess);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return false;
        }

        // Process the topics to make sure they are valid
        doTopicPass(buildData, topics, fixedUrlsSuccess, usedIdAttributes);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return false;
        }

        return fixedUrlsSuccess;
    }

    /**
     * TODO
     *
     * @param buildData Information and data structures for the build.
     * @param topics
     * @return
     */
    protected boolean populateDatabaseTopics(final BuildData buildData, final Map<String, BaseTopicWrapper<?>> topics) {
        boolean fixedUrlsSuccess = false;
        final List<TopicWrapper> latestTopics = new ArrayList<TopicWrapper>();
        final List<TopicWrapper> revisionTopics = new ArrayList<TopicWrapper>();

        // Calculate the ids of all the topics to get
        final List<SpecTopic> specTopics = buildData.getContentSpec().getSpecTopics();
        for (final SpecTopic specTopic : specTopics) {
            // Determine which topics we need to fetch the latest topics for and which topics we need to fetch revisions for.
            final TopicWrapper topic;
            if (specTopic.getRevision() != null && !buildData.getBuildOptions().getUseLatestVersions()) {
                topic = topicProvider.getTopic(specTopic.getDBId(), specTopic.getRevision());
                revisionTopics.add(topic);
            } else {
                topic = topicProvider.getTopic(specTopic.getDBId());
                latestTopics.add(topic);
            }

            // Add the topic to the topics collection
            final String key = DocbookBuildUtilities.getTopicBuildKey(topic);
            topics.put(key, topic);
        }

        final Set<String> processedFileNames = new HashSet<String>();
        if (latestTopics != null) {
            /*
             * assign fixed urls property tags to the topics. If fixedUrlsSuccess is true, the id of the topic sections,
             * xref injection points and file names in the zip file will be taken from the fixed url property tag,
             * defaulting back to the TopicID## format if for some reason that property tag does not exist.
             */
            fixedUrlsSuccess = setFixedURLsPass(latestTopics, processedFileNames);
        } else {
            fixedUrlsSuccess = true;
        }

        // Ensure that our revision topics FixedURLs are still valid
        setFixedURLsForRevisionsPass(revisionTopics, processedFileNames);

        return fixedUrlsSuccess;
    }

    /**
     * Gets the translated topics from the REST Interface and also creates any dummy translations for topics that have yet to be
     * translated.
     *
     * @param buildData        Information and data structures for the build.
     * @param translatedTopics The translated topic collection to add translated topics to.
     */
    private void populateTranslatedTopicDatabase(final BuildData buildData, final Map<String, BaseTopicWrapper<?>> translatedTopics) {
        // Loop over each Spec Topic in the content spec and get it's translated topic
        final List<SpecTopic> specTopics = buildData.getContentSpec().getSpecTopics();
        for (final SpecTopic specTopic : specTopics) {
            getTranslatedTopicForSpecTopic(buildData, specTopic, translatedTopics);
        }

        // Ensure that our translated topics FixedURLs are still valid
        final Set<String> processedFileNames = new HashSet<String>();

        // Ensure that the fixed urls are still unique
        final List<TranslatedTopicWrapper> translatedTopicList = new ArrayList<TranslatedTopicWrapper>();
        for (final Entry<String, BaseTopicWrapper<?>> topicEntry : translatedTopics.entrySet()) {
            translatedTopicList.add((TranslatedTopicWrapper) topicEntry.getValue());
        }
        setFixedURLsForRevisionsPass(translatedTopicList, processedFileNames);
    }

    /**
     * TODO
     *
     * @param buildData Information and data structures for the build.
     * @param specTopic The spec topic to find the Translated Topic for.
     * @return
     */
    protected void getTranslatedTopicForSpecTopic(final BuildData buildData, final SpecTopic specTopic,
            final Map<String, BaseTopicWrapper<?>> translatedTopics) {
        // Check if the spec topic has a matching translated topic node, if not then create a dummy topic
        if (specTopic.getTranslationUniqueId() != null) {
            final TranslatedCSNodeWrapper translatedCSNode = translatedCSNodeProvider.getTranslatedCSNode(
                    Integer.parseInt(specTopic.getTranslationUniqueId()));

            // Check if the translated node has a specific conditional translated topic, otherwise find the normal translated topic
            if (translatedCSNode.getTranslatedTopic() != null) {
                final TranslatedTopicWrapper translatedTopic = translatedCSNode.getTranslatedTopic();
                String key = DocbookBuildUtilities.getTranslatedTopicBuildKey(translatedTopic, translatedCSNode);
                translatedTopics.put(key, translatedTopic);
                buildData.getBuildDatabase().add(specTopic, key);
            } else {
                getLatestTranslatedTopicForSpecTopic(buildData, specTopic, translatedTopics);
            }
        } else {
            getLatestTranslatedTopicForSpecTopic(buildData, specTopic, translatedTopics);
        }
    }

    protected void getLatestTranslatedTopicForSpecTopic(final BuildData buildData, final SpecTopic specTopic,
            final Map<String, BaseTopicWrapper<?>> translatedTopics) {
        final TopicWrapper topic = topicProvider.getTopic(specTopic.getDBId(), specTopic.getRevision());
        String key = DocbookBuildUtilities.getTopicBuildKey(topic);

        // If the topic has already been processed then return
        if (translatedTopics.containsKey(key)) return;

        // Get the matching latest translated topic and pushed translated topics
        final Pair<TranslatedTopicWrapper, TranslatedTopicWrapper> latestTranslations = getLatestTranslations(buildData, topic,
                specTopic.getRevision());
        final TranslatedTopicWrapper latestTranslatedTopic = latestTranslations.getFirst();
        final TranslatedTopicWrapper latestPushedTranslatedTopic = latestTranslations.getSecond();

        // If the latest translation and latest pushed topic matches, then use that if not a dummy topic should be created
        if (latestTranslatedTopic != null && latestPushedTranslatedTopic != null && latestPushedTranslatedTopic.getTopicRevision().equals(
                latestTranslatedTopic.getTopicRevision())) {
            translatedTopics.put(key, latestTranslatedTopic);
            buildData.getBuildDatabase().add(specTopic, key);
        } else {
            final TranslatedTopicWrapper translatedTopic = createDummyTranslatedTopic(topic, buildData.getBuildLocale());
            translatedTopics.put(key, translatedTopic);
            buildData.getBuildDatabase().add(specTopic, key);
        }
    }

    /**
     * Find the latest pushed and translated topics for a topic. We need to do this since translations are only added when some
     * content is added in Zanata. So if the latest translated topic doesn't match the topic revision of the latest pushed then
     * we will need to create a dummy topic for the latest pushed topic.
     *
     * @param buildData Information and data structures for the build.
     * @param topic     The topic to find the latest translated topic and pushed translation.
     * @param rev       The revision for the topic as specified in the ContentSpec.
     * @return A Pair whose first element is the Latest Translated Topic and second element is the Latest Pushed Translation.
     */
    private Pair<TranslatedTopicWrapper, TranslatedTopicWrapper> getLatestTranslations(final BuildData buildData, final TopicWrapper topic,
            final Integer rev) {
        TranslatedTopicWrapper latestTranslatedTopic = null;
        TranslatedTopicWrapper latestPushedTranslatedTopic = null;
        if (topic.getTranslatedTopics() != null && topic.getTranslatedTopics().getItems() != null) {
            final List<TranslatedTopicWrapper> topics = topic.getTranslatedTopics().getItems();
            for (final TranslatedTopicWrapper tempTopic : topics) {
                // Find the Latest Translated Topic
                if (buildData.getBuildLocale().equals(
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
     * Creates a dummy translated topic so that a book can be built using the same relationships as a normal build.
     *
     * @param topic  The topic to create the dummy topic from.
     * @param locale The locale to build the dummy translations for.
     * @return The dummy translated topic.
     */
    private TranslatedTopicWrapper createDummyTranslatedTopic(final TopicWrapper topic, final String locale) {
        final TranslatedTopicWrapper translatedTopic = translatedTopicProvider.newTranslatedTopic();
        translatedTopic.setTopic(topic);
        translatedTopic.setId(topic.getId() * -1);

        final TranslatedTopicWrapper pushedTranslatedTopic = EntityUtilities.returnPushedTranslatedTopic(translatedTopic);

        /*
         * Try and use the untranslated default locale translated topic as the base for the dummy topic. If that fails then
         * create a dummy topic from the passed RESTTopicV1.
         */
        if (pushedTranslatedTopic != null) {
            final TranslatedTopicWrapper defaultLocaleTranslatedTopic = pushedTranslatedTopic.clone(false);

            // Negate the ID to show it isn't a proper translated topic
            defaultLocaleTranslatedTopic.setId(topic.getId() * -1);

            // Prefix the locale to show that it is missing the related translated topic
            defaultLocaleTranslatedTopic.setTitle(
                    "[" + defaultLocaleTranslatedTopic.getLocale() + "] " + defaultLocaleTranslatedTopic.getTitle());

            // Change the locale since the default locale translation is being transformed into a dummy translation
            defaultLocaleTranslatedTopic.setLocale(locale);

            return defaultLocaleTranslatedTopic;
        } else {
            // If we get to this point then no translation exists or the default locale translation failed to be downloaded.
            translatedTopic.setTopicId(topic.getId());
            translatedTopic.setTopicRevision(topic.getRevision());
            translatedTopic.setTranslationPercentage(100);
            translatedTopic.setXml(topic.getXml());
            translatedTopic.setTags(topic.getTags());
            translatedTopic.setSourceURLs(topic.getSourceURLs());
            translatedTopic.setProperties(topic.getProperties());
            translatedTopic.setLocale(locale);

            // Prefix the locale to show that it is missing the related translated topic
            translatedTopic.setTitle("[" + topic.getLocale() + "] " + topic.getTitle());

            return translatedTopic;
        }
    }

    /**
     * Do the first topic pass on the database and check if the base XML is valid and set the Document Object's for each spec
     * topic. Also collect the ID Attributes that are used within the topics.
     *
     * @param buildData        Information and data structures for the build.
     * @param topics           The list of topics to be checked and added to the database.
     * @param useFixedUrls     Whether the Fixed URL Properties should be used for the topic ID attributes.
     * @param usedIdAttributes The set of Used ID Attributes that should be added to.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    private void doTopicPass(final BuildData buildData, final Map<String, BaseTopicWrapper<?>> topics, final boolean useFixedUrls,
            final Map<Integer, Set<String>> usedIdAttributes) throws BuildProcessingException {
        log.info("Doing " + buildData.getBuildLocale() + " First topic pass");

        // Check that we have some topics to process
        if (topics != null) {
            log.info("\tProcessing " + topics.size() + " Topics");

            final int showPercent = 5;
            final float total = topics.size();
            float current = 0;
            int lastPercent = 0;

            // Process each topic
            for (final Entry<String, BaseTopicWrapper<?>> topicEntry : topics.entrySet()) {
                final BaseTopicWrapper<?> topic = topicEntry.getValue();
                final String key = topicEntry.getKey();

                ++current;
                final int percent = Math.round(current / total * 100);
                if (percent - lastPercent >= showPercent) {
                    lastPercent = percent;
                    log.info("\tFirst topic Pass " + percent + "% Done");
                }

                // Get the Topic ID
                final Integer topicId = topic.getTopicId();
                final Integer topicRevision = topic.getTopicRevision();

                boolean revHistoryTopic = topic.hasTag(CSConstants.REVISION_HISTORY_TAG_ID);
                boolean legalNoticeTopic = topic.hasTag(CSConstants.LEGAL_NOTICE_TAG_ID);

                Document topicDoc = null;
                final String topicXML = topic.getXml();

                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return;
                }

                boolean xmlValid = true;

                // Check that the Topic XML exists and isn't empty
                if (topicXML == null || topicXML.trim().isEmpty()) {
                    // Create an empty topic with the topic title from the resource file
                    final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                            getErrorEmptyTopicTemplate().getValue(), buildData.getBuildOptions());

                    buildData.getErrorDatabase().addWarning(topic, ErrorType.NO_CONTENT, BuilderConstants.WARNING_EMPTY_TOPIC_XML);
                    topicDoc = DocbookBuildUtilities.setTopicXMLForError(topic, topicXMLErrorTemplate, useFixedUrls);
                    xmlValid = false;
                }

                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return;
                }

                // Make sure we have valid XML
                if (xmlValid) {
                    try {
                        topicDoc = XMLUtilities.convertStringToDocument(topic.getXml());

                        if (topicDoc != null) {
                            // Ensure the topic is wrapped in a section and the title matches the topic
                            DocBookUtilities.wrapDocumentInSection(topicDoc);
                            DocBookUtilities.setSectionTitle(topic.getTitle(), topicDoc);
                        } else {
                            final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                                    getErrorInvalidValidationTopicTemplate().getValue(), buildData.getBuildOptions());
                            final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(topic.getXml());
                            buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                                    BuilderConstants.ERROR_INVALID_XML_CONTENT + " The processed XML is <programlisting>" +
                                            xmlStringInCDATA + "</programlisting>");
                            topicDoc = DocbookBuildUtilities.setTopicXMLForError(topic, topicXMLErrorTemplate, useFixedUrls);
                        }
                    } catch (Exception ex) {
                        final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                                getErrorInvalidValidationTopicTemplate().getValue(), buildData.getBuildOptions());
                        final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(topic.getXml());
                        buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                                BuilderConstants.ERROR_BAD_XML_STRUCTURE + " " + StringUtilities.escapeForXML(
                                        ex.getMessage()) + " The processed XML is <programlisting>" + xmlStringInCDATA +
                                        "</programlisting>");
                        topicDoc = DocbookBuildUtilities.setTopicXMLForError(topic, topicXMLErrorTemplate, useFixedUrls);
                    }
                }

                // Make sure the topic has the correct root element and other items
                if (revHistoryTopic) {
                    DocBookUtilities.wrapDocumentInAppendix(topicDoc);
                    topicDoc.getDocumentElement().setAttribute("id", "appe-" + buildData.getEscapedBookTitle() + "-Revision_History");
                } else if (legalNoticeTopic) {
                    DocBookUtilities.wrapDocumentInLegalNotice(topicDoc);
                } else {
                    // Ensure the topic is wrapped in a section and the title matches the topic
                    DocBookUtilities.wrapDocumentInSection(topicDoc);
                    DocBookUtilities.setSectionTitle(topic.getTitle(), topicDoc);

                    processTopicSectionInfo(topic, topicDoc);
                    DocbookBuildUtilities.processTopicID(topic, topicDoc, useFixedUrls);
                }

                /*
                 * Extract the id attributes used in this topic. We'll use this data in the second pass to make sure that
                 * individual topics don't repeat id attributes.
                 */
                DocbookBuildUtilities.collectIdAttributes(topicId, topicDoc, usedIdAttributes);

                // Add the document & topic to the database spec topics
                final List<SpecTopic> specTopics = buildData.getBuildDatabase().getSpecTopicsForKey(key);
                for (final SpecTopic specTopic : specTopics) {
                    // Check if the app should be shutdown
                    if (isShuttingDown.get()) {
                        return;
                    }

                    if (buildData.getBuildOptions().getUseLatestVersions()) {
                        specTopic.setTopic(topic.clone(false));
                        specTopic.setXMLDocument((Document) topicDoc.cloneNode(true));
                    } else {
                        /*
                         * Only set the topic for the spec topic if it matches the spec topic revision. If the Spec Topic
                         * Revision is null then we need to ensure that we get the latest version of the topic that was
                         * downloaded.
                         */
                        if ((specTopic.getRevision() == null && (specTopic.getTopic() == null || specTopic.getTopic().getRevision() >=
                                topicRevision)) || (specTopic.getRevision() != null && specTopic.getRevision() >= topicRevision)) {
                            specTopic.setTopic(topic.clone(false));
                            specTopic.setXMLDocument((Document) topicDoc.cloneNode(true));
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
     * @param buildData        Information and data structures for the build.
     * @param usedIdAttributes The set of ids that have been used in the set of topics in the content spec.
     * @param useFixedUrls     If during processing the fixed urls should be used.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    @SuppressWarnings("unchecked")
    private <T extends BaseTopicWrapper<T>> void doSpecTopicPass(final BuildData buildData,
            final Map<Integer, Set<String>> usedIdAttributes, final boolean useFixedUrls) throws BuildProcessingException {
        log.info("Doing " + buildData.getBuildLocale() + " Spec Topic Pass");
        final List<SpecTopic> specTopics = buildData.getBuildDatabase().getAllSpecTopics();

        log.info("\tProcessing " + specTopics.size() + " Spec Topics");

        final int showPercent = 5;
        final float total = specTopics.size();
        float current = 0;
        int lastPercent = 0;

        // Create the related topics database to be used for CSP builds
        final TocTopicDatabase relatedTopicsDatabase = new TocTopicDatabase();
        final List<T> topics = buildData.getBuildDatabase().getAllTopics(true);
        relatedTopicsDatabase.setTopics(topics);

        for (final SpecTopic specTopic : specTopics) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            if (log.isDebugEnabled()) log.debug("\tProcessing SpecTopic " + specTopic.getId() + (specTopic.getRevision() != null ? (", " +
                    "Revision " + specTopic.getRevision()) : ""));

            ++current;
            final int percent = Math.round(current / total * 100);
            if (percent - lastPercent >= showPercent) {
                lastPercent = percent;
                log.info("\tProcessing Pass " + percent + "% Done");
            }

            final BaseTopicWrapper<?> topic = specTopic.getTopic();
            final Document doc = specTopic.getXMLDocument();

            assert doc != null;
            assert topic != null;

            final DocbookXMLPreProcessor xmlPreProcessor = new DocbookXMLPreProcessor(getConstantTranslatedStrings());

            if (doc != null) {
                // Process the conditional statements
                if (!buildData.getContentSpec().getPublicanCfg().contains("condition:")) {
                    final String condition = specTopic.getConditionStatement(true);
                    DocBookUtilities.processConditions(condition, doc, BuilderConstants.DEFAULT_CONDITION);
                }

                final boolean valid = processSpecTopicInjections(buildData, specTopic, xmlPreProcessor, relatedTopicsDatabase,
                        useFixedUrls);

                /*
                 * If the topic is a translated topic then check to see if the translated topic hasn't been pushed for
                 * translation, is untranslated, has incomplete translations or contains fuzzy text.
                 */
                if (topic instanceof TranslatedTopicWrapper) {
                    // Check the topic itself isn't a dummy topic
                    if (EntityUtilities.isDummyTopic(topic) && EntityUtilities.hasBeenPushedForTranslation(
                            (TranslatedTopicWrapper) topic)) {
                        buildData.getErrorDatabase().addWarning(topic, ErrorType.UNTRANSLATED, BuilderConstants.WARNING_UNTRANSLATED_TOPIC);
                    } else if (EntityUtilities.isDummyTopic(topic)) {
                        buildData.getErrorDatabase().addWarning(topic, ErrorType.NOT_PUSHED_FOR_TRANSLATION,
                                BuilderConstants.WARNING_NONPUSHED_TOPIC);
                    } else {
                        // Check if the topic's content isn't fully translated
                        if (((TranslatedTopicWrapper) topic).getTranslationPercentage() < 100) {
                            buildData.getErrorDatabase().addWarning(topic, ErrorType.INCOMPLETE_TRANSLATION,
                                    BuilderConstants.WARNING_INCOMPLETE_TRANSLATION);
                        }

                        if (((TranslatedTopicWrapper) topic).getContainsFuzzyTranslations()) {
                            buildData.getErrorDatabase().addWarning(topic, ErrorType.FUZZY_TRANSLATION,
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
                            getErrorInvalidInjectionTopicTemplate().getValue(), buildData.getBuildOptions());

                    final String xmlStringInCDATA = DocbookBuildUtilities.convertDocumentToCDATAFormattedString(doc,
                            getXMLFormatProperties());
                    buildData.getErrorDatabase().addError(topic,
                            BuilderConstants.ERROR_INVALID_INJECTIONS + " The processed XML is <programlisting>" + xmlStringInCDATA +
                                    "</programlisting>");

                    DocbookBuildUtilities.setSpecTopicXMLForError(specTopic, topicXMLErrorTemplate, useFixedUrls);
                } else {
                    // Add the standard boilerplate xml
                    xmlPreProcessor.processTopicAdditionalInfo(specTopic, doc, buildData.getContentSpec().getBugzillaOptions(),
                            buildData.getBuildOptions(), buildData.getBuildName(), buildData.getBuildDate(), buildData.getZanataDetails());

                    // Make sure the XML is valid docbook after the standard processing has been done
                    validateTopicXML(buildData, specTopic, doc, useFixedUrls);
                }

                // Check to see if the translated topic revision is an older topic than the topic revision specified in the map
                if (topic instanceof TranslatedTopicWrapper) {
                    final TranslatedTopicWrapper pushedTranslatedTopic = EntityUtilities.returnPushedTranslatedTopic(
                            (TranslatedTopicWrapper) topic);
                    if (pushedTranslatedTopic != null && specTopic.getRevision() != null && !pushedTranslatedTopic.getTopicRevision()
                            .equals(
                            specTopic.getRevision())) {
                        if (EntityUtilities.isDummyTopic(topic)) {
                            buildData.getErrorDatabase().addWarning((T) topic, ErrorType.OLD_UNTRANSLATED,
                                    BuilderConstants.WARNING_OLD_UNTRANSLATED_TOPIC);
                        } else {
                            buildData.getErrorDatabase().addWarning((T) topic, ErrorType.OLD_TRANSLATION,
                                    BuilderConstants.WARNING_OLD_TRANSLATED_TOPIC);
                        }
                    }
                }

                // Ensure that all of the id attributes are valid by setting any duplicates with a post fixed number.
                DocbookBuildUtilities.setUniqueIds(specTopic, specTopic.getXMLDocument(), specTopic.getXMLDocument(), usedIdAttributes);
            }
        }
    }

    /**
     * Process the Injections for a SpecTopic and add any errors to the error database.
     *
     * @param buildData             Information and data structures for the build.
     * @param specTopic             The Build Topic to do injection processing on.
     * @param xmlPreProcessor       The XML Processor to use for Injections.
     * @param relatedTopicsDatabase The Database of Related Topics.
     * @param useFixedUrls          If during processing the fixed urls should be used.
     * @return True if no errors occurred or if the build is set to ignore missing injections, otherwise false.
     */
    @SuppressWarnings("unchecked")
    protected boolean processSpecTopicInjections(final BuildData buildData, final SpecTopic specTopic,
            final DocbookXMLPreProcessor xmlPreProcessor, final TocTopicDatabase relatedTopicsDatabase, final boolean useFixedUrls) {
        final BaseTopicWrapper<?> topic = specTopic.getTopic();
        final Document doc = specTopic.getXMLDocument();
        final Level baseLevel = buildData.getContentSpec().getBaseLevel();
        boolean valid = true;

        // Process the injection points
        if (buildData.getInjectionOptions().isInjectionAllowed()) {

            final ArrayList<Integer> customInjectionIds = new ArrayList<Integer>();

            xmlPreProcessor.processPrerequisiteInjections(specTopic, doc, useFixedUrls);
            xmlPreProcessor.processPrevRelationshipInjections(specTopic, doc, useFixedUrls);
            xmlPreProcessor.processLinkListRelationshipInjections(specTopic, doc, useFixedUrls);
            xmlPreProcessor.processNextRelationshipInjections(specTopic, doc, useFixedUrls);
            xmlPreProcessor.processSeeAlsoInjections(specTopic, doc, useFixedUrls);

            // Process the topics XML and insert the injection links
            final List<Integer> customInjectionErrors = xmlPreProcessor.processInjections(baseLevel, specTopic, customInjectionIds, doc,
                    buildData.getBuildOptions(), relatedTopicsDatabase, useFixedUrls);

            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return false;
            }

            // Handle any errors that occurred while processing the injections
            valid = processSpecTopicInjectionErrors(buildData, topic, customInjectionErrors);

            // Check for dummy topics
            if (topic instanceof TranslatedTopicWrapper) {
                // Add the warning for the topics relationships that haven't been translated
                if (topic.getOutgoingRelationships() != null && topic.getOutgoingRelationships().getItems() != null) {
                    final List<? extends BaseTopicWrapper<?>> relatedTopics = topic.getOutgoingRelationships().getItems();
                    for (final BaseTopicWrapper<?> relatedTopic : relatedTopics) {
                        // Check if the app should be shutdown
                        if (isShuttingDown.get()) {
                            return false;
                        }

                        final TranslatedTopicWrapper relatedTranslatedTopic = (TranslatedTopicWrapper) relatedTopic;

                        // Only show errors for topics that weren't included in the injections
                        if (!customInjectionErrors.contains(relatedTranslatedTopic.getTopicId())) {
                            if ((!baseLevel.isSpecTopicInLevelByTopicID(
                                    relatedTranslatedTopic.getTopicId()) && !buildData.getBuildOptions().getIgnoreMissingCustomInjections
                                    ()) || baseLevel.isSpecTopicInLevelByTopicID(
                                    relatedTranslatedTopic.getTopicId())) {
                                if (EntityUtilities.isDummyTopic(relatedTopic) && EntityUtilities.hasBeenPushedForTranslation(
                                        relatedTranslatedTopic)) {
                                    buildData.getErrorDatabase().addWarning(topic,
                                            "Topic ID " + relatedTranslatedTopic.getTopicId() + ", " +
                                                    "Revision " + relatedTranslatedTopic.getTopicRevision() + ", " +
                                                    "Title \"" + relatedTopic.getTitle() + "\" is an untranslated topic.");
                                } else if (EntityUtilities.isDummyTopic(relatedTopic)) {
                                    buildData.getErrorDatabase().addWarning(topic,
                                            "Topic ID " + relatedTranslatedTopic.getTopicId() + ", " +
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
     * @param buildData             Information and data structures for the build.
     * @param topic                 The topic that the errors occurred for.
     * @param customInjectionErrors The List of Custom Injection Errors.
     * @return True if no errors were processed or if the build is set to ignore missing injections, otherwise false.
     */
    protected boolean processSpecTopicInjectionErrors(final BuildData buildData, final BaseTopicWrapper<?> topic,
            final List<Integer> customInjectionErrors) {
        boolean valid = true;

        if (!customInjectionErrors.isEmpty()) {
            final String message = "Topic has referenced Topic(s) " + CollectionUtilities.toSeperatedString(
                    customInjectionErrors) + " in a custom injection point that was either not related, " +
                    "or not included in the filter used to build this book.";
            if (buildData.getBuildOptions().getIgnoreMissingCustomInjections()) {
                buildData.getErrorDatabase().addWarning(topic, ErrorType.INVALID_INJECTION, message);
            } else {
                buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_INJECTION, message);
                valid = false;
            }
        }

        return valid;
    }

    /**
     * Wrap all of the topics, images, common content, etc... files into a ZIP Archive.
     *
     * @param buildData    Information and data structures for the build.
     * @param requester    The User who requested the book be built.
     * @param useFixedUrls If during processing the fixed urls should be used.
     * @return A ZIP Archive containing all the information to build the book.
     * @throws BuildProcessingException Any build issue that should not occur under normal circumstances. Ie a Template can't be
     *                                  converted to a DOM Document.
     */
    private HashMap<String, byte[]> doBuildZipPass(final BuildData buildData, final UserWrapper requester,
            final boolean useFixedUrls) throws BuildProcessingException {
        log.info("Building the ZIP file");

        final StringBuffer bookXIncludes = new StringBuffer();

        // Add the base book information
        final HashMap<String, byte[]> files = buildData.getOutputFiles();
        final String bookBase = buildBookBase(buildData, requester);

        // add the images to the book
        addImagesToBook(buildData);

        final LinkedList<org.jboss.pressgang.ccms.contentspec.Node> levelData = buildData.getContentSpec().getBaseLevel().getChildNodes();

        // Loop through and create each chapter and the topics inside those chapters
        for (final org.jboss.pressgang.ccms.contentspec.Node node : levelData) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return null;
            }

            if (node instanceof Level) {
                final Level level = (Level) node;

                if (level.hasSpecTopics()) {
                    createRootElementXML(buildData, bookXIncludes, level, useFixedUrls);
                } else if (buildData.getBuildOptions().isAllowEmptySections()) {
                    bookXIncludes.append(DocBookUtilities.wrapInPara("No Content"));
                }
            } else if (node instanceof SpecTopic) {
                final SpecTopic specTopic = (SpecTopic) node;
                final String topicFileName = createTopicXMLFile(buildData, specTopic, buildData.getBookTopicsFolder(), useFixedUrls);

                if (topicFileName != null) {
                    bookXIncludes.append(
                            "\t<xi:include href=\"topics/" + topicFileName + "\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
                }
            }
        }

        if (buildData.getBuildOptions().getInsertEditorLinks() && buildData.isTranslationBuild()) {
            final String translateLinkChapter = DocBookUtilities.addDocbook45XMLDoctype(buildTranslateCSChapter(buildData),
                    buildData.getEntityFileName(), "chapter");
            files.put(buildData.getBookLocaleFolder() + "Translate.xml", StringUtilities.getStringBytes(
                    StringUtilities.cleanTextForXML(translateLinkChapter == null ? "" : translateLinkChapter)));
            bookXIncludes.append("\t<xi:include href=\"Translate.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
        }

        // add any compiler errors
        if (!buildData.getBuildOptions().getSuppressErrorsPage() && buildData.getErrorDatabase().hasItems(buildData.getBuildLocale())) {
            final String compilerOutput = DocBookUtilities.addDocbook45XMLDoctype(buildErrorChapter(buildData),
                    buildData.getEntityFileName(), "chapter");
            files.put(buildData.getBookLocaleFolder() + "Errors.xml",
                    StringUtilities.getStringBytes(StringUtilities.cleanTextForXML(compilerOutput == null ? "" : compilerOutput)));
            bookXIncludes.append("\t<xi:include href=\"Errors.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
        }

        // add the report chapter
        if (buildData.getBuildOptions().getShowReportPage()) {
            final String compilerOutput = DocBookUtilities.addDocbook45XMLDoctype(buildReportChapter(buildData),
                    buildData.getEntityFileName(), "chapter");
            files.put(buildData.getBookLocaleFolder() + "Report.xml",
                    StringUtilities.getStringBytes(StringUtilities.cleanTextForXML(compilerOutput == null ? "" : compilerOutput)));
            bookXIncludes.append("\t<xi:include href=\"Report.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
        }

        // build the content specification page
        if (!buildData.getBuildOptions().getSuppressContentSpecPage()) {
            try {
                files.put(buildData.getBookLocaleFolder() + "Build_Content_Specification.xml", DocBookUtilities.buildAppendix(
                        DocBookUtilities.wrapInPara("<programlisting>" + XMLUtilities.wrapStringInCDATA(
                                buildData.getContentSpec().toString()) + "</programlisting>"), "Build Content Specification").getBytes(
                        ENCODING));
            } catch (UnsupportedEncodingException e) {
                // UTF-8 is a valid format so this should exception should never get thrown
                log.error(e.getMessage());
            }
            bookXIncludes.append(
                    "	<xi:include href=\"Build_Content_Specification.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
        }

        final String book = bookBase.replace(BuilderConstants.XIINCLUDES_INJECTION_STRING, bookXIncludes);
        try {
            files.put(buildData.getBookLocaleFolder() + buildData.getEscapedBookTitle() + ".xml", book.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this exception should never get thrown
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
     * @param buildData Information and data structures for the build.
     * @param requester The User who requested the book be built.
     * @return A Document object to be used in generating the book.xml
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected String buildBookBase(final BuildData buildData, final UserWrapper requester) throws BuildProcessingException {
        log.info("\tAdding standard files to Publican ZIP file");

        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, String> overrides = buildData.getBuildOptions().getOverrides();
        final Map<String, byte[]> overrideFiles = buildData.getOverrideFiles();
        final Map<String, byte[]> files = buildData.getOutputFiles();

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
        String basicBook = bookXmlTemplate.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, buildData.getEscapedBookTitle());
        basicBook = basicBook.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
        basicBook = basicBook.replaceAll(BuilderConstants.VERSION_REGEX, contentSpec.getVersion());
        basicBook = basicBook.replaceAll(BuilderConstants.DRAFT_REGEX, buildData.getBuildOptions().getDraft() ? "status=\"draft\"" : "");

        // Add the preface to the book.xml
        basicBook = basicBook.replaceAll(BuilderConstants.PREFACE_REGEX,
                "<xi:include href=\"" + PREFACE_FILE_NAME + "\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />");

        // Add the revision history to the book.xml
        basicBook = basicBook.replaceAll(BuilderConstants.REV_HISTORY_REGEX,
                "<xi:include href=\"" + REVISION_HISTORY_FILE_NAME + "\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />");

        // Setup publican.cfg
        final String fixedPublicanCfg = buildPublicanCfgFile(buildData, publicanCfg);
        try {
            files.put(buildData.getRootBookFolder() + "publican.cfg", fixedPublicanCfg.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error(e.getMessage());
        }

        // Setup Book_Info.xml
        final String fixedBookInfo = buildBookInfoFile(buildData, bookInfoTemplate);
        try {
            if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
                files.put(buildData.getBookLocaleFolder() + "Article_Info.xml", fixedBookInfo.getBytes(ENCODING));
            } else {
                files.put(buildData.getBookLocaleFolder() + "Book_Info.xml", fixedBookInfo.getBytes(ENCODING));
            }
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error(e.getMessage());
        }

        // Setup Author_Group.xml
        if (overrides.containsKey(CSConstants.AUTHOR_GROUP_OVERRIDE) && overrideFiles.containsKey(CSConstants.AUTHOR_GROUP_OVERRIDE)) {
            // Add the override Author_Group.xml file to the book
            files.put(buildData.getBookLocaleFolder() + AUTHOR_GROUP_FILE_NAME, overrideFiles.get(CSConstants.AUTHOR_GROUP_OVERRIDE));

        } else {
            buildAuthorGroup(buildData);
        }

        // Add the Feedback.xml if the override exists
        if (overrides.containsKey(CSConstants.FEEDBACK_OVERRIDE) && overrideFiles.containsKey(CSConstants.FEEDBACK_OVERRIDE)) {
            // Add the override Feedback.xml file to the book
            files.put(buildData.getBookLocaleFolder() + FEEDBACK_FILE_NAME, overrideFiles.get(CSConstants.FEEDBACK_OVERRIDE));
        } else if (contentSpec.getFeedback() != null) {
            try {
                final String feedbackXml = DocbookBuildUtilities.convertDocumentToDocbook45FormattedString(
                        contentSpec.getFeedback().getXMLDocument(), DocBookUtilities.TOPIC_ROOT_NODE_NAME, buildData.getEntityFileName(),
                        getXMLFormatProperties());
                // Add the revision history directly to the book
                files.put(buildData.getBookLocaleFolder() + FEEDBACK_FILE_NAME, feedbackXml.getBytes(ENCODING));
            } catch (UnsupportedEncodingException e) {
                // UTF-8 is a valid format so this should exception should never get thrown
                log.error(e.getMessage());
            }
        }

        // Setup Legal_Notice.xml
        if (contentSpec.getLegalNotice() != null) {
            final String legalNoticeXML = DocbookBuildUtilities.convertDocumentToDocbook45FormattedString(
                    contentSpec.getLegalNotice().getXMLDocument(), "legalnotice", buildData.getEntityFileName(), getXMLFormatProperties());
            try {
                files.put(buildData.getBookLocaleFolder() + "Legal_Notice.xml", legalNoticeXML.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                /* UTF-8 is a valid format so this should exception should never get thrown */
                log.error(e);
            }
        }

        // Setup Preface.xml
        String fixedPrefaceXml = prefaceXmlTemplate.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, buildData.getEscapedBookTitle());

        final String prefaceTitleTranslation = getConstantTranslatedStrings().getProperty("PREFACE");
        if (prefaceTitleTranslation != null) {
            fixedPrefaceXml = fixedPrefaceXml.replace("<title>Preface</title>", "<title>" + prefaceTitleTranslation + "</title>");
        }

        try {
            files.put(buildData.getBookLocaleFolder() + PREFACE_FILE_NAME, fixedPrefaceXml.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error(e.getMessage());
        }

        // Add any common content files that need to be included locally
        if (buildData.getBuildOptions().getCommonContentLocale() != null && buildData.getBuildOptions().getCommonContentDirectory() !=
                null) {
            addPublicanCommonContentToBook(buildData, buildData.getBuildOptions().getCommonContentLocale(),
                    buildData.getBuildOptions().getCommonContentDirectory());
        }

        // Setup Revision_History.xml
        buildRevisionHistory(buildData, overrides, requester);

        // Build the book .ent file
        final String entFile = buildBookEntityFile(buildData, bookEntityTemplate);
        try {
            files.put(buildData.getBookLocaleFolder() + buildData.getEntityFileName(), entFile.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error(e.getMessage());
        }

        // Setup the images and files folders
        addBookBaseFilesAndImages(buildData);

        return basicBook;
    }

    /**
     * Adds the basic Images and Files to the book that are the minimum requirements to build it.
     *
     * @param buildData Information and data structures for the build.
     */
    protected void addBookBaseFilesAndImages(final BuildData buildData) {
        final String iconSvg = stringConstantProvider.getStringConstant(DocbookBuilderConstants.ICON_SVG_ID).getValue();
        try {
            buildData.getOutputFiles().put(buildData.getBookImagesFolder() + "icon.svg", iconSvg.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error(e.getMessage());
        }
    }

    /**
     * Builds the Book_Info.xml file that is a basic requirement to build the book.
     *
     * @param buildData        Information and data structures for the build.
     * @param bookInfoTemplate The Book_Info.xml template to add content to.
     * @return The Book_Info.xml file filled with content from the Content Spec.
     */
    protected String buildBookInfoFile(final BuildData buildData, final String bookInfoTemplate) {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, String> overrides = buildData.getBuildOptions().getOverrides();

        String bookInfo = bookInfoTemplate.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, buildData.getEscapedBookTitle());
        // Set the book title
        bookInfo = bookInfo.replaceAll(BuilderConstants.TITLE_REGEX, contentSpec.getTitle());
        // Set the book subtitle
        bookInfo = bookInfo.replaceAll(BuilderConstants.SUBTITLE_REGEX,
                contentSpec.getSubtitle() == null ? BuilderConstants.SUBTITLE_DEFAULT : contentSpec.getSubtitle());
        // Set the book product
        bookInfo = bookInfo.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
        // Set the book product version
        bookInfo = bookInfo.replaceAll(BuilderConstants.VERSION_REGEX, contentSpec.getVersion());
        // Set or remove the book edition
        if (contentSpec.getEdition() == null) {
            bookInfo = bookInfo.replaceAll("<edition>.*</edition>(\r)?\n", "");
        } else {
            bookInfo = bookInfo.replaceAll(BuilderConstants.EDITION_REGEX, contentSpec.getEdition());
        }
        // Set the book pubsnumber
        final String pubsNumber = overrides.containsKey(CSConstants.PUBSNUMBER_OVERRIDE) ? overrides.get(
                CSConstants.PUBSNUMBER_OVERRIDE) : (contentSpec.getPubsNumber() == null ? BuilderConstants.DEFAULT_PUBSNUMBER :
                contentSpec.getPubsNumber().toString());
        bookInfo = bookInfo.replaceAll(BuilderConstants.PUBSNUMBER_REGEX, "<pubsnumber>" + pubsNumber + "</pubsnumber>");
        // Set the book abstract
        bookInfo = bookInfo.replaceAll(BuilderConstants.ABSTRACT_REGEX,
                contentSpec.getAbstract() == null ? BuilderConstants.DEFAULT_ABSTRACT : ("<abstract>\n\t\t<para>\n\t\t\t" +
                        contentSpec.getAbstract() + "\n\t\t</para>\n\t</abstract>\n"));
        // Set the book to have a Legal Notice
        bookInfo = bookInfo.replaceAll(BuilderConstants.LEGAL_NOTICE_REGEX, BuilderConstants.LEGAL_NOTICE_XML);

        return bookInfo;
    }

    /**
     * Builds the publican.cfg file that is a basic requirement to build the publican book.
     *
     * @param buildData           Information and data structures for the build.
     * @param publicanCfgTemplate The publican.cfg template to add content to.
     * @return The publican.cfg file filled with content from the Content Spec.
     */
    protected String buildPublicanCfgFile(final BuildData buildData, final String publicanCfgTemplate) {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, String> overrides = buildData.getBuildOptions().getOverrides();

        final String brand = overrides.containsKey(CSConstants.BRAND_OVERRIDE) ? overrides.get(
                CSConstants.BRAND_OVERRIDE) : (contentSpec.getBrand() == null ? BuilderConstants.DEFAULT_BRAND : contentSpec.getBrand());

        // Setup publican.cfg
        String publicanCfg = publicanCfgTemplate.replaceAll(BuilderConstants.BRAND_REGEX, brand);
        publicanCfg = publicanCfg.replaceFirst("type\\:\\s*.*($|\\r\\n|\\n)",
                "type: " + contentSpec.getBookType().toString().replaceAll("-Draft", "") + "\n");
        publicanCfg = publicanCfg.replaceAll("xml_lang\\:\\s*.*?($|\\r\\n|\\n)", "xml_lang: " + buildData.getOutputLocale() + "\n");
        if (!publicanCfg.matches(".*\n$")) {
            publicanCfg += "\n";
        }

        // Remove the image width
        publicanCfg = publicanCfg.replaceFirst("max_image_width:\\s*\\d+\\s*(\\r)?\\n", "");
        publicanCfg = publicanCfg.replaceFirst("toc_section_depth:\\s*\\d+\\s*(\\r)?\\n", "");

        if (contentSpec.getPublicanCfg() != null) {
            // Remove the git_branch if the content spec contains a git_branch
            if (contentSpec.getPublicanCfg().contains("git_branch")) {
                publicanCfg = publicanCfg.replaceFirst("git_branch:\\s*.*(\\r)?(\\n)?", "");
            }
            publicanCfg += DocbookBuildUtilities.cleanUserPublicanCfg(contentSpec.getPublicanCfg());

            if (!publicanCfg.matches(".*\n$")) {
                publicanCfg += "\n";
            }
        }

        if (buildData.getBuildOptions().getPublicanShowRemarks()) {
            // Remove any current show_remarks definitions
            if (publicanCfg.contains("show_remarks")) {
                publicanCfg = publicanCfg.replaceAll("show_remarks:\\s*\\d+\\s*(\\r)?(\\n)?", "");
            }
            publicanCfg += "show_remarks: 1\n";
        }

        publicanCfg += "docname: " + buildData.getEscapedBookTitle().replaceAll("_", " ") + "\n";
        publicanCfg += "product: " + buildData.getOriginalBookProduct() + "\n";

        if (buildData.getBuildOptions().getCvsPkgOption() != null) {
            publicanCfg += "cvs_pkg: " + buildData.getBuildOptions().getCvsPkgOption() + "\n";
        }

        return publicanCfg;
    }

    /**
     * Builds the book .ent file that is a basic requirement to build the book.
     *
     * @param buildData          Information and data structures for the build.
     * @param entityFileTemplate The entity file template to add content to.
     * @return The book .ent file filled with content from the Content Spec.
     */
    protected String buildBookEntityFile(final BuildData buildData, final String entityFileTemplate) {
        final ContentSpec contentSpec = buildData.getContentSpec();
        // Setup the <<contentSpec.title>>.ent file
        String entFile = entityFileTemplate.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, buildData.getEscapedBookTitle());
        entFile = entFile.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
        entFile = entFile.replaceAll(BuilderConstants.TITLE_REGEX, buildData.getOriginalBookTitle());
        entFile = entFile.replaceAll(BuilderConstants.YEAR_FORMAT_REGEX, contentSpec.getCopyrightYear() == null ? Integer.toString(
                Calendar.getInstance().get(Calendar.YEAR)) : contentSpec.getCopyrightYear());
        entFile = entFile.replaceAll(BuilderConstants.CONTENT_SPEC_COPYRIGHT_REGEX, contentSpec.getCopyrightHolder());
        entFile = entFile.replaceAll(BuilderConstants.BZPRODUCT_REGEX,
                contentSpec.getBugzillaProduct() == null ? buildData.getOriginalBookProduct() : contentSpec.getBugzillaProduct());
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
     * @param buildData              Information and data structures for the build.
     * @param commonContentLocale    The Common_Content Locale to be used.
     * @param commonContentDirectory The Common_Content directory.
     */
    protected void addPublicanCommonContentToBook(final BuildData buildData, final String commonContentLocale,
            final String commonContentDirectory) {
        final String brand = buildData.getContentSpec().getBrand() == null ? BuilderConstants.DEFAULT_BRAND : buildData.getContentSpec()
                .getBrand();

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
                        buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + fileName, file.getBytes(ENCODING));
                    }
                } else {
                    final File commonBrandFile = new File(commonBrandDir + fileName);
                    if (commonBrandFile.exists() && commonBrandFile.isFile()) {
                        final String file = FileUtilities.readFileContents(commonBrandFile);
                        if (!file.isEmpty()) {
                            buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + fileName, file.getBytes(ENCODING));
                        }
                    }
                }
            } catch (UnsupportedEncodingException e) {
                log.error(e.getMessage());
            }
        }

    }

    /**
     * Creates all the chapters/appendixes for a book and generates the section/topic data inside of each chapter.
     *
     * @param buildData     Information and data structures for the build.
     * @param bookXIncludes The string based list of XIncludes to be used in the book.xml
     * @param level         The level to build the chapter from.
     * @param useFixedUrls  If Fixed URL Properties should be used for topic ID attributes.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected void createRootElementXML(final BuildData buildData, final StringBuffer bookXIncludes, final Level level,
            final boolean useFixedUrls) throws BuildProcessingException {
        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        // Get the name of the element based on the type
        final String elementName = level.getLevelType() == LevelType.PROCESS ? "chapter" : level.getLevelType().getTitle().toLowerCase(
                Locale.ENGLISH);

        Document chapter = null;
        try {
            chapter = XMLUtilities.convertStringToDocument("<" + elementName + "></" + elementName + ">");
        } catch (Exception ex) {
            // Exit since we shouldn't fail at converting a basic chapter
            log.debug("", ex);
            throw new BuildProcessingException("Failed to create a basic XML document");
        }

        // Create the title
        final String chapterName = level.getUniqueLinkId(useFixedUrls);
        final String chapterXMLName = chapterName + ".xml";

        // Add to the list of XIncludes that will get set in the book.xml
        bookXIncludes.append("\t<xi:include href=\"" + chapterXMLName + "\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");

        // Create the chapter.xml
        final Element titleNode = chapter.createElement("title");
        if (buildData.isTranslationBuild() && level.getTranslatedTitle() != null && !level.getTranslatedTitle().isEmpty()) {
            titleNode.setTextContent(level.getTranslatedTitle());
        } else {
            titleNode.setTextContent(level.getTitle());
        }
        chapter.getDocumentElement().appendChild(titleNode);
        chapter.getDocumentElement().setAttribute("id", level.getUniqueLinkId(useFixedUrls));
        createSectionXML(buildData, level, chapter, chapter.getDocumentElement(), buildData.getBookTopicsFolder() + chapterName + "/",
                useFixedUrls);

        // Add the boiler plate text and add the chapter to the book
        final String chapterString = DocbookBuildUtilities.convertDocumentToDocbook45FormattedString(chapter, elementName,
                buildData.getEntityFileName(), getXMLFormatProperties());
        try {
            buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + chapterXMLName, chapterString.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error(e.getMessage());
        }
    }

    /**
     * Creates all the chapters/appendixes for a book that are contained within another part/chapter/appendix and generates the
     * section/topic data inside of each chapter.
     *
     * @param buildData           Information and data structures for the build.
     * @param doc                 The document object to add the child level content to.
     * @param level               The level to build the chapter from.
     * @param parentFileDirectory The parent file location, so any files can be saved in a subdirectory of the parents location.
     * @param useFixedUrls        If Fixed URL Properties should be used for topic ID attributes.
     * @return The Element that specifies the XiInclude for the chapter/appendix in the files.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected Element createSubRootElementXML(final BuildData buildData, final Document doc, final Level level,
            final String parentFileDirectory, final boolean useFixedUrls) throws BuildProcessingException {
        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return null;
        }

        // Get the name of the element based on the type
        final String elementName = level.getLevelType() == LevelType.PROCESS ? "chapter" : level.getLevelType().getTitle().toLowerCase(
                Locale.ENGLISH);

        Document chapter = null;
        try {
            chapter = XMLUtilities.convertStringToDocument("<" + elementName + "></" + elementName + ">");
        } catch (Exception ex) {
            // Exit since we shouldn't fail at converting a basic chapter
            log.debug("", ex);
            throw new BuildProcessingException("Failed to create a basic XML document");
        }

        // Create the title
        final String chapterName = level.getUniqueLinkId(useFixedUrls);
        final String chapterXMLName = chapterName + ".xml";

        // Create the chapter.xml
        final Element titleNode = chapter.createElement("title");
        if (buildData.isTranslationBuild() && level.getTranslatedTitle() != null && !level.getTranslatedTitle().isEmpty()) {
            titleNode.setTextContent(level.getTranslatedTitle());
        } else {
            titleNode.setTextContent(level.getTitle());
        }
        chapter.getDocumentElement().appendChild(titleNode);
        chapter.getDocumentElement().setAttribute("id", level.getUniqueLinkId(useFixedUrls));
        createSectionXML(this.buildData, level, chapter, chapter.getDocumentElement(), parentFileDirectory + chapterName + "/",
                useFixedUrls);

        // Add the boiler plate text and add the chapter to the book
        final String chapterString = DocbookBuildUtilities.convertDocumentToDocbook45FormattedString(chapter, elementName,
                buildData.getEntityFileName(), getXMLFormatProperties());
        try {
            buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + chapterXMLName, chapterString.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error(e.getMessage());
        }

        // Create the XIncludes that will get set in the book.xml
        final Element xiInclude = doc.createElement("xi:include");
        xiInclude.setAttribute("href", chapterXMLName);
        xiInclude.setAttribute("xmlns:xi", "http://www.w3.org/2001/XInclude");

        return xiInclude;
    }

    /**
     * Creates the section component of a chapter.xml for a specific Level.
     *
     * @param buildData          Information and data structures for the build.
     * @param level              The section level object to get content from.
     * @param chapter            The chapter document object that this section is to be added to.
     * @param parentNode         The parent XML node of this section.
     * @param parentFileLocation The parent file location, so any files can be saved in a subdirectory of the parents location.
     * @param useFixedUrls       If Fixed URL Properties should be used for topic ID attributes.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected void createSectionXML(final BuildData buildData, final Level level, final Document chapter, final Element parentNode,
            final String parentFileLocation, final boolean useFixedUrls) throws BuildProcessingException {
        final LinkedList<org.jboss.pressgang.ccms.contentspec.Node> levelData = level.getChildNodes();

        // Get the name of the element based on the type
        final String elementName = level.getLevelType() == LevelType.PROCESS ? "chapter" : level.getLevelType().getTitle().toLowerCase(
                Locale.ENGLISH);
        final Element intro = chapter.createElement(elementName + "intro");

        // Storage container to hold the levels so they can be added in proper order with the intro
        final LinkedList<Node> childNodes = new LinkedList<Node>();

        // Add the section and topics for this level to the chapter.xml
        for (final org.jboss.pressgang.ccms.contentspec.Node node : levelData) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            if (node instanceof Level && node.getParent() != null && (((Level) node).getParent().getLevelType() == LevelType.BASE || (
                    (Level) node).getParent().getLevelType() == LevelType.PART)) {
                final Level childLevel = (Level) node;

                // Create a new file for the Chapter/Appendix
                final Element xiInclude = createSubRootElementXML(buildData, chapter, childLevel, parentFileLocation, useFixedUrls);
                if (xiInclude != null) {
                    childNodes.add(xiInclude);
                }
            } else if (node instanceof Level) {
                final Level childLevel = (Level) node;

                // Create the section and its title
                final Element sectionNode = chapter.createElement("section");
                final Element sectionTitleNode = chapter.createElement("title");
                if (buildData.isTranslationBuild() && childLevel.getTranslatedTitle() != null && !childLevel.getTranslatedTitle().isEmpty())
                    sectionTitleNode.setTextContent(childLevel.getTranslatedTitle());
                else sectionTitleNode.setTextContent(childLevel.getTitle());
                sectionNode.appendChild(sectionTitleNode);
                sectionNode.setAttribute("id", childLevel.getUniqueLinkId(useFixedUrls));

                // Ignore sections that have no spec topics
                if (!childLevel.hasSpecTopics()) {
                    if (buildData.getBuildOptions().isAllowEmptySections()) {
                        Element warning = chapter.createElement("warning");
                        warning.setTextContent("No Content");
                        sectionNode.appendChild(warning);
                    } else {
                        continue;
                    }
                } else {
                    // Add this sections child sections/topics
                    createSectionXML(buildData, childLevel, chapter, sectionNode, parentFileLocation, useFixedUrls);
                }

                childNodes.add(sectionNode);
            } else if (node instanceof SpecTopic) {
                final SpecTopic specTopic = (SpecTopic) node;

                final String topicFileName = createTopicXMLFile(buildData, specTopic, parentFileLocation, useFixedUrls);

                if (topicFileName != null) {
                    // Remove the initial file location as we only where it lives in the topics directory
                    final String fixedParentFileLocation = buildData.getBuildOptions().getFlattenTopics() ? "topics/" :
                            parentFileLocation.replace(
                            buildData.getBookLocaleFolder(), "");

                    final Element topicNode = chapter.createElement("xi:include");
                    topicNode.setAttribute("href", fixedParentFileLocation + topicFileName);
                    topicNode.setAttribute("xmlns:xi", "http://www.w3.org/2001/XInclude");

                    if (specTopic.getParent() != null && ((Level) specTopic.getParent()).getLevelType() == LevelType.PART) {
                        intro.appendChild(topicNode);
                    } else {
                        childNodes.add(topicNode);
                    }
                }
            }
        }

        // Add the intro text from the inner topic if it exists
        if (level.getInnerTopic() != null) {
            if (level.getLevelType() == LevelType.PART) {
                addTopicContentsToLevelDocument(level, level.getInnerTopic(), intro, chapter);
            } else {
                addTopicContentsToLevelDocument(level, level.getInnerTopic(), parentNode, chapter);
            }
        }

        // Add the child nodes and intro to the parent
        if (intro.hasChildNodes()) {
            parentNode.appendChild(intro);
        }

        for (final Node node : childNodes) {
            parentNode.appendChild(node);
        }
    }

    /**
     * Adds a Topics contents as the introduction text for a Level.
     *
     * @param level      The level the intro topic is being added for.
     * @param specTopic  The Topic that contains the introduction content.
     * @param parentNode The DOM parent node the intro content is to be appended to.
     * @param doc        The DOM Document the content is to be added to.
     */
    protected void addTopicContentsToLevelDocument(final Level level, final SpecTopic specTopic, final Element parentNode,
            final Document doc) {
        final Node section = doc.importNode(specTopic.getXMLDocument().getDocumentElement(), true);

        if (level.getLevelType() != LevelType.PART) {
            // Reposition the section intro
            final List<Node> sectionInfoNodes = XMLUtilities.getDirectChildNodes(section,
                    DocBookUtilities.TOPIC_ROOT_SECTIONINFO_NODE_NAME);
            if (sectionInfoNodes.size() != 0) {
                final String introType = parentNode.getNodeName() + "info";

                // Check if the parent already has the intro text
                final List<Node> intros = XMLUtilities.getDirectChildNodes(parentNode, introType);
                final Node introNode;
                if (intros.size() == 0) {
                    introNode = doc.createElement(introType);
                    parentNode.insertBefore(introNode, parentNode.getFirstChild());
                } else {
                    introNode = intros.get(0);
                }

                // Merge the info text
                final NodeList sectionIntroChildren = sectionInfoNodes.get(0).getChildNodes();
                final Node firstNode = introNode.getFirstChild();
                for (int i = 0; i < sectionIntroChildren.getLength(); i++) {
                    if (firstNode != null) {
                        introNode.insertBefore(sectionIntroChildren.item(i), firstNode);
                    } else {
                        introNode.appendChild(sectionIntroChildren.item(i));
                    }
                }
            }
        }

        // Remove the title and sectioninfo
        final List<Node> titleNodes = XMLUtilities.getDirectChildNodes(section, DocBookUtilities.TOPIC_ROOT_TITLE_NODE_NAME,
                DocBookUtilities.TOPIC_ROOT_SECTIONINFO_NODE_NAME);
        for (final Node removeNode : titleNodes) {
            section.removeChild(removeNode);
        }

        // Move the contents of the section to the chapter/level
        final NodeList sectionChildren = section.getChildNodes();
        for (int i = 0; i < sectionChildren.getLength(); i++) {
            parentNode.appendChild(sectionChildren.item(i));
        }
    }

    /**
     * Creates the Topic component of a chapter.xml for a specific SpecTopic.
     *
     * @param buildData          Information and data structures for the build.
     * @param specTopic          The build topic object to get content from.
     * @param parentFileLocation The topics parent file location, so the topic can be saved in a subdirectory.
     * @param useFixedUrls       If Fixed URL Properties should be used for topic ID attributes.  @return The filename of the new topic
     *                           XML file.
     * @return The Topics filename.
     */
    protected String createTopicXMLFile(final BuildData buildData, final SpecTopic specTopic, final String parentFileLocation,
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

            final String fixedParentFileLocation = buildData.getBuildOptions().getFlattenTopics() ? buildData.getBookTopicsFolder() :
                    parentFileLocation;

            final String topicXML = DocbookBuildUtilities.convertDocumentToDocbook45FormattedString(specTopic.getXMLDocument(),
                    DocBookUtilities.TOPIC_ROOT_NODE_NAME, buildData.getEntityFileName(), getXMLFormatProperties());
            try {
                buildData.getOutputFiles().put(fixedParentFileLocation + topicFileName, topicXML.getBytes(ENCODING));
            } catch (UnsupportedEncodingException e) {
                // UTF-8 is a valid format so this should exception should never get thrown
                log.error(e.getMessage());
            }

            return topicFileName;
        }

        return null;
    }

    /**
     * Adds all the images found using the {@link #processImageLocations(BuildData)} method to the files map that will alter be turned
     * into a ZIP archive.
     *
     * @param buildData Information and data structures for the build.
     */
    private void addImagesToBook(final BuildData buildData) {
        /* Load the database constants */
        final byte[] failpenguinPng = blobConstantProvider.getBlobConstant(DocbookBuilderConstants.FAILPENGUIN_PNG_ID).getValue();

        /* download the image files that were identified in the processing stage */
        float imageProgress = 0;
        final float imageTotal = buildData.getImageLocations().size();
        final int showPercent = 5;
        int lastPercent = 0;

        for (final TopicImageData imageLocation : buildData.getImageLocations()) {
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
                        buildData.getErrorDatabase().addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES,
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
                                if (image.getLocale().equals(buildData.getBuildLocale())) {
                                    languageImageFile = image;
                                } else if (image.getLocale().equals(getDefaultBuildLocale()) && languageImageFile == null) {
                                    languageImageFile = image;
                                }
                            }
                        }

                        if (languageImageFile != null && languageImageFile.getImageData() != null) {
                            success = true;
                            buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + imageLocation.getImageName(),
                                    languageImageFile.getImageData());
                        } else {
                            buildData.getErrorDatabase().addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES,
                                    "ImageFile ID " + imageID + " from image location " + imageLocation.getImageName() + " was not found!");
                        }
                    }
                } catch (final NumberFormatException ex) {
                    success = false;
                    buildData.getErrorDatabase().addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES,
                            imageLocation.getImageName() + " is not a valid image. Must be in the format [ImageFileID].extension e.g. 123" +
                                    ".png, or images/321.jpg");
                    log.debug("", ex);
                } catch (final Exception ex) {
                    success = false;
                    buildData.getErrorDatabase().addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES,
                            imageLocation.getImageName() + " is not a valid image. Must be in the format [ImageFileID].extension e.g. 123" +
                                    ".png, or images/321.jpg");
                    log.debug("", ex);
                }
            }

            // Put in a place holder in the image couldn't be found
            if (!success) {
                buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + imageLocation.getImageName(), failpenguinPng);
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
     * @param buildData Information and data structures for the build.
     * @throws BuildProcessingException
     */
    private void buildAuthorGroup(final BuildData buildData) throws BuildProcessingException {
        log.info("\tBuilding " + AUTHOR_GROUP_FILE_NAME);

        // Setup Author_Group.xml
        final String authorGroupXml = stringConstantProvider.getStringConstant(DocbookBuilderConstants.AUTHOR_GROUP_XML_ID).getValue();
        String fixedAuthorGroupXml = authorGroupXml;
        Document authorDoc = null;
        try {
            authorDoc = XMLUtilities.convertStringToDocument(fixedAuthorGroupXml);
        } catch (Exception ex) {
            // Exit since we shouldn't fail at converting the basic author group
            log.debug("", ex);
            throw new BuildProcessingException("Failed to convert the " + AUTHOR_GROUP_FILE_NAME + " template into a DOM document");
        }
        final LinkedHashMap<Integer, AuthorInformation> authorIDtoAuthor = new LinkedHashMap<Integer, AuthorInformation>();

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        // Get the mapping of authors using the topics inside the content spec
        for (final Integer topicId : buildData.getBuildDatabase().getTopicIds()) {
            final BaseTopicWrapper<?> topic = buildData.getBuildDatabase().getSpecTopicsForTopicID(topicId).get(0).getTopic();
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

        // Sort and make sure duplicate authors don't exist
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
            if (!insertedAuthor) {
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
        fixedAuthorGroupXml = DocbookBuildUtilities.convertDocumentToDocbook45FormattedString(authorDoc, "authorgroup",
                buildData.getEntityFileName(), getXMLFormatProperties());
        try {
            buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + AUTHOR_GROUP_FILE_NAME,
                    fixedAuthorGroupXml.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error(e.getMessage());
        }
    }

    /**
     * Builds the revision history for the book. The revision history used will be determined in the following order:<br/>
     * <br />
     * 1. Revision History Override<br/>
     * 2. Content Spec Revision History Topic<br/>
     * 3. Revision History Template
     *
     * @param buildData Information and data structures for the build.
     * @param requester The user who requested the build action.
     * @param overrides The overrides to use for the build.
     * @throws BuildProcessingException
     */
    protected void buildRevisionHistory(final BuildData buildData, final Map<String, String> overrides,
            final UserWrapper requester) throws BuildProcessingException {
        final ContentSpec contentSpec = buildData.getContentSpec();

        // Replace the basic injection data inside the revision history
        final String revisionHistoryXml = stringConstantProvider.getStringConstant(
                DocbookBuilderConstants.REVISION_HISTORY_XML_ID).getValue();
        final String fixedRevisionHistoryXml = revisionHistoryXml.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX,
                buildData.getEscapedBookTitle());

        // Setup Revision_History.xml
        if (overrides.containsKey(CSConstants.REVISION_HISTORY_OVERRIDE) && buildData.getOverrideFiles().containsKey(
                CSConstants.REVISION_HISTORY_OVERRIDE)) {
            byte[] revHistory = buildData.getOverrideFiles().get(CSConstants.REVISION_HISTORY_OVERRIDE);
            if (buildData.getBuildOptions().getRevisionMessages() != null && !buildData.getBuildOptions().getRevisionMessages().isEmpty()) {
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
                        buildRevisionHistoryFromTemplate(buildData, revHistoryOverride.replace(docType, ""), requester);
                    } else {
                        buildRevisionHistoryFromTemplate(buildData, revHistoryOverride, requester);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage());
                    buildRevisionHistoryFromTemplate(buildData, fixedRevisionHistoryXml, requester);
                }
            } else {
                // Add the revision history directly to the book
                buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + REVISION_HISTORY_FILE_NAME, revHistory);
            }
        } else if (contentSpec.getRevisionHistory() != null) {
            final String revisionHistoryXML = DocbookBuildUtilities.convertDocumentToDocbook45FormattedString(
                    contentSpec.getRevisionHistory().getXMLDocument(), "appendix", buildData.getEntityFileName(), getXMLFormatProperties());
            if (buildData.getBuildOptions().getRevisionMessages() != null && !buildData.getBuildOptions().getRevisionMessages().isEmpty()) {
                buildRevisionHistoryFromTemplate(buildData, revisionHistoryXML, requester);
            } else if (buildData.getErrorDatabase().hasErrorData(contentSpec.getRevisionHistory().getTopic())) {
                buildRevisionHistoryFromTemplate(buildData, revisionHistoryXML, requester);
            } else {
                // Add the revision history directly to the book
                try {
                    buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + REVISION_HISTORY_FILE_NAME,
                            revisionHistoryXML.getBytes(ENCODING));
                } catch (UnsupportedEncodingException e) {
                    // UTF-8 is a valid format so this should exception should never get thrown */
                    log.error(e.getMessage());
                }
            }
        } else {
            buildRevisionHistoryFromTemplate(buildData, fixedRevisionHistoryXml, requester);
        }
    }

    /**
     * Builds the revision history using the requester of the build.
     *
     * @param buildData          Information and data structures for the build.
     * @param revisionHistoryXml The Revision_History.xml file/template to add revision information to.
     * @param requester          The user who requested the build action.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected void buildRevisionHistoryFromTemplate(final BuildData buildData, final String revisionHistoryXml,
            final UserWrapper requester) throws BuildProcessingException {
        log.info("\tBuilding " + REVISION_HISTORY_FILE_NAME);

        Document revHistoryDoc;
        try {
            revHistoryDoc = XMLUtilities.convertStringToDocument(revisionHistoryXml);
        } catch (Exception ex) {
            // Exit since we shouldn't fail at converting the basic revision history
            log.debug("", ex);
            throw new BuildProcessingException("Failed to convert the " + REVISION_HISTORY_FILE_NAME + " template into a DOM document");
        }

        if (revHistoryDoc == null) {
            throw new BuildProcessingException("Failed to convert the " + REVISION_HISTORY_FILE_NAME + " template into a DOM document");
        }

        revHistoryDoc.getDocumentElement().setAttribute("id", "appe-" + buildData.getEscapedBookTitle() + "-Revision_History");

        final String reportHistoryTitleTranslation = getConstantTranslatedStrings().getProperty("REVISION_HISTORY");
        if (reportHistoryTitleTranslation != null) {
            DocBookUtilities.setRootElementTitle(reportHistoryTitleTranslation, revHistoryDoc);
        }

        // Find the revhistory node
        final Element revHistory;
        final NodeList revHistories = revHistoryDoc.getElementsByTagName("revhistory");
        if (revHistories.getLength() > 0) {
            revHistory = (Element) revHistories.item(0);
        } else {
            final Element simpara = revHistoryDoc.createElement("simpara");
            revHistory = revHistoryDoc.createElement("revhistory");
            simpara.appendChild(revHistory);
            revHistoryDoc.getDocumentElement().appendChild(simpara);
        }

        final TagWrapper author = requester == null ? null : tagProvider.getTagByName(requester.getUsername());

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        // An assigned writer tag exists for the User so check if there is an AuthorInformation tuple for that writer
        if (author != null) {
            AuthorInformation authorInfo = EntityUtilities.getAuthorInformation(providerFactory, author.getId());
            if (authorInfo != null) {
                final Element revision = generateRevision(buildData, revHistoryDoc, authorInfo, requester);

                addRevisionToRevHistory(revHistory, revision);
            } else {
                // No AuthorInformation so Use the default value
                authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME, BuilderConstants.DEFAULT_AUTHOR_LASTNAME,
                        BuilderConstants.DEFAULT_EMAIL);
                final Element revision = generateRevision(buildData, revHistoryDoc, authorInfo, requester);

                addRevisionToRevHistory(revHistory, revision);
            }
        }
        // No assigned writer exists for the uploader so use default values
        else {
            final AuthorInformation authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME,
                    BuilderConstants.DEFAULT_AUTHOR_LASTNAME, BuilderConstants.DEFAULT_EMAIL);
            final Element revision = generateRevision(buildData, revHistoryDoc, authorInfo, requester);

            addRevisionToRevHistory(revHistory, revision);
        }

        // Add the revision history to the book
        final String fixedRevisionHistoryXml = DocbookBuildUtilities.convertDocumentToDocbook45FormattedString(revHistoryDoc, "appendix",
                buildData.getEntityFileName(), getXMLFormatProperties());
        try {
            buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + REVISION_HISTORY_FILE_NAME,
                    fixedRevisionHistoryXml.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
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
     * @param buildData  Information and data structures for the build.
     * @param xmlDoc     An XML DOM document that contains key regex expressions.
     * @param authorInfo An AuthorInformation entity object containing the details for who requested the build.
     * @param requester  The user object for the build request.
     * @return Returns an XML element that represents a {@code <revision>} element initialised with the authors information.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected Element generateRevision(final BuildData buildData, final Document xmlDoc, final AuthorInformation authorInfo,
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
        revDateEle.setTextContent(dateFormatter.format(buildData.getBuildDate()));
        revision.appendChild(revDateEle);

        /*
         * Determine the revnumber to use. If we have an override specified then use that directly. If not then build up the
         * revision number using the Book Edition and Publication Number. The format to build it in is: <EDITION>-<PUBSNUMBER>.
         * If Edition only specifies a x or x.y version (eg 5 or 5.1) then postfix the version so it matches the x.y.z format
         * (eg 5.0.0).
         */
        final String overrideRevnumber = buildData.getBuildOptions().getOverrides().get(CSConstants.REVNUMBER_OVERRIDE);
        final String revnumber;
        if (overrideRevnumber == null) {
            revnumber = DocbookBuildUtilities.generateRevisionNumber(buildData.getContentSpec());
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
        if (buildData.getBuildOptions().getRevisionMessages() != null && !buildData.getBuildOptions().getRevisionMessages().isEmpty()) {
            for (final String revMessage : buildData.getBuildOptions().getRevisionMessages()) {
                final Element revMemberEle = xmlDoc.createElement("member");
                revMemberEle.setTextContent(revMessage);
                simplelist.appendChild(revMemberEle);
            }
        }

        // Add the revision information
        final Element listMemberEle = xmlDoc.createElement("member");

        final ContentSpec contentSpec = buildData.getContentSpec();
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
     * @param buildData Information and data structures for the build.@return The Chapter represented as Docbook markup.
     */
    private String buildTranslateCSChapter(final BuildData buildData) {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final TranslatedContentSpecWrapper translatedContentSpec = EntityUtilities.getClosestTranslatedContentSpecById(providerFactory,
                contentSpec.getId(), contentSpec.getRevision());

        final String para;
        if (translatedContentSpec != null) {
            final String url = translatedContentSpec.getEditorURL(buildData.getZanataDetails(), buildData.getBuildLocale());

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
     * @param buildData Information and data structures for the build.
     * @return A docbook formatted string representation of the error chapter.
     */
    private String buildErrorChapter(final BuildData buildData) {
        log.info("\tBuilding Error Chapter");

        String errorItemizedLists = "";

        if (buildData.getErrorDatabase().hasItems(buildData.getBuildLocale())) {
            for (final TopicErrorData topicErrorData : buildData.getErrorDatabase().getErrors(buildData.getBuildLocale())) {
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
            final String errorGlossary = buildErrorChapterGlossary(buildData, "Compiler Glossary");
            if (errorGlossary != null) {
                errorItemizedLists += errorGlossary;
            }
        } else {
            errorItemizedLists = "<para>No Errors Found</para>";
        }

        if (buildData.getContentSpec().getBookType() == BookType.ARTICLE || buildData.getContentSpec().getBookType() == BookType
                .ARTICLE_DRAFT) {
            return DocBookUtilities.buildSection(errorItemizedLists, "Compiler Output");
        } else {
            return DocBookUtilities.buildChapter(errorItemizedLists, "Compiler Output");
        }
    }

    /**
     * Builds the Glossary used in the Error Chapter.
     *
     * @param buildData Information and data structures for the build.
     * @param title     The title for the glossary.
     * @return A docbook formatted string representation of the glossary.
     */
    private String buildErrorChapterGlossary(final BuildData buildData, final String title) {
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
        if (buildData.isTranslationBuild()) {
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
     * @param buildData Information and data structures for the build.
     * @return The Docbook Report Chapter formatted as a String.
     */
    private String buildReportChapter(final BuildData buildData) {
        log.info("\tBuilding Report Chapter");

        final ContentSpec contentSpec = buildData.getContentSpec();
        final String locale = buildData.getBuildLocale();
        final ZanataDetails zanataDetails = buildData.getZanataDetails();

        String reportChapter = "";

        final List<TopicErrorData> noContentTopics = buildData.getErrorDatabase().getErrorsOfType(locale, ErrorType.NO_CONTENT);
        final List<TopicErrorData> invalidInjectionTopics = buildData.getErrorDatabase().getErrorsOfType(locale,
                ErrorType.INVALID_INJECTION);
        final List<TopicErrorData> invalidContentTopics = buildData.getErrorDatabase().getErrorsOfType(locale, ErrorType.INVALID_CONTENT);
        final List<TopicErrorData> invalidImageTopics = buildData.getErrorDatabase().getErrorsOfType(locale, ErrorType.INVALID_IMAGES);
        final List<TopicErrorData> untranslatedTopics = buildData.getErrorDatabase().getErrorsOfType(locale, ErrorType.UNTRANSLATED);
        final List<TopicErrorData> incompleteTranslatedTopics = buildData.getErrorDatabase().getErrorsOfType(locale,
                ErrorType.INCOMPLETE_TRANSLATION);
        final List<TopicErrorData> fuzzyTranslatedTopics = buildData.getErrorDatabase().getErrorsOfType(locale,
                ErrorType.FUZZY_TRANSLATION);
        final List<TopicErrorData> notPushedTranslatedTopics = buildData.getErrorDatabase().getErrorsOfType(locale,
                ErrorType.NOT_PUSHED_FOR_TRANSLATION);
        final List<TopicErrorData> oldTranslatedTopics = buildData.getErrorDatabase().getErrorsOfType(locale, ErrorType.OLD_TRANSLATION);
        final List<TopicErrorData> oldUntranslatedTopics = buildData.getErrorDatabase().getErrorsOfType(locale, ErrorType.OLD_UNTRANSLATED);

        final List<String> list = new LinkedList<String>();
        list.add(DocBookUtilities.buildListItem("Total Number of Errors: " + getNumErrors()));
        list.add(DocBookUtilities.buildListItem("Total Number of Warnings: " + getNumWarnings()));
        list.add(DocBookUtilities.buildListItem("Number of Topics with No Content: " + noContentTopics.size()));
        list.add(DocBookUtilities.buildListItem("Number of Topics with Invalid Injection points: " + invalidInjectionTopics.size()));
        list.add(DocBookUtilities.buildListItem("Number of Topics with Invalid Content: " + invalidContentTopics.size()));
        list.add(DocBookUtilities.buildListItem("Number of Topics with Invalid Image references: " + invalidImageTopics.size()));

        if (buildData.isTranslationBuild()) {
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
        if (buildData.isTranslationBuild()) {
            reportChapter += generateAllTopicZanataUrl(buildData);
        }

        final boolean showEditorLinks = buildData.getBuildOptions().getInsertEditorLinks();

        // Create the Report Tables
        reportChapter += ReportUtilities.buildReportTable(noContentTopics, "Topics that have no Content", showEditorLinks, zanataDetails);

        reportChapter += ReportUtilities.buildReportTable(invalidContentTopics, "Topics that have Invalid XML Content", showEditorLinks,
                zanataDetails);

        reportChapter += ReportUtilities.buildReportTable(invalidInjectionTopics, "Topics that have Invalid Injection points in the XML",
                showEditorLinks, zanataDetails);

        reportChapter += ReportUtilities.buildReportTable(invalidImageTopics, "Topics that have Invalid Image references in the XML",
                showEditorLinks, zanataDetails);

        if (buildData.isTranslationBuild()) {
            reportChapter += ReportUtilities.buildReportTable(notPushedTranslatedTopics, "Topics that haven't been pushed for Translation",
                    showEditorLinks, zanataDetails);

            reportChapter += ReportUtilities.buildReportTable(untranslatedTopics, "Topics that haven't been Translated", showEditorLinks,
                    zanataDetails);

            reportChapter += ReportUtilities.buildReportTable(incompleteTranslatedTopics, "Topics that have Incomplete Translations",
                    showEditorLinks, zanataDetails);

            reportChapter += ReportUtilities.buildReportTable(fuzzyTranslatedTopics, "Topics that have fuzzy Translations", showEditorLinks,
                    zanataDetails);

            reportChapter += ReportUtilities.buildReportTable(oldUntranslatedTopics,
                    "Topics that haven't been Translated but are using previous revisions", showEditorLinks, zanataDetails);

            reportChapter += ReportUtilities.buildReportTable(oldTranslatedTopics,
                    "Topics that have been Translated using a previous revision", showEditorLinks, zanataDetails);
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
     * @param buildData Information and data structures for the build.@return The docbook generated content.
     */
    protected String generateAllTopicZanataUrl(final BuildData buildData) {
        final ZanataDetails zanataDetails = buildData.getZanataDetails();
        final String zanataServerUrl = zanataDetails == null ? null : zanataDetails.getServer();
        final String zanataProject = zanataDetails == null ? null : zanataDetails.getProject();
        final String zanataVersion = zanataDetails == null ? null : zanataDetails.getVersion();

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
            zanataUrl.append("localeId=" + buildData.getBuildLocale());

            // Add all the Topic Zanata Ids
            final List<TranslatedTopicWrapper> topics = buildData.getBuildDatabase().getAllTopics();
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
                    zanataUrl.append("localeId=" + buildData.getBuildLocale());
                }
            }

            // Add the CSP Zanata ID
            final TranslatedContentSpecWrapper translatedContentSpec = EntityUtilities.getClosestTranslatedContentSpecById(providerFactory,
                    buildData.getContentSpec().getId(), buildData.getContentSpec().getRevision());
            if (translatedContentSpec != null) {
                zanataUrl.append("&amp;");
                zanataUrl.append("doc=" + translatedContentSpec.getZanataId());
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
     * Processes the Topics in the BuildDatabase and builds up the images found within the topics XML. If the image reference is
     * blank or invalid it is replaced by the fail penguin image.
     *
     * @param buildData Information and data structures for the build.
     */
    @SuppressWarnings("unchecked")
    private void processImageLocations(final BuildData buildData) {
        final List<Integer> topicIds = buildData.getBuildDatabase().getTopicIds();
        for (final Integer topicId : topicIds) {
            // TODO Deal with different spec topic revisions in this method once images are fixed
            final SpecTopic specTopic = buildData.getBuildDatabase().getSpecTopicsForTopicID(topicId).get(0);
            final BaseTopicWrapper<?> topic = specTopic.getTopic();

            if (log.isDebugEnabled()) log.debug("\tProcessing SpecTopic " + specTopic.getId() + (specTopic.getRevision() != null ? (", " +
                    "Revision " + specTopic.getRevision()) : ""));

            /*
             * Images have to be in the image folder in Publican. Here we loop through all the imagedata elements and fix up any
             * reference to an image that is not in the images folder.
             */
            final List<Node> images = XMLUtilities.getChildNodes(specTopic.getXMLDocument(), "imagedata", "inlinegraphic");

            for (final Node imageNode : images) {
                final NamedNodeMap attributes = imageNode.getAttributes();
                if (attributes != null) {
                    final Node fileRefAttribute = attributes.getNamedItem("fileref");

                    if (fileRefAttribute != null && (fileRefAttribute.getNodeValue() == null || fileRefAttribute.getNodeValue().isEmpty()
                    )) {
                        fileRefAttribute.setNodeValue("images/" + BuilderConstants.FAILPENGUIN_PNG_NAME + ".jpg");
                        buildData.getImageLocations().add(new TopicImageData(topic, fileRefAttribute.getNodeValue()));
                    } else if (fileRefAttribute != null) {
                        // TODO Uncomment once image processing is fixed.
//                        if (specTopic.getRevision() == null)
//                        {
                        if (fileRefAttribute != null && !fileRefAttribute.getNodeValue().startsWith("images/")) {
                            fileRefAttribute.setNodeValue("images/" + fileRefAttribute.getNodeValue());
                        }

                        buildData.getImageLocations().add(new TopicImageData(topic, fileRefAttribute.getNodeValue()));

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
//                            buildData.getImageLocations().add(new TopicImageData(topic, fileRefAttribute.getNodeValue(),
// specTopic.getRevision()));
//                        }
                    }
                }
            }
        }
    }

    /**
     * Validates the XML after the first set of injections have been processed.
     *
     * @param buildData    Information and data structures for the build.
     * @param specTopic    The topic that is being validated.
     * @param topicDoc     A Document object that holds the Topic's XML
     * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
     * @return The validate document or a template if it failed validation.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    @SuppressWarnings("unchecked")
    private boolean validateTopicXML(final BuildData buildData, final SpecTopic specTopic, final Document topicDoc,
            final boolean useFixedUrls) throws BuildProcessingException {
        final BaseTopicWrapper<?> topic = specTopic.getTopic();

        byte[] entityData = new byte[0];
        try {
            entityData = BuilderConstants.DUMMY_CS_NAME_ENT_FILE.getBytes(ENCODING);
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error("", e);
        }

        // Validate the topic against its DTD/Schema
        final SAXXMLValidator validator = new SAXXMLValidator();
        if (!validator.validateXML(topicDoc, BuilderConstants.ROCBOOK_45_DTD, rocbookdtd.getValue(), "Book.ent", entityData)) {
            final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                    getErrorInvalidValidationTopicTemplate().getValue(), buildData.getBuildOptions());

            final String xmlStringInCDATA = DocbookBuildUtilities.convertDocumentToCDATAFormattedString(topicDoc, getXMLFormatProperties());
            buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                    BuilderConstants.ERROR_INVALID_TOPIC_XML + " The error is <emphasis>" + validator.getErrorText() + "</emphasis>. The " +
                            "processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");
            DocbookBuildUtilities.setSpecTopicXMLForError(specTopic, topicXMLErrorTemplate, useFixedUrls);

            return false;
        }

        // Check the content of the XML for things not picked up by DTD validation
        final List<String> xmlErrors = DocbookBuildUtilities.checkTopicForInvalidContent(topic, topicDoc);
        if (xmlErrors.size() > 0) {
            final String topicXMLErrorTemplate = DocbookBuildUtilities.buildTopicErrorTemplate(topic,
                    getErrorInvalidValidationTopicTemplate().getValue(), buildData.getBuildOptions());

            final String xmlStringInCDATA = DocbookBuildUtilities.convertDocumentToCDATAFormattedString(topicDoc, getXMLFormatProperties());

            // Add the error and processed XML to the error message
            final String errorMessage = CollectionUtilities.toSeperatedString(xmlErrors,
                    "</para><para>" + BuilderConstants.ERROR_INVALID_TOPIC_XML + " ");
            buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                    BuilderConstants.ERROR_INVALID_TOPIC_XML + " " + errorMessage + "</para><para>The processed XML is <programlisting>" +
                            xmlStringInCDATA +
                            "</programlisting>");

            DocbookBuildUtilities.setSpecTopicXMLForError(specTopic, topicXMLErrorTemplate, useFixedUrls);

            return false;
        }

        return true;
    }

    /**
     * Process a topic and add the section info information. This information consists of the keywordset information. The
     * keywords are populated using the tags assigned to the topic.
     *
     * @param topic The Topic to create the sectioninfo for.
     * @param doc   The XML Document DOM oject for the topics XML.
     */
    protected void processTopicSectionInfo(final BaseTopicWrapper<?> topic, final Document doc) {
        if (doc == null || topic == null) return;

        final CollectionWrapper<TagWrapper> tags = topic.getTags();

        if (tags != null && tags.getItems() != null && tags.getItems().size() > 0) {
            // Find the sectioninfo node in the document, or create one if it doesn't exist
            final Element sectionInfo;
            final List<Node> sectionInfoNodes = XMLUtilities.getDirectChildNodes(doc.getDocumentElement(), "sectioninfo");
            if (sectionInfoNodes.size() == 1) {
                sectionInfo = (Element) sectionInfoNodes.get(0);
            } else {
                sectionInfo = doc.createElement("sectioninfo");
            }

            // Build up the keywordset
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

            // Only update the section info if we've added data
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
     * @param processedFileNames A modifiable Set of filenames that have already been processed.
     * @return True if the fixed url property tags were able to be created for all topics, and false otherwise.
     */
    protected boolean setFixedURLsPass(final List<TopicWrapper> topics, final Set<String> processedFileNames) {
        log.info("Doing Fixed URL Pass");

        int tries = 0;
        boolean success = false;

        try {
            // This first pass will update or correct the fixed url property tags on the current revision
            while (tries < BuilderConstants.MAXIMUM_SET_PROP_TAGS_RETRY && !success) {
                ++tries;
                final CollectionWrapper<TopicWrapper> updateTopics = topicProvider.newTopicCollection();

                for (final TopicWrapper topic : topics) {

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

                updateFixedURLsForTopics(updateTopics, topics);
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
    protected <T extends BaseTopicWrapper<T>> void setFixedURLsForRevisionsPass(final List<T> topics,
            final Set<String> processedFileNames) {
        log.info("Doing Revisions Fixed URL Pass");

        /*
         * Now loop over the revision topics, and make sure their fixed url property tags are unique. They only have to be
         * unique within the book.
         */
        for (final BaseTopicWrapper<?> topic : topics) {

            // Get the existing property tag
            PropertyTagInTopicWrapper existingUniqueURL = topic.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);

            // Create a property tag if none exists
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

                // Update the fixed url
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
