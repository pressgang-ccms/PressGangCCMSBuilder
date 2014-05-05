package org.jboss.pressgang.ccms.contentspec.builder;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.io.JsonStringEncoder;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.ITopicNode;
import org.jboss.pressgang.ccms.contentspec.InfoTopic;
import org.jboss.pressgang.ccms.contentspec.InitialContent;
import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.buglinks.BaseBugLinkStrategy;
import org.jboss.pressgang.ccms.contentspec.buglinks.BugLinkOptions;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuilderCreationException;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.DocBookBuildingOptions;
import org.jboss.pressgang.ccms.contentspec.exceptions.BugLinkException;
import org.jboss.pressgang.ccms.utils.structures.InjectionError;
import org.jboss.pressgang.ccms.contentspec.builder.structures.TopicErrorData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.TopicErrorDatabase.ErrorLevel;
import org.jboss.pressgang.ccms.contentspec.builder.structures.TopicErrorDatabase.ErrorType;
import org.jboss.pressgang.ccms.contentspec.builder.structures.TopicImageData;
import org.jboss.pressgang.ccms.contentspec.builder.utils.DocBookBuildUtilities;
import org.jboss.pressgang.ccms.contentspec.builder.utils.ReportUtilities;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.contentspec.entities.AuthorInformation;
import org.jboss.pressgang.ccms.contentspec.enums.BookType;
import org.jboss.pressgang.ccms.contentspec.enums.LevelType;
import org.jboss.pressgang.ccms.contentspec.enums.TopicType;
import org.jboss.pressgang.ccms.contentspec.interfaces.ShutdownAbleApp;
import org.jboss.pressgang.ccms.contentspec.sort.AuthorInformationComparator;
import org.jboss.pressgang.ccms.contentspec.structures.XMLFormatProperties;
import org.jboss.pressgang.ccms.contentspec.utils.ContentSpecUtilities;
import org.jboss.pressgang.ccms.contentspec.utils.EntityUtilities;
import org.jboss.pressgang.ccms.contentspec.utils.TranslationUtilities;
import org.jboss.pressgang.ccms.provider.BlobConstantProvider;
import org.jboss.pressgang.ccms.provider.ContentSpecProvider;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.jboss.pressgang.ccms.provider.FileProvider;
import org.jboss.pressgang.ccms.provider.ImageProvider;
import org.jboss.pressgang.ccms.provider.PropertyTagProvider;
import org.jboss.pressgang.ccms.provider.ServerSettingsProvider;
import org.jboss.pressgang.ccms.provider.StringConstantProvider;
import org.jboss.pressgang.ccms.provider.TagProvider;
import org.jboss.pressgang.ccms.provider.TopicProvider;
import org.jboss.pressgang.ccms.provider.TranslatedCSNodeProvider;
import org.jboss.pressgang.ccms.provider.TranslatedContentSpecProvider;
import org.jboss.pressgang.ccms.provider.TranslatedTopicProvider;
import org.jboss.pressgang.ccms.provider.exception.NotFoundException;
import org.jboss.pressgang.ccms.provider.exception.ProviderException;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.utils.common.ResourceUtilities;
import org.jboss.pressgang.ccms.utils.common.StringUtilities;
import org.jboss.pressgang.ccms.utils.common.XMLUtilities;
import org.jboss.pressgang.ccms.utils.common.XMLValidator;
import org.jboss.pressgang.ccms.utils.common.ZipUtilities;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.utils.constants.CommonFilterConstants;
import org.jboss.pressgang.ccms.utils.structures.DocBookVersion;
import org.jboss.pressgang.ccms.utils.structures.Pair;
import org.jboss.pressgang.ccms.wrapper.BlobConstantWrapper;
import org.jboss.pressgang.ccms.wrapper.FileWrapper;
import org.jboss.pressgang.ccms.wrapper.ImageWrapper;
import org.jboss.pressgang.ccms.wrapper.LanguageFileWrapper;
import org.jboss.pressgang.ccms.wrapper.LanguageImageWrapper;
import org.jboss.pressgang.ccms.wrapper.PropertyTagInTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.ServerEntitiesWrapper;
import org.jboss.pressgang.ccms.wrapper.StringConstantWrapper;
import org.jboss.pressgang.ccms.wrapper.TagWrapper;
import org.jboss.pressgang.ccms.wrapper.TopicWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedCSNodeStringWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedCSNodeWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedContentSpecWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.collection.CollectionWrapper;
import org.jboss.pressgang.ccms.wrapper.collection.UpdateableCollectionWrapper;
import org.jboss.pressgang.ccms.zanata.ZanataDetails;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Entity;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A builder to build DocBook compatible output using a Content Specification. The builder works in the following stages:
 * <br />
 * <ol>
 * <li>
 * Populate Pass
 * <ul>
 * <li>Downloads all the topics using the REST API and creates the SpecTopic/Level database.</li>
 * </ul>
 * </li>
 * <li>
 * Topic Pass
 * <ul>
 * <li>Goes through each topic and converts the XML into a DOM element. If the XML is empty or can't be converted into a
 * DOM element it is replaced by a template. Then it initialises all the SpecTopics with the XML and Topic information.</li>
 * </ul>
 * </li>
 * <li>
 * Spec Topic First Pass
 * <ul>
 * <li>Goes through each SpecTopic and processes the XML to remove conditional statements. This stage also collects all of
 * the xml id's in the book, which are used in the next step.
 * </li>
 * </ul>
 * </li>
 * <li>
 * Topic First Pass
 * <ul>
 * <li>Goes through each SpecTopic and processes the XML to check that each &lt;xref&gt; or &lt;link&gt; has a valid
 * reference to some point in the book.
 * </li>
 * </ul>
 * </li>
 * <li>
 * Spec Topic Second Pass
 * <ul>
 * <li>Goes through each SpecTopic and processes the XML to resolve injections and then do a proper validation on the XML
 * content. It will then go through the XML and fix any possible duplicate ids (for example if the same topic is included
 * twice in a Content Spec).
 * </li>
 * </ul>
 * </li>
 * <li>
 * Link Second Pass
 * <ul>
 * <li>Goes through each SpecTopic and fixes any links where the link may no longer exist due to the linked topic being
 * replaced by an error template. If there is any found, than the old link is set to point to the error template.
 * </li>
 * </ul>
 * </li>
 * <li>
 * Build Pass
 * <ul>
 * <li>This step also builds the book structure using the content specification and goes through and converts each SpecTopics
 * DOM XML representation into a XML string. This step will also download any images and additional files and add them to the
 * output.
 * </li>
 * </ul>
 * </li>
 * </ol>
 */
public class DocBookBuilder implements ShutdownAbleApp {
    protected static final Logger log = Logger.getLogger(DocBookBuilder.class);
    protected static final DateFormat DATE_FORMATTER = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    protected static final boolean INCLUDE_CHECKSUMS = false;
    protected static final Integer MAX_URL_LENGTH = 4000;
    protected static final String ENCODING = "UTF-8";
    protected static final String REVISION_HISTORY_FILE_NAME = "Revision_History.xml";
    protected static final String FEEDBACK_FILE_NAME = "Feedback.xml";
    protected static final String AUTHOR_GROUP_FILE_NAME = "Author_Group.xml";
    protected static final String PREFACE_FILE_NAME = "Preface.xml";
    protected static final String LEGAL_NOTICE_FILE_NAME = "Legal_Notice.xml";

    protected final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    protected final AtomicBoolean shutdown = new AtomicBoolean(false);

    protected final XMLFormatProperties xmlFormatProperties = new XMLFormatProperties();
    protected final DataProviderFactory providerFactory;
    protected final ContentSpecProvider contentSpecProvider;
    protected final TranslatedCSNodeProvider translatedCSNodeProvider;
    protected final TopicProvider topicProvider;
    protected final TranslatedTopicProvider translatedTopicProvider;
    protected final TagProvider tagProvider;
    protected final PropertyTagProvider propertyTagProvider;
    protected final StringConstantProvider stringConstantProvider;
    protected final BlobConstantProvider blobConstantProvider;
    protected final ImageProvider imageProvider;

    private final BlobConstantWrapper rocbookDtd;
    private final BlobConstantWrapper docbookRng;
    private final String docbook45Entities;
    /**
     * The set of Messages to use when building
     */
    private final ResourceBundle messagesResourceBundle = ResourceBundle.getBundle("org.jboss.pressgang.ccms.contentspec.builder.Messages");
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
     * All data associated with the build.
     */
    private BuildData buildData;

    public DocBookBuilder(final DataProviderFactory providerFactory) throws BuilderCreationException {
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

        final ServerEntitiesWrapper serverEntities = providerFactory.getProvider(
                ServerSettingsProvider.class).getServerSettings().getEntities();
        rocbookDtd = blobConstantProvider.getBlobConstant(serverEntities.getRocBook45DTDBlobConstantId());
        docbookRng = blobConstantProvider.getBlobConstant(serverEntities.getDocBook50RNGBlobConstantId());
        errorEmptyTopicTemplate = stringConstantProvider.getStringConstant(serverEntities.getEmptyTopicStringConstantId());
        errorInvalidInjectionTopicTemplate = stringConstantProvider.getStringConstant(serverEntities.getInvalidInjectionStringConstantId());
        errorInvalidValidationTopicTemplate = stringConstantProvider.getStringConstant(serverEntities.getInvalidTopicStringConstantId());
        final StringConstantWrapper xmlElementsProperties = stringConstantProvider.getStringConstant(
                serverEntities.getXmlFormattingStringConstantId());

        /*
         * Get the XML formatting details. These are used to pretty-print the XML when it is converted into a String.
         */
        final Properties prop = new Properties();
        try {
            prop.load(new StringReader(xmlElementsProperties.getValue()));
        } catch (IOException e) {
            log.error(messagesResourceBundle.getString("FAILED_READING_XML_FORMATTING"));
            throw new BuilderCreationException(messagesResourceBundle.getString("FAILED_READING_XML_FORMATTING"));
        }

        final String verbatimElementsString = prop.getProperty(CommonConstants.VERBATIM_XML_ELEMENTS_PROPERTY_KEY);
        final String inlineElementsString = prop.getProperty(CommonConstants.INLINE_XML_ELEMENTS_PROPERTY_KEY);
        final String contentsInlineElementsString = prop.getProperty(CommonConstants.CONTENTS_INLINE_XML_ELEMENTS_PROPERTY_KEY);

        xmlFormatProperties.setVerbatimElements(CollectionUtilities.toArrayList(verbatimElementsString.split("[\\s]*,[\\s]*")));
        xmlFormatProperties.setInlineElements(CollectionUtilities.toArrayList(inlineElementsString.split("[\\s]*,[\\s]*")));
        xmlFormatProperties.setContentsInlineElements(CollectionUtilities.toArrayList(contentsInlineElementsString.split("[\\s]*,[\\s]*")));

        docbook45Entities = ResourceUtilities.resourceFileToString("/", "docbook.ent");
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

    protected ResourceBundle getMessages() {
        return messagesResourceBundle;
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
    public HashMap<String, byte[]> buildBook(final ContentSpec contentSpec, final String requester,
            final DocBookBuildingOptions buildingOptions) throws BuilderCreationException, BuildProcessingException {
        return buildBook(contentSpec, requester, buildingOptions, new HashMap<String, byte[]>());
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
    public HashMap<String, byte[]> buildTranslatedBook(final ContentSpec contentSpec, final String requester,
            final DocBookBuildingOptions buildingOptions,
            final ZanataDetails zanataDetails) throws BuilderCreationException, BuildProcessingException {
        return buildTranslatedBook(contentSpec, requester, buildingOptions, new HashMap<String, byte[]>(), zanataDetails);
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
    public HashMap<String, byte[]> buildBook(final ContentSpec contentSpec, final String requester,
            final DocBookBuildingOptions buildingOptions,
            final Map<String, byte[]> overrideFiles) throws BuilderCreationException, BuildProcessingException {
        return buildBook(contentSpec, requester, buildingOptions, overrideFiles, null, false);
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
    public HashMap<String, byte[]> buildTranslatedBook(final ContentSpec contentSpec, final String requester,
            final DocBookBuildingOptions buildingOptions, final Map<String, byte[]> overrideFiles,
            final ZanataDetails zanataDetails) throws BuilderCreationException, BuildProcessingException {
        return buildBook(contentSpec, requester, buildingOptions, overrideFiles, zanataDetails, true);
    }

    @SuppressWarnings("unchecked")
    protected HashMap<String, byte[]> buildBook(final ContentSpec contentSpec, final String requester,
            final DocBookBuildingOptions buildingOptions, final Map<String, byte[]> overrideFiles, final ZanataDetails zanataDetails,
            final boolean translationBuild) throws BuilderCreationException, BuildProcessingException {
        if (contentSpec == null) {
            throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
        }

        // Reset the builder
        resetBuilder();

        // If there is no requester specified than set it as unknown
        final String fixedRequester = requester == null ? "Unknown" : requester;

        // Create the build data
        final BuildData buildData = new BuildData(fixedRequester, contentSpec, buildingOptions, zanataDetails, providerFactory);
        setBuildData(buildData);

        // Set the override files if any were passed
        if (overrideFiles != null) {
            buildData.getOverrideFiles().putAll(overrideFiles);
        }

        // Set whether this is a translation build
        buildData.setTranslationBuild(translationBuild);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        // Get the translations
        if (translationBuild) {
            pullTranslations(contentSpec, buildData.getBuildLocale());
        }

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        doPopulateDatabasePass(buildData);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        final Map<SpecTopic, Set<String>> usedIdAttributes = new HashMap<SpecTopic, Set<String>>();
        doSpecTopicFirstPass(buildData, usedIdAttributes);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        /*
         * We need to create a list of all id's in the book to check if links are valid. So generate the id attribute that are
         * used by topics, section and chapters. Then add any id's that were found in the topics.
         */
        final Set<String> bookIdAttributes = buildData.getBuildDatabase().getIdAttributes(buildData);
        for (final Entry<SpecTopic, Set<String>> entry : usedIdAttributes.entrySet()) {
            bookIdAttributes.addAll(entry.getValue());
        }

        doLinkPass(buildData, bookIdAttributes);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        // second topic pass to set the ids and process injections
        doSpecTopicSecondPass(buildData, usedIdAttributes);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            shutdown.set(true);
            return null;
        }

        doLinkSecondPass(buildData, usedIdAttributes);

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

        return doBuildZipPass(buildData);
    }

    /**
     * Get the translations from the REST API and replace the original strings with the values downloaded.
     *
     * @param contentSpec The Content Spec to get and replace the translations for.
     * @param locale      The locale to pull the translations for.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected void pullTranslations(final ContentSpec contentSpec, final String locale) throws BuildProcessingException {
        final CollectionWrapper<TranslatedContentSpecWrapper> translatedContentSpecs = providerFactory.getProvider(
                TranslatedContentSpecProvider.class).getTranslatedContentSpecsWithQuery("query;" +
                CommonFilterConstants.ZANATA_IDS_FILTER_VAR + "=CS" + contentSpec.getId() + "-" + contentSpec.getRevision());

        // Ensure that the passed content spec has a translation
        if (translatedContentSpecs == null || translatedContentSpecs.isEmpty()) {
            throw new BuildProcessingException(
                    "Unable to find any translations for Content Spec " + contentSpec.getId() + (contentSpec.getRevision() == null ? "" :
                            (", Revision " + contentSpec.getRevision())));
        }

        final TranslatedContentSpecWrapper translatedContentSpec = translatedContentSpecs.getItems().get(0);
        if (translatedContentSpec.getTranslatedNodes() != null) {
            final Map<String, String> translations = new HashMap<String, String>();

            // Iterate over each translated node and build up the list of translated strings for the content spec.
            final List<TranslatedCSNodeWrapper> translatedCSNodes = translatedContentSpec.getTranslatedNodes().getItems();
            for (final TranslatedCSNodeWrapper translatedCSNode : translatedCSNodes) {
                // Only process nodes that have content pushed to Zanata
                if (!isNullOrEmpty(translatedCSNode.getOriginalString())) {
                    if (translatedCSNode.getTranslatedStrings() != null) {
                        final List<TranslatedCSNodeStringWrapper> translatedCSNodeStrings = translatedCSNode.getTranslatedStrings()
                                .getItems();
                        for (final TranslatedCSNodeStringWrapper translatedCSNodeString : translatedCSNodeStrings) {
                            if (translatedCSNodeString.getLocale().equals(locale)) {
                                translations.put(translatedCSNode.getOriginalString(), translatedCSNodeString.getTranslatedString());
                            }
                        }
                    }
                }
            }

            // Resolve any entities to make sure that the source string match
            final List<Entity> entities = XMLUtilities.parseEntitiesFromString(contentSpec.getEntities());
            TranslationUtilities.resolveCustomContentSpecEntities(entities, translatedContentSpec.getContentSpec());

            // Replace all the translated strings
            TranslationUtilities.replaceTranslatedStrings(translatedContentSpec.getContentSpec(), contentSpec, translations);

            // Set the Unique Ids so that they can be used later
            setTranslationUniqueIds(contentSpec, translatedContentSpec);
        }
    }

    /**
     * Sets the Translation Unique Ids on all the content spec nodes, that have a matching translated node.
     *
     * @param contentSpec           The content spec that contains all the nodes to set the translation unique ids for.
     * @param translatedContentSpec The Translated Content Spec object, that holds details on the translated nodes.
     */
    protected void setTranslationUniqueIds(final ContentSpec contentSpec,
            final TranslatedContentSpecWrapper translatedContentSpec) throws BuildProcessingException {
        final List<TranslatedCSNodeWrapper> translatedCSNodes = translatedContentSpec.getTranslatedNodes().getItems();
        for (final TranslatedCSNodeWrapper translatedCSNode : translatedCSNodes) {
            final org.jboss.pressgang.ccms.contentspec.Node node = ContentSpecUtilities.findMatchingContentSpecNode(contentSpec,
                    translatedCSNode.getNodeId());
            if (node != null) {
                node.setTranslationUniqueId(translatedCSNode.getId().toString());
            } else {
                // This shouldn't happen, but take care of it incase it does due to another bug
                throw new BuildProcessingException("Unable to find a matching Content Spec Node object for Translated Node " +
                        translatedCSNode.getId());
            }
        }
    }

    /**
     * Validate all the book links in the each topic to ensure that they exist somewhere in the book. If they don't then the
     * topic XML is replaced with a generic error template.
     *
     * @param buildData        Information and data structures for the build.
     * @param bookIdAttributes A set of all the id's that exist in the book.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    @SuppressWarnings("unchecked")
    protected void validateTopicLinks(final BuildData buildData, final Set<String> bookIdAttributes) throws BuildProcessingException {
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
                DocBookBuildUtilities.getTopicLinkIds(doc, linkIds);

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
                    final String xmlStringInCDATA = DocBookBuildUtilities.convertDocumentToCDATAFormattedString(doc,
                            getXMLFormatProperties());
                    buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                            "The following link(s) " + CollectionUtilities.toSeperatedString(invalidLinks, ", ") +
                                    " don't exist. The processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");

                    // Find the Topic ID
                    final Integer topicId = topic.getTopicId();

                    final List<ITopicNode> buildTopics = buildData.getBuildDatabase().getTopicNodesForTopicID(topicId);
                    for (final ITopicNode topicNode : buildTopics) {
                        DocBookBuildUtilities.setTopicNodeXMLForError(
                                buildData, topicNode, getErrorInvalidValidationTopicTemplate().getValue());
                    }
                }
            }
        }
    }

    /**
     * Populates the SpecTopicDatabase with the SpecTopics inside the content specification. It also adds the equivalent real
     * topics to each SpecTopic.
     *
     * @param buildData Information and data structures for the build.
     * @return True if the database was populated successfully otherwise false.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    @SuppressWarnings("unchecked")
    private void doPopulateDatabasePass(final BuildData buildData) throws BuildProcessingException {
        log.info("Doing " + buildData.getBuildLocale() + " Populate Database Pass");

        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, BaseTopicWrapper<?>> topics = new HashMap<String, BaseTopicWrapper<?>>();
        final boolean fixedUrlsSuccess;
        if (buildData.isTranslationBuild()) {
            //Translations should reference an existing historical topic with the fixed urls set, so we assume this to be the case
            fixedUrlsSuccess = true;
            populateTranslatedTopicDatabase(buildData, topics);
        } else {
            fixedUrlsSuccess = populateDatabaseTopics(buildData, topics);
        }
        buildData.setUseFixedUrls(fixedUrlsSuccess);

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        // Add all the levels to the database
        DocBookBuildUtilities.addLevelsToDatabase(buildData.getBuildDatabase(), contentSpec.getBaseLevel());

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        // Process the topics to make sure they are valid
        doTopicPass(buildData, topics);
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
        final List<TopicWrapper> allTopics = new ArrayList<TopicWrapper>();
        final List<TopicWrapper> latestTopics = new ArrayList<TopicWrapper>();
        final List<TopicWrapper> revisionTopics = new ArrayList<TopicWrapper>();

        // Calculate the ids of all the topics to get
        final List<ITopicNode> topicNodes = buildData.getContentSpec().getAllTopicNodes();
        for (final ITopicNode topicNode : topicNodes) {
            // Determine which topics we need to fetch the latest topics for and which topics we need to fetch revisions for.
            final TopicWrapper topic;
            if (topicNode.getRevision() != null && !buildData.getBuildOptions().getUseLatestVersions()) {
                topic = topicProvider.getTopic(topicNode.getDBId(), topicNode.getRevision());
                revisionTopics.add(topic);
                allTopics.add(topic);
            } else if (buildData.getBuildOptions().getMaxRevision() != null && !buildData.getBuildOptions().getUseLatestVersions()) {
                topic = topicProvider.getTopic(topicNode.getDBId(), buildData.getBuildOptions().getMaxRevision());
                revisionTopics.add(topic);
                allTopics.add(topic);
            } else {
                topic = topicProvider.getTopic(topicNode.getDBId());
                latestTopics.add(topic);
                allTopics.add(topic);
            }

            // Add the topic to the topics collection
            final String key = DocBookBuildUtilities.getTopicBuildKey(topic);
            topics.put(key, topic);
            buildData.getBuildDatabase().add(topicNode, key);
        }

        final Set<String> processedFileNames = new HashSet<String>();
        // If we are doing a build on a server then we shouldn't set the fixed urls
        if (buildData.getBuildOptions().isServerBuild()) {
            // Ensure that our revision topics FixedURLs are still valid
            setFixedURLsForRevisionsPass(buildData, allTopics, processedFileNames);
            fixedUrlsSuccess = true;
        } else {
            if (latestTopics != null) {
                /*
                 * assign fixed urls property tags to the topics. If fixedUrlsSuccess is true, the id of the topic sections,
                 * xref injection points and file names in the zip file will be taken from the fixed url property tag,
                 * defaulting back to the TopicID## format if for some reason that property tag does not exist.
                 */
                fixedUrlsSuccess = setFixedURLsPass(buildData, latestTopics, processedFileNames);
            } else {
                fixedUrlsSuccess = true;
            }

            // Ensure that our revision topics FixedURLs are still valid
            setFixedURLsForRevisionsPass(buildData, revisionTopics, processedFileNames);
        }

        return fixedUrlsSuccess;
    }

    /**
     * Gets the translated topics from the REST Interface and also creates any dummy translations for topics that have yet to be
     * translated.
     *
     * @param buildData        Information and data structures for the build.
     * @param translatedTopics The translated topic collection to add translated topics to.
     */
    private void populateTranslatedTopicDatabase(final BuildData buildData,
            final Map<String, BaseTopicWrapper<?>> translatedTopics) throws BuildProcessingException {
        final List<ITopicNode> topicNodes = buildData.getContentSpec().getAllTopicNodes();

        final int showPercent = 10;
        final float total = topicNodes.size();
        float current = 0;
        int lastPercent = 0;

        // Loop over each Topic Node in the content spec and get it's translated topic
        for (final ITopicNode topicNode : topicNodes) {
            getTranslatedTopicForTopicNode(buildData, topicNode, translatedTopics);

            ++current;
            final int percent = Math.round(current / total * 100);
            if (percent - lastPercent >= showPercent) {
                lastPercent = percent;
                log.info("\tPopulate " + buildData.getBuildLocale() + " Database Pass " + percent + "% Done");
            }
        }

        // Ensure that our translated topics FixedURLs are still valid
        final Set<String> processedFileNames = new HashSet<String>();

        // Ensure that the fixed urls are still unique
        final List<TranslatedTopicWrapper> translatedTopicList = new ArrayList<TranslatedTopicWrapper>();
        for (final Entry<String, BaseTopicWrapper<?>> topicEntry : translatedTopics.entrySet()) {
            translatedTopicList.add((TranslatedTopicWrapper) topicEntry.getValue());
        }
        setFixedURLsForRevisionsPass(buildData, translatedTopicList, processedFileNames);
    }

    /**
     * TODO
     *
     * @param buildData Information and data structures for the build.
     * @param topicNode The spec topic to find the Translated Topic for.
     * @return
     */
    protected void getTranslatedTopicForTopicNode(final BuildData buildData, final ITopicNode topicNode,
            final Map<String, BaseTopicWrapper<?>> translatedTopics) throws BuildProcessingException {
        final TopicWrapper topic;
        if (topicNode.getRevision() != null) {
            topic = topicProvider.getTopic(topicNode.getDBId(), topicNode.getRevision());
        } else if (buildData.getBuildOptions().getMaxRevision() != null) {
            topic = topicProvider.getTopic(topicNode.getDBId(), buildData.getBuildOptions().getMaxRevision());
        } else {
            topic = topicProvider.getTopic(topicNode.getDBId());
        }

        // Check if the spec topic has a matching translated topic node, if not then create a dummy topic
        if (topicNode.getTranslationUniqueId() != null) {
            final TranslatedCSNodeWrapper translatedCSNode = translatedCSNodeProvider.getTranslatedCSNode(
                    Integer.parseInt(topicNode.getTranslationUniqueId()));

            // Check if the translated node has a specific conditional translated topic, otherwise find the normal translated topic
            if (translatedCSNode.getTranslatedTopics() != null && !translatedCSNode.getTranslatedTopics().isEmpty()) {
                // Get the matching latest translated topic and pushed translated topics
                final Pair<TranslatedTopicWrapper, TranslatedTopicWrapper> latestTranslations = getLatestTranslations(buildData,
                        translatedCSNode.getTranslatedTopics(), topicNode.getRevision(), topic.getLocale());
                final TranslatedTopicWrapper latestTranslatedTopic = latestTranslations.getFirst();
                final TranslatedTopicWrapper latestPushedTranslatedTopic = latestTranslations.getSecond();

                // If the latest translation and latest pushed topic matches, then use that if not a dummy topic should be created
                TranslatedTopicWrapper translatedTopic = null;
                if (latestTranslatedTopic != null && latestPushedTranslatedTopic != null && latestPushedTranslatedTopic.getTopicRevision
                        ().equals(
                        latestTranslatedTopic.getTopicRevision())) {
                    translatedTopic = translatedTopicProvider.getTranslatedTopic(latestTranslatedTopic.getId());
                } else if (latestPushedTranslatedTopic != null) {
                    latestPushedTranslatedTopic.setTopic(topic);
                    translatedTopic = createDummyTranslatedTopicFromExisting(latestPushedTranslatedTopic, buildData.getBuildLocale());
                } else {
                    translatedTopic = createDummyTranslatedTopic(topic, buildData.getBuildLocale());
                }
                translatedTopic.setTranslatedCSNode(translatedCSNode);

                // Create the key and add the topic to the build database
                String key = DocBookBuildUtilities.getTranslatedTopicBuildKey(translatedTopic, translatedCSNode);
                translatedTopics.put(key, translatedTopic);
                buildData.getBuildDatabase().add(topicNode, key);
            } else {
                getLatestTranslatedTopicForTopicNode(buildData, topicNode, topic, translatedTopics);
            }
        } else {
            getLatestTranslatedTopicForTopicNode(buildData, topicNode, topic, translatedTopics);
        }
    }

    protected void getLatestTranslatedTopicForTopicNode(final BuildData buildData, final ITopicNode topicNode, final TopicWrapper topic,
            final Map<String, BaseTopicWrapper<?>> translatedTopics) {
        String key = DocBookBuildUtilities.getTopicBuildKey(topic);

        // If the topic has already been processed then add the spec topic and return
        if (translatedTopics.containsKey(key)) {
            buildData.getBuildDatabase().add(topicNode, key);
            return;
        }

        // Get the matching latest translated topic and pushed translated topics
        final Pair<TranslatedTopicWrapper, TranslatedTopicWrapper> latestTranslations = getLatestTranslations(buildData, topic,
                topicNode.getRevision());
        final TranslatedTopicWrapper latestTranslatedTopic = latestTranslations.getFirst();
        final TranslatedTopicWrapper latestPushedTranslatedTopic = latestTranslations.getSecond();

        // If the latest translation and latest pushed topic matches, then use that if not a dummy topic should be created
        if (latestTranslatedTopic != null && latestPushedTranslatedTopic != null && latestPushedTranslatedTopic.getTopicRevision().equals(
                latestTranslatedTopic.getTopicRevision())) {
            final TranslatedTopicWrapper translatedTopic = translatedTopicProvider.getTranslatedTopic(latestTranslatedTopic.getId());
            translatedTopics.put(key, translatedTopic);
            buildData.getBuildDatabase().add(topicNode, key);
        } else {
            final TranslatedTopicWrapper translatedTopic = createDummyTranslatedTopicFromTopic(topic, buildData.getBuildLocale());
            translatedTopics.put(key, translatedTopic);
            buildData.getBuildDatabase().add(topicNode, key);
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
        return getLatestTranslations(buildData, topic.getTranslatedTopics(), rev, topic.getLocale());
    }

    /**
     * Find the latest pushed and translated topics for a topic. We need to do this since translations are only added when some
     * content is added in Zanata. So if the latest translated topic doesn't match the topic revision of the latest pushed then
     * we will need to create a dummy topic for the latest pushed topic.
     *
     * @param buildData        Information and data structures for the build.
     * @param translatedTopics The translated topics to search find the latest translated topic and pushed translation.
     * @param rev              The revision for the topic as specified in the ContentSpec.
     * @param baseLocale       The original sources locale.
     * @return A Pair whose first element is the Latest Translated Topic and second element is the Latest Pushed Translation.
     */
    private Pair<TranslatedTopicWrapper, TranslatedTopicWrapper> getLatestTranslations(final BuildData buildData,
            final CollectionWrapper<TranslatedTopicWrapper> translatedTopics, final Integer rev, final String baseLocale) {
        TranslatedTopicWrapper latestTranslatedTopic = null;
        TranslatedTopicWrapper latestPushedTranslatedTopic = null;
        if (translatedTopics != null && translatedTopics.getItems() != null) {
            final List<TranslatedTopicWrapper> topics = translatedTopics.getItems();
            for (final TranslatedTopicWrapper tempTopic : topics) {
                // Find the Latest Translated Topic
                if (buildData.getBuildLocale().equals(
                        tempTopic.getLocale()) && (latestTranslatedTopic == null || latestTranslatedTopic.getTopicRevision() < tempTopic
                        .getTopicRevision()) && (rev == null || tempTopic.getTopicRevision() <= rev)) {
                    latestTranslatedTopic = tempTopic;
                }

                // Find the Latest Pushed Topic
                if (baseLocale.equals(
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
    private TranslatedTopicWrapper createDummyTranslatedTopicFromTopic(final TopicWrapper topic, final String locale) {
        final TranslatedTopicWrapper pushedTranslatedTopic = EntityUtilities.returnPushedTranslatedTopic(topic);

        /*
         * Try and use the untranslated default locale translated topic as the base for the dummy topic. If that fails then
         * create a dummy topic from the passed RESTTopicV1.
         */
        if (pushedTranslatedTopic != null) {
            pushedTranslatedTopic.setTopic(topic);
            return createDummyTranslatedTopicFromExisting(pushedTranslatedTopic, locale);
        } else {
            return createDummyTranslatedTopic(topic, locale);
        }
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

        // If we get to this point then no translation exists or the default locale translation failed to be downloaded.
        translatedTopic.setTopicId(topic.getId());
        translatedTopic.setTopicRevision(topic.getRevision());
        translatedTopic.setTranslationPercentage(100);
        translatedTopic.setXml(topic.getXml());
        translatedTopic.setTags(topic.getTags());
        translatedTopic.setSourceURLs(topic.getSourceURLs());
        translatedTopic.setProperties(topic.getProperties());
        translatedTopic.setLocale(locale);
        translatedTopic.setTitle(topic.getTitle());

        return translatedTopic;
    }

    /**
     * Creates a dummy translated topic from an existing translated topic so that a book can be built using the same relationships as a
     * normal build.
     *
     * @param translatedTopic The translated topic to create the dummy translated topic from.
     * @param locale          The locale to build the dummy translations for.
     * @return The dummy translated topic.
     */
    private TranslatedTopicWrapper createDummyTranslatedTopicFromExisting(final TranslatedTopicWrapper translatedTopic,
            final String locale) {
        // Make sure some collections are loaded, so the clone works properly
        translatedTopic.getTags();
        translatedTopic.getProperties();

        // Clone the existing version
        final TranslatedTopicWrapper defaultLocaleTranslatedTopic = translatedTopic.clone(false);

        // Negate the ID to show it isn't a proper translated topic
        defaultLocaleTranslatedTopic.setId(translatedTopic.getTopicId() * -1);

        // Change the locale since the default locale translation is being transformed into a dummy translation
        defaultLocaleTranslatedTopic.setLocale(locale);

        return defaultLocaleTranslatedTopic;
    }

    /**
     * Do the first topic pass on the database and check if the base XML is valid and set the Document Object's for each spec
     * topic. Also collect the ID Attributes that are used within the topics.
     *
     * @param buildData Information and data structures for the build.
     * @param topics    The list of topics to be checked and added to the database.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    private void doTopicPass(final BuildData buildData, final Map<String, BaseTopicWrapper<?>> topics) throws BuildProcessingException {
        log.info("Doing " + buildData.getBuildLocale() + " First topic pass");

        // Check that we have some topics to process
        if (topics != null) {
            log.info("\tProcessing " + topics.size() + " Topics");

            final int showPercent = 10;
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
                final Integer topicRevision = topic.getTopicRevision();

                boolean revHistoryTopic = topic.hasTag(buildData.getServerEntities().getRevisionHistoryTagId());
                boolean legalNoticeTopic = topic.hasTag(buildData.getServerEntities().getLegalNoticeTagId());
                boolean authorGroupTopic = topic.hasTag(buildData.getServerEntities().getAuthorGroupTagId());
                boolean abstractTopic = topic.hasTag(buildData.getServerEntities().getAbstractTagId());
                boolean infoTopic = topic.hasTag(buildData.getServerEntities().getInfoTagId());

                Document topicDoc = null;
                final String topicXML = topic.getXml();

                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return;
                }

                boolean xmlValid = true;

                // Check that the Topic XML exists and isn't empty
                if (topicXML == null || topicXML.trim().isEmpty()) {
                    buildData.getErrorDatabase().addWarning(topic, ErrorType.NO_CONTENT, BuilderConstants.WARNING_EMPTY_TOPIC_XML);
                    topicDoc = DocBookBuildUtilities.setTopicXMLForError(buildData, topic, getErrorEmptyTopicTemplate().getValue());
                    xmlValid = false;
                }

                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return;
                }

                // Make sure we have valid XML
                if (xmlValid) {
                    try {
                        final String fixedTopicXML;
                        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
                            fixedTopicXML = DocBookUtilities.addDocBook50Namespace(topicXML);
                        } else {
                            fixedTopicXML = topicXML;
                        }

                        topicDoc = XMLUtilities.convertStringToDocument(fixedTopicXML);

                        if (topicDoc == null) {
                            final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(topic.getXml());
                            buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                                    BuilderConstants.ERROR_INVALID_XML_CONTENT + " The processed XML is <programlisting>" +
                                            xmlStringInCDATA + "</programlisting>");
                            topicDoc = DocBookBuildUtilities.setTopicXMLForError(buildData, topic,
                                    getErrorInvalidValidationTopicTemplate().getValue());
                        }
                    } catch (Exception ex) {
                        final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(topic.getXml());
                        buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                                BuilderConstants.ERROR_BAD_XML_STRUCTURE + " " + StringUtilities.escapeForXML(
                                        ex.getMessage()) + " The processed XML is <programlisting>" + xmlStringInCDATA +
                                        "</programlisting>");
                        topicDoc = DocBookBuildUtilities.setTopicXMLForError(buildData, topic,
                                getErrorInvalidValidationTopicTemplate().getValue());
                    }
                }

                // Make sure the topic has the correct root element and other items
                if (revHistoryTopic) {
                    // If it is a translated build then check if we have anything more to merge together
                    if (buildData.isTranslationBuild()) {
                        topicDoc = mergeAdditionalTranslatedXML(buildData, topicDoc, (TranslatedTopicWrapper) topic,
                                TopicType.REVISION_HISTORY);
                    }

                    DocBookUtilities.wrapDocumentInAppendix(topicDoc);
                } else if (authorGroupTopic) {
                    // If it is a translated build then check if we have anything more to merge together
                    if (buildData.isTranslationBuild()) {
                        topicDoc = mergeAdditionalTranslatedXML(buildData, topicDoc, (TranslatedTopicWrapper) topic,
                                TopicType.AUTHOR_GROUP);
                    }

                    DocBookUtilities.wrapDocumentInAuthorGroup(topicDoc);
                } else if (legalNoticeTopic) {
                    DocBookUtilities.wrapDocumentInLegalNotice(topicDoc);
                } else if (abstractTopic) {
                    DocBookUtilities.wrapDocument(topicDoc, "abstract");
                } else if (infoTopic) {
                    if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
                        DocBookUtilities.wrapDocument(topicDoc, "info");
                    } else {
                        DocBookUtilities.wrapDocument(topicDoc, "sectioninfo");
                    }
                } else {
                    // Ensure the topic is wrapped in a section and the title matches the topic
                    DocBookUtilities.wrapDocumentInSection(topicDoc);
                    DocBookUtilities.setSectionTitle(buildData.getDocBookVersion(), topic.getTitle(), topicDoc);

                    processTopicSectionInfo(buildData, topic, topicDoc);
                }

                // Set the root element ID
                DocBookBuildUtilities.processTopicID(buildData, topic, topicDoc);

                // Add the document & topic to the database spec topics
                final List<ITopicNode> specTopics = buildData.getBuildDatabase().getTopicNodesForKey(key);
                for (final ITopicNode specTopic : specTopics) {
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
     * Merges the Additional Translated XML of a Translated Topic into the original Topic XML content.
     *
     * @param buildData
     * @param topicDoc        The transformed original XML content.
     * @param translatedTopic The Translated Topic that is being merged.
     * @param topicType       The type of topic being merged.
     * @return The merged DOM document.
     * @throws BuildProcessingException
     */
    private Document mergeAdditionalTranslatedXML(BuildData buildData, final Document topicDoc,
            final TranslatedTopicWrapper translatedTopic, final TopicType topicType) throws BuildProcessingException {
        Document retValue = topicDoc;
        if (!isNullOrEmpty(translatedTopic.getTranslatedAdditionalXML())) {
            Document additionalXMLDoc = null;
            try {
                additionalXMLDoc = XMLUtilities.convertStringToDocument(translatedTopic.getTranslatedAdditionalXML());
            } catch (Exception ex) {
                buildData.getErrorDatabase().addError(translatedTopic, ErrorType.INVALID_CONTENT,
                        BuilderConstants.ERROR_INVALID_TOPIC_XML + " " + StringUtilities.escapeForXML(ex.getMessage()));
                retValue = DocBookBuildUtilities.setTopicXMLForError(buildData, translatedTopic,
                        getErrorInvalidValidationTopicTemplate().getValue());
            }

            if (additionalXMLDoc != null) {
                // Merge the two together
                try {
                    if (TopicType.AUTHOR_GROUP.equals(topicType)) {
                        DocBookBuildUtilities.mergeAuthorGroups(topicDoc, additionalXMLDoc);
                    } else if (TopicType.REVISION_HISTORY.equals(topicType)) {
                        DocBookBuildUtilities.mergeRevisionHistories(topicDoc, additionalXMLDoc);
                    }
                } catch (BuildProcessingException ex) {
                    final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(translatedTopic.getTranslatedAdditionalXML());
                    buildData.getErrorDatabase().addError(translatedTopic, ErrorType.INVALID_CONTENT,
                            BuilderConstants.ERROR_BAD_XML_STRUCTURE + " " + StringUtilities.escapeForXML(
                                    ex.getMessage()) + " The processed XML is <programlisting>" + xmlStringInCDATA +
                                    "</programlisting>");
                    retValue = DocBookBuildUtilities.setTopicXMLForError(buildData, translatedTopic,
                            getErrorInvalidValidationTopicTemplate().getValue());
                }
            } else {
                final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(translatedTopic.getTranslatedAdditionalXML());
                buildData.getErrorDatabase().addError(translatedTopic, ErrorType.INVALID_CONTENT,
                        BuilderConstants.ERROR_INVALID_XML_CONTENT + " The processed XML is <programlisting>" +
                                xmlStringInCDATA + "</programlisting>");
                retValue = DocBookBuildUtilities.setTopicXMLForError(buildData, translatedTopic,
                        getErrorInvalidValidationTopicTemplate().getValue());
            }
        }

        return retValue;
    }

    /**
     * Loops through each of the spec topics in the database and sets the injections and unique ids for each id attribute in the
     * Topics XML.
     *
     * @param buildData        Information and data structures for the build.
     * @param usedIdAttributes The set of ids that have been used in the set of topics in the content spec.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    @SuppressWarnings("unchecked")
    private <T extends BaseTopicWrapper<T>> void doSpecTopicFirstPass(final BuildData buildData,
            final Map<SpecTopic, Set<String>> usedIdAttributes) throws BuildProcessingException {
        final List<SpecTopic> specTopics = buildData.getBuildDatabase().getAllSpecTopics();

        for (final SpecTopic specTopic : specTopics) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            if (log.isDebugEnabled()) log.debug("\tProcessing SpecTopic " + specTopic.getId() + (specTopic.getRevision() != null ? (", " +
                    "Revision " + specTopic.getRevision()) : ""));

            final BaseTopicWrapper<?> topic = specTopic.getTopic();
            final Document doc = specTopic.getXMLDocument();

            assert doc != null;
            assert topic != null;

            if (doc != null) {
                // Process the conditional statements
                processConditions(buildData, specTopic, doc);

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

                // Check to see if the translated topic revision is an older topic than the topic revision specified in the map
                if (topic instanceof TranslatedTopicWrapper) {
                    final TranslatedTopicWrapper pushedTranslatedTopic = EntityUtilities.returnPushedTranslatedTopic(
                            (TranslatedTopicWrapper) topic);
                    if (pushedTranslatedTopic != null && specTopic.getRevision() != null && !pushedTranslatedTopic.getTopicRevision()
                            .equals(
                            specTopic.getRevision())) {
                        if (EntityUtilities.isDummyTopic(topic)) {
                            buildData.getErrorDatabase().addWarning(topic, ErrorType.OLD_UNTRANSLATED,
                                    BuilderConstants.WARNING_OLD_UNTRANSLATED_TOPIC);
                        } else {
                            buildData.getErrorDatabase().addWarning(topic, ErrorType.OLD_TRANSLATION,
                                    BuilderConstants.WARNING_OLD_TRANSLATED_TOPIC);
                        }
                    }
                }

                /*
                 * Extract the id attributes used in this topic. We'll use this data in the second pass to make sure that
                 * individual topics don't repeat id attributes.
                 */
                DocBookBuildUtilities.collectIdAttributes(buildData.getDocBookVersion(), specTopic, doc, usedIdAttributes);
            }
        }
    }

    /**
     * Checks if the conditional pass should be performed.
     *
     * @param buildData Information and data structures for the build.
     * @param specTopic The spec topic the conditions should be processed for,
     * @param doc       The DOM Document to process the conditions against.
     */
    protected void processConditions(final BuildData buildData, final SpecTopic specTopic, final Document doc) {
        final String condition = specTopic.getConditionStatement(true);
        DocBookUtilities.processConditions(condition, doc, BuilderConstants.DEFAULT_CONDITION);
    }

    /**
     * Go through each topic and ensure that the links are valid, and then sets the duplicate ids for the SpecTopics/Levels
     *
     * @param buildData
     * @param bookIdAttributes The set of ids that have been used in the set of topics in the content spec.
     * @throws BuildProcessingException
     */
    protected void doLinkPass(final BuildData buildData, final Set<String> bookIdAttributes) throws BuildProcessingException {
        log.info("Doing " + buildData.getBuildLocale() + " Topic Link Pass");

        validateTopicLinks(buildData, bookIdAttributes);

        // Apply the duplicate ids for the spec topics
        buildData.getBuildDatabase().setDatabaseDuplicateIds(buildData);
    }

    /**
     * Loops through each of the spec topics in the database and sets the injections and unique ids for each id attribute in the
     * Topics XML.
     *
     * @param buildData        Information and data structures for the build.
     * @param usedIdAttributes The set of ids that have been used in the set of topics in the content spec.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    @SuppressWarnings("unchecked")
    private <T extends BaseTopicWrapper<T>> void doSpecTopicSecondPass(final BuildData buildData,
            final Map<SpecTopic, Set<String>> usedIdAttributes) throws BuildProcessingException {
        log.info("Doing " + buildData.getBuildLocale() + " Spec Topic Pass");
        final List<ITopicNode> topicNodes = buildData.getBuildDatabase().getAllTopicNodes();

        log.info("\tProcessing " + topicNodes.size() + " Spec Topics");

        final int showPercent = 10;
        final float total = topicNodes.size();
        float current = 0;
        int lastPercent = 0;

        final DocBookXMLPreProcessor xmlPreProcessor = buildData.getXMLPreProcessor();

        for (final ITopicNode topicNode : topicNodes) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            if (log.isDebugEnabled()) log.debug("\tProcessing SpecTopic " + topicNode.getId() + (topicNode.getRevision() != null ? (", " +
                    "Revision " + topicNode.getRevision()) : ""));

            ++current;
            final int percent = Math.round(current / total * 100);
            if (percent - lastPercent >= showPercent) {
                lastPercent = percent;
                log.info("\tProcessing Pass " + percent + "% Done");
            }

            final BaseTopicWrapper<?> topic = topicNode.getTopic();
            final Document doc = topicNode.getXMLDocument();

            assert doc != null;
            assert topic != null;

            if (doc != null) {
                final boolean valid = processSpecTopicInjections(buildData, topicNode, xmlPreProcessor);

                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return;
                }

                if (!valid) {
                    final String xmlStringInCDATA = DocBookBuildUtilities.convertDocumentToCDATAFormattedString(doc,
                            getXMLFormatProperties());
                    buildData.getErrorDatabase().addError(topic,
                            BuilderConstants.ERROR_INVALID_INJECTIONS + " The processed XML is <programlisting>" + xmlStringInCDATA +
                                    "</programlisting>");

                    DocBookBuildUtilities.setTopicNodeXMLForError(buildData, topicNode, getErrorInvalidInjectionTopicTemplate().getValue());
                } else {
                    // Check for any possible invalid injection references
                    final List<InjectionError> injectionErrors = XMLUtilities.checkForInvalidInjections(doc);
                    if (!injectionErrors.isEmpty()) {
                        for (final InjectionError injectionError : injectionErrors) {
                            final List<String> injectionErrorMsgs = new ArrayList<String>();
                            for (final String msg : injectionError.getMessages()) {
                                injectionErrorMsgs.add(DocBookUtilities.buildListItem(msg));
                            }

                            final String errorMsg = "\"" + injectionError.getInjection().trim() + "\" " + BuilderConstants
                                    .WARNING_POSSIBLE_INVALID_INJECTIONS + DocBookUtilities.wrapListItems(
                                    injectionErrorMsgs);
                            buildData.getErrorDatabase().addWarning(topic, ErrorType.POSSIBLE_INVALID_INJECTION, errorMsg);
                        }
                    }
                }

                // Ensure that all of the id attributes are valid by setting any duplicates with a post fixed number.
                DocBookBuildUtilities.setUniqueIds(buildData, topicNode, topicNode.getXMLDocument().getDocumentElement(),
                        topicNode.getXMLDocument(), usedIdAttributes);

                // Make sure the XML is valid docbook after the standard processing has been done
                if (validateTopicXML(buildData, topicNode, doc) && topicNode instanceof SpecTopic) {
                    // Add the editor/report a bug links (these should always be valid)
                    xmlPreProcessor.processTopicAdditionalInfo(buildData, (SpecTopic) topicNode, doc);
                } else {
                    // Re-run the unique id pass, as the topic would have been replaced by an error template
                    DocBookBuildUtilities.setUniqueIds(buildData, topicNode, topicNode.getXMLDocument().getDocumentElement(),
                            topicNode.getXMLDocument(), usedIdAttributes);
                }
            }
        }
    }

    /**
     * Fixes any topics links that have been broken due to the linked topics XML being invalid.
     *
     * @param buildData        Information and data structures for the build.
     * @param usedIdAttributes The set of ids that have been used in the set of topics in the content spec.
     * @throws BuildProcessingException
     */
    protected void doLinkSecondPass(final BuildData buildData,
            final Map<SpecTopic, Set<String>> usedIdAttributes) throws BuildProcessingException {
        final List<SpecTopic> topics = buildData.getBuildDatabase().getAllSpecTopics();
        for (final SpecTopic specTopic : topics) {
            final Document doc = specTopic.getXMLDocument();

            // Get the XRef links in the topic document
            final Set<String> linkIds = new HashSet<String>();
            DocBookBuildUtilities.getTopicLinkIds(doc, linkIds);

            final Map<String, SpecTopic> invalidLinks = new HashMap<String, SpecTopic>();

            for (final String linkId : linkIds) {
                // Ignore error links
                if (linkId.startsWith(CommonConstants.ERROR_XREF_ID_PREFIX)) continue;

                // Find the linked topic
                SpecTopic linkedTopic = null;
                for (final Map.Entry<SpecTopic, Set<String>> usedIdEntry : usedIdAttributes.entrySet()) {
                    if (usedIdEntry.getValue().contains(linkId)) {
                        linkedTopic = usedIdEntry.getKey();
                        break;
                    }
                }

                // If the linked topic has been set as an error, than update the links to point to the topic id
                if (linkedTopic != null && buildData.getErrorDatabase().hasErrorData(linkedTopic.getTopic())) {
                    final TopicErrorData errorData = buildData.getErrorDatabase().getErrorData(linkedTopic.getTopic());
                    if (errorData.hasFatalErrors()) {
                        invalidLinks.put(linkId, linkedTopic);
                    }
                }
            }

            // Go through and fix any invalid links
            if (!invalidLinks.isEmpty()) {
                final List<Node> linkNodes = XMLUtilities.getChildNodes(doc, "xref", "link");
                for (final Node linkNode : linkNodes) {
                    final String linkId = ((Element) linkNode).getAttribute("linkend");

                    if (invalidLinks.containsKey(linkId)) {
                        final SpecTopic linkedTopic = invalidLinks.get(linkId);
                        ((Element) linkNode).setAttribute("linkend",
                                linkedTopic.getUniqueLinkId(buildData.getServerEntities().getFixedUrlPropertyTagId(),
                                        buildData.isUseFixedUrls()));
                    }
                }
            }
        }
    }

    /**
     * Process the Injections for a SpecTopic and add any errors to the error database.
     *
     * @param buildData       Information and data structures for the build.
     * @param topicNode       The Build Topic to do injection processing on.
     * @param xmlPreProcessor The XML Processor to use for Injections.
     * @return True if no errors occurred or if the build is set to ignore missing injections, otherwise false.
     */
    @SuppressWarnings("unchecked")
    protected boolean processSpecTopicInjections(final BuildData buildData, final ITopicNode topicNode,
            final DocBookXMLPreProcessor xmlPreProcessor) {
        final BaseTopicWrapper<?> topic = topicNode.getTopic();
        final Document doc = topicNode.getXMLDocument();
        final boolean useFixedUrls = buildData.isUseFixedUrls();
        final Integer fixedUrlPropertyTagId = buildData.getServerEntities().getFixedUrlPropertyTagId();
        boolean valid = true;

        // Process the injection points
        if (buildData.getInjectionOptions().isInjectionAllowed()) {

            final ArrayList<String> customInjectionIds = new ArrayList<String>();

            if (topicNode instanceof SpecTopic) {
                final SpecTopic specTopic = (SpecTopic) topicNode;
                xmlPreProcessor.processPrevRelationshipInjections(specTopic, doc, useFixedUrls, fixedUrlPropertyTagId);
                xmlPreProcessor.processNextRelationshipInjections(specTopic, doc, useFixedUrls, fixedUrlPropertyTagId);

                // Front Matter topics are injected later as they need to be grouped
                if (topicNode.getTopicType() != TopicType.INITIAL_CONTENT) {
                    xmlPreProcessor.processPrerequisiteInjections(specTopic, doc, useFixedUrls, fixedUrlPropertyTagId);
                    xmlPreProcessor.processLinkListRelationshipInjections(specTopic, doc, useFixedUrls, fixedUrlPropertyTagId);
                    xmlPreProcessor.processSeeAlsoInjections(specTopic, doc, useFixedUrls, fixedUrlPropertyTagId);
                }
            }

            // Process the topics XML and insert the injection links
            final List<String> customInjectionErrors = xmlPreProcessor.processInjections(buildData.getContentSpec(), topicNode,
                    customInjectionIds, doc, buildData.getBuildOptions(), buildData.getBuildDatabase(), useFixedUrls,
                    fixedUrlPropertyTagId);

            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return false;
            }

            // Handle any errors that occurred while processing the injections
            valid = processSpecTopicInjectionErrors(buildData, topic, customInjectionErrors);
        }

        return valid;
    }

    /**
     * Process the Injections for a SpecTopic and add any errors to the error database.
     *
     * @param buildData       Information and data structures for the build.
     * @param level
     * @param doc
     * @param xmlPreProcessor The XML Processor to use for Injections.
     */
    protected void processLevelInjections(final BuildData buildData, final Level level, final Document doc, final Element node,
            final DocBookXMLPreProcessor xmlPreProcessor) {
        final boolean useFixedUrls = buildData.isUseFixedUrls();
        final Integer fixedUrlPropertyTagId = buildData.getServerEntities().getFixedUrlPropertyTagId();

        // Process the injection points
        if (buildData.getInjectionOptions().isInjectionAllowed()) {
            xmlPreProcessor.processPrerequisiteInjections(level, doc, node, useFixedUrls, fixedUrlPropertyTagId);
            xmlPreProcessor.processLinkListRelationshipInjections(level, doc, node, useFixedUrls, fixedUrlPropertyTagId);
            xmlPreProcessor.processSeeAlsoInjections(level, doc, node, useFixedUrls, fixedUrlPropertyTagId);
        }
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
            final List<String> customInjectionErrors) {
        boolean valid = true;

        if (!customInjectionErrors.isEmpty()) {
            final String message = "Topic has referenced Topic/Level(s) " + CollectionUtilities.toSeperatedString(
                    customInjectionErrors) + " in a custom injection point that was not included this book.";
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
     * @param buildData Information and data structures for the build.
     * @return A ZIP Archive containing all the information to build the book.
     * @throws BuildProcessingException Any build issue that should not occur under normal circumstances. Ie a Template can't be
     *                                  converted to a DOM Document.
     */
    private HashMap<String, byte[]> doBuildZipPass(final BuildData buildData) throws BuildProcessingException {
        log.info("Building the ZIP file");

        // Add the base book information
        final HashMap<String, byte[]> files = buildData.getOutputFiles();

        // Build the book base
        buildBookBase(buildData);

        // Add the additional files
        buildBookAdditions(buildData);

        // add the images to the book
        addImagesToBook(buildData);

        // add any additional files to the book
        addAdditionalFilesToBook(buildData);

        return files;
    }

    /**
     * Builds the Book.xml file of a Docbook from the resource files for a specific content specification.
     *
     * @param buildData
     * @throws BuildProcessingException
     */
    protected void buildBookBase(final BuildData buildData) throws BuildProcessingException {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final String escapedTitle = buildData.getEscapedBookTitle();

        // Get the template from the server
        final String bookXmlTemplate;
        if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
            bookXmlTemplate = stringConstantProvider.getStringConstant(
                    buildData.getServerEntities().getArticleStringConstantId()).getValue();
        } else {
            bookXmlTemplate = stringConstantProvider.getStringConstant(buildData.getServerEntities().getBookStringConstantId()).getValue();
        }

        // Setup the basic book.xml
        String basicBook = bookXmlTemplate.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, buildData.getEscapedBookTitle());
        basicBook = basicBook.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
        basicBook = basicBook.replaceAll(BuilderConstants.VERSION_REGEX, contentSpec.getVersion());
        basicBook = basicBook.replaceAll(BuilderConstants.DRAFT_REGEX, buildData.getBuildOptions().getDraft() ? "status=\"draft\"" : "");

        // Add the preface to the book.xml
        basicBook = basicBook.replaceAll(BuilderConstants.PREFACE_REGEX,
                "<xi:include href=\"Preface.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />");

        // Remove the Injection sequence as we'll add the revision history and xiinclude element later
        basicBook = basicBook.replaceAll(BuilderConstants.XIINCLUDES_INJECTION_STRING, "");
        basicBook = basicBook.replaceAll(BuilderConstants.REV_HISTORY_REGEX, "");

        // Create the Book.xml DOM Document
        Document bookBase = null;
        try {
            // Find and Remove the Doctype first
            final String doctype = XMLUtilities.findDocumentType(basicBook);

            bookBase = XMLUtilities.convertStringToDocument(doctype == null ? basicBook : basicBook.replace(doctype, ""));
        } catch (Exception e) {
            throw new BuildProcessingException(e);
        }

        boolean flattenStructure = buildData.getBuildOptions().isServerBuild() || buildData.getBuildOptions().getFlatten();
        final List<org.jboss.pressgang.ccms.contentspec.Node> levelData = contentSpec.getBaseLevel().getChildNodes();

        // Loop through and create each chapter and the topics inside those chapters
        log.info("\tBuilding Level and Topic XML Files");

        for (final org.jboss.pressgang.ccms.contentspec.Node node : levelData) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            if (node instanceof Level) {
                final Level level = (Level) node;

                if (level instanceof InitialContent && level.hasSpecTopics()) {
                    addLevelsInitialContent(buildData, (InitialContent) level, bookBase, bookBase.getDocumentElement(), false);
                } else if (level.hasSpecTopics()) {
                    // If the book is an article than just include it directly and don't create a new file
                    if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
                        // Create the section and its title
                        final Element sectionNode = bookBase.createElement("section");
                        setUpRootElement(buildData, level, bookBase, sectionNode);

                        createSectionXML(buildData, level, bookBase, sectionNode, buildData.getBookTopicsFolder(), flattenStructure);

                        bookBase.getDocumentElement().appendChild(sectionNode);
                    } else {
                        final Element xiInclude = createRootElementXML(buildData, bookBase, level, flattenStructure);
                        if (xiInclude != null) {
                            bookBase.getDocumentElement().appendChild(xiInclude);
                        }
                    }
                } else if (buildData.getBuildOptions().isAllowEmptySections()) {
                    final Element para = bookBase.createElement("para");
                    para.setTextContent("No Content");
                    bookBase.getDocumentElement().appendChild(para);
                }
            } else if (node instanceof SpecTopic) {
                final SpecTopic specTopic = (SpecTopic) node;

                Node topicNode = null;
                if (flattenStructure) {
                    // Include the topic as is, into the chapter
                    topicNode = bookBase.importNode(specTopic.getXMLDocument().getDocumentElement(), true);
                } else {
                    final String topicFileName = createTopicXMLFile(buildData, specTopic, buildData.getBookTopicsFolder());
                    if (topicFileName != null) {
                        topicNode = XMLUtilities.createXIInclude(bookBase, "topics/" + topicFileName);
                    }
                }

                // Add the node to the Book
                if (topicNode != null) {
                    bookBase.getDocumentElement().appendChild(topicNode);
                }
            }
        }

        // Insert the editor link for the content spec if it's a translation
        if (buildData.getBuildOptions().getInsertEditorLinks() && buildData.isTranslationBuild()) {
            final String translateLinkChapter = buildTranslateCSChapter(buildData);
            buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + "Translate.xml", StringUtilities.getStringBytes(
                    StringUtilities.cleanTextForXML(translateLinkChapter == null ? "" : translateLinkChapter)));

            // Create and append the XI Include element
            final Element translateXMLNode = XMLUtilities.createXIInclude(bookBase, "Translate.xml");
            bookBase.getDocumentElement().appendChild(translateXMLNode);
        }

        // Add any compiler errors
        if (!buildData.getBuildOptions().getSuppressErrorsPage() && buildData.getErrorDatabase().hasItems(buildData.getBuildLocale())) {
            final String compilerOutput = buildErrorChapter(buildData);
            buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + "Errors.xml",
                    StringUtilities.getStringBytes(StringUtilities.cleanTextForXML(compilerOutput == null ? "" : compilerOutput)));

            // Create and append the XI Include element
            final Element translateXMLNode = XMLUtilities.createXIInclude(bookBase, "Errors.xml");
            bookBase.getDocumentElement().appendChild(translateXMLNode);
        }

        // Add the report chapter
        if (buildData.getBuildOptions().getShowReportPage()) {
            final String compilerOutput = buildReportChapter(buildData);
            buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + "Report.xml",
                    StringUtilities.getStringBytes(StringUtilities.cleanTextForXML(compilerOutput == null ? "" : compilerOutput)));

            // Create and append the XI Include element
            final Element translateXMLNode = XMLUtilities.createXIInclude(bookBase, "Report.xml");
            bookBase.getDocumentElement().appendChild(translateXMLNode);
        }

        // Build the content specification page
        if (!buildData.getBuildOptions().getSuppressContentSpecPage()) {
            final String contentSpecPage = DocBookUtilities.buildAppendix(DocBookUtilities.wrapInPara(
                    "<programlisting>" + XMLUtilities.wrapStringInCDATA(contentSpec.toString(INCLUDE_CHECKSUMS)) + "</programlisting>"),
                    "Build Content Specification");
            addToZip(buildData.getBookLocaleFolder() + "Build_Content_Specification.xml",
                    DocBookBuildUtilities.addDocBookPreamble(buildData.getDocBookVersion(), contentSpecPage, "appendix",
                            buildData.getEntityFileName()), buildData);

            // Create and append the XI Include element
            final Element translateXMLNode = XMLUtilities.createXIInclude(bookBase, "Build_Content_Specification.xml");
            bookBase.getDocumentElement().appendChild(translateXMLNode);
        }

        // Add the revision history to the book.xml
        final Element revisionHistoryXMLNode = XMLUtilities.createXIInclude(bookBase, "Revision_History.xml");
        bookBase.getDocumentElement().appendChild(revisionHistoryXMLNode);

        // Add the index node if required
        if (contentSpec.getIncludeIndex()) {
            final Element indexNode = bookBase.createElement("index");
            bookBase.getDocumentElement().appendChild(indexNode);
        }

        // Change the DOM Document into a string so it can be added to the ZIP
        final String rootElementName = contentSpec.getBookType().toString().toLowerCase().replace("-draft", "");
        final String book = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(), bookBase,
                rootElementName, buildData.getEntityFileName(), getXMLFormatProperties());
        addToZip(buildData.getBookLocaleFolder() + escapedTitle + ".xml", book, buildData);
    }

    /**
     * Builds the basics of a Docbook from the resource files for a specific content specification.
     *
     * @param buildData Information and data structures for the build.
     * @return A Document object to be used in generating the book.xml
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected void buildBookAdditions(final BuildData buildData) throws BuildProcessingException {
        log.info("\tAdding standard files to Publican ZIP file");

        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, String> overrides = buildData.getBuildOptions().getOverrides();
        final Map<String, byte[]> overrideFiles = buildData.getOverrideFiles();

        // Load the templates from the server
        final String bookInfoTemplate;
        if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
            bookInfoTemplate = stringConstantProvider.getStringConstant(
                    buildData.getServerEntities().getArticleInfoStringConstantId()).getValue();
        } else {
            bookInfoTemplate = stringConstantProvider.getStringConstant(
                    buildData.getServerEntities().getBookInfoStringConstantId()).getValue();
        }

        // Setup Book_Info.xml
        buildBookInfoFile(buildData, bookInfoTemplate);

        // Setup Author_Group.xml
        if (overrides.containsKey(CSConstants.AUTHOR_GROUP_OVERRIDE) && overrideFiles.containsKey(CSConstants.AUTHOR_GROUP_OVERRIDE)) {
            // Add the override Author_Group.xml file to the book
            addToZip(buildData.getBookLocaleFolder() + AUTHOR_GROUP_FILE_NAME, overrideFiles.get(CSConstants.AUTHOR_GROUP_OVERRIDE),
                    buildData);
        } else if (contentSpec.getAuthorGroup() != null) {
            final TopicErrorData errorData = buildData.getErrorDatabase().getErrorData(contentSpec.getAuthorGroup().getTopic());
            if (errorData != null && errorData.hasFatalErrors()) {
                buildAuthorGroup(buildData, contentSpec.getAuthorGroup());
            } else {
                final String authorGroupXML = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(),
                        contentSpec.getAuthorGroup().getXMLDocument(), "authorgroup", buildData.getEntityFileName(),
                        getXMLFormatProperties());

                addToZip(buildData.getBookLocaleFolder() + AUTHOR_GROUP_FILE_NAME, authorGroupXML, buildData);
            }
        } else {
            buildAuthorGroup(buildData);
        }

        // Add the Feedback.xml if the override exists
        if (overrides.containsKey(CSConstants.FEEDBACK_OVERRIDE) && overrideFiles.containsKey(CSConstants.FEEDBACK_OVERRIDE)) {
            // Add the override Feedback.xml file to the book
            addToZip(buildData.getBookLocaleFolder() + FEEDBACK_FILE_NAME, overrideFiles.get(CSConstants.FEEDBACK_OVERRIDE), buildData);
        } else if (contentSpec.getFeedback() != null) {
            final String feedbackXml = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(),
                    contentSpec.getFeedback().getXMLDocument(), DocBookUtilities.TOPIC_ROOT_NODE_NAME, buildData.getEntityFileName(),
                    getXMLFormatProperties());
            // Add the feedback directly to the book
            addToZip(buildData.getBookLocaleFolder() + FEEDBACK_FILE_NAME, feedbackXml, buildData);
        }

        // Setup Legal_Notice.xml
        if (contentSpec.getLegalNotice() != null) {
            final String legalNoticeXML = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(),
                    contentSpec.getLegalNotice().getXMLDocument(), "legalnotice", buildData.getEntityFileName(), getXMLFormatProperties());
            addToZip(buildData.getBookLocaleFolder() + LEGAL_NOTICE_FILE_NAME, legalNoticeXML, buildData);
        }

        // Setup Preface.xml
        buildBookPreface(buildData);

        // Setup Revision_History.xml
        buildRevisionHistory(buildData, overrides);

        // Build the book .ent file
        final String entFile = buildBookEntityFile(buildData);
        addToZip(buildData.getBookLocaleFolder() + buildData.getEntityFileName(), entFile, buildData);

        // Setup the images and files folders
        addBookBaseFilesAndImages(buildData);
    }

    /**
     * Adds the basic Images and Files to the book that are the minimum requirements to build it.
     *
     * @param buildData Information and data structures for the build.
     */
    protected void addBookBaseFilesAndImages(final BuildData buildData) throws BuildProcessingException {
        final String pressgangWebsiteJS = buildPressGangWebsiteJS(buildData);

        addToZip(buildData.getBookImagesFolder() + "icon.svg", ResourceUtilities.resourceFileToByteArray("/", "icon.svg"), buildData);
        addToZip(buildData.getBookFilesFolder() + "pressgang_website.js", pressgangWebsiteJS, buildData);
    }

    /**
     * Build up the pressgang_website.js file to be used in help overlays from the topics used in the build.
     */
    protected String buildPressGangWebsiteJS(final BuildData buildData) throws BuildProcessingException {
        try {
            final ContentSpec contentSpec = buildData.getContentSpec();
            final StringBuilder retValue = new StringBuilder("pressgang_website_callback([\n");

            final JsonStringEncoder encoder = JsonStringEncoder.getInstance();

            final List<? extends BaseTopicWrapper<?>> topics = buildData.getBuildDatabase().getAllTopics();
            boolean initial = true;
            for (final BaseTopicWrapper<?> topic : topics) {
                // Ignore info topics
                if (topic.hasTag(buildData.getServerEntities().getInfoTagId())) continue;

                // Insert a newline and comma to separate each array variable only after the first variable has been set
                if (!initial) {
                    retValue.append(",\n");
                } else {
                    initial = false;
                }

                // Find the fixed url to use
                final Integer topicId = topic.getTopicId();
                final List<ITopicNode> topicNodes = buildData.getBuildDatabase().getTopicNodesForTopicID(topicId);
                final SpecTopic specTopic = (SpecTopic) topicNodes.get(0);
                final String fixedUrl = specTopic.getUniqueLinkId(buildData.getServerEntities().getFixedUrlPropertyTagId(),
                        buildData.isUseFixedUrls());

                // Find the new since value to use
                final List<PropertyTagInTopicWrapper> newSinceProperties = topic.getProperties(
                        buildData.getServerEntities().getPressGangWebsitePropertyTagId());
                final List<String> values = new ArrayList<String>();
                if (newSinceProperties != null) {
                    for (final PropertyTagInTopicWrapper newSinceProperty : newSinceProperties) {
                        values.add(newSinceProperty.getValue());
                    }
                    Collections.sort(values);
                }

                // Opening brace
                retValue.append("\t{");

                // Data

                retValue.append("\"topicId\":").append(topicId);
                retValue.append(",\"target\":\"").append(new String(encoder.quoteAsUTF8(fixedUrl), ENCODING)).append("\"");
                retValue.append(",\"title\":\"").append(new String(encoder.quoteAsUTF8(topic.getTitle()), ENCODING)).append("\"");
                retValue.append(",\"newSince\":\"").append(
                        values.isEmpty() ? "" : new String(encoder.quoteAsUTF8(values.get(values.size() - 1)), ENCODING)).append("\"");


                // Closing brace
                retValue.append("}");
            }

            retValue.append("]");

            // Add the content spec id
            retValue.append(", ").append(contentSpec.getId());

            // Add the product
            retValue.append(", \"").append(new String(encoder.quoteAsUTF8(contentSpec.getProduct()), ENCODING)).append("\"");

            // Add the title
            retValue.append(", \"").append(new String(encoder.quoteAsUTF8(contentSpec.getTitle()), ENCODING)).append("\"");

            // Add the version
            if (contentSpec.getVersion() != null) {
                retValue.append(", \"").append(new String(encoder.quoteAsUTF8(contentSpec.getVersion()), ENCODING)).append("\"");
            } else {
                retValue.append(", null");
            }

            retValue.append(");");
            return retValue.toString();
        } catch (UnsupportedEncodingException e) {
            throw new BuildProcessingException(e);
        }
    }

    /**
     * Builds the Book_Info.xml file that is a basic requirement to build the book.
     *
     * @param buildData        Information and data structures for the build.
     * @param bookInfoTemplate The Book_Info.xml template to add content to.
     */
    protected void buildBookInfoFile(final BuildData buildData, final String bookInfoTemplate) throws BuildProcessingException {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, String> overrides = buildData.getBuildOptions().getOverrides();

        String bookInfo = bookInfoTemplate.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, buildData.getEscapedBookTitle());

        // DocBook 5 changed the name of <articleinfo>/<bookinfo> to just <info>
        final String rootElementName;
        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
            rootElementName = "info";
            bookInfo = bookInfo.replaceAll("<(/)?bookinfo", "<$1info").replaceAll("<(/)?articleinfo", "<$1info");

            // Change the corpauthor element to orgname
            bookInfo = bookInfo.replaceAll("<(/)?corpauthor>", "<$1orgname>");

            // change the "id" attribute to "xml:id"
            bookInfo = bookInfo.replace("id=\"", "xml:id=\"");
        } else {
            if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
                rootElementName = "articleinfo";
            } else {
                rootElementName = "bookinfo";
            }
        }

        // Set the book title
        bookInfo = bookInfo.replaceAll(BuilderConstants.TITLE_REGEX,
                DocBookBuildUtilities.escapeForReplaceAll(DocBookUtilities.escapeForXML(contentSpec.getTitle())));
        // Set the book subtitle
        bookInfo = bookInfo.replaceAll(BuilderConstants.SUBTITLE_REGEX,
                contentSpec.getSubtitle() == null ? BuilderConstants.SUBTITLE_DEFAULT : DocBookBuildUtilities.escapeForReplaceAll(
                        DocBookUtilities.escapeForXML(contentSpec.getSubtitle())));
        // Set the book product
        bookInfo = bookInfo.replaceAll(BuilderConstants.PRODUCT_REGEX,
                DocBookBuildUtilities.escapeForReplaceAll(DocBookUtilities.escapeForXML(contentSpec.getProduct())));
        // Set the book product version
        bookInfo = bookInfo.replaceAll(BuilderConstants.VERSION_REGEX, contentSpec.getVersion() == null ? "" : contentSpec.getVersion());
        // Set or remove the book edition
        if (contentSpec.getEdition() == null) {
            bookInfo = bookInfo.replaceAll("<edition>.*</edition>(\r)?\n", "");
        } else {
            bookInfo = bookInfo.replaceAll(BuilderConstants.EDITION_REGEX, contentSpec.getEdition());
        }

        // Get the pubsnumber
        final String pubsNumber = overrides.containsKey(CSConstants.PUBSNUMBER_OVERRIDE) ? overrides.get(
                CSConstants.PUBSNUMBER_OVERRIDE) : (contentSpec.getPubsNumber() == null ? BuilderConstants.DEFAULT_PUBSNUMBER :
                contentSpec.getPubsNumber().toString());

        // pubsnumber is different is docbook 5
        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
            bookInfo = bookInfo.replaceAll(BuilderConstants.PUBSNUMBER_REGEX, "<biblioid class=\"pubsnumber\">" + pubsNumber +
                    "</biblioid>");
        } else {
            bookInfo = bookInfo.replaceAll(BuilderConstants.PUBSNUMBER_REGEX, "<pubsnumber>" + pubsNumber + "</pubsnumber>");
        }
        // Set the book abstract
        bookInfo = bookInfo.replaceAll(BuilderConstants.ABSTRACT_REGEX, getBookAbstract(contentSpec));
        // Set the book to have a Legal Notice
        bookInfo = bookInfo.replaceAll(BuilderConstants.LEGAL_NOTICE_REGEX, BuilderConstants.LEGAL_NOTICE_XML);

        if (!isNullOrEmpty(contentSpec.getBrandLogo())) {
            final String fixedLogoPath = contentSpec.getBrandLogo().contains(
                    "Common_Content/images/") ? contentSpec.getBrandLogo() : ("Common_Content/images/" + contentSpec.getBrandLogo());
            bookInfo = bookInfo.replaceFirst("<imagedata.*?/>", "<imagedata fileref=\"" + fixedLogoPath + "\" />");
        }

        // Add the preamble to the book info
        bookInfo = DocBookBuildUtilities.addDocBookPreamble(buildData.getDocBookVersion(), bookInfo, rootElementName,
                buildData.getEntityFileName());

        if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
            addToZip(buildData.getBookLocaleFolder() + "Article_Info.xml", bookInfo, buildData);
        } else {
            addToZip(buildData.getBookLocaleFolder() + "Book_Info.xml", bookInfo, buildData);
        }
    }

    /**
     * Gets the book abstract that should be inserted into the Book_Info.xml file.
     *
     * @param contentSpec The content spec to get the abstract from.
     * @return The XML content that represents the abstract.
     */
    private String getBookAbstract(final ContentSpec contentSpec) {
        final String retValue;
        if (contentSpec.getAbstract() == null && contentSpec.getAbstractTopic() == null) {
            retValue = BuilderConstants.DEFAULT_ABSTRACT;
        } else if (contentSpec.getAbstract() == null) {
            retValue = DocBookBuildUtilities.convertDocumentToFormattedString(contentSpec.getAbstractTopic().getXMLDocument(),
                    getXMLFormatProperties());
        } else if (contentSpec.getAbstract().matches("^<(formal|sim)?para>(.|\\s)*")) {
            retValue = "<abstract>\n\t\t" + contentSpec.getAbstract() + "\n\t</abstract>\n";
        } else {
            retValue = "<abstract>\n\t\t<para>\n\t\t\t" + contentSpec.getAbstract() + "\n\t\t</para>\n\t</abstract>\n";
        }

        return DocBookBuildUtilities.escapeForReplaceAll(DocBookUtilities.escapeForXML(retValue));
    }

    /**
     * Builds the Preface.xml file for the book.
     *
     * @param buildData
     * @throws BuildProcessingException
     */
    protected void buildBookPreface(final BuildData buildData) throws BuildProcessingException {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, String> overrides = buildData.getBuildOptions().getOverrides();
        final Map<String, byte[]> overrideFiles = buildData.getOverrideFiles();

        final Document prefaceDoc;
        try {
            prefaceDoc = XMLUtilities.convertStringToDocument("<preface></preface>");
        } catch (Exception e) {
            throw new BuildProcessingException(e);
        }

        // Add the title
        final String prefaceTitleTranslation = buildData.getConstants().getString("PREFACE");
        final Element titleEle = prefaceDoc.createElement("title");
        titleEle.setTextContent(prefaceTitleTranslation);
        prefaceDoc.getDocumentElement().appendChild(titleEle);

        // Add the Conventions.xml
        final Element conventions = XMLUtilities.createXIInclude(prefaceDoc, "Common_Content/Conventions.xml");
        prefaceDoc.getDocumentElement().appendChild(conventions);

        // Add the Feedback.xml
        if (overrides.containsKey(CSConstants.FEEDBACK_OVERRIDE) && overrideFiles.containsKey(
                CSConstants.FEEDBACK_OVERRIDE) || contentSpec.getFeedback() != null) {
            final Element xinclude = XMLUtilities.createXIInclude(prefaceDoc, "Feedback.xml");
            prefaceDoc.getDocumentElement().appendChild(xinclude);
        } else {
            final Element xinclude = XMLUtilities.createXIInclude(prefaceDoc, "Common_Content/Feedback.xml");
            prefaceDoc.getDocumentElement().appendChild(xinclude);
        }

        final String prefaceXml = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(), prefaceDoc,
                "preface", buildData.getEntityFileName(), getXMLFormatProperties());
        addToZip(buildData.getBookLocaleFolder() + PREFACE_FILE_NAME, prefaceXml, buildData);
    }

    /**
     * Builds the book .ent file that is a basic requirement to build the book.
     *
     * @param buildData Information and data structures for the build.
     * @return The book .ent file filled with content from the Content Spec.
     */
    protected String buildBookEntityFile(final BuildData buildData) throws BuildProcessingException {
        final ContentSpec contentSpec = buildData.getContentSpec();

        final StringBuilder retValue = new StringBuilder();

        if (!buildData.getBuildOptions().getUseOldBugLinks() && buildData.getBuildOptions().getInsertBugLinks()) {
            // Add the bug link entities
            retValue.append("<!-- BUG LINK ENTITIES -->\n");
            try {
                final BaseBugLinkStrategy bugLinkStrategy = buildData.getBugLinkStrategy();
                final BugLinkOptions bugLinkOptions = buildData.getBugLinkOptions();
                retValue.append(bugLinkStrategy.generateEntities(bugLinkOptions, buildData.getBuildName(), buildData.getBuildDate()));
            } catch (UnsupportedEncodingException e) {
                throw new BuildProcessingException(e);
            } catch (BugLinkException e) {
                throw new BuildProcessingException(e);
            } catch (final Exception ex) {
                throw new BuildProcessingException("Failed to insert Bug Links into the DOM Document", ex);
            }
        }

        retValue.append("<!-- CS ENTITIES -->\n");
        final String entities = ContentSpecUtilities.generateEntitiesForContentSpec(contentSpec, buildData.getDocBookVersion(),
                buildData.getEscapedBookTitle(), buildData.getOriginalBookTitle(), buildData.getOriginalBookProduct());
        retValue.append(entities);

        // Add the docbook.ent file for DocBook 5 builds
        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
            retValue.append("<!-- START DOCBOOK ENTITIES -->\n");
            retValue.append(docbook45Entities);
        }

        return retValue.toString();
    }

    /**
     * Creates all the chapters/appendixes for a book and generates the section/topic data inside of each chapter.
     *
     * @param buildData        Information and data structures for the build.
     * @param doc              The Book document object to add the child level content to.
     * @param level            The level to build the chapter from.
     * @param flattenStructure Whether or not the build should be flattened.
     * @return The Element that specifies the XiInclude for the chapter/appendix in the files.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected Element createRootElementXML(final BuildData buildData, final Document doc, final Level level,
            final boolean flattenStructure) throws BuildProcessingException {
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
            throw new BuildProcessingException(getMessages().getString("FAILED_CREATING_BASIC_TEMPLATE"));
        }

        // Create the title
        final String chapterName = level.getUniqueLinkId(buildData.getServerEntities().getFixedUrlPropertyTagId(),
                buildData.isUseFixedUrls());
        final String chapterXMLName = chapterName + ".xml";

        // Create the xiInclude to be added to the book.xml file
        final Element xiInclude = XMLUtilities.createXIInclude(doc, chapterXMLName);

        // Setup the title and id
        setUpRootElement(buildData, level, chapter, chapter.getDocumentElement());

        // Create and add the chapter/level contents
        createSectionXML(buildData, level, chapter, chapter.getDocumentElement(), buildData.getBookTopicsFolder() + chapterName + "/",
                flattenStructure);

        // Add the boiler plate text and add the chapter to the book
        final String chapterString = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(), chapter,
                elementName, buildData.getEntityFileName(), getXMLFormatProperties());
        addToZip(buildData.getBookLocaleFolder() + chapterXMLName, chapterString, buildData);

        return xiInclude;
    }

    /**
     * Creates all the chapters/appendixes for a book that are contained within another part/chapter/appendix and generates the
     * section/topic data inside of each chapter.
     *
     * @param buildData           Information and data structures for the build.
     * @param doc                 The document object to add the child level content to.
     * @param level               The level to build the chapter from.
     * @param parentFileDirectory The parent file location, so any files can be saved in a subdirectory of the parents location.
     * @param flattenStructure    Whether or not the build should be flattened.
     * @return The Element that specifies the XiInclude for the chapter/appendix in the files.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected Element createSubRootElementXML(final BuildData buildData, final Document doc, final Level level,
            final String parentFileDirectory, final boolean flattenStructure) throws BuildProcessingException {
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
            throw new BuildProcessingException(getMessages().getString("FAILED_CREATING_BASIC_TEMPLATE"));
        }

        // Create the title
        final String chapterName = level.getUniqueLinkId(buildData.getServerEntities().getFixedUrlPropertyTagId(),
                buildData.isUseFixedUrls());
        final String chapterXMLName = chapterName + ".xml";

        // Setup the title and id
        setUpRootElement(buildData, level, chapter, chapter.getDocumentElement());

        // Create and add the chapter/level contents
        createSectionXML(buildData, level, chapter, chapter.getDocumentElement(), parentFileDirectory + chapterName + "/",
                flattenStructure);

        // Add the boiler plate text and add the chapter to the book
        final String chapterString = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(), chapter,
                elementName, buildData.getEntityFileName(), getXMLFormatProperties());
        addToZip(buildData.getBookLocaleFolder() + chapterXMLName, chapterString, buildData);

        // Create the XIncludes that will get set in the book.xml
        final Element xiInclude = XMLUtilities.createXIInclude(doc, chapterXMLName);

        return xiInclude;
    }

    /**
     * Sets up an elements title, info and id based on the passed level.
     *
     * @param buildData Information and data structures for the build.
     * @param level     The level to build the root element is being built for.
     * @param doc       The document object the content is being added to.
     * @param ele
     */
    protected void setUpRootElement(final BuildData buildData, final Level level, final Document doc, Element ele) {
        final Element titleNode = doc.createElement("title");
        if (buildData.isTranslationBuild() && level.getTranslatedTitle() != null && !level.getTranslatedTitle().isEmpty()) {
            titleNode.setTextContent(DocBookUtilities.escapeForXML(level.getTranslatedTitle()));
        } else {
            titleNode.setTextContent(DocBookUtilities.escapeForXML(level.getTitle()));
        }

        // Add the info if the container has one
        final Element infoElement;
        if (level.getInfoTopic() != null) {
            final InfoTopic infoTopic = level.getInfoTopic();
            final Node info = doc.importNode(infoTopic.getXMLDocument().getDocumentElement(), true);

            // Generate the info node
            final String elementInfoName;
            if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
                elementInfoName = "info";
            } else {
                elementInfoName = ele.getNodeName() + "info";
            }
            infoElement = doc.createElement(elementInfoName);

            // Move the contents of the info to the chapter/level
            final NodeList infoChildren = info.getChildNodes();
            while (infoChildren.getLength() > 0) {
                infoElement.appendChild(infoChildren.item(0));
            }
        } else {
            infoElement = null;
        }

        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
            ele.appendChild(titleNode);
            if (infoElement != null) {
                ele.appendChild(infoElement);
            }
        } else {
            if (infoElement != null) {
                ele.appendChild(infoElement);
            }
            ele.appendChild(titleNode);
        }

        DocBookBuildUtilities.setDOMElementId(buildData.getDocBookVersion(), ele,
                level.getUniqueLinkId(buildData.getServerEntities().getFixedUrlPropertyTagId(), buildData.isUseFixedUrls()));
    }

    /**
     * Creates the section component of a chapter.xml for a specific Level.
     *
     * @param buildData          Information and data structures for the build.
     * @param level              The section level object to get content from.
     * @param chapter            The chapter document object that this section is to be added to.
     * @param parentNode         The parent XML node of this section.
     * @param parentFileLocation The parent file location, so any files can be saved in a subdirectory of the parents location.
     * @param flattenStructure   Whether or not the build should be flattened.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected void createSectionXML(final BuildData buildData, final Level level, final Document chapter, final Element parentNode,
            final String parentFileLocation, final Boolean flattenStructure) throws BuildProcessingException {
        final List<org.jboss.pressgang.ccms.contentspec.Node> levelData = level.getChildNodes();

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
                    (Level) node).getParent().getLevelType() == LevelType.PART) && ((Level) node).getLevelType() != LevelType
                    .INITIAL_CONTENT) {
                final Level childLevel = (Level) node;

                // Create a new file for the Chapter/Appendix
                final Element xiInclude = createSubRootElementXML(buildData, chapter, childLevel, parentFileLocation, flattenStructure);
                if (xiInclude != null) {
                    childNodes.add(xiInclude);
                }
            } else if (node instanceof Level && ((Level) node).getLevelType() == LevelType.INITIAL_CONTENT) {
                if (level.getLevelType() == LevelType.PART) {
                    addLevelsInitialContent(buildData, (InitialContent) node, chapter, intro, false);
                } else {
                    addLevelsInitialContent(buildData, (InitialContent) node, chapter, parentNode);
                }
            } else if (node instanceof Level) {
                final Level childLevel = (Level) node;

                // Create the section and its title
                final Element sectionNode = chapter.createElement("section");
                setUpRootElement(buildData, childLevel, chapter, sectionNode);

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
                    createSectionXML(buildData, childLevel, chapter, sectionNode, parentFileLocation, flattenStructure);
                }

                childNodes.add(sectionNode);
            } else if (node instanceof SpecTopic) {
                final SpecTopic specTopic = (SpecTopic) node;
                final Document topicDoc = specTopic.getXMLDocument();

                Node topicNode = null;
                if (flattenStructure) {
                    // Include the topic as is, into the chapter
                    topicNode = chapter.importNode(topicDoc.getDocumentElement(), true);
                } else {
                    // Create the topic file and add the reference to the chapter
                    final String topicFileName = createTopicXMLFile(buildData, specTopic, parentFileLocation);
                    if (topicFileName != null) {

                        // Remove the initial file location as we only want where it lives in the topics directory
                        final String fixedParentFileLocation = buildData.getBuildOptions().getFlattenTopics() ? "topics/" :
                                parentFileLocation.replace(
                                buildData.getBookLocaleFolder(), "");

                        topicNode = XMLUtilities.createXIInclude(chapter, fixedParentFileLocation + topicFileName);
                    }
                }

                // Add the node to the chapter
                if (topicNode != null) {
                    if (specTopic.getParent() != null && ((Level) specTopic.getParent()).getLevelType() == LevelType.PART) {
                        intro.appendChild(topicNode);
                    } else {
                        childNodes.add(topicNode);
                    }
                }
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

    protected void addLevelsInitialContent(final BuildData buildData, final InitialContent initialContent, final Document chapter,
            final Element parentNode) throws BuildProcessingException {
        addLevelsInitialContent(buildData, initialContent, chapter, parentNode, true);
    }

    protected void addLevelsInitialContent(final BuildData buildData, final InitialContent initialContent, final Document chapter,
            final Element parentNode, final boolean includeInfo) throws BuildProcessingException {
        // Copy the body content of the topics to the level's front matter
        for (final SpecTopic initialContentTopic : initialContent.getSpecTopics()) {
            // Insert the topic DOM document into the parent document
            addTopicContentsToLevelDocument(buildData.getDocBookVersion(), initialContent, initialContentTopic, parentNode, chapter,
                    includeInfo);
        }

        final DocBookXMLPreProcessor xmlPreProcessor = buildData.getXMLPreProcessor();

        // Process the see also/prereq injections for the level
        processLevelInjections(buildData, initialContent, chapter, parentNode, xmlPreProcessor);

        // Add the bug links for the front matter content
        xmlPreProcessor.processInitialContentBugLink(buildData, initialContent, chapter, parentNode);
    }

    /**
     * Adds a Topics contents as the introduction text for a Level.
     *
     * @param docBookVersion
     * @param level          The level the intro topic is being added for.
     * @param specTopic      The Topic that contains the introduction content.
     * @param parentNode     The DOM parent node the intro content is to be appended to.
     * @param doc            The DOM Document the content is to be added to.
     */
    protected void addTopicContentsToLevelDocument(final DocBookVersion docBookVersion, final Level level, final SpecTopic specTopic,
            final Element parentNode, final Document doc) {
        addTopicContentsToLevelDocument(docBookVersion, level, specTopic, parentNode, doc, true);
    }

    /**
     * Adds a Topics contents as the introduction text for a Level.
     *
     * @param docBookVersion
     * @param level          The level the intro topic is being added for.
     * @param specTopic      The Topic that contains the introduction content.
     * @param parentNode     The DOM parent node the intro content is to be appended to.
     * @param doc            The DOM Document the content is to be added to.
     */
    protected void addTopicContentsToLevelDocument(final DocBookVersion docBookVersion, final Level level, final SpecTopic specTopic,
            final Element parentNode, final Document doc, final boolean includeInfo) {
        final Node section = doc.importNode(specTopic.getXMLDocument().getDocumentElement(), true);

        final String infoName;
        if (docBookVersion == DocBookVersion.DOCBOOK_50) {
            infoName = "info";
        } else {
            infoName = DocBookUtilities.TOPIC_ROOT_SECTIONINFO_NODE_NAME;
        }

        if (includeInfo && (level.getLevelType() != LevelType.PART)) {
            // Reposition the sectioninfo
            final List<Node> sectionInfoNodes = XMLUtilities.getDirectChildNodes(section, infoName);
            if (sectionInfoNodes.size() != 0) {
                final String parentInfoName;
                if (docBookVersion == DocBookVersion.DOCBOOK_50) {
                    parentInfoName = "info";
                } else {
                    parentInfoName = parentNode.getNodeName() + "info";
                }

                // Check if the parent already has a info node
                final List<Node> infoNodes = XMLUtilities.getDirectChildNodes(parentNode, parentInfoName);
                final Node infoNode;
                if (infoNodes.size() == 0) {
                    infoNode = doc.createElement(parentInfoName);
                    DocBookUtilities.setInfo(docBookVersion, (Element) infoNode, parentNode);
                } else {
                    infoNode = infoNodes.get(0);
                }

                // Merge the info text
                final NodeList sectionInfoChildren = sectionInfoNodes.get(0).getChildNodes();
                final Node firstNode = infoNode.getFirstChild();
                while (sectionInfoChildren.getLength() > 0) {
                    if (firstNode != null) {
                        infoNode.insertBefore(sectionInfoChildren.item(0), firstNode);
                    } else {
                        infoNode.appendChild(sectionInfoChildren.item(0));
                    }
                }
            }
        }

        // Remove the title and sectioninfo
        final List<Node> titleNodes = XMLUtilities.getDirectChildNodes(section, DocBookUtilities.TOPIC_ROOT_TITLE_NODE_NAME, infoName);
        for (final Node removeNode : titleNodes) {
            section.removeChild(removeNode);
        }

        // Move the contents of the section to the chapter/level
        final NodeList sectionChildren = section.getChildNodes();
        while (sectionChildren.getLength() > 0) {
            parentNode.appendChild(sectionChildren.item(0));
        }
    }

    /**
     * Creates the Topic component of a chapter.xml for a specific SpecTopic.
     *
     * @param buildData          Information and data structures for the build.
     * @param specTopic          The build topic object to get content from.
     * @param parentFileLocation The topics parent file location, so the topic can be saved in a subdirectory.
     * @return The Topics filename.
     */
    protected String createTopicXMLFile(final BuildData buildData, final SpecTopic specTopic,
            final String parentFileLocation) throws BuildProcessingException {
        String topicFileName;
        final BaseTopicWrapper<?> topic = specTopic.getTopic();

        if (topic != null) {
            topicFileName = specTopic.getUniqueLinkId(buildData.getServerEntities().getFixedUrlPropertyTagId(),
                    buildData.isUseFixedUrls()) + ".xml";

            final String fixedParentFileLocation = buildData.getBuildOptions().getFlattenTopics() ? buildData.getBookTopicsFolder() :
                    parentFileLocation;
            final String fixedEntityPath = fixedParentFileLocation.replace(buildData.getBookLocaleFolder(), "").replaceAll(
                    ".*?" + File.separator + "", "../");

            final String topicXML = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(),
                    specTopic.getXMLDocument(), DocBookUtilities.TOPIC_ROOT_NODE_NAME, fixedEntityPath + buildData.getEntityFileName(),
                    getXMLFormatProperties());

            addToZip(fixedParentFileLocation + topicFileName, topicXML, buildData);

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
        // Load the database constants
        final byte[] failpenguinPng = blobConstantProvider.getBlobConstant(
                buildData.getServerEntities().getFailPenguinBlobConstantId()).getValue();

        // Download the image files that were identified in the processing stage
        float imageProgress = 0;
        final float imageTotal = buildData.getImageLocations().size();
        final int showPercent = 10;
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

            if (    /* characters were found */
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

                        // Find the image that matches this locale. If the locale isn't found then use the default locale
                        LanguageImageWrapper languageImageFile = null;
                        if (imageFile.getLanguageImages() != null && imageFile.getLanguageImages().getItems() != null) {
                            final List<LanguageImageWrapper> languageImages = imageFile.getLanguageImages().getItems();
                            for (final LanguageImageWrapper image : languageImages) {
                                if (image.getLocale().equals(buildData.getBuildLocale())) {
                                    languageImageFile = image;
                                    break;
                                } else if (image.getLocale().equals(
                                        buildData.getServerSettings().getDefaultLocale()) && languageImageFile == null) {
                                    languageImageFile = image;
                                }
                            }
                        }

                        if (languageImageFile != null && languageImageFile.getImageData() != null) {
                            success = true;
                            addToZip(buildData.getBookLocaleFolder() + imageLocation.getImageName(), languageImageFile.getImageData(),
                                    buildData);
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
        buildAuthorGroup(buildData, null);
    }

    /**
     * Builds the Author_Group.xml using the assigned writers for topics inside of the content specification.
     *
     * @param buildData Information and data structures for the build.
     * @param specTopic The topic to build the Author Group in place of.
     * @throws BuildProcessingException
     */
    private void buildAuthorGroup(final BuildData buildData, final SpecTopic specTopic) throws BuildProcessingException {
        log.info("\tBuilding " + AUTHOR_GROUP_FILE_NAME);

        // Setup Author_Group.xml
        Document authorDoc = null;
        try {
            authorDoc = XMLUtilities.convertStringToDocument("<authorgroup></authorgroup>");
        } catch (Exception ex) {
            // Exit since we shouldn't fail at converting the basic author group
            log.debug("", ex);
            throw new BuildProcessingException(
                    String.format(getMessages().getString("FAILED_CONVERTING_TEMPLATE"), AUTHOR_GROUP_FILE_NAME));
        }
        final LinkedHashMap<Integer, AuthorInformation> authorIDtoAuthor = new LinkedHashMap<Integer, AuthorInformation>();

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        // Set the id
        if (specTopic != null) {
            DocBookBuildUtilities.processTopicID(buildData, specTopic.getTopic(), authorDoc);
        } else {
            DocBookBuildUtilities.setDOMElementId(buildData.getDocBookVersion(), authorDoc.getDocumentElement(), "Author_Group");
        }

        // Get the mapping of authors using the topics inside the content spec
        for (final Integer topicId : buildData.getBuildDatabase().getTopicIds()) {
            final BaseTopicWrapper<?> topic = buildData.getBuildDatabase().getTopicNodesForTopicID(topicId).get(0).getTopic();
            final List<TagWrapper> authorTags = topic.getTagsInCategories(
                    CollectionUtilities.toArrayList(buildData.getServerEntities().getWriterCategoryId()));

            if (authorTags.size() > 0) {
                for (final TagWrapper author : authorTags) {
                    if (!authorIDtoAuthor.containsKey(author.getId())) {
                        final AuthorInformation authorInfo = EntityUtilities.getAuthorInformation(providerFactory,
                                buildData.getServerEntities(), author.getId(), author.getRevision());
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

        boolean insertedAuthor = false;

        // If one or more authors were found then remove the default and attempt to add them
        if (!authors.isEmpty()) {
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
                final Element lastNameEle = authorDoc.createElement("surname");
                lastNameEle.setTextContent(authorInfo.getLastName());

                // Docbook 5 needs <firstname>/<surname> wrapped in <personname>
                if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
                    final Element personnameEle = authorDoc.createElement("personname");
                    authorEle.appendChild(personnameEle);

                    personnameEle.appendChild(firstNameEle);
                    personnameEle.appendChild(lastNameEle);
                } else {
                    authorEle.appendChild(firstNameEle);
                    authorEle.appendChild(lastNameEle);
                }

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
        }

        // If no authors were inserted then use a default value
        if (!insertedAuthor) {
            // Use the author "PressGang CCMS Build System"
            final Element authorEle = authorDoc.createElement("author");
            authorDoc.getDocumentElement().appendChild(authorEle);

            // Use the author "PressGang Alpha Build System"
            final Element firstNameEle = authorDoc.createElement("firstname");
            firstNameEle.setTextContent("");
            final Element lastNameEle = authorDoc.createElement("surname");
            lastNameEle.setTextContent("PressGang CCMS Build System");

            // Docbook 5 needs <firstname>/<surname> wrapped in <personname>
            if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
                final Element personnameEle = authorDoc.createElement("personname");
                authorEle.appendChild(personnameEle);

                personnameEle.appendChild(firstNameEle);
                personnameEle.appendChild(lastNameEle);
            } else {
                authorEle.appendChild(firstNameEle);
                authorEle.appendChild(lastNameEle);
            }

            // Add the affiliation
            final Element affiliationEle = authorDoc.createElement("affiliation");
            final Element orgEle = authorDoc.createElement("orgname");
            orgEle.setTextContent("Red&nbsp;Hat");
            affiliationEle.appendChild(orgEle);
            final Element orgDivisionEle = authorDoc.createElement("orgdiv");
            orgDivisionEle.setTextContent("Engineering Content Services");
            affiliationEle.appendChild(orgDivisionEle);
            authorEle.appendChild(affiliationEle);
        }

        // Add the Author_Group.xml to the book
        final String authorGroupXml = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(),
                authorDoc, "authorgroup", buildData.getEntityFileName(), getXMLFormatProperties());
        addToZip(buildData.getBookLocaleFolder() + AUTHOR_GROUP_FILE_NAME, authorGroupXml, buildData);
    }

    /**
     * Builds the revision history for the book. The revision history used will be determined in the following order:<br/>
     * <br />
     * 1. Revision History Override<br/>
     * 2. Content Spec Revision History Topic<br/>
     * 3. Revision History Template
     *
     * @param buildData Information and data structures for the build.
     * @param overrides The overrides to use for the build.
     * @throws BuildProcessingException
     */
    protected void buildRevisionHistory(final BuildData buildData, final Map<String, String> overrides) throws BuildProcessingException {
        final ContentSpec contentSpec = buildData.getContentSpec();

        // Replace the basic injection data inside the revision history
        final String revisionHistoryXml = stringConstantProvider.getStringConstant(
                buildData.getServerEntities().getRevisionHistoryStringConstantId()).getValue();

        // DocBook 5 shouldn't have the <revhistory> wrapped in a <simpara>
        final String fixedRevisionHistoryXml;
        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
            fixedRevisionHistoryXml = revisionHistoryXml.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX,
                    buildData.getEscapedBookTitle()).replace("<simpara>", "").replace("</simpara>", "");
        } else {
            fixedRevisionHistoryXml = revisionHistoryXml.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, buildData.getEscapedBookTitle());
        }

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

                    if (buildData.getBuildOptions().getRevisionMessages() != null && !buildData.getBuildOptions().getRevisionMessages()
                            .isEmpty()) {
                        // Add a revision message to the Revision_History.xml
                        final String revHistoryOverride = buffer.toString();
                        final String docType = XMLUtilities.findDocumentType(revHistoryOverride);
                        if (docType != null) {
                            buildRevisionHistoryFromTemplate(buildData, revHistoryOverride.replace(docType, ""));
                        } else {
                            buildRevisionHistoryFromTemplate(buildData, revHistoryOverride);
                        }
                    } else {
                        addToZip(buildData.getBookLocaleFolder() + "Revision_History.xml", buffer.toString(), buildData);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage());
                    buildRevisionHistoryFromTemplate(buildData, fixedRevisionHistoryXml);
                }
            } else {
                // Add the revision history directly to the book
                buildData.getOutputFiles().put(buildData.getBookLocaleFolder() + REVISION_HISTORY_FILE_NAME, revHistory);
            }
        } else if (contentSpec.getRevisionHistory() != null) {
            final TopicErrorData errorData = buildData.getErrorDatabase().getErrorData(contentSpec.getRevisionHistory().getTopic());
            final String revisionHistoryXML = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(),
                    contentSpec.getRevisionHistory().getXMLDocument(), "appendix", buildData.getEntityFileName(), getXMLFormatProperties());
            if (buildData.getBuildOptions().getRevisionMessages() != null && !buildData.getBuildOptions().getRevisionMessages().isEmpty()) {
                buildRevisionHistoryFromTemplate(buildData, revisionHistoryXML);
            } else if (errorData != null && errorData.hasFatalErrors()) {
                buildRevisionHistoryFromTemplate(buildData, revisionHistoryXML);
            } else {
                // Add the revision history directly to the book
                addToZip(buildData.getBookLocaleFolder() + REVISION_HISTORY_FILE_NAME, revisionHistoryXML, buildData);
            }
        } else {
            buildRevisionHistoryFromTemplate(buildData, fixedRevisionHistoryXml);
        }
    }

    /**
     * Builds the revision history using the requester of the build.
     *
     * @param buildData          Information and data structures for the build.
     * @param revisionHistoryXml The Revision_History.xml file/template to add revision information to.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected void buildRevisionHistoryFromTemplate(final BuildData buildData,
            final String revisionHistoryXml) throws BuildProcessingException {
        log.info("\tBuilding " + REVISION_HISTORY_FILE_NAME);

        Document revHistoryDoc;
        try {
            revHistoryDoc = XMLUtilities.convertStringToDocument(revisionHistoryXml);
        } catch (Exception ex) {
            // Exit since we shouldn't fail at converting the basic revision history
            log.debug("", ex);
            throw new BuildProcessingException(
                    String.format(getMessages().getString("FAILED_CONVERTING_TEMPLATE"), REVISION_HISTORY_FILE_NAME));
        }

        if (revHistoryDoc == null) {
            throw new BuildProcessingException(
                    String.format(getMessages().getString("FAILED_CONVERTING_TEMPLATE"), REVISION_HISTORY_FILE_NAME));
        }

        final String reportHistoryTitleTranslation = buildData.getConstants().getString("REVISION_HISTORY");
        if (reportHistoryTitleTranslation != null) {
            DocBookUtilities.setRootElementTitle(reportHistoryTitleTranslation, revHistoryDoc);
        }

        // Find the revhistory node
        final Element revHistory;
        final NodeList revHistories = revHistoryDoc.getElementsByTagName("revhistory");
        if (revHistories.getLength() > 0) {
            revHistory = (Element) revHistories.item(0);
        } else {
            revHistory = revHistoryDoc.createElement("revhistory");
            // <revhistory> should be a direct child of <appendix> in docbook 5
            if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
                revHistoryDoc.getDocumentElement().appendChild(revHistory);
            } else {
                final Element simpara = revHistoryDoc.createElement("simpara");
                simpara.appendChild(revHistory);
                revHistoryDoc.getDocumentElement().appendChild(simpara);
            }
        }

        final TagWrapper author = buildData.getRequester() == null ? null : tagProvider.getTagByName(buildData.getRequester());

        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        // An assigned writer tag exists for the User so check if there is an AuthorInformation tuple for that writer
        if (author != null) {
            AuthorInformation authorInfo = EntityUtilities.getAuthorInformation(providerFactory, buildData.getServerEntities(),
                    author.getId(), author.getRevision());
            if (authorInfo != null) {
                final Element revision = generateRevision(buildData, revHistoryDoc, authorInfo);

                addRevisionToRevHistory(revHistory, revision);
            } else {
                // No AuthorInformation so Use the default value
                authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME, BuilderConstants.DEFAULT_AUTHOR_LASTNAME,
                        BuilderConstants.DEFAULT_EMAIL);
                final Element revision = generateRevision(buildData, revHistoryDoc, authorInfo);

                addRevisionToRevHistory(revHistory, revision);
            }
        }
        // No assigned writer exists for the uploader so use default values
        else {
            final AuthorInformation authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME,
                    BuilderConstants.DEFAULT_AUTHOR_LASTNAME, BuilderConstants.DEFAULT_EMAIL);
            final Element revision = generateRevision(buildData, revHistoryDoc, authorInfo);

            addRevisionToRevHistory(revHistory, revision);
        }

        // Add the revision history to the book
        final String fixedRevisionHistoryXml = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(),
                revHistoryDoc, "appendix", buildData.getEntityFileName(), getXMLFormatProperties());
        addToZip(buildData.getBookLocaleFolder() + REVISION_HISTORY_FILE_NAME, fixedRevisionHistoryXml, buildData);
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
     * @return Returns an XML element that represents a {@code <revision>} element initialised with the authors information.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    protected Element generateRevision(final BuildData buildData, final Document xmlDoc,
            final AuthorInformation authorInfo) throws BuildProcessingException {
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
            revnumber = DocBookBuildUtilities.generateRevisionNumber(buildData.getContentSpec());
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

        final Element lastName = xmlDoc.createElement("surname");
        lastName.setTextContent(authorInfo.getLastName());

        // Docbook 5 needs <firstname>/<surname> wrapped in <personname>
        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
            final Element personname = xmlDoc.createElement("personname");
            author.appendChild(personname);

            personname.appendChild(firstName);
            personname.appendChild(lastName);
        } else {
            author.appendChild(firstName);
            author.appendChild(lastName);
        }

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
                                contentSpec.getId()).getRevision()) + (authorInfo.getAuthorId() > 0 ? (" by " + buildData.getRequester())
                        : ""));
            } else {
                listMemberEle.setTextContent(String.format(BuilderConstants.BUILT_MSG, contentSpec.getId(),
                        contentSpec.getRevision()) + (authorInfo.getAuthorId() > 0 ? (" by " + buildData.getRequester()) : ""));
            }
        } else {
            listMemberEle.setTextContent(
                    BuilderConstants.BUILT_FILE_MSG + (authorInfo.getAuthorId() > 0 ? (" by " + buildData.getRequester()) : ""));
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

                topicErrorItems.add(DocBookUtilities.buildListItem("INFO: " + StringEscapeUtils.escapeXml(topic.getTitle())));
                if (tags != null && !tags.isEmpty()) {
                    topicErrorItems.add(DocBookUtilities.buildListItem("INFO: " + StringEscapeUtils.escapeXml(tags)));
                }

                if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
                    topicErrorItems.add(DocBookUtilities.buildListItem("INFO: <link xlink:href=\"" + url + "\">Topic URL</link>"));
                } else {
                    topicErrorItems.add(DocBookUtilities.buildListItem("INFO: <ulink url=\"" + url + "\">Topic URL</ulink>"));
                }

                for (final String error : topicErrorData.getItemsOfType(ErrorLevel.ERROR)) {
                    topicErrorItems.add(DocBookUtilities.buildListItem("ERROR: " + error));
                }

                for (final String warning : topicErrorData.getItemsOfType(ErrorLevel.WARNING)) {
                    topicErrorItems.add(DocBookUtilities.buildListItem("WARNING: " + warning));
                }

                /*
                 * this should never be false, because a topic will only be listed in the errors collection once a error or
                 * warning has been added. The count of 2 comes from the standard list items we added above for the title and
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

                    errorItemizedLists += DocBookUtilities.wrapListItems(buildData.getDocBookVersion(), topicErrorItems, title, id);
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
            return DocBookBuildUtilities.addDocBookPreamble(buildData.getDocBookVersion(),
                    DocBookUtilities.buildSection(errorItemizedLists, "Compiler Output"), "section", buildData.getEntityFileName());
        } else {
            return DocBookBuildUtilities.addDocBookPreamble(buildData.getDocBookVersion(),
                    DocBookUtilities.buildChapter(errorItemizedLists, "Compiler Output"), "chapter", buildData.getEntityFileName());
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
                        DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_NO_CONTENT_TOPIC_DEFINITION)));

        // Invalid XML entity or element
        glossary.append(DocBookUtilities.wrapInGlossEntry(
                DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_INVALID_XML_CONTENT + "\""),
                DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_INVALID_XML_CONTENT_DEFINITION)));

        // No Content Error
        glossary.append(
                DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_BAD_XML_STRUCTURE + "\""),
                        DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_BAD_XML_STRUCTURE_DEFINITION)));

        // Invalid Docbook XML Error
        glossary.append(
                DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_INVALID_TOPIC_XML + "\""),
                        DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_INVALID_TOPIC_XML_DEFINITION)));

        // Invalid Injections Error
        glossary.append(
                DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_INVALID_INJECTIONS + "\""),
                        DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_INVALID_INJECTIONS_DEFINITION)));

        // Possible Invalid Injections Warning
        glossary.append(DocBookUtilities.wrapInGlossEntry(
                DocBookUtilities.wrapInGlossTerm("\"... " + BuilderConstants.WARNING_POSSIBLE_INVALID_INJECTIONS + "\""),
                DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_POSSIBLE_INVALID_INJECTIONS_DEFINITION)));

        // Add the glossary terms and definitions
        if (buildData.isTranslationBuild()) {
            // Incomplete translation warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_INCOMPLETE_TRANSLATION + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_INCOMPLETE_TRANSLATED_TOPIC_DEFINITION)));

            // Fuzzy translation warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_FUZZY_TRANSLATION + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_FUZZY_TRANSLATED_TOPIC_DEFINITION)));

            // Untranslated Content warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_UNTRANSLATED_TOPIC + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_UNTRANSLATED_TOPIC_DEFINITION)));

            // Non Pushed Translation Content warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_NONPUSHED_TOPIC + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_NONPUSHED_TOPIC_DEFINITION)));

            // Old Translation Content warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_OLD_TRANSLATED_TOPIC + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_OLD_TRANSLATED_TOPIC_DEFINITION)));

            // Old Untranslated Content warning
            glossary.append(DocBookUtilities.wrapInGlossEntry(
                    DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_OLD_UNTRANSLATED_TOPIC + "\""),
                    DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_OLD_UNTRANSLATED_TOPIC_DEFINITION)));
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
            return DocBookBuildUtilities.addDocBookPreamble(buildData.getDocBookVersion(),
                    DocBookUtilities.buildSection(reportChapter, "Status Report"), "section", buildData.getEntityFileName());
        } else {
            return DocBookBuildUtilities.addDocBookPreamble(buildData.getDocBookVersion(),
                    DocBookUtilities.buildChapter(reportChapter, "Status Report"), "chapter", buildData.getEntityFileName());
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
            final ITopicNode topicNode = buildData.getBuildDatabase().getTopicNodesForTopicID(topicId).get(0);
            final BaseTopicWrapper<?> topic = topicNode.getTopic();

            if (log.isDebugEnabled()) log.debug("\tProcessing SpecTopic " + topicNode.getId() + (topicNode.getRevision() != null ? (", " +
                    "Revision " + topicNode.getRevision()) : ""));

            /*
             * Images have to be in the image folder in Publican. Here we loop through all the imagedata elements and fix up any
             * reference to an image that is not in the images folder.
             */
            final List<Node> images = XMLUtilities.getChildNodes(topicNode.getXMLDocument(), "imagedata", "inlinegraphic");

            for (final Node imageNode : images) {
                final NamedNodeMap attributes = imageNode.getAttributes();
                if (attributes != null) {
                    final Node fileRefAttribute = attributes.getNamedItem("fileref");

                    if (fileRefAttribute != null && (fileRefAttribute.getNodeValue() == null
                            || fileRefAttribute.getNodeValue().isEmpty())) {
                        fileRefAttribute.setNodeValue("images/" + BuilderConstants.FAILPENGUIN_PNG_NAME + ".jpg");
                        buildData.getImageLocations().add(new TopicImageData(topic, fileRefAttribute.getNodeValue()));
                    } else if (fileRefAttribute != null && fileRefAttribute.getNodeValue() != null) {
                        final String fileRefValue = fileRefAttribute.getNodeValue();
                        if (BuilderConstants.IMAGE_FILE_REF_PATTERN.matcher(fileRefValue).matches()) {
                            if (fileRefValue.startsWith("./images/")) {
                                fileRefAttribute.setNodeValue(fileRefValue.substring(2));
                            } else if (!fileRefValue.startsWith("images/")) {
                                fileRefAttribute.setNodeValue("images/" + fileRefValue);
                            }

                            buildData.getImageLocations().add(new TopicImageData(topic, fileRefAttribute.getNodeValue()));
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates the XML after the first set of injections have been processed.
     *
     * @param buildData Information and data structures for the build.
     * @param topicNode The topic that is being validated.
     * @param topicDoc  A Document object that holds the Topic's XML
     * @return The validate document or a template if it failed validation.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    @SuppressWarnings("unchecked")
    private boolean validateTopicXML(final BuildData buildData, final ITopicNode topicNode,
            final Document topicDoc) throws BuildProcessingException {
        final XMLValidator validator = new XMLValidator();
        final ContentSpec contentSpec = buildData.getContentSpec();
        final BaseTopicWrapper<?> topic = topicNode.getTopic();

        final StringBuilder entity = new StringBuilder(CSConstants.DUMMY_CS_NAME_ENT_FILE);
        // Add any custom entities
        if (!isNullOrEmpty(contentSpec.getEntities())) {
            entity.append(contentSpec.getEntities());
        }

        // Wrap the document so it can be validated.
        final String xml = XMLUtilities.convertDocumentToString(topicDoc, ENCODING);
        final Pair<String, String> wrappedTopic = DocBookUtilities.wrapForValidation(buildData.getDocBookVersion(), xml);
        final String fixedTopicXml = wrappedTopic.getSecond();
        final String rootElementName = wrappedTopic.getFirst();

        // Get the schema/dtd as well as any additional content required for validation
        final String titleXML;
        final String docbookFileName;
        final XMLValidator.ValidationMethod validationMethod;
        final byte[] docbookSchema;
        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
            docbookFileName = BuilderConstants.DOCBOOK_50_RNG;
            validationMethod = XMLValidator.ValidationMethod.RELAXNG;
            docbookSchema = docbookRng.getValue();
            titleXML = DocBookUtilities.addDocBook50Namespace("<section><title>" + topic.getTitle() + "</title><para /></section>",
                    "section");
            entity.append(docbook45Entities);
        } else {
            docbookFileName = BuilderConstants.ROCBOOK_45_DTD;
            validationMethod = XMLValidator.ValidationMethod.DTD;
            docbookSchema = rocbookDtd.getValue();
            titleXML = "<section><title>" + topic.getTitle() + "</title><para /></section>";
        }
        final String entityData = entity.toString();

        // First check to see if the title is valid XML
        if (!validator.validate(validationMethod, titleXML, docbookFileName, docbookSchema, entityData, "section")) {
            // The title is invalid so replace it with something that is valid
            topic.setTitle("Invalid Topic");
            DocBookUtilities.setSectionTitle(buildData.getDocBookVersion(), topic.getTitle(), topicDoc);
        }

        // Validate the topic against its DTD/Schema
        if (!validator.validate(validationMethod, fixedTopicXml, docbookFileName, docbookSchema, entityData, rootElementName)) {
            // Store the error message
            final String errorMsg = validator.getErrorText();
            final String cleanedErrorMsg = errorMsg.replace("|", " | ").replace(",", ", ").replaceFirst("\\.$", "");

            final String xmlStringInCDATA = DocBookBuildUtilities.convertDocumentToCDATAFormattedString(topicDoc, getXMLFormatProperties());
            buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                    BuilderConstants.ERROR_INVALID_TOPIC_XML + " The error is <emphasis>" + StringEscapeUtils.escapeXml(
                            cleanedErrorMsg) + "</emphasis>. The processed XML is <programlisting>" + xmlStringInCDATA +
                            "</programlisting>");
            DocBookBuildUtilities.setTopicNodeXMLForError(buildData, topicNode, getErrorInvalidValidationTopicTemplate().getValue());

            return false;
        }

        // Check the content of the XML for things not picked up by DTD validation
        final List<String> xmlErrors = DocBookBuildUtilities.checkTopicForInvalidContent(topic, topicDoc, buildData);
        if (xmlErrors.size() > 0) {
            final String xmlStringInCDATA = DocBookBuildUtilities.convertDocumentToCDATAFormattedString(topicDoc, getXMLFormatProperties());

            // Add the error and processed XML to the error message
            final String errorMessage = CollectionUtilities.toSeperatedString(xmlErrors,
                    "</para><para>" + BuilderConstants.ERROR_INVALID_TOPIC_XML + " ");
            buildData.getErrorDatabase().addError(topic, ErrorType.INVALID_CONTENT,
                    BuilderConstants.ERROR_INVALID_TOPIC_XML + " " + errorMessage + "</para><para>The processed XML is <programlisting>" +
                            xmlStringInCDATA + "</programlisting>");

            DocBookBuildUtilities.setTopicNodeXMLForError(buildData, topicNode, getErrorInvalidValidationTopicTemplate().getValue());

            return false;
        }

        return true;
    }

    /**
     * Process a topic and add the section info information. This information consists of the keywordset information. The
     * keywords are populated using the tags assigned to the topic.
     *
     * @param buildData Information and data structures for the build.
     * @param topic     The Topic to create the sectioninfo for.
     * @param doc       The XML Document DOM object for the topics XML.
     */
    protected void processTopicSectionInfo(final BuildData buildData, final BaseTopicWrapper<?> topic, final Document doc) {
        if (doc == null || topic == null) return;

        final String infoName;
        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
            infoName = "info";
        } else {
            infoName = DocBookUtilities.TOPIC_ROOT_SECTIONINFO_NODE_NAME;
        }

        final CollectionWrapper<TagWrapper> tags = topic.getTags();
        final List<Integer> seoCategoryIds = buildData.getServerSettings().getSEOCategoryIds();

        if (seoCategoryIds != null && !seoCategoryIds.isEmpty() && tags != null && tags.getItems() != null && tags.getItems().size() > 0) {
            // Find the sectioninfo node in the document, or create one if it doesn't exist
            final Element sectionInfo;
            final List<Node> sectionInfoNodes = XMLUtilities.getDirectChildNodes(doc.getDocumentElement(), infoName);
            if (sectionInfoNodes.size() == 1) {
                sectionInfo = (Element) sectionInfoNodes.get(0);
            } else {
                sectionInfo = doc.createElement(infoName);
            }

            // Build up the keywordset
            final Element keywordSet = doc.createElement("keywordset");

            final List<TagWrapper> tagItems = tags.getItems();
            for (final TagWrapper tag : tagItems) {
                if (tag.getName() == null || tag.getName().isEmpty()) continue;

                if (tag.containedInCategories(seoCategoryIds)) {
                    final Element keyword = doc.createElement("keyword");
                    keyword.setTextContent(tag.getName());

                    keywordSet.appendChild(keyword);
                }
            }

            // Only update the section info if we've added data
            if (keywordSet.hasChildNodes()) {
                sectionInfo.appendChild(keywordSet);

                DocBookUtilities.setInfo(buildData.getDocBookVersion(), sectionInfo, doc.getDocumentElement());
            }
        }
    }

    /**
     * This method does a pass over all the topics returned by the query and attempts to create unique Fixed URL if one does not
     * already exist.
     *
     * @param buildData          Information and data structures for the build.
     * @param topics             The list of topics to set the Fixed URL's for.
     * @param processedFileNames A modifiable Set of filenames that have already been processed.
     * @return True if the fixed url property tags were able to be created for all topics, and false otherwise.
     */
    protected boolean setFixedURLsPass(final BuildData buildData, final List<TopicWrapper> topics, final Set<String> processedFileNames) {
        log.info("Doing Fixed URL Pass");

        boolean success = true;

        try {
            final CollectionWrapper<TopicWrapper> allUpdatedTopics = topicProvider.newTopicCollection();
            CollectionWrapper<TopicWrapper> updateTopics = topicProvider.newTopicCollection();

            for (final TopicWrapper topic : topics) {
                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return false;
                }

                if (topic.hasTag(buildData.getServerEntities().getInfoTagId())) {
                    // Completely ignore info topics, as we cannot relate to them
                    continue;
                } else if (DocBookBuildUtilities.useStaticFixedURLForTopic(buildData, topic)) {
                    // Ignore certain topics as those are unique per book and should have a static name
                    final String value = DocBookBuildUtilities.getStaticFixedURLForTopic(buildData, topic);
                    setFixedURLPropertyTag(buildData, topic, value);
                } else {
                    try {
                        // Create the PropertyTagCollection to be used to update any data
                        final UpdateableCollectionWrapper<PropertyTagInTopicWrapper> updatePropertyTags = propertyTagProvider
                                .newPropertyTagInTopicCollection(
                                topic);

                        // Get a list of all property tag items that exist for the current topic
                        final List<PropertyTagInTopicWrapper> existingUniqueURLs = topic.getProperties(
                                buildData.getServerEntities().getFixedUrlPropertyTagId());

                        // Remove any Duplicate Fixed URL's
                        PropertyTagInTopicWrapper existingUniqueURL = null;
                        for (int i = 0; i < existingUniqueURLs.size(); i++) {
                            final PropertyTagInTopicWrapper propertyTag = existingUniqueURLs.get(i);
                            if (i == 0) {
                                existingUniqueURL = propertyTag;
                            } else {
                                updatePropertyTags.addRemoveItem(propertyTag);
                                topic.getProperties().getItems().remove(propertyTag);
                            }
                        }

                        if (existingUniqueURL == null || !existingUniqueURL.isValid()) {
                            // generate the base url
                            String baseUrlName = DocBookBuildUtilities.createURLTitle(topic.getTitle());

                            // deal with topics that have no valid characters
                            if (isNullOrEmpty(baseUrlName)) {
                                baseUrlName = "TopicID" + topic.getTopicId();
                            }

                            // generate a unique fixed url
                            String postFix = "";

                            for (int uniqueCount = 1; uniqueCount <= BuilderConstants.MAXIMUM_SET_PROP_TAG_NAME_RETRY; ++uniqueCount) {
                                // Check to make sure we aren't already using the name locally
                                if (processedFileNames.contains(baseUrlName + postFix)) {
                                    postFix = uniqueCount + "";
                                    continue;
                                }

                                // Check that the fixed url is unique on the server
                                final String query = "query;propertyTag" + buildData.getServerEntities().getFixedUrlPropertyTagId() + "=" +
                                        URLEncoder.encode(baseUrlName + postFix, ENCODING);
                                final CollectionWrapper<TopicWrapper> queryTopics = topicProvider.getTopicsWithQuery(query);

                                if (queryTopics.size() != 0) {
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
                                        if (existing.getId().equals(buildData.getServerEntities().getFixedUrlPropertyTagId())) {
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
                                    propertyTag.setId(buildData.getServerEntities().getFixedUrlPropertyTagId());
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
                            allUpdatedTopics.addItem(updateTopic);
                        }
                    } catch (ProviderException e) {
                        success = false;
                    }
                }

                // Do batch updates to avoid the timeout issue. See BZ#1065586
                if (updateTopics.size() >= BuilderConstants.FIXED_URL_BATCH_SIZE) {
                    log.debug("Doing batch update of fixed urls");
                    topicProvider.updateTopics(updateTopics);
                    updateTopics = topicProvider.newTopicCollection();
                }
            }

            if (updateTopics.getItems() != null && updateTopics.getItems().size() != 0) {
                topicProvider.updateTopics(updateTopics);
            }

            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return false;
            }

            updateFixedURLsForTopics(buildData, allUpdatedTopics, topics);
        } catch (final Exception ex) {
            log.debug(ex);
            success = false;
        }

        if (!success) {
            log.error("\tFailed to update the Fixed URLs for the topics");
        }

        // did we blow the try count?
        return success;
    }

    /**
     * Ensure that the FixedURL Properties for revision topics are still valid inside the book. Revision topics can either be
     * Normal Topics or Translated Topics (which are actually a saved normal revision).
     *
     * @param buildData          Information and data structures for the build.
     * @param topics             The list of revision topics.
     * @param processedFileNames A List of file names that has already been processed. (ie in the setFixedURLsPass() method)
     */
    protected <T extends BaseTopicWrapper<T>> void setFixedURLsForRevisionsPass(final BuildData buildData, final List<T> topics,
            final Set<String> processedFileNames) {
        log.info("Doing Revisions Fixed URL Pass");

        /*
         * Now loop over the revision topics, and make sure their fixed url property tags are unique. They only have to be
         * unique within the book.
         */
        for (final BaseTopicWrapper<?> topic : topics) {
            if (topic.hasTag(buildData.getServerEntities().getInfoTagId())) {
                // Completely ignore info topics, as we cannot relate to them
                continue;
            } else if (DocBookBuildUtilities.useStaticFixedURLForTopic(buildData, topic)) {
                // Ignore certain topics as those are unique per book and should have a static name
                final String value = DocBookBuildUtilities.getStaticFixedURLForTopic(buildData, topic);
                setFixedURLPropertyTag(buildData, topic, value);
            } else {
                // Get the existing property tag and value
                final PropertyTagInTopicWrapper existingUniqueURL = topic.getProperty(
                        buildData.getServerEntities().getFixedUrlPropertyTagId());
                String value = existingUniqueURL == null ? null : existingUniqueURL.getValue();

                // Check if a new value needs to be calculated
                if (value == null || value.isEmpty() || processedFileNames.contains(value)) {
                    String baseUrlName;
                    if (topic instanceof TranslatedTopicWrapper) {
                        baseUrlName = DocBookBuildUtilities.createURLTitle(((TranslatedTopicWrapper) topic).getTopic().getTitle());
                    } else {
                        baseUrlName = DocBookBuildUtilities.createURLTitle(topic.getTitle());
                    }

                    // If the title has no characters that can be used in a url, then just use a generic one
                    if (isNullOrEmpty(baseUrlName) || baseUrlName.matches("^\\d+$")) {
                        baseUrlName = "TopicID" + topic.getTopicId();
                    }

                    String postFix = "";
                    for (int uniqueCount = 1; ; ++uniqueCount) {
                        if (!processedFileNames.contains(baseUrlName + postFix)) {
                            value = baseUrlName + postFix;
                            break;
                        } else {
                            postFix = uniqueCount + "";
                        }
                    }
                }

                // Set the property tag value and add it to the processed file names
                setFixedURLPropertyTag(buildData, topic, value);
                processedFileNames.add(value);
            }
        }
    }

    /**
     * Sets the Fixed URL Property Tag value for a Topic. If a Fixed URL Property Tag doesn't exist then one is created and added to the
     * topic.
     *
     * @param buildData Information and data structures for the build.
     * @param topic     The topic to set the Fixed URL Property Tag for.
     * @param fixedURL  The Fixed URL value to be set.
     */
    protected void setFixedURLPropertyTag(final BuildData buildData, final BaseTopicWrapper<?> topic, final String fixedURL) {
        // Get the existing property tag
        PropertyTagInTopicWrapper existingUniqueURL = topic.getProperty(buildData.getServerEntities().getFixedUrlPropertyTagId());

        // Create a property tag if none exists
        if (existingUniqueURL == null) {
            existingUniqueURL = propertyTagProvider.newPropertyTagInTopic(topic);
            existingUniqueURL.setId(buildData.getServerEntities().getFixedUrlPropertyTagId());
            if (topic.getProperties() == null) {
                topic.setProperties(propertyTagProvider.newPropertyTagInTopicCollection(topic));
            }
            topic.getProperties().addItem(existingUniqueURL);
        }

        // Update the fixed url
        existingUniqueURL.setValue(fixedURL);
    }

    /**
     * Update the Fixed URL Property Tags from a collection of updated topics.
     *
     * @param buildData      Information and data structures for the build.
     * @param updatedTopics  The collection of updated topics.
     * @param originalTopics The collection of original topics.
     */
    protected void updateFixedURLsForTopics(final BuildData buildData, final CollectionWrapper<TopicWrapper> updatedTopics,
            final List<TopicWrapper> originalTopics) {
        /* copy the topics fixed url properties to our local collection */
        if (updatedTopics.getItems() != null && updatedTopics.getItems().size() != 0) {
            final List<TopicWrapper> updateItems = updatedTopics.getItems();
            for (final TopicWrapper topicWithFixedUrl : updateItems) {
                for (final TopicWrapper topic : originalTopics) {
                    final PropertyTagInTopicWrapper fixedUrlProp = topicWithFixedUrl.getProperty(
                            buildData.getServerEntities().getFixedUrlPropertyTagId());

                    if (topic != null && topicWithFixedUrl.getId().equals(topic.getId())) {
                        CollectionWrapper<PropertyTagInTopicWrapper> properties = topic.getProperties();
                        if (properties == null) {
                            properties = propertyTagProvider.newPropertyTagInTopicCollection(topic);
                        } else if (properties.getItems() != null) {
                            // remove any current url's
                            final List<PropertyTagInTopicWrapper> propertyTags = new ArrayList<PropertyTagInTopicWrapper>(
                                    properties.getItems());
                            for (final PropertyTagInTopicWrapper prop : propertyTags) {
                                if (prop.getId().equals(buildData.getServerEntities().getFixedUrlPropertyTagId())) {
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
                                        if (prop.getId().equals(buildData.getServerEntities().getFixedUrlPropertyTagId())) {
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

    /**
     * Adds a file to the files ZIP.
     *
     * @param path      The path to add the file to.
     * @param file      The file to add to the ZIP.
     * @param buildData
     */
    protected void addToZip(final String path, final String file, BuildData buildData) throws BuildProcessingException {
        try {
            buildData.getOutputFiles().put(path, file.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            /* UTF-8 is a valid format so this should exception should never get thrown */
            throw new BuildProcessingException(e);
        }
    }

    /**
     * Adds a file to the files ZIP.
     *
     * @param path      The path to add the file to.
     * @param file      The file to add to the ZIP.
     * @param buildData
     */
    protected void addToZip(final String path, final byte[] data, final BuildData buildData) {
        buildData.getOutputFiles().put(path, data);
    }

    /**
     * Adds the additional files defined in a content spec to the book.
     *
     * @param buildData Information and data structures for the build.
     */
    protected void addAdditionalFilesToBook(final BuildData buildData) throws BuildProcessingException {
        final FileProvider fileProvider = providerFactory.getProvider(FileProvider.class);
        final ContentSpec contentSpec = buildData.getContentSpec();

        if (contentSpec.getFiles() != null) {
            log.info("\tDownloading Additional Files");

            for (final org.jboss.pressgang.ccms.contentspec.File file : contentSpec.getFiles()) {
                try {
                    final FileWrapper fileEntity = fileProvider.getFile(file.getId(), file.getRevision());

                    // Find the file that matches this locale. If the locale isn't found then use the default locale
                    LanguageFileWrapper languageFileFile = null;
                    if (fileEntity.getLanguageFiles() != null && fileEntity.getLanguageFiles().getItems() != null) {
                        final List<LanguageFileWrapper> languageFiles = fileEntity.getLanguageFiles().getItems();
                        for (final LanguageFileWrapper languageFile : languageFiles) {
                            if (languageFile.getLocale().equals(buildData.getBuildLocale())) {
                                languageFileFile = languageFile;
                            } else if (languageFile.getLocale().equals(
                                    buildData.getServerSettings().getDefaultLocale()) && languageFileFile == null) {
                                languageFileFile = languageFile;
                            }
                        }
                    }

                    if (languageFileFile != null && languageFileFile.getFileData() != null) {
                        // Determine the file path
                        final String filePath;
                        if (!isNullOrEmpty(fileEntity.getFilePath())) {
                            if (fileEntity.getFilePath().endsWith("/") || fileEntity.getFilePath().endsWith("\\")) {
                                filePath = fileEntity.getFilePath();
                            } else {
                                filePath = fileEntity.getFilePath() + "/";
                            }
                        } else {
                            filePath = "";
                        }

                        // Explode the ZIP archive if requested
                        if (fileEntity.isExplodeArchive()) {
                            try {
                                final Map<String, byte[]> files = ZipUtilities.unzipFile(languageFileFile.getFileData(), false);
                                for (final Entry<String, byte[]> entry : files.entrySet()) {
                                    addToZip(buildData.getBookFilesFolder() + filePath + entry.getKey(), entry.getValue(), buildData);
                                }
                            } catch (IOException e) {
                                throw new BuildProcessingException(e);
                            }
                        } else {
                            addToZip(buildData.getBookFilesFolder() + filePath + fileEntity.getFilename(), languageFileFile.getFileData(),
                                    buildData);
                        }
                    } else {
                        throw new BuildProcessingException("File ID " + fileEntity.getId() + " has no language files!");
                    }
                } catch (NotFoundException e) {
                    throw new BuildProcessingException("File ID " + file.getId() + " could not be found!");
                }
            }
        }
    }
}
