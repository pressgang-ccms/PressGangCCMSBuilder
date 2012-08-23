package com.redhat.contentspec.builder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.pressgangccms.contentspec.ContentSpec;
import org.jboss.pressgangccms.contentspec.Level;
import org.jboss.pressgangccms.contentspec.SpecTopic;
import org.jboss.pressgangccms.contentspec.constants.CSConstants;
import org.jboss.pressgangccms.contentspec.entities.AuthorInformation;
import org.jboss.pressgangccms.contentspec.entities.InjectionOptions;
import org.jboss.pressgangccms.contentspec.enums.BookType;
import org.jboss.pressgangccms.contentspec.enums.LevelType;
import org.jboss.pressgangccms.contentspec.interfaces.ShutdownAbleApp;
import org.jboss.pressgangccms.contentspec.rest.RESTManager;
import org.jboss.pressgangccms.contentspec.rest.RESTReader;
import org.jboss.pressgangccms.contentspec.sort.AuthorInformationComparator;
import org.jboss.pressgangccms.docbook.constants.DocbookBuilderConstants;
import org.jboss.pressgangccms.docbook.processing.XMLPreProcessor;
import org.jboss.pressgangccms.docbook.structures.TocTopicDatabase;
import org.jboss.pressgangccms.docbook.structures.TopicErrorData;
import org.jboss.pressgangccms.docbook.structures.TopicErrorDatabase;
import org.jboss.pressgangccms.docbook.structures.TopicErrorDatabase.ErrorLevel;
import org.jboss.pressgangccms.docbook.structures.TopicErrorDatabase.ErrorType;
import org.jboss.pressgangccms.docbook.structures.TopicImageData;
import org.jboss.pressgangccms.rest.v1.collections.RESTPropertyTagCollectionV1;
import org.jboss.pressgangccms.rest.v1.collections.RESTTopicCollectionV1;
import org.jboss.pressgangccms.rest.v1.collections.RESTTranslatedTopicCollectionV1;
import org.jboss.pressgangccms.rest.v1.collections.base.BaseRestCollectionV1;
import org.jboss.pressgangccms.rest.v1.components.ComponentTopicV1;
import org.jboss.pressgangccms.rest.v1.components.ComponentTranslatedTopicV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTBlobConstantV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTImageV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTLanguageImageV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTPropertyTagV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTStringConstantV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTTagV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTTopicV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTTranslatedTopicV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTUserV1;
import org.jboss.pressgangccms.rest.v1.entities.base.RESTBaseTopicV1;
import org.jboss.pressgangccms.rest.v1.exceptions.InternalProcessingException;
import org.jboss.pressgangccms.rest.v1.exceptions.InvalidParameterException;
import org.jboss.pressgangccms.rest.v1.expansion.ExpandDataDetails;
import org.jboss.pressgangccms.rest.v1.expansion.ExpandDataTrunk;
import org.jboss.pressgangccms.utils.common.CollectionUtilities;
import org.jboss.pressgangccms.utils.common.DocBookUtilities;
import org.jboss.pressgangccms.utils.common.ExceptionUtilities;
import org.jboss.pressgangccms.utils.common.FileUtilities;
import org.jboss.pressgangccms.utils.common.StringUtilities;
import org.jboss.pressgangccms.utils.common.XMLUtilities;
import org.jboss.pressgangccms.utils.constants.CommonConstants;
import org.jboss.pressgangccms.utils.structures.Pair;
import org.jboss.pressgangccms.zanata.ZanataDetails;
import org.jboss.resteasy.specimpl.PathSegmentImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.code.regexp.NamedMatcher;
import com.google.code.regexp.NamedPattern;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import com.redhat.contentspec.builder.constants.BuilderConstants;
import com.redhat.contentspec.builder.exception.BuilderCreationException;
import com.redhat.contentspec.builder.utils.ReportUtilities;
import com.redhat.contentspec.builder.utils.SAXXMLValidator;
import com.redhat.contentspec.structures.CSDocbookBuildingOptions;
import com.redhat.contentspec.structures.SpecDatabase;

public class DocbookBuilder<T extends RESTBaseTopicV1<T, U>, U extends BaseRestCollectionV1<T, U>> implements ShutdownAbleApp
{
	private static final Logger log = Logger.getLogger(DocbookBuilder.class);
	private static final String STARTS_WITH_NUMBER_RE = "^(?<Numbers>\\d+)(?<EverythingElse>.*)$";
	private static final String STARTS_WITH_INVALID_SEQUENCE_RE = "^(?<InvalidSeq>[^\\w\\d]+)(?<EverythingElse>.*)$";

	private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
	private final AtomicBoolean shutdown = new AtomicBoolean(false);

	private final List<String> verbatimElements;
	private final List<String> inlineElements;
	private final List<String> contentsInlineElements;

	private final RESTReader reader;
	private final RESTManager restManager;
	private final RESTBlobConstantV1 rocbookdtd;
	private final String defaultLocale;
	private final ZanataDetails zanataDetails;

	/** The StringConstant that holds the error template for a topic with no content. */
	private final RESTStringConstantV1 errorEmptyTopic;
	/** The StringConstant that holds the error template for a topic with invalid injection references. */
	private final RESTStringConstantV1 errorInvalidInjectionTopic;
	/** The StringConstant that holds the error template for a topic that failed validation. */
	private final RESTStringConstantV1 errorInvalidValidationTopic;
	/** The StringConstant that holds the formatting XML element properties file. */
	private final RESTStringConstantV1 xmlElementsProperties;

	/** The Docbook/Formatting Building Options to be used when building. */
	private CSDocbookBuildingOptions docbookBuildingOptions;
	/** The options that specify what injections are allowed when building. */
	private InjectionOptions injectionOptions;
	/** The date of this build. */
	private Date buildDate;

	/** The escaped version of the books title. */
	private String escapedTitle;
	/** The locale the book is to be built in. */
	private String locale;

	/** The root path for the books storage. */
	private String BOOK_FOLDER;
	/** The locale path for the books storage. eg. /{@code<TITLE>}/en-US/ */
	private String BOOK_LOCALE_FOLDER;
	/** The path where topics are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/topics/ */
	private String BOOK_TOPICS_FOLDER;
	/** The path where images are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/images/ */
	private String BOOK_IMAGES_FOLDER;
	/** The path where generic files are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/files/ */
	private String BOOK_FILES_FOLDER;

	/** Jackson object mapper. */
	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Holds the compiler errors that form the Errors.xml file in the compiled
	 * docbook.
	 */
	private TopicErrorDatabase<T, U> errorDatabase;;

	/**
	 * Holds the SpecTopics and their XML that exist within the content specification.
	 */
	private SpecDatabase specDatabase;

	/**
	 * Holds information on file url locations, which will be downloaded and
	 * included in the docbook zip file.
	 */
	private final ArrayList<TopicImageData<T, U>> imageLocations = new ArrayList<TopicImageData<T, U>>();

	/** The Topic class to be used for building. (RESTTranslatedTopicV1 or RESTTopicV1) */
	private Class<T> clazz;

	public DocbookBuilder(final RESTManager restManager, final RESTBlobConstantV1 rocbookDtd, final String defaultLocale) throws InvalidParameterException, InternalProcessingException
	{
		this(restManager, rocbookDtd, defaultLocale, new ZanataDetails());
	}

	public DocbookBuilder(final RESTManager restManager, final RESTBlobConstantV1 rocbookDtd, final String defaultLocale, final ZanataDetails zanataDetails) throws InvalidParameterException, InternalProcessingException
	{
		reader = restManager.getReader();
		this.restManager = restManager;
		this.rocbookdtd = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.ROCBOOK_DTD_BLOB_ID, "");
		this.errorEmptyTopic = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.CSP_EMPTY_TOPIC_ERROR_XML_ID, "");
		this.errorInvalidInjectionTopic = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.CSP_INVALID_INJECTION_TOPIC_ERROR_XML_ID, "");
		this.errorInvalidValidationTopic = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.CSP_INVALID_VALIDATION_TOPIC_ERROR_XML_ID, "");
		this.xmlElementsProperties = restManager.getRESTClient().getJSONStringConstant(CommonConstants.XML_ELEMENTS_STRING_CONSTANT_ID, "");

		this.defaultLocale = defaultLocale;
		this.zanataDetails = zanataDetails;

		/*
		 * Get the XML formatting details. These are used to pretty-print
		 * the XML when it is converted into a String.
		 */
		final Properties prop = new Properties();
		try
		{
			prop.load(new StringReader(xmlElementsProperties.getValue()));
		}
		catch (IOException e)
		{
			log.error("Failed to read the XML Elements Property file");
			System.exit(-1);
		}
		
		final String verbatimElementsString = prop.getProperty(CommonConstants.VERBATIM_XML_ELEMENTS_PROPERTY_KEY);
		final String inlineElementsString = prop.getProperty(CommonConstants.INLINE_XML_ELEMENTS_PROPERTY_KEY);
		final String contentsInlineElementsString = prop.getProperty(CommonConstants.CONTENTS_INLINE_XML_ELEMENTS_PROPERTY_KEY);

		verbatimElements = CollectionUtilities.toArrayList(verbatimElementsString.split("[\\s]*,[\\s]*"));
		inlineElements = CollectionUtilities.toArrayList(inlineElementsString.split("[\\s]*,[\\s]*"));
		contentsInlineElements = CollectionUtilities.toArrayList(contentsInlineElementsString.split("[\\s]*,[\\s]*"));
	}

	@Override
	public void shutdown()
	{
		isShuttingDown.set(true);
	}

	@Override
	public boolean isShutdown()
	{
		return shutdown.get();
	}

	/**
	 * Gets the number of warnings that occurred during the last build.
	 *
	 * @return The number of warnings that occurred.
	 */
	public int getNumWarnings()
	{
		int numWarnings = 0;
		if (errorDatabase != null && errorDatabase.getErrors(locale) != null)
		{
			for (final TopicErrorData<T, U> errorData : errorDatabase.getErrors(locale))
			{
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
	public int getNumErrors()
	{
		int numErrors = 0;
		if (errorDatabase != null && errorDatabase.getErrors(locale) != null)
		{
			for (final TopicErrorData<T, U> errorData : errorDatabase.getErrors(locale))
			{
				numErrors += errorData.getItemsOfType(ErrorLevel.ERROR).size();
			}
		}
		return numErrors;
	}

	/**
	 * Builds a Docbook Formatted Book using a Content Specification to define
	 * the structure and contents of the book.
	 *
	 * @param contentSpec The content specification to build from.
	 * @param requester The user who requested the build.
	 * @param buildingOptions The options to be used when building.
	 * @param searchTagsUrl The search URL that lists the topics (used mainly from
	 * 		skynet builds).
	 * @return Returns a mapping of file names/locations to files. This HashMap can be used
	 * to build a ZIP archive.
	 * @throws Exception Any unexpected errors.
	 */
	@SuppressWarnings("unchecked")
	public HashMap<String, byte[]> buildBook(final ContentSpec contentSpec, final RESTUserV1 requester,
			final CSDocbookBuildingOptions buildingOptions, final String searchTagsUrl)
			throws Exception
	{
		if (contentSpec == null)
		{
			throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
		}

		errorDatabase = new TopicErrorDatabase<T, U>();
		specDatabase = new SpecDatabase();

		if (contentSpec.getLocale() == null || contentSpec.getLocale().equals(defaultLocale))
		{
			clazz = (Class<T>) RESTTopicV1.class;
		}
		else
		{
			clazz = (Class<T>) RESTTranslatedTopicV1.class;
		}

		// Setup the constants
		escapedTitle = DocBookUtilities.escapeTitle(contentSpec.getTitle());
		locale = contentSpec.getLocale() == null ? defaultLocale : contentSpec.getLocale();
		BOOK_FOLDER = escapedTitle + "/";
		BOOK_LOCALE_FOLDER = BOOK_FOLDER + locale + "/";
		BOOK_TOPICS_FOLDER = BOOK_LOCALE_FOLDER + "topics/";
		BOOK_IMAGES_FOLDER = BOOK_LOCALE_FOLDER + "images/";
		BOOK_FILES_FOLDER = BOOK_LOCALE_FOLDER + "files/";
		buildDate = new Date();

		this.docbookBuildingOptions = buildingOptions;

		/*
		 * Apply the build options from the content spec only if the build
		 * options are true. We do this so that if the options are turned off
		 * earlier then we don't re-enable them.
		 */
		if (docbookBuildingOptions.getInsertSurveyLink())
		{
			docbookBuildingOptions.setInsertSurveyLink(contentSpec.isInjectSurveyLinks());
		}
		if (docbookBuildingOptions.getInsertBugzillaLinks())
		{
			docbookBuildingOptions.setInsertBugzillaLinks(contentSpec.isInjectBugLinks());
		}

		// Add the options that were passed to the builder
		injectionOptions = new InjectionOptions();

		// Get the injection mode
		InjectionOptions.UserType injectionType = InjectionOptions.UserType.NONE;
		final Boolean injection = buildingOptions.getInjection();
		if (injection != null && !injection)
		{
			injectionType = InjectionOptions.UserType.OFF;
		}
		else if (injection != null && injection)
		{
			injectionType = InjectionOptions.UserType.ON;
		}

		// Add the strict injection types
		if (buildingOptions.getInjectionTypes() != null)
		{
			for (final String injectType : buildingOptions.getInjectionTypes())
			{
				injectionOptions.addStrictTopicType(injectType.trim());
			}
			if (injection != null && injection)
			{
				injectionType = InjectionOptions.UserType.STRICT;
			}
		}

		// Set the injection mode
		injectionOptions.setClientType(injectionType);

		// Set the injection options for the content spec
		if (contentSpec.getInjectionOptions() != null)
		{
			injectionOptions.setContentSpecType(contentSpec.getInjectionOptions().getContentSpecType());
			injectionOptions.addStrictTopicTypes(contentSpec.getInjectionOptions().getStrictTopicTypes());
		}

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			shutdown.set(true);
			return null;
		}

		final Map<Integer, Set<String>> usedIdAttributes = new HashMap<Integer, Set<String>>();
		final boolean fixedUrlsSuccess = doPopulateDatabasePass(contentSpec, usedIdAttributes);

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			shutdown.set(true);
			return null;
		}

		/*
		 * We need to create a list of all id's in the book to check if links
		 * are valid. So generate the id attribute that are used by topics,
		 * section and chapters. Then add any id's that were found in the topics.
		 */
		final Set<String> bookIdAttributes = specDatabase.getIdAttributes(fixedUrlsSuccess);
		for (final Integer id : usedIdAttributes.keySet())
		{
			bookIdAttributes.addAll(usedIdAttributes.get(id));
		}
		validateTopicLinks(bookIdAttributes, fixedUrlsSuccess);

		// second topic pass to set the ids and process injections
		doSpecTopicPass(contentSpec, searchTagsUrl, usedIdAttributes, fixedUrlsSuccess, BuilderConstants.BUILD_NAME);

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			shutdown.set(true);
			return null;
		}

		/* Process the images in the topics */
		processImageLocations();

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			shutdown.set(true);
			return null;
		}

		return doBuildZipPass(contentSpec, requester, fixedUrlsSuccess);
	}

	/**
	 * Validate all the book links in the each topic to ensure that
	 * they exist somewhere in the book. If they don't then the topic
	 * XML is replaced with a generic error template.
	 *
	 * @param bookIdAttributes A set of all the id's that exist in the book.
	 * @param useFixedUrls Whether or not the fixed urls should be used for
	 * topic ID's.
	 */
	@SuppressWarnings("unchecked")
	private void validateTopicLinks(final Set<String> bookIdAttributes, final boolean useFixedUrls)
	{
		log.info("Doing " + locale + " Topic Link Pass");

		final List<SpecTopic> topics = specDatabase.getAllSpecTopics();
		final Set<Integer> processedTopics = new HashSet<Integer>();
		for (final SpecTopic specTopic : topics)
		{
			final T topic = (T) specTopic.getTopic();
			final Document doc = specTopic.getXmlDocument();

			/*
			 * We only to to process topics at this point and not spec topics.
			 * So check to see if the topic has all ready been processed.
			 */
			if (!processedTopics.contains(topic.getId()))
			{
				processedTopics.add(topic.getId());

				/* Get the XRef links in the topic document */
				final Set<String> linkIds = new HashSet<String>();
				getTopicLinkIds(doc, linkIds);

				for (final String linkId : linkIds)
				{
					/* Check if the xref linkend id exists in the book */
					if (!bookIdAttributes.contains(linkId))
					{
						final String topicXMLErrorTemplate = buildTopicErrorTemplate(topic, errorInvalidValidationTopic.getValue());

						final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(XMLUtilities.convertNodeToString(doc, verbatimElements, inlineElements, contentsInlineElements, true));
						errorDatabase.addError(topic, ErrorType.INVALID_CONTENT, "Topic references non-exist ID's in links. The processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");

						/* Find the Topic ID */
						final Integer topicId;
						if (topic instanceof RESTTranslatedTopicV1)
						{
							topicId = ((RESTTranslatedTopicV1) topic).getTopicId();
						}
						else
						{
							topicId = topic.getId();
						}

						final List<SpecTopic> specTopics = specDatabase.getSpecTopicsForTopicID(topicId);
						for (final SpecTopic spec : specTopics)
						{
							setSpecTopicXMLForError(spec, topicXMLErrorTemplate, useFixedUrls);
						}
					}
				}
			}
		}
	}

	/**
	 * Get any ids that are referenced by a "link" or "xref"
	 * XML attribute within the node. Any ids that are found
	 * are added to the passes linkIds set.
	 *
	 * @param node The DOM XML node to check for links.
	 * @param linkIds The set of current found link ids.
	 */
	private void getTopicLinkIds(final Node node, final Set<String> linkIds)
	{
		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return;
		}
		
		// If the node is null then there isn't anything to find, so just return.
		if (node == null)
		{
			return;
		}

		if (node.getNodeName().equals("xref") || node.getNodeName().equals("link"))
		{
			final NamedNodeMap attributes = node.getAttributes();
			if (attributes != null)
			{
				final Node idAttribute = attributes.getNamedItem("linkend");
				if (idAttribute != null)
				{
					final String idAttibuteValue = idAttribute.getNodeValue();
					linkIds.add(idAttibuteValue);
				}
			}
		}

		final NodeList elements = node.getChildNodes();
		for (int i = 0; i < elements.getLength(); ++i)
		{
			getTopicLinkIds(elements.item(i), linkIds);
		}
	}

	/**
	 * Populates the SpecTopicDatabase with the SpecTopics inside the content specification.
	 * It also adds the equivalent real topics to each SpecTopic.
	 *
	 * @param contentSpec The content spec to populate the database from.
	 * @param usedIdAttributes The set of Used ID Attributes that should be added to.
	 * @return True if the database was populated successfully otherwise false.
	 */
	@SuppressWarnings("unchecked")
	private boolean doPopulateDatabasePass(final ContentSpec contentSpec, final Map<Integer, Set<String>> usedIdAttributes)
	{
		log.info("Doing " + locale + " Populate Database Pass");

		/* Calculate the ids of all the topics to get */
		final Set<Pair<Integer, Integer>> topicToRevisions = getTopicIdsFromLevel(contentSpec.getBaseLevel());

		/*
		 * Determine which topics we need to fetch the latest topics
		 * for and which topics we need to fetch revisions for.
		 */
		final Set<Integer> topicIds = new HashSet<Integer>();
		final Set<Pair<Integer, Integer>> topicRevisions = new HashSet<Pair<Integer, Integer>>();
		for (final Pair<Integer, Integer> topicToRevision : topicToRevisions)
		{
			if (topicToRevision.getSecond() != null)
			{
				topicRevisions.add(topicToRevision);
			}
			else
			{
				topicIds.add(topicToRevision.getFirst());
			}
		}

		final BaseRestCollectionV1<T, U> topics;
		final boolean fixedUrlsSuccess;
		if (contentSpec.getLocale() == null || contentSpec.getLocale().equals(defaultLocale))
		{
			final RESTTopicCollectionV1 normalTopics;

			/* Ensure that the collection doesn't equal null */
			final RESTTopicCollectionV1 topicCollection = reader.getTopicsByIds(CollectionUtilities.toArrayList(topicIds), false);
			if (topicCollection == null)
			{
				normalTopics = new RESTTopicCollectionV1();
			}
			else
			{
				normalTopics = topicCollection;
			}

			/*
			 * Fetch each topic that is a revision separately since this
			 * functionality isn't offered by the REST API.
			 */
			for (final Pair<Integer, Integer> topicToRevision : topicRevisions)
			{
				final RESTTopicV1 topicRevision = reader.getTopicById(topicToRevision.getFirst(), topicToRevision.getSecond());
				if (topicRevision != null)
				{
					normalTopics.addItem(topicRevision);
				}
			}

			/*
			 * assign fixed urls property tags to the topics. If
			 * fixedUrlsSuccess is true, the id of the topic sections,
			 * xref injection points and file names in the zip file
			 * will be taken from the fixed url property tag, defaulting
			 * back to the TopicID## format if for some reason that
			 * property tag does not exist.
			 */
			fixedUrlsSuccess = setFixedURLsPass(normalTopics);

			topics = (BaseRestCollectionV1<T, U>) normalTopics;
		}
		else
		{
			/*
			 * Translations should reference an existing historical topic with
			 * the fixed urls set, so we assume this to be the case
			 */
			fixedUrlsSuccess = true;

			/* set the topics variable now all initialisation is done */
			topics = (BaseRestCollectionV1<T, U>) getTranslatedTopics(contentSpec, topicIds, topicRevisions);
		}

		// Check if the app should be shutdown
        if (isShuttingDown.get())
        {
            return false;
        }

		/* Add all the levels and topics to the database first */
		addLevelAndTopicsToDatabase(contentSpec.getBaseLevel(), fixedUrlsSuccess);

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return false;
		}

		/* Set the duplicate id's for each topic */
		specDatabase.setDatabaseDulicateIds();

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return false;
		}

		/* Pass the topics to make sure they are valid */
		doTopicPass(topics, fixedUrlsSuccess, usedIdAttributes);

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return false;
		}

		return fixedUrlsSuccess;
	}
	
	/**
	 * Gets the translated topics from the REST Interface and also creates any dummy translations
	 * for topics that have yet to be translated.
	 * 
	 * @param contentSpec The ContentSpec object used to build the book.
	 * @param topicIds A Set of topic id's that are to be used to get the latest translations.
	 * @param topicRevisions A Set of topic id's to revisions, used to get translations closest to
	 * specific revisions.
	 * @return A collection of TranslatedTopics or null if a shutdown was requested.
	 */
	private RESTTranslatedTopicCollectionV1 getTranslatedTopics(final ContentSpec contentSpec, final Set<Integer> topicIds, final Set<Pair<Integer, Integer>> topicRevisions)
	{
	    final RESTTranslatedTopicCollectionV1 translatedTopics = new RESTTranslatedTopicCollectionV1();

        /* Ensure that the collection doesn't equal null */
        final RESTTopicCollectionV1 topicCollection = reader.getTopicsByIds(CollectionUtilities.toArrayList(topicIds), true);

        /*
         * Populate the dummy topic ids using the latest topics.
         * We will remove the topics that exist a little later.
         */
        final Set<Integer> dummyTopicIds = new HashSet<Integer>(topicIds);
        
        /* Remove any topic ids for translated topics that were found */
        if (topicCollection != null)
        {
            if (topicCollection != null && topicCollection.getItems() != null)
            {
                for (final RESTTopicV1 topic : topicCollection.getItems())
                {
                    // Check if the app should be shutdown
                    if (isShuttingDown.get())
                    {
                        return null;
                    }

                    // Get the matching latest translated topic and pushed translated topics
                    final Pair<RESTTranslatedTopicV1, RESTTranslatedTopicV1> lastestTranslations = getLatestTranslations(topic, null);
                    final RESTTranslatedTopicV1 latestTranslatedTopic = lastestTranslations.getFirst();
                    final RESTTranslatedTopicV1 latestPushedTranslatedTopic = lastestTranslations.getSecond();

                    // If the latest translation and latest pushed topic matches, then use that if not a dummy topic should be created
                    if (latestTranslatedTopic != null && latestPushedTranslatedTopic != null
                            && latestPushedTranslatedTopic.getTopicRevision().equals(latestTranslatedTopic.getTopicRevision()))
                    {
                        final RESTTranslatedTopicV1 translatedTopic = reader.getTranslatedTopicById(latestTranslatedTopic.getId());
                        if (translatedTopic != null)
                        {
                            dummyTopicIds.remove(topic.getId());
                            translatedTopics.addItem(translatedTopic);
                        }
                    }
                }
            }
        }

        /*
         * Fetch each topic that is a revision separately since this
         * functionality isn't offered by the REST API.
         */
        final Set<Pair<Integer, Integer>> dummyTopicRevisionIds = new HashSet<Pair<Integer, Integer>>();
        for (final Pair<Integer, Integer> topicToRevision : topicRevisions)
        {   
            // Check if the app should be shutdown
            if (isShuttingDown.get())
            {
                return null;
            }
            
            final RESTTopicV1 topic = reader.getTopicById(topicToRevision.getFirst(), topicToRevision.getSecond(), true);

            if (topic != null)
            {
                // Get the matching latest translated topic and pushed translated topics
                final Pair<RESTTranslatedTopicV1, RESTTranslatedTopicV1> lastestTranslations = getLatestTranslations(topic, topicToRevision.getSecond());
                final RESTTranslatedTopicV1 latestTranslatedTopic = lastestTranslations.getFirst();
                final RESTTranslatedTopicV1 latestPushedTranslatedTopic = lastestTranslations.getSecond();
    
                // If the latest translation and latest pushed topic matches, then use that if not a dummy topic should be created
                if (latestTranslatedTopic != null && latestPushedTranslatedTopic != null
                        && latestPushedTranslatedTopic.getTopicRevision().equals(latestTranslatedTopic.getTopicRevision()))
                {
                    final RESTTranslatedTopicV1 translatedTopic = reader.getTranslatedTopicById(latestTranslatedTopic.getId());
                    if (translatedTopic != null)
                    {
                        translatedTopics.addItem(translatedTopic);
                    }
                    else
                    {
                        dummyTopicRevisionIds.add(topicToRevision);
                    }
                }
                else
                {
                    dummyTopicRevisionIds.add(topicToRevision);
                }
            }
            else
            {
                dummyTopicRevisionIds.add(topicToRevision);
            }
        }

        /* Create the dummy translated topics */
        if (!dummyTopicIds.isEmpty() || !dummyTopicRevisionIds.isEmpty())
        {
            populateDummyTranslatedTopicsPass(translatedTopics, dummyTopicIds, dummyTopicRevisionIds);
        }
        
        return translatedTopics;
	}
	
	/**
     * Find the latest pushed and translated topics for a topic. We need to do this since translations are
     * only added when some content is added in Zanata. So if the latest translated topic doesn't match
     * the topic revision of the latest pushed then we will need to create a dummy topic for the latest
     * pushed topic.
     * 
     * @param topic The topic to find the latest translated topic and pushed translation.
     * @param rev The revision for the topic as specified in the ContentSpec.
     * @return A Pair whose first element is the Latest Translated Topic and second element is the Latest
     * Pushed Translation.
     */
	private Pair<RESTTranslatedTopicV1, RESTTranslatedTopicV1> getLatestTranslations(final RESTTopicV1 topic, final Integer rev)
	{
        RESTTranslatedTopicV1 latestTranslatedTopic = null;
        RESTTranslatedTopicV1 latestPushedTranslatedTopic = null;
        if (topic.getTranslatedTopics_OTM() != null && topic.getTranslatedTopics_OTM().getItems() != null)
        {
            for (final RESTTranslatedTopicV1 tempTopic : topic.getTranslatedTopics_OTM().getItems())
            {
                // Find the Latest Translated Topic
                if (locale.equals(tempTopic.getLocale())
                        && (latestTranslatedTopic == null || latestTranslatedTopic.getTopicRevision() < tempTopic.getTopicRevision())
                        && (rev == null || tempTopic.getTopicRevision() <= rev))
                {
                    latestTranslatedTopic = tempTopic;
                }
                
                // Find the Latest Pushed Topic
                if (topic.getLocale().equals(tempTopic.getLocale())
                        && (latestPushedTranslatedTopic == null || latestPushedTranslatedTopic.getTopicRevision() < tempTopic.getTopicRevision())
                        && (rev == null || tempTopic.getTopicRevision() <= rev))
                {
                    latestPushedTranslatedTopic = tempTopic;
                }
            }
        }
        
        return new Pair<RESTTranslatedTopicV1, RESTTranslatedTopicV1>(latestTranslatedTopic, latestPushedTranslatedTopic);
	}

	/**
	 * Adds the levels and topics in the provided Level object
	 * to the local content spec database.
	 *
	 * @param level The content spec level to be added to the database.
	 * @param useFixedUrls Whether fixed URL's are to be used for the level ID
	 * attributes.
	 */
	private void addLevelAndTopicsToDatabase(final Level level, final boolean useFixedUrls)
	{
		/* Add the level to the database */
		specDatabase.add(level, createURLTitle(level.getTitle()));

		/* Add the topics at this level to the database */
		for (final SpecTopic specTopic : level.getSpecTopics())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get())
			{
				return;
			}

			specDatabase.add(specTopic, specTopic.getUniqueLinkId(useFixedUrls));
		}

		/* Add the child levels to the database */
		for (final Level childLevel : level.getChildLevels())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get())
			{
				return;
			}

			addLevelAndTopicsToDatabase(childLevel, useFixedUrls);
		}
	}

	/**
	 * Gets a Set of Topic ID's to Revisions from the content specification level for each
	 * Spec Topic.
	 *
	 * @param level The level to scan for topics.
	 * @return A Set of Topic ID/Revision Pairs that represent the topics in the level.
	 */
	private Set<Pair<Integer, Integer>> getTopicIdsFromLevel(final Level level)
	{
		/* Add the topics at this level to the database */
		final Set<Pair<Integer, Integer>> topicIds = new HashSet<Pair<Integer, Integer>>();
		for (final SpecTopic specTopic : level.getSpecTopics())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get())
			{
				return topicIds;
			}

			if (specTopic.getDBId() != 0)
			{
				topicIds.add(new Pair<Integer, Integer>(specTopic.getDBId(), specTopic.getRevision()));
			}
		}

		/* Add the child levels to the database */
		for (final Level childLevel : level.getChildLevels())
		{
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
	 * @param topics The set of topics to add the dummy translated topics to.
	 * @param dummyTopicIds The list of topics to be added as dummy translated topics.
	 * @param dummyRevisionTopicIds
	 */
	private void populateDummyTranslatedTopicsPass(final RESTTranslatedTopicCollectionV1 topics, final Set<Integer> dummyTopicIds, Set<Pair<Integer, Integer>> dummyRevisionTopicIds)
	{
		log.info("\tDoing dummy Translated Topic pass");

		final RESTTopicCollectionV1 dummyTopics;

		RESTTopicCollectionV1 tempCollection = reader.getTopicsByIds(CollectionUtilities.toArrayList(dummyTopicIds), true);
		if (tempCollection == null)
		{
			dummyTopics = new RESTTopicCollectionV1();
		}
		else
		{
			dummyTopics = tempCollection;
		}

		/* Add any revision topics */
		for (final Pair<Integer, Integer> topicToRevision : dummyRevisionTopicIds)
		{
			final RESTTopicV1 topic = reader.getTopicById(topicToRevision.getFirst(), topicToRevision.getSecond(), true);
			if (topic != null)
			{
				dummyTopics.addItem(topic);
			}
		}
		
		/* Only continue if we found dummy topics */
		if (dummyTopics == null || dummyTopics.getItems() == null || dummyTopics.getItems().isEmpty())
		{
			return;
		}

		/* Split the topics up into their different locales */
		final Map<Integer, RESTTranslatedTopicV1> translatedTopicsMap = new HashMap<Integer, RESTTranslatedTopicV1>();

		if (topics != null && topics.getItems() != null)
		{
			for (final RESTTranslatedTopicV1 topic : topics.getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get())
				{
					return;
				}

				translatedTopicsMap.put(topic.getTopicId(), topic);
			}
		}

		/* create and add the dummy topics */
		for (final RESTTopicV1 topic : dummyTopics.getItems())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get())
			{
				return;
			}

			if (!translatedTopicsMap.containsKey(topic.getId()))
			{
				final RESTTranslatedTopicV1 dummyTopic = createDummyTranslatedTopic(translatedTopicsMap, topic, true, locale);

				topics.addItem(dummyTopic);
			}
		}
	}

	/**
	 * Creates a dummy translated topic so that a book
	 * can be built using the same relationships as a
	 * normal build.
	 *
	 * @param translatedTopicsMap A map of topic ids to translated topics.
	 * @param topic The topic to create the dummy topic from.
	 * @param expandRelationships Whether the relationships should be expanded for the dummy topic.
	 * @param locale The locale to build the dummy translations for.
	 * @return The dummy translated topic.
	 */
	private RESTTranslatedTopicV1 createDummyTranslatedTopic(final Map<Integer, RESTTranslatedTopicV1> translatedTopicsMap,
			final RESTTopicV1 topic, final boolean expandRelationships, final String locale)
	{
		final RESTTranslatedTopicV1 translatedTopic = new RESTTranslatedTopicV1();
		translatedTopic.setTopic(topic);
		translatedTopic.setId(topic.getId() * -1);
		
		final RESTTranslatedTopicV1 pushedTranslatedTopic = ComponentTranslatedTopicV1.returnPushedTranslatedTopic(translatedTopic);
		
		/* 
		 * Try and use the untranslated default locale translated topic as the 
		 * base for the dummy topic. If that fails then create a dummy topic from
		 * the passed RESTTopicV1.
		 */
		if (pushedTranslatedTopic != null)
		{
			final RESTTranslatedTopicV1 defaultLocaleTranslatedTopic = reader.getTranslatedTopicById(pushedTranslatedTopic.getId());
			
			if (defaultLocaleTranslatedTopic != null)
			{
				/* Negate the ID to show it isn't a proper translated topic */
				defaultLocaleTranslatedTopic.setId(topic.getId() * -1);
				
				/* prefix the locale to show that it is missing the related translated topic */
				defaultLocaleTranslatedTopic.setTitle("[" + defaultLocaleTranslatedTopic.getLocale() + "] " + defaultLocaleTranslatedTopic.getTitle());
				
				/* Change the locale since the default locale translation is being transformed into a dummy translation */
				defaultLocaleTranslatedTopic.setLocale(locale);
				
				return defaultLocaleTranslatedTopic;
			}
		}
		
		/* 
		 * If we get to this point then no translation exists or
		 * the default locale translation failed to be downloaded.
		 */
		translatedTopic.setTopicId(topic.getId());
		translatedTopic.setTopicRevision(topic.getRevision().intValue());
		translatedTopic.setTranslationPercentage(100);
		translatedTopic.setRevision(topic.getRevision());
		translatedTopic.setXml(topic.getXml());
		translatedTopic.setTags(topic.getTags());
		translatedTopic.setSourceUrls_OTM(topic.getSourceUrls_OTM());
		translatedTopic.setProperties(topic.getProperties());
		translatedTopic.setLocale(locale);

		/* prefix the locale to show that it is missing the related translated topic */
		translatedTopic.setTitle("[" + topic.getLocale() + "] " + topic.getTitle());

		/* Add the dummy outgoing relationships */
		if (topic.getOutgoingRelationships() != null && topic.getOutgoingRelationships().getItems() != null)
		{
			final RESTTranslatedTopicCollectionV1 outgoingRelationships = new RESTTranslatedTopicCollectionV1();
			for (final RESTTopicV1 relatedTopic : topic.getOutgoingRelationships().getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get())
				{
					return translatedTopic;
				}

				/* check to see if the translated topic already exists */
				if (translatedTopicsMap.containsKey(relatedTopic.getId()))
				{
					outgoingRelationships.addItem(translatedTopicsMap.get(relatedTopic.getId()));
				}
				else
				{
					outgoingRelationships.addItem(createDummyTranslatedTopic(translatedTopicsMap, relatedTopic, false, locale));
				}
			}
			translatedTopic.setOutgoingRelationships(outgoingRelationships);
		}

		/* Add the dummy incoming relationships */
		if (topic.getIncomingRelationships() != null && topic.getIncomingRelationships().getItems() != null)
		{
			final RESTTranslatedTopicCollectionV1 incomingRelationships = new RESTTranslatedTopicCollectionV1();
			for (final RESTTopicV1 relatedTopic : topic.getIncomingRelationships().getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get())
				{
					return translatedTopic;
				}

				/* check to see if the translated topic already exists */
				if (translatedTopicsMap.containsKey(relatedTopic.getId()))
				{
					incomingRelationships.addItem(translatedTopicsMap.get(relatedTopic.getId()));
				}
				else
				{
					incomingRelationships.addItem(createDummyTranslatedTopic(translatedTopicsMap, relatedTopic, false, locale));
				}
			}
			translatedTopic.setIncomingRelationships(incomingRelationships);
		}

		return translatedTopic;
	}

	/**
	 * Do the first topic pass on the database and check if the base XML
	 * is valid and set the Document Object's for each spec topic. Also
	 * collect the ID Attributes that are used within the topics.
	 *
	 * @param topics The list of topics to be checked and added to the database.
	 * @param useFixedUrls Whether the Fixed URL Properties should be used for the
	 * topic ID attributes.
	 * @param usedIdAttributes The set of Used ID Attributes that should be added to.
	 */
	private void doTopicPass(final BaseRestCollectionV1<T, U> topics, final boolean useFixedUrls, final Map<Integer, Set<String>> usedIdAttributes)
	{
		log.info("Doing " + locale + " First topic pass");

		/* Check that we have some topics to process */
		if (topics != null && topics.getItems() != null)
		{
			log.info("\tProcessing " + topics.getItems().size() + " Topics");

			final int showPercent = 5;
			final float total = topics.getItems().size();
			float current = 0;
			int lastPercent = 0;

			/* Process each topic */
			for (final T topic : topics.getItems())
			{
				++current;
				final int percent = Math.round(current / total * 100);
				if (percent - lastPercent >= showPercent)
				{
					lastPercent = percent;
					log.info("\tFirst topic Pass " + percent + "% Done");
				}

				/* Find the Topic ID */
				final Integer topicId;
				if (topic instanceof RESTTranslatedTopicV1)
				{
					topicId = ((RESTTranslatedTopicV1) topic).getTopicId();
				}
				else
				{
					topicId = topic.getId();
				}

				Document topicDoc = null;
				final String topicXML = topic == null ? null : topic.getXml();

				// Check if the app should be shutdown
				if (isShuttingDown.get())
				{
					return;
				}

				boolean xmlValid = true;

				// Check that the Topic XML exists and isn't empty
				if (topicXML == null || topicXML.equals(""))
				{
					// Create an empty topic with the topic title from the resource file
					final String topicXMLErrorTemplate = buildTopicErrorTemplate(topic, errorEmptyTopic.getValue());

					errorDatabase.addWarning(topic, ErrorType.NO_CONTENT, BuilderConstants.WARNING_EMPTY_TOPIC_XML);
					topicDoc = setTopicXMLForError(topic, topicXMLErrorTemplate, useFixedUrls);
					xmlValid = false;
				}

				// Check if the app should be shutdown
				if (isShuttingDown.get())
				{
					return;
				}

				/* make sure we have valid XML */
				if (xmlValid)
				{
					try
					{
						topicDoc = XMLUtilities.convertStringToDocument(topic.getXml());

						if (topicDoc != null)
						{
							/* Ensure the topic is wrapped in a section and the title matches the topic */
							DocBookUtilities.wrapDocumentInSection(topicDoc);
							DocBookUtilities.setSectionTitle(topic.getTitle(), topicDoc);
						}
						else
						{
							final String topicXMLErrorTemplate = buildTopicErrorTemplate(topic, errorInvalidValidationTopic.getValue());

							errorDatabase.addError(topic, ErrorType.INVALID_CONTENT, BuilderConstants.ERROR_INVALID_XML_CONTENT);
							topicDoc = setTopicXMLForError(topic, topicXMLErrorTemplate, useFixedUrls);
						}
					}
					catch (SAXException ex)
					{
						final String topicXMLErrorTemplate = buildTopicErrorTemplate(topic, errorInvalidValidationTopic.getValue());

						errorDatabase.addError(topic, ErrorType.INVALID_CONTENT, BuilderConstants.ERROR_BAD_XML_STRUCTURE + " " + StringUtilities.escapeForXML(ex.getMessage()));
						topicDoc = setTopicXMLForError(topic, topicXMLErrorTemplate, useFixedUrls);
					}
				}

				/*
				 * Extract the id attributes used in this topic. We'll use this data
				 * in the second pass to make sure that individual topics don't
				 * repeat id attributes.
				 */
				collectIdAttributes(topicId, topicDoc, usedIdAttributes);

				processTopicID(topic, topicDoc, useFixedUrls);

				/* Add the document & topic to the database spec topics */
				final List<SpecTopic> specTopics = specDatabase.getSpecTopicsForTopicID(topicId);
				for (final SpecTopic specTopic : specTopics)
				{
					// Check if the app should be shutdown
					if (isShuttingDown.get())
					{
						return;
					}

					specTopic.setTopic(topic.clone(false));
					specTopic.setXmlDocument((Document) topicDoc.cloneNode(true));
				}

			}
		}
		else
		{
			log.info("\tProcessing 0 Topics");
		}
	}

	/**
	 * Loops through each of the spec topics in the database and sets the injections and unique ids for
	 * each id attribute in the Topics XML.
	 *
	 * @param contentSpec The content specification used to build the book.
	 * @param searchTagsUrl The URL for the search to list the topics in the book.
	 * @param usedIdAttributes The set of ids that have been used in the set of topics in the content spec.
	 * @param useFixedUrls If during processing the fixed urls should be used.
	 * @param buildName A specific name for the build to be used in bug links.
	 */
	@SuppressWarnings("unchecked")
	private void doSpecTopicPass(final ContentSpec contentSpec, final String searchTagsUrl, final Map<Integer, Set<String>> usedIdAttributes,
			final boolean useFixedUrls, final String buildName)
	{
		log.info("Doing " + locale + " Spec Topic Pass");
		final List<SpecTopic> specTopics = specDatabase.getAllSpecTopics();

		log.info("\tProcessing " + specTopics.size() + " Spec Topics");

		final int showPercent = 5;
		final float total = specTopics.size();
		float current = 0;
		int lastPercent = 0;

		/* Create the related topics database to be used for CSP builds */
		final TocTopicDatabase<T, U> relatedTopicsDatabase = new TocTopicDatabase<T, U>();
		relatedTopicsDatabase.setTopics(specDatabase.<T, U>getAllTopics());

		for (final SpecTopic specTopic : specTopics)
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get())
			{
				return;
			}

			++current;
			final int percent = Math.round(current / total * 100);
			if (percent - lastPercent >= showPercent)
			{
				lastPercent = percent;
				log.info("\tProcessing Pass " + percent + "% Done");
			}

			final T topic = (T) specTopic.getTopic();
			final Document doc = specTopic.getXmlDocument();
			final Level baseLevel = contentSpec.getBaseLevel();

			final XMLPreProcessor<T, U> xmlPreProcessor = new XMLPreProcessor<T, U>();
			boolean valid = true;

			if (doc != null)
			{
				/* process the conditional statements */
				final String condition = specTopic.getConditionStatement(true);
				processConditionalStatements(condition, doc);
				
				/* process the injection points */
				if (injectionOptions.isInjectionAllowed())
				{

					final ArrayList<Integer> customInjectionIds = new ArrayList<Integer>();
					final List<Integer> genericInjectionErrors;
					final List<Integer> customInjectionErrors;

					if (contentSpec.getOutputStyle().equalsIgnoreCase(CSConstants.SKYNET_OUTPUT_FORMAT))
					{
						/*
						 * create a collection of the tags that make up the topics types
						 * that will be included in generic injection points
						 */
						final List<Pair<Integer, String>> topicTypeTagDetails = new ArrayList<Pair<Integer, String>>();
						topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.TASK_TAG_ID, DocbookBuilderConstants.TASK_TAG_NAME));
						topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.REFERENCE_TAG_ID, DocbookBuilderConstants.REFERENCE_TAG_NAME));
						topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.CONCEPT_TAG_ID, DocbookBuilderConstants.CONCEPT_TAG_NAME));
						topicTypeTagDetails.add(Pair.newPair(DocbookBuilderConstants.CONCEPTUALOVERVIEW_TAG_ID, DocbookBuilderConstants.CONCEPTUALOVERVIEW_TAG_NAME));

						customInjectionErrors = xmlPreProcessor.processInjections(baseLevel, specTopic, customInjectionIds, doc, docbookBuildingOptions, null, useFixedUrls);

						genericInjectionErrors = xmlPreProcessor.processGenericInjections(baseLevel, specTopic, doc, customInjectionIds, topicTypeTagDetails, docbookBuildingOptions, useFixedUrls);
					}
					else
					{
						xmlPreProcessor.processPrerequisiteInjections(specTopic, doc, useFixedUrls);
						xmlPreProcessor.processPrevRelationshipInjections(specTopic, doc, useFixedUrls);
						xmlPreProcessor.processLinkListRelationshipInjections(specTopic, doc, useFixedUrls);
						xmlPreProcessor.processNextRelationshipInjections(specTopic, doc, useFixedUrls);
						xmlPreProcessor.processSeeAlsoInjections(specTopic, doc, useFixedUrls);

						customInjectionErrors = xmlPreProcessor.processInjections(baseLevel, specTopic, customInjectionIds, doc, docbookBuildingOptions, relatedTopicsDatabase, useFixedUrls);

						genericInjectionErrors = new ArrayList<Integer>();
					}

					final List<Integer> topicContentFragmentsErrors = xmlPreProcessor.processTopicContentFragments(specTopic, doc, docbookBuildingOptions);
					final List<Integer> topicTitleFragmentsErrors = xmlPreProcessor.processTopicTitleFragments(specTopic, doc, docbookBuildingOptions);

					// Check if the app should be shutdown
					if (isShuttingDown.get())
					{
						return;
					}

					if (!customInjectionErrors.isEmpty())
					{
						final String message = "Topic has referenced Topic(s) " + CollectionUtilities.toSeperatedString(customInjectionErrors)
								+ " in a custom injection point that was either not related, or not included in the filter used to build this book.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, ErrorType.INVALID_INJECTION, message);
						}
						else
						{
							errorDatabase.addError(topic, ErrorType.INVALID_INJECTION, message);
							valid = false;
						}
					}


					if (!genericInjectionErrors.isEmpty())
					{
						final String message = "Topic has related Topic(s) " + CollectionUtilities.toSeperatedString(CollectionUtilities.toAbsIntegerList(genericInjectionErrors))
								+ " that were not included in the filter used to build this book.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, ErrorType.INVALID_INJECTION, message);
						}
						else
						{
							errorDatabase.addError(topic, ErrorType.INVALID_INJECTION, message);
							valid = false;
						}
					}

					if (!topicContentFragmentsErrors.isEmpty())
					{
						final String message = "Topic has injected content from Topic(s) " + CollectionUtilities.toSeperatedString(topicContentFragmentsErrors)
								+ " that were not related.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, ErrorType.INVALID_INJECTION, message);
						}
						else
						{
							errorDatabase.addError(topic, ErrorType.INVALID_INJECTION, message);
							valid = false;
						}
					}

					if (!topicTitleFragmentsErrors.isEmpty())
					{
						final String message = "Topic has injected a title from Topic(s) " + CollectionUtilities.toSeperatedString(topicTitleFragmentsErrors)
								+ " that were not related.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, ErrorType.INVALID_INJECTION, message);
						}
						else
						{
							errorDatabase.addError(topic, ErrorType.INVALID_INJECTION, message);
							valid = false;
						}
					}

					/* check for dummy topics */
					if (topic instanceof RESTTranslatedTopicV1)
					{
						/* Add the warning for the topics relationships that haven't been translated */
						if (topic.getOutgoingRelationships() != null && topic.getOutgoingRelationships().getItems() != null)
						{
							for (final T relatedTopic : topic.getOutgoingRelationships().getItems())
							{
								// Check if the app should be shutdown
								if (isShuttingDown.get())
								{
									return;
								}

								final RESTTranslatedTopicV1 relatedTranslatedTopic = (RESTTranslatedTopicV1) relatedTopic;

								/* Only show errors for topics that weren't included in the injections */
								if (!customInjectionErrors.contains(relatedTranslatedTopic.getTopicId()) && !genericInjectionErrors.contains(relatedTopic.getId()))
								{
									if ((!baseLevel.isSpecTopicInLevelByTopicID(relatedTranslatedTopic.getTopicId()) && !docbookBuildingOptions.getIgnoreMissingCustomInjections()) || baseLevel.isSpecTopicInLevelByTopicID(relatedTranslatedTopic.getTopicId()))
									{
										if (ComponentTranslatedTopicV1.returnIsDummyTopic(relatedTopic) && ComponentTranslatedTopicV1.hasBeenPushedForTranslation(relatedTranslatedTopic))
										{
											errorDatabase.addWarning(topic, "Topic ID " + relatedTranslatedTopic.getTopicId() + ", Revision " + relatedTranslatedTopic.getTopicRevision() + ", Title \"" + relatedTopic.getTitle() + "\" is an untranslated topic.");
										}
										else if (ComponentTranslatedTopicV1.returnIsDummyTopic(relatedTopic))
										{
											errorDatabase.addWarning(topic, "Topic ID " + relatedTranslatedTopic.getTopicId() + ", Revision " + relatedTranslatedTopic.getTopicRevision() + ", Title \"" + relatedTopic.getTitle() + "\" hasn't been pushed for translation.");
										}
									}
								}
							}
						}

						/* Check the topic itself isn't a dummy topic */
						if (ComponentTranslatedTopicV1.returnIsDummyTopic(topic) && ComponentTranslatedTopicV1.hasBeenPushedForTranslation((RESTTranslatedTopicV1) topic))
						{
							errorDatabase.addWarning(topic, ErrorType.UNTRANSLATED, BuilderConstants.WARNING_UNTRANSLATED_TOPIC);
						}
						else if (ComponentTranslatedTopicV1.returnIsDummyTopic(topic))
						{
							errorDatabase.addWarning(topic, ErrorType.NOT_PUSHED_FOR_TRANSLATION, BuilderConstants.WARNING_NONPUSHED_TOPIC);
						}
						/* Check if the topic's content isn't fully translated */
						else if (((RESTTranslatedTopicV1) topic).getTranslationPercentage() < 100)
						{
							errorDatabase.addWarning(topic, ErrorType.INCOMPLETE_TRANSLATION, BuilderConstants.WARNING_INCOMPLETE_TRANSLATION);
						}
					}
				}

				// Check if the app should be shutdown
				if (isShuttingDown.get())
				{
					return;
				}

				if (!valid)
				{
					final String topicXMLErrorTemplate = buildTopicErrorTemplate(topic, errorInvalidInjectionTopic.getValue());

					final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(XMLUtilities.convertNodeToString(doc, verbatimElements, inlineElements, contentsInlineElements, true));
					errorDatabase.addError(topic, BuilderConstants.ERROR_INVALID_INJECTIONS + " The processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");

					setSpecTopicXMLForError(specTopic, topicXMLErrorTemplate, useFixedUrls);
				}
				else
				{
					/* add the standard boilerplate xml */
					xmlPreProcessor.processTopicAdditionalInfo(specTopic, doc, contentSpec.getBugzillaOptions(), docbookBuildingOptions, buildName, searchTagsUrl, buildDate, zanataDetails);

					/*
					 * make sure the XML is valid docbook after the standard
					 * processing has been done
					 */
					validateTopicXML(specTopic, doc, useFixedUrls);
				}
				
				/* 
				 * Check to see if the translated topic revision is an older
				 * topic than the topic revision specified in the map
				 */
				if (topic instanceof RESTTranslatedTopicV1)
				{
					final RESTTranslatedTopicV1 pushedTranslatedTopic = ComponentTranslatedTopicV1.returnPushedTranslatedTopic((RESTTranslatedTopicV1) topic);
					if (pushedTranslatedTopic != null && specTopic.getRevision() != null && !pushedTranslatedTopic.getTopicRevision().equals(specTopic.getRevision()))
					{
						if (ComponentTranslatedTopicV1.returnIsDummyTopic(topic))
						{
							errorDatabase.addWarning((T) topic, ErrorType.OLD_UNTRANSLATED, BuilderConstants.WARNING_OLD_UNTRANSLATED_TOPIC);
						}
						else
						{
							errorDatabase.addWarning((T) topic, ErrorType.OLD_TRANSLATION, BuilderConstants.WARNING_OLD_TRANSLATED_TOPIC);
						}
					}
				}

				/*
				 * Ensure that all of the id attributes are valid
				 * by setting any duplicates with a post fixed number.
				 */
				setUniqueIds(specTopic, specTopic.getXmlDocument(), usedIdAttributes);
			}
		}
	}

	/**
	 * Collects any nodes that have the "condition" attribute in the
	 * passed node or any of it's children nodes.
	 *
	 * @param node The node to collect condition elements from.
	 * @param conditionalNodes A mapping of nodes to their conditions
	 */
	protected void collectConditionalStatements(final Node node, final Map<Node, List<String>> conditionalNodes)
	{
		final NamedNodeMap attributes = node.getAttributes();
		if (attributes != null)
		{
			final Node attr = attributes.getNamedItem("condition");
			
			if (attr != null)
			{
				final String conditionStatement = attr.getNodeValue();
				
				final String[] conditions = conditionStatement.split(",");

				conditionalNodes.put(node, Arrays.asList(conditions));
			}
		}

		final NodeList elements = node.getChildNodes();
		for (int i = 0; i < elements.getLength(); ++i)
		{
			collectConditionalStatements(elements.item(i), conditionalNodes);
		}
	}

	/**
	 * Check the XML Document and it's children for condition
	 * statements. If any are found then check if the condition
	 * matches the passed condition string. If they don't match
	 * then remove the nodes.
	 *
	 * @param condition The condition regex to be tested against.
	 * @param doc The Document to check for conditional statements.
	 */
	protected void processConditionalStatements(final String condition, final Document doc)
	{
		final Map<Node, List<String>> conditionalNodes = new HashMap<Node, List<String>>();
		collectConditionalStatements(doc.getDocumentElement(), conditionalNodes);
		
		// Loop through each condition found and see if it matches
		for (final Node node : conditionalNodes.keySet())
		{
			boolean matched = false;
			
			final List<String> nodeConditions = conditionalNodes.get(node);
			
			// Check to see if the condition matches
			for (final String nodeCondition : nodeConditions)
			{
				if (condition != null && nodeCondition.matches(condition))
				{
					matched = true;
				}
				else if (condition == null && nodeCondition.matches(BuilderConstants.DEFAULT_CONDITION))
				{
					matched = true;
				}
			}
			
			// If there was no match then remove the node
			if (!matched)
			{
				final Node parentNode = node.getParentNode();
				if (parentNode != null)
				{
					parentNode.removeChild(node);
				}
			}
		}
	}

	/**
	 * Sets the "id" attributes in the supplied XML node so that they will be
	 * unique within the book.
	 *
	 * @param specTopic The topic the node belongs to.
	 * @param node The node to process for id attributes.
	 * @param usedIdAttributes The list of usedIdAttributes.
	 */
	private void setUniqueIds(final SpecTopic specTopic, final Node node, final Map<Integer, Set<String>> usedIdAttributes)
	{
		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return;
		}

		final NamedNodeMap attributes = node.getAttributes();
		if (attributes != null)
		{
			final Node idAttribute = attributes.getNamedItem("id");
			if (idAttribute != null)
			{
				final String idAttributeValue = idAttribute.getNodeValue();
				String fixedIdAttributeValue = idAttributeValue;

				if (specTopic.getDuplicateId() != null)
				{
					fixedIdAttributeValue += "-" + specTopic.getDuplicateId();
				}

				if (!isUniqueAttributeId(idAttributeValue, specTopic.getDBId(), usedIdAttributes))
				{
					fixedIdAttributeValue += "-" + specTopic.getStep();
				}

				setUniqueIdReferences(node, idAttributeValue, fixedIdAttributeValue);

				idAttribute.setNodeValue(fixedIdAttributeValue);
			}
		}

		final NodeList elements = node.getChildNodes();
		for (int i = 0; i < elements.getLength(); ++i)
		{
			setUniqueIds(specTopic, elements.item(i), usedIdAttributes);
		}
	}

	/**
	 * ID attributes modified in the setUniqueIds() method may have been referenced
	 * locally in the XML. When an ID is updated, and attribute that referenced
	 * that ID is also updated.
	 *
	 * @param node
	 *            The node to check for attributes
	 * @param id
	 *            The old ID attribute value
	 * @param fixedId
	 *            The new ID attribute
	 */
	private void setUniqueIdReferences(final Node node, final String id, final String fixedId)
	{
		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return;
		}

		final NamedNodeMap attributes = node.getAttributes();
		if (attributes != null)
		{
			for (int i = 0; i < attributes.getLength(); ++i)
			{
				final String attibuteValue = attributes.item(i).getNodeValue();
				if (attibuteValue.equals(id))
				{
					attributes.item(i).setNodeValue(fixedId);
				}
			}
		}

		final NodeList elements = node.getChildNodes();
		for (int i = 0; i < elements.getLength(); ++i)
		{
			setUniqueIdReferences(elements.item(i), id, fixedId);
		}
	}

	/**
	 * Checks to see if a supplied attribute id is unique within this book, based
	 * upon the used id attributes that were calculated earlier.
	 *
	 * @param id The Attribute id to be checked
	 * @param topicId The id of the topic the attribute id was found in
	 * @param usedIdAttributes The set of used ids calculated earlier
	 * @return True if the id is unique otherwise false.
	 */
	private boolean isUniqueAttributeId(final String id, final Integer topicId, final Map<Integer, Set<String>> usedIdAttributes)
	{
		boolean retValue = true;

		if (usedIdAttributes.containsKey(topicId))
		{
			for (final Integer topicId2 : usedIdAttributes.keySet())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get())
				{
					return false;
				}

				if (topicId2.equals(topicId))
				{
					continue;
				}

				final Set<String> ids2 = usedIdAttributes.get(topicId2);

				if (ids2.contains(id))
				{
					retValue = false;
				}
			}
		}

		return retValue;
	}

	/**
	 * This function scans the supplied XML node and it's children for id
	 * attributes, collecting them in the usedIdAttributes parameter.
	 *
	 * @param node
	 *            The current node being processed (will be the document root to
	 *            start with, and then all the children as this function is
	 *            recursively called)
	 * @param topicId The ID of the topic that we are collecting attribute ID's for.
	 * @param usedIdAttributes The set of Used ID Attributes that should be added to.
	 */
	private void collectIdAttributes(final Integer topicId, final Node node, final Map<Integer, Set<String>> usedIdAttributes)
	{
		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return;
		}

		final NamedNodeMap attributes = node.getAttributes();
		if (attributes != null)
		{
			final Node idAttribute = attributes.getNamedItem("id");
			if (idAttribute != null)
			{
				final String idAttibuteValue = idAttribute.getNodeValue();
				if (!usedIdAttributes.containsKey(topicId))
				{
					usedIdAttributes.put(topicId, new HashSet<String>());
				}
				usedIdAttributes.get(topicId).add(idAttibuteValue);
			}
		}

		final NodeList elements = node.getChildNodes();
		for (int i = 0; i < elements.getLength(); ++i)
		{
			collectIdAttributes(topicId, elements.item(i), usedIdAttributes);
		}
	}

	/**
	 * TODO
	 *
	 * @param contentSpec TODO
	 * @param requester TODO
	 * @param useFixedUrls TODO
	 * @return TODO
	 * @throws InvalidParameterException TODO
	 * @throws InternalProcessingException TODO
	 */
	private HashMap<String, byte[]> doBuildZipPass(final ContentSpec contentSpec, final RESTUserV1 requester, final boolean useFixedUrls)
			throws InvalidParameterException, InternalProcessingException
	{
		log.info("Building the ZIP file");

		final StringBuffer bookXIncludes = new StringBuffer();

		/* Add the base book information */
		final HashMap<String, byte[]> files = new HashMap<String, byte[]>();
		final String bookBase = buildBookBase(contentSpec, requester, files);

		/* add the images to the book */
		addImagesToBook(files, locale);

		final LinkedList<org.jboss.pressgangccms.contentspec.Node> levelData = contentSpec.getBaseLevel().getChildNodes();

		// Loop through and create each chapter and the topics inside those chapters
		for (final org.jboss.pressgangccms.contentspec.Node node : levelData)
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get())
			{
				return null;
			}

			if (node instanceof Level)
			{
				final Level level = (Level) node;

				if (level.hasSpecTopics())
				{
					createRootElementXML(files, bookXIncludes, level, useFixedUrls);
				}
				else if (docbookBuildingOptions.isAllowEmptySections())
				{
					bookXIncludes.append(DocBookUtilities.wrapInPara("No Content"));
				}
			}
		}

		/* add any compiler errors */
		if (!docbookBuildingOptions.getSuppressErrorsPage() && errorDatabase.hasItems(locale))
		{
			final String compilerOutput = DocBookUtilities.addXMLBoilerplate(buildErrorChapter(locale), this.escapedTitle + ".ent", "chapter");
			files.put(BOOK_LOCALE_FOLDER + "Errors.xml", StringUtilities.getStringBytes(StringUtilities.cleanTextForXML(compilerOutput == null ? "" : compilerOutput)));
			bookXIncludes.append("	<xi:include href=\"Errors.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
		}

		/* add the report chapter */
		if (docbookBuildingOptions.getShowReportPage())
		{
			final String compilerOutput = DocBookUtilities.addXMLBoilerplate(buildReportChapter(locale), this.escapedTitle + ".ent", "chapter");
			files.put(BOOK_LOCALE_FOLDER + "Report.xml", StringUtilities.getStringBytes(StringUtilities.cleanTextForXML(compilerOutput == null ? "" : compilerOutput)));
			bookXIncludes.append("	<xi:include href=\"Report.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
		}

		/* build the content specification page */
		if (!docbookBuildingOptions.getSuppressContentSpecPage())
		{
			try
			{
				files.put(BOOK_LOCALE_FOLDER + "Build_Content_Specification.xml", DocBookUtilities.buildAppendix(DocBookUtilities.wrapInPara("<programlisting>" + XMLUtilities.wrapStringInCDATA(contentSpec.toString()) + "</programlisting>"), "Build Content Specification").getBytes("UTF-8"));
			}
			catch (UnsupportedEncodingException e)
			{
				/* UTF-8 is a valid format so this should exception should never get thrown */
				log.error(e.getMessage());
			}
			bookXIncludes.append("	<xi:include href=\"Build_Content_Specification.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
		}

		final String book = bookBase.replace(BuilderConstants.XIINCLUDES_INJECTION_STRING, bookXIncludes);
		try
		{
			files.put(BOOK_LOCALE_FOLDER + escapedTitle + ".xml", book.getBytes("UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			/* UTF-8 is a valid format so this should exception should never get thrown */
			log.error(e.getMessage());
		}

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return null;
		}

		return files;
	}

	/**
	 * Builds the basics of a Docbook from the resource files for a specific content specification.
	 *
	 * @param contentSpec The content specification object to be built.
	 * @param requester The User who requested the book be built.
	 * @param files The mapping of file names/locations to files that will be packaged into the ZIP
	 * archive.
	 * @return A Document object to be used in generating the book.xml
	 * @throws InternalProcessingException If an error occurred during the REST API call.
	 * @throws InvalidParameterException If an error occurred during the REST API call.
	 */
	private String buildBookBase(final ContentSpec contentSpec, final RESTUserV1 requester, final Map<String, byte[]> files)
			throws InvalidParameterException, InternalProcessingException
	{
		log.info("\tAdding standard files to Publican ZIP file");

		final Map<String, String> overrides = docbookBuildingOptions.getOverrides();

		final String publicanCfg = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.PUBLICAN_CFG_ID, "").getValue();
		final String bookEnt = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.BOOK_ENT_ID, "").getValue();
		final String prefaceXml = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.CSP_PREFACE_XML_ID, "").getValue();

		final String brand = contentSpec.getBrand() == null ? BuilderConstants.BRAND_DEFAULT : contentSpec.getBrand();

		// Setup the basic book.xml
		final String bookXml;
		if (contentSpec.getBookType() == BookType.ARTICLE)
		{
			bookXml = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.ARTICLE_XML_ID, "").getValue();
		}
		else
		{
			bookXml = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.BOOK_XML_ID, "").getValue();
		}
		String basicBook = bookXml.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		basicBook = basicBook.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
		basicBook = basicBook.replaceAll(BuilderConstants.VERSION_REGEX, contentSpec.getVersion());

		// Setup publican.cfg
		String fixedPublicanCfg = publicanCfg.replaceAll(BuilderConstants.BRAND_REGEX, brand);
		fixedPublicanCfg = fixedPublicanCfg.replaceFirst(BuilderConstants.BOOK_TYPE_REGEX, contentSpec.getBookType().toString());
		fixedPublicanCfg = fixedPublicanCfg.replaceAll("xml_lang\\: .*?($|\\r\\n|\\n)", "xml_lang: " + locale + "\n");

		// Remove the image width for CSP output
		if (!contentSpec.getOutputStyle().equals(CSConstants.SKYNET_OUTPUT_FORMAT))
		{
			fixedPublicanCfg = fixedPublicanCfg.replaceFirst("max_image_width:[\\s]*\\d+(\\r)?\\n", "");
			fixedPublicanCfg = fixedPublicanCfg.replaceFirst("toc_section_depth:[\\s]*\\d+(\\r)?\\n", "");
		}

		if (contentSpec.getPublicanCfg() != null)
		{
			/* Remove the git_branch if the content spec contains a git_branch */
			if (contentSpec.getPublicanCfg().indexOf("git_branch") != -1)
			{
				fixedPublicanCfg = fixedPublicanCfg.replaceFirst("git_branch:[ ]*.*(\\r)?(\\n)?", "");
			}
			fixedPublicanCfg += contentSpec.getPublicanCfg();
		}

		if (docbookBuildingOptions.getPublicanShowRemarks())
		{
			fixedPublicanCfg += "\nshow_remarks: 1";
		}

		if (docbookBuildingOptions.getCvsPkgOption() != null)
		{
			fixedPublicanCfg += "\ncvs_pkg: " + docbookBuildingOptions.getCvsPkgOption();
		}

		try
		{
			files.put(BOOK_FOLDER + "publican.cfg", fixedPublicanCfg.getBytes("UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			/* UTF-8 is a valid format so this should exception should never get thrown */
			log.error(e.getMessage());
		}
		
		final String bookInfo;
		if (contentSpec.getBookType() == BookType.ARTICLE)
		{
			bookInfo = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.ARTICLE_INFO_XML_ID, "").getValue();
		}
		else
		{
			bookInfo = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.BOOK_INFO_XML_ID, "").getValue();
		}

		// Setup Book_Info.xml
		final String pubsNumber = overrides.containsKey("pubsnumber") ? overrides.get("pubsnumber") : (contentSpec.getPubsNumber() == null ? BuilderConstants.PUBSNUMBER_DEFAULT : contentSpec.getPubsNumber().toString());
		String fixedBookInfo = bookInfo.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		fixedBookInfo = fixedBookInfo.replaceAll(BuilderConstants.TITLE_REGEX, contentSpec.getTitle());
		fixedBookInfo = fixedBookInfo.replaceAll(BuilderConstants.SUBTITLE_REGEX, contentSpec.getSubtitle() == null ? BuilderConstants.SUBTITLE_DEFAULT : contentSpec.getSubtitle());
		fixedBookInfo = fixedBookInfo.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
		fixedBookInfo = fixedBookInfo.replaceAll(BuilderConstants.VERSION_REGEX, contentSpec.getVersion());
		fixedBookInfo = fixedBookInfo.replaceAll(BuilderConstants.EDITION_REGEX, contentSpec.getEdition() == null ? BuilderConstants.EDITION_DEFAULT : contentSpec.getEdition());
		fixedBookInfo = fixedBookInfo.replaceAll(BuilderConstants.PUBSNUMBER_REGEX, pubsNumber);

		if (!contentSpec.getOutputStyle().equals(CSConstants.SKYNET_OUTPUT_FORMAT))
		{
			fixedBookInfo = fixedBookInfo.replaceAll(BuilderConstants.ABSTRACT_REGEX, contentSpec.getAbstract() == null ? BuilderConstants.DEFAULT_ABSTRACT :
					("<abstract>\n\t\t<para>\n\t\t\t" + contentSpec.getAbstract() + "\n\t\t</para>\n\t</abstract>\n"));
			fixedBookInfo = fixedBookInfo.replaceAll(BuilderConstants.LEGAL_NOTICE_REGEX, BuilderConstants.LEGAL_NOTICE_XML);
		}

		try
		{
			if (contentSpec.getBookType() == BookType.ARTICLE)
			{
				files.put(BOOK_LOCALE_FOLDER + "Article_Info.xml", fixedBookInfo.getBytes("UTF-8"));
			}
			else
			{
				files.put(BOOK_LOCALE_FOLDER + "Book_Info.xml", fixedBookInfo.getBytes("UTF-8"));
			}
		}
		catch (UnsupportedEncodingException e)
		{
			/* UTF-8 is a valid format so this should exception should never get thrown */
			log.error(e.getMessage());
		}

		// Setup Author_Group.xml
		if (overrides.containsKey(CSConstants.AUTHOR_GROUP_OVERRIDE))
		{
			final File authorGrp = new File(overrides.get(CSConstants.AUTHOR_GROUP_OVERRIDE));
			if (authorGrp.exists() && authorGrp.isFile())
			{
				try
				{
					final FileInputStream fis = new FileInputStream(authorGrp);
					final BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
					final StringBuilder buffer = new StringBuilder();
					String line = "";
					while ((line = reader.readLine()) != null)
					{
						buffer.append(line + "\n");
					}

					// Add the parsed file to the book
					files.put(BOOK_LOCALE_FOLDER + "Author_Group.xml", buffer.toString().getBytes("UTF-8"));
				}
				catch (Exception e)
				{
					log.error(e.getMessage());
					buildAuthorGroup(contentSpec, files);
				}
			}
			else
			{
				log.error("Author_Group.xml override is an invalid file. Using the default Author_Group.xml instead.");
				buildAuthorGroup(contentSpec, files);
			}
		}
		else
		{
			buildAuthorGroup(contentSpec, files);
		}

		if (!contentSpec.getOutputStyle().equals(CSConstants.SKYNET_OUTPUT_FORMAT))
		{
			// Setup Preface.xml
			String fixedPrefaceXml = prefaceXml.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
			try
			{
				files.put(BOOK_LOCALE_FOLDER + "Preface.xml", fixedPrefaceXml.getBytes("UTF-8"));
			}
			catch (UnsupportedEncodingException e)
			{
				/* UTF-8 is a valid format so this should exception should never get thrown */
				log.error(e.getMessage());
			}

			// Add the preface to the book.xml
			basicBook = basicBook.replaceAll(BuilderConstants.PREFACE_REGEX, "<xi:include href=\"Preface.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />");

			// Add the revision history to the book.xml
			basicBook = basicBook.replaceAll(BuilderConstants.REV_HISTORY_REGEX, "<xi:include href=\"Revision_History.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />");
		}

		// Add any common content files that need to be included locally
		if (docbookBuildingOptions.getCommonContentLocale() != null && docbookBuildingOptions.getCommonContentDirectory() != null)
		{
			addPublicanCommonContentToBook(contentSpec, docbookBuildingOptions.getCommonContentLocale(), docbookBuildingOptions.getCommonContentDirectory(), files);
		}

		// Setup Revision_History.xml
		if (overrides.containsKey(CSConstants.REVISION_HISTORY_OVERRIDE))
		{
			final File revHistory = new File(overrides.get(CSConstants.REVISION_HISTORY_OVERRIDE));
			if (revHistory.exists() && revHistory.isFile())
			{
				try
				{
					final FileInputStream fis = new FileInputStream(revHistory);
					final BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
					final StringBuilder buffer = new StringBuilder();
					String line = "";
					while ((line = reader.readLine()) != null)
					{
						buffer.append(line + "\n");
					}

					// Add the parsed file to the book
					files.put(BOOK_LOCALE_FOLDER + "Revision_History.xml", buffer.toString().getBytes("UTF-8"));
				}
				catch (Exception e)
				{
					log.error(e.getMessage());
					buildRevisionHistory(contentSpec, requester, files);
				}
			}
			else
			{
				log.error("Revision_History.xml override is an invalid file. Using the default Revision_History.xml instead.");
				buildRevisionHistory(contentSpec, requester, files);
			}
		}
		else
		{
			buildRevisionHistory(contentSpec, requester, files);
		}

		// Setup the <<contentSpec.title>>.ent file
		String entFile = bookEnt.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		entFile = entFile.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
		entFile = entFile.replaceAll(BuilderConstants.TITLE_REGEX, contentSpec.getTitle());
		entFile = entFile.replaceAll(BuilderConstants.YEAR_FORMAT_REGEX, Integer.toString(Calendar.getInstance().get(Calendar.YEAR)));
		entFile = entFile.replaceAll(BuilderConstants.CONTENT_SPEC_COPYRIGHT_REGEX, contentSpec.getCopyrightHolder());
		entFile = entFile.replaceAll(BuilderConstants.BZPRODUCT_REGEX, contentSpec.getBugzillaProduct() == null ? contentSpec.getProduct() : contentSpec.getBugzillaProduct());
		entFile = entFile.replaceAll(BuilderConstants.BZCOMPONENT_REGEX, contentSpec.getBugzillaComponent() == null ? BuilderConstants.DEFAULT_BZCOMPONENT : contentSpec.getBugzillaComponent());
		entFile = entFile.replaceAll(BuilderConstants.CONTENT_SPEC_BUGZILLA_URL_REGEX, contentSpec.getBugzillaURL() == null ? BuilderConstants.DEFAULT_BUGZILLA_URL : contentSpec.getBugzillaURL());
		try
		{
			files.put(BOOK_LOCALE_FOLDER + escapedTitle + ".ent", entFile.getBytes("UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			/* UTF-8 is a valid format so this should exception should never get thrown */
			log.error(e.getMessage());
		}

		// Setup the images and files folders
		final String iconSvg = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.ICON_SVG_ID, "").getValue();
		try
		{
			files.put(BOOK_IMAGES_FOLDER + "icon.svg", iconSvg.getBytes("UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			/* UTF-8 is a valid format so this should exception should never get thrown */
			log.error(e.getMessage());
		}

		if (contentSpec.getOutputStyle() != null && contentSpec.getOutputStyle().equals(CSConstants.SKYNET_OUTPUT_FORMAT))
		{
			final String jbossSvg = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.JBOSS_SVG_ID, "").getValue();

			final String yahooDomEventJs = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.YAHOO_DOM_EVENT_JS_ID, "").getValue();
			final String treeviewMinJs = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.TREEVIEW_MIN_JS_ID, "").getValue();
			final String treeviewCss = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.TREEVIEW_CSS_ID, "").getValue();
			final String jqueryMinJs = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.JQUERY_MIN_JS_ID, "").getValue();

			final byte[] treeviewSpriteGif = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.TREEVIEW_SPRITE_GIF_ID, "").getValue();
			final byte[] treeviewLoadingGif = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.TREEVIEW_LOADING_GIF_ID, "").getValue();
			final byte[] check1Gif = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.CHECK1_GIF_ID, "").getValue();
			final byte[] check2Gif = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.CHECK2_GIF_ID, "").getValue();

			// these files are used by the YUI treeview
			files.put(BOOK_FILES_FOLDER + "yahoo-dom-event.js", StringUtilities.getStringBytes(yahooDomEventJs));
			files.put(BOOK_FILES_FOLDER + "treeview-min.js", StringUtilities.getStringBytes(treeviewMinJs));
			files.put(BOOK_FILES_FOLDER + "treeview.css", StringUtilities.getStringBytes(treeviewCss));
			files.put(BOOK_FILES_FOLDER + "jquery.min.js", StringUtilities.getStringBytes(jqueryMinJs));

			// these are the images that are referenced in the treeview.css file
			files.put(BOOK_FILES_FOLDER + "treeview-sprite.gif", treeviewSpriteGif);
			files.put(BOOK_FILES_FOLDER + "treeview-loading.gif", treeviewLoadingGif);
			files.put(BOOK_FILES_FOLDER + "check1.gif", check1Gif);
			files.put(BOOK_FILES_FOLDER + "check2.gif", check2Gif);

			files.put(BOOK_IMAGES_FOLDER + "jboss.svg", StringUtilities.getStringBytes(jbossSvg));
		}

		return basicBook;
	}

	/**
	 * Adds the Publican Common_Content files specified by the brand,
	 * locale and directory location build options . If the Common_Content
	 * files don't exist at the directory, brand and locale specified then
	 * the "common" brand will be used instead. If the file still don't exist
	 * then the files are skipped and will rely on XML XI Include Fallbacks.
	 *
	 * @param contentSpec The Content Spec that is used to build the book.
	 * @param commonContentLocale The Common_Content Locale to be used.
	 * @param commonContentDirectory The Common_Content directory.
	 * @param files The Mapping of file names to file contents to be used to build
	 * the ZIP archive.
	 */
	protected void addPublicanCommonContentToBook(final ContentSpec contentSpec, final String commonContentLocale,
			final String commonContentDirectory, final Map<String, byte[]> files)
	{
		final String brand = contentSpec.getBrand() == null ? BuilderConstants.BRAND_DEFAULT : contentSpec.getBrand();
		
		final String brandDir = commonContentDirectory + (commonContentDirectory.endsWith("/") ? "" : "/")
				+ brand + File.separator + commonContentLocale + File.separator;
		final String commonBrandDir = commonContentDirectory + (commonContentDirectory.endsWith("/") ? "" : "/")
				+ BuilderConstants.BRAND_DEFAULT + File.separator + commonContentLocale + File.separator;
		
		/*
		 * We need to pull the Conventions.xml, Feedback.xml & Legal_Notice.xml
		 * from the publican Common_Content directory. First we need to check if
		 * the files exist for the brand, if they don't then we need to check the
		 * common directory.
		 */
		
		for (final String fileName : BuilderConstants.COMMON_CONTENT_FILES)
		{
			final File brandFile = new File(brandDir + fileName);
			
			try
			{
				if (brandFile.exists() && brandFile.isFile())
				{
					final String file = FileUtilities.readFileContents(brandFile);
					if (file != null)
					{
						files.put(BOOK_LOCALE_FOLDER + fileName, file.getBytes("UTF-8"));
					}
				}
				else
				{
					final File commonBrandFile = new File(commonBrandDir + fileName);
					if (commonBrandFile.exists() && commonBrandFile.isFile())
					{
						final String file = FileUtilities.readFileContents(commonBrandFile);
						if (file != null)
						{
							files.put(BOOK_LOCALE_FOLDER + fileName, file.getBytes("UTF-8"));
						}
					}
				}
			}
			catch (UnsupportedEncodingException e)
			{
				log.debug(e.getMessage());
			}
		}
		
	}

	/**
	 * Creates all the chapters/appendixes for a book and generates the section/topic data inside of each chapter.
	 *
	 * @param files The mapping of File Names/Locations to actual file content.
	 * @param bookXIncludes The string based list of XIncludes to be used in the book.xml
	 * @param level The level to build the chapter from.
	 * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
	 */
	protected void createRootElementXML(final Map<String, byte[]> files, final StringBuffer bookXIncludes, final Level level, final boolean useFixedUrls)
	{
		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return;
		}

		/* Get the name of the element based on the type */
		final String elementName = level.getType() == LevelType.PROCESS ? "chapter" : level.getType().getTitle().toLowerCase();

		Document chapter = null;
		try
		{
			chapter = XMLUtilities.convertStringToDocument("<" + elementName + "></" + elementName + ">");
		}
		catch (SAXException ex)
		{
			/* Exit since we shouldn't fail at converting a basic chapter */
			log.debug(ExceptionUtilities.getStackTrace(ex));
			System.exit(-1);
		}

		// Create the title
		final String chapterName = level.getUniqueLinkId(useFixedUrls) + ".xml";

		// Add to the list of XIncludes that will get set in the book.xml
		bookXIncludes.append("\t<xi:include href=\"" + chapterName + "\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");

		//Create the chapter.xml
		final Element titleNode = chapter.createElement("title");
		titleNode.setTextContent(level.getTitle());
		chapter.getDocumentElement().appendChild(titleNode);
		chapter.getDocumentElement().setAttribute("id", level.getUniqueLinkId(useFixedUrls));
		createSectionXML(files, level, chapter, chapter.getDocumentElement(), useFixedUrls, elementName);

		// Add the boiler plate text and add the chapter to the book
		final String chapterString = DocBookUtilities.addXMLBoilerplate(XMLUtilities.convertNodeToString(chapter, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent", elementName);
		try
		{
			files.put(BOOK_LOCALE_FOLDER + chapterName, chapterString.getBytes("UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			/* UTF-8 is a valid format so this should exception should never get thrown */
			log.error(e.getMessage());
		}
	}

	/**
	 * Creates all the chapters/appendixes for a book that are contained within another part/chapter/appendix
	 * and generates the section/topic data inside of each chapter.
	 *
	 * @param files The mapping of File Names/Locations to actual file content.
	 * @param doc The document object to add the child level content to.
	 * @param parentNode The node of the parent in the document to add to.
	 * @param level The level to build the chapter from.
	 * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
	 */
	protected void createSubRootElementXML(final Map<String, byte[]> files, final Document doc, final Node parentNode,
			final Level level, final boolean useFixedUrls)
	{
		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return;
		}

		/* Get the name of the element based on the type */
		final String elementName = level.getType() == LevelType.PROCESS ? "chapter" : level.getType().getTitle().toLowerCase();

		Document chapter = null;
		try
		{
			chapter = XMLUtilities.convertStringToDocument("<" + elementName + "></" + elementName + ">");
		}
		catch (SAXException ex)
		{
			/* Exit since we shouldn't fail at converting a basic chapter */
			log.debug(ExceptionUtilities.getStackTrace(ex));
			System.exit(-1);
		}

		// Create the title
		final String chapterName = level.getUniqueLinkId(useFixedUrls) + ".xml";

		// Add to the list of XIncludes that will get set in the book.xml
		final Element xiInclude = doc.createElement("xi:include");
		xiInclude.setAttribute("href", chapterName);
		xiInclude.setAttribute("xmlns:xi", "http://www.w3.org/2001/XInclude");
		parentNode.appendChild(xiInclude);

		//Create the chapter.xml
		final Element titleNode = chapter.createElement("title");
		titleNode.setTextContent(level.getTitle());
		chapter.getDocumentElement().appendChild(titleNode);
		chapter.getDocumentElement().setAttribute("id", level.getUniqueLinkId(useFixedUrls));
		createSectionXML(files, level, chapter, chapter.getDocumentElement(), useFixedUrls, elementName);

		// Add the boiler plate text and add the chapter to the book
		final String chapterString = DocBookUtilities.addXMLBoilerplate(XMLUtilities.convertNodeToString(chapter, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent", elementName);
		try
		{
			files.put(BOOK_LOCALE_FOLDER + chapterName, chapterString.getBytes("UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			/* UTF-8 is a valid format so this should exception should never get thrown */
			log.error(e.getMessage());
		}
	}

	/**
	 * Creates the section component of a chapter.xml for a specific ContentLevel.
	 *
	 * @param files The mapping of File Names/Locations to actual file content.
	 * @param level The section level object to get content from.
	 * @param chapter The chapter document object that this section is to be added to.
	 * @param parentNode The parent XML node of this section.
	 * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
	 * @param rootElementName The root element name for this section (ie chapter, section, appendix).
	 */
	@SuppressWarnings("unchecked")
	protected void createSectionXML(final Map<String, byte[]> files, final Level level, final Document chapter, final Element parentNode,
			final boolean useFixedUrls, final String rootElementName)
	{
		final LinkedList<org.jboss.pressgangccms.contentspec.Node> levelData = level.getChildNodes();

		// Add the section and topics for this level to the chapter.xml
		for (final org.jboss.pressgangccms.contentspec.Node node : levelData)
		{

			// Check if the app should be shutdown
			if (isShuttingDown.get())
			{
				return;
			}

			if (node instanceof Level && node.getParent() != null && (((Level)node).getParent().getType() == LevelType.BASE || ((Level)node).getParent().getType() == LevelType.PART))
			{
				final Level childLevel = (Level) node;

				// Create a new file for the Chapter/Appendix
				createSubRootElementXML(files, chapter, parentNode, childLevel, useFixedUrls);
			}
			else if (node instanceof Level)
			{
				final Level childLevel = (Level) node;

				// Create the section and its title
				final Element sectionNode = chapter.createElement("section");
				final Element sectionTitleNode = chapter.createElement("title");
				sectionTitleNode.setTextContent(childLevel.getTitle());
				sectionNode.appendChild(sectionTitleNode);
				sectionNode.setAttribute("id", childLevel.getUniqueLinkId(useFixedUrls));

				// Ignore sections that have no spec topics
				if (!childLevel.hasSpecTopics())
				{
					if (docbookBuildingOptions.isAllowEmptySections())
					{
						Element warning = chapter.createElement("warning");
						warning.setTextContent("No Content");
						sectionNode.appendChild(warning);
					}
					else
					{
						continue;
					}
				}
				else
				{
					// Add this sections child sections/topics
					createSectionXML(files, childLevel, chapter, sectionNode, useFixedUrls, rootElementName);
				}

				parentNode.appendChild(sectionNode);
			}
			else if (node instanceof SpecTopic)
			{
				final SpecTopic specTopic = (SpecTopic) node;
				String topicFileName;

				final T topic = (T) specTopic.getTopic();
				if (topic != null)
				{
					if (topic instanceof RESTTranslatedTopicV1)
					{
						if (useFixedUrls)
						{
							topicFileName = ComponentTranslatedTopicV1.returnXrefPropertyOrId((RESTTranslatedTopicV1) topic, CommonConstants.FIXED_URL_PROP_TAG_ID);
						}
						else
						{
							topicFileName = ComponentTranslatedTopicV1.returnXRefID((RESTTranslatedTopicV1) topic);
						}
					}
					else
					{
						if (useFixedUrls)
						{
							topicFileName = ComponentTopicV1.returnXrefPropertyOrId((RESTTopicV1) topic, CommonConstants.FIXED_URL_PROP_TAG_ID);
						}
						else
						{
							topicFileName = ComponentTopicV1.returnXRefID((RESTTopicV1) topic);
						}
					}

					if (specTopic.getDuplicateId() != null)
					{
						topicFileName += "-" + specTopic.getDuplicateId();
					}

					topicFileName += ".xml";

					final Element topicNode = chapter.createElement("xi:include");
					topicNode.setAttribute("href", "topics/" + topicFileName);
					topicNode.setAttribute("xmlns:xi", "http://www.w3.org/2001/XInclude");
					parentNode.appendChild(topicNode);

					final String topicXML = DocBookUtilities.addXMLBoilerplate(XMLUtilities.convertNodeToString(specTopic.getXmlDocument(), verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent", rootElementName);
					try
					{
						files.put(BOOK_TOPICS_FOLDER + topicFileName, topicXML.getBytes("UTF-8"));
					}
					catch (UnsupportedEncodingException e)
					{
						/* UTF-8 is a valid format so this should exception should never get thrown */
						log.error(e.getMessage());
					}
				}
			}
		}
	}

	/**
	 * Adds all the images found using the {@link #processImageLocations()} method
	 * to the files map that will alter be turned into a ZIP archive.
	 *
	 * @param files The mapping of File Names/Locations to actual file content.
	 * @param locale The locale for the book.
	 * @throws InvalidParameterException If an error occurred during a REST API call.
	 * @throws InternalProcessingException If an error occurred during a REST API call.
	 */
	private void addImagesToBook(final HashMap<String, byte[]> files, final String locale)
			throws InvalidParameterException, InternalProcessingException
	{
		/* Load the database constants */
		final byte[] failpenguinPng = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.FAILPENGUIN_PNG_ID, "").getValue();

		/* download the image files that were identified in the processing stage */
		float imageProgress = 0;
		final float imageTotal = this.imageLocations.size();
		final int showPercent = 5;
		int lastPercent = 0;

		for (final TopicImageData<T, U> imageLocation : this.imageLocations)
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get())
			{
				return;
			}

			boolean success = false;

			final int extensionIndex = imageLocation.getImageName().lastIndexOf(".");
			final int pathIndex = imageLocation.getImageName().lastIndexOf("/");
			final int hypenIndex = imageLocation.getImageName().lastIndexOf("-");

			if (/* characters were found */
					extensionIndex != -1 && pathIndex != -1
					/* the path character was found before the extension */
					&& extensionIndex > pathIndex)
			{
				try
				{
					/*
					 * The file name minus the extension should be an integer
					 * that references an ImageFile record ID.
					 */
					final String imageID;
					if (hypenIndex != -1)
					{
						imageID = imageLocation.getImageName().substring(pathIndex + 1, Math.min(extensionIndex, hypenIndex));
					}
					else
					{
						imageID = imageLocation.getImageName().substring(pathIndex + 1, extensionIndex);
					}

					/*
					 * If the image is the failpenguin the that means that an error has
					 * already occurred most likely from not specifying an image file at
					 * all.
					 */
					if (imageID.equals("failpenguinPng"))
					{
						success = false;
						errorDatabase.addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES, "No image filename specified. Must be in the format [ImageFileID].extension e.g. 123.png, or images/321.jpg");
					}
					else
					{
						/* Expand the Language Images */
						final ExpandDataTrunk expand = new ExpandDataTrunk();
						final ExpandDataTrunk expandLanguages = new ExpandDataTrunk(new ExpandDataDetails(RESTImageV1.LANGUAGEIMAGES_NAME));

						expandLanguages.setBranches(CollectionUtilities.toArrayList(new ExpandDataTrunk(new ExpandDataDetails(RESTLanguageImageV1.IMAGEDATA_NAME))));
						expand.setBranches(CollectionUtilities.toArrayList(expandLanguages));

						final String expandString = mapper.writeValueAsString(expand);

						final RESTImageV1 imageFile;
						imageFile = restManager.getRESTClient().getJSONImage(Integer.parseInt(imageID), expandString);
						/*if (imageLocation.getRevision() == null)
						{
							imageFile = restManager.getRESTClient().getJSONImage(Integer.parseInt(imageID), expandString);
						}
						else
						{
							imageFile = restManager.getRESTClient().getJSONImageRevision(Integer.parseInt(imageID), imageLocation.getRevision(), expandString);
						}*/

						/* Find the image that matches this locale. If the locale isn't found then use the default locale */
						RESTLanguageImageV1 langaugeImageFile = null;
						if (imageFile.getLanguageImages_OTM() != null && imageFile.getLanguageImages_OTM().getItems() != null)
						{
							for (final RESTLanguageImageV1 image : imageFile.getLanguageImages_OTM().getItems())
							{
								if (image.getLocale().equals(locale))
								{
									langaugeImageFile = image;
								}
								else if (image.getLocale().equals(defaultLocale) && langaugeImageFile == null)
								{
									langaugeImageFile = image;
								}
							}
						}

						if (langaugeImageFile != null && langaugeImageFile.getImageData() != null)
						{
							success = true;
							files.put(BOOK_LOCALE_FOLDER + imageLocation.getImageName(), langaugeImageFile.getImageData());
						}
						else
						{
							errorDatabase.addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES, "ImageFile ID " + imageID + " from image location " + imageLocation.getImageName() + " was not found!");
							log.error("ImageFile ID " + imageID + " from image location " + imageLocation.getImageName() + " was not found!");
						}
					}
				}
				catch (final NumberFormatException ex)
				{
					success = false;
					errorDatabase.addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES, imageLocation.getImageName() + " is not a valid image. Must be in the format [ImageFileID].extension e.g. 123.png, or images/321.jpg");
					log.debug(ExceptionUtilities.getStackTrace(ex));
				}
				catch (final Exception ex)
				{
					success = false;
					errorDatabase.addError(imageLocation.getTopic(), ErrorType.INVALID_IMAGES, imageLocation.getImageName() + " is not a valid image. Must be in the format [ImageFileID].extension e.g. 123.png, or images/321.jpg");
					log.debug(ExceptionUtilities.getStackTrace(ex));
				}
			}

			/* put in a place holder */
			if (!success)
			{
				files.put(BOOK_LOCALE_FOLDER + imageLocation.getImageName(), failpenguinPng);
			}

			final int progress = Math.round(imageProgress / imageTotal * 100);
			if (progress - lastPercent >= showPercent)
			{
				lastPercent = progress;
				log.info("\tDownloading Images " + Math.round(progress) + "% done");
			}

			++imageProgress;
		}
	}

	/**
	 * Builds the Author_Group.xml using the assigned writers for topics inside
	 * of the content specification.
	 *
	 * @param contentSpec The content spec used to build the book.
	 * @param files The mapping of File Names/Locations to actual file content.
	 * @throws InvalidParameterException If an error occurred during a REST API call.
	 * @throws InternalProcessingException If an error occurred during a REST API call.
	 */
	@SuppressWarnings("unchecked")
	private void buildAuthorGroup(final ContentSpec contentSpec, final Map<String, byte[]> files)
			throws InvalidParameterException, InternalProcessingException
	{
		log.info("\tBuilding Author_Group.xml");

		// Setup Author_Group.xml
		final String authorGroupXml = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.AUTHOR_GROUP_XML_ID, "").getValue();
		String fixedAuthorGroupXml = authorGroupXml;
		Document authorDoc = null;
		try
		{
			authorDoc = XMLUtilities.convertStringToDocument(fixedAuthorGroupXml);
		}
		catch (SAXException ex)
		{
			/* Exit since we shouldn't fail at converting the basic author group */
			log.debug(ExceptionUtilities.getStackTrace(ex));
			System.exit(-1);
		}
		final LinkedHashMap<Integer, AuthorInformation> authorIDtoAuthor = new LinkedHashMap<Integer, AuthorInformation>();

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return;
		}

		// Get the mapping of authors using the topics inside the content spec
		for (final Integer topicId : specDatabase.getTopicIds())
		{
			final T topic = (T) specDatabase.getSpecTopicsForTopicID(topicId).get(0).getTopic();
			final List<RESTTagV1> authorTags;
			if (topic instanceof RESTTranslatedTopicV1)
			{
				authorTags = ComponentTranslatedTopicV1.returnTagsInCategoriesByID(topic, CollectionUtilities.toArrayList(CSConstants.WRITER_CATEGORY_ID));
			}
			else
			{
				authorTags = ComponentTopicV1.returnTagsInCategoriesByID(topic, CollectionUtilities.toArrayList(CSConstants.WRITER_CATEGORY_ID));
			}
			if (authorTags.size() > 0)
			{
				for (final RESTTagV1 author : authorTags)
				{
					if (!authorIDtoAuthor.containsKey(author.getId()))
					{
						final AuthorInformation authorInfo = reader.getAuthorInformation(author.getId());
						if (authorInfo != null)
						{
							authorIDtoAuthor.put(author.getId(), authorInfo);
						}
					}
				}
			}
		}

		/* Sort and make sure duplicate authors don't exist */
		final Set<AuthorInformation> authors = new TreeSet<AuthorInformation>(new AuthorInformationComparator());
		for (final Integer authorId : authorIDtoAuthor.keySet())
		{
			final AuthorInformation authorInfo = authorIDtoAuthor.get(authorId);
			if (authorInfo != null)
			{
				authors.add(authorInfo);
			}
		}

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return;
		}

		// If one or more authors were found then remove the default and attempt to add them
		if (!authors.isEmpty())
		{
			// Clear the template data
			XMLUtilities.emptyNode(authorDoc.getDocumentElement());
			boolean insertedAuthor = false;

			// For each author attempt to find the author information records and populate Author_Group.xml.
			for (final AuthorInformation authorInfo : authors)
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get())
				{
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
				if (authorInfo.getOrganization() != null)
				{
					final Element affiliationEle = authorDoc.createElement("affiliation");
					final Element orgEle = authorDoc.createElement("orgname");
					orgEle.setTextContent(authorInfo.getOrganization());
					affiliationEle.appendChild(orgEle);
					if (authorInfo.getOrgDivision() != null)
					{
						final Element orgDivisionEle = authorDoc.createElement("orgdiv");
						orgDivisionEle.setTextContent(authorInfo.getOrgDivision());
						affiliationEle.appendChild(orgDivisionEle);
					}
					authorEle.appendChild(affiliationEle);
				}

				// Add an email if one exists
				if (authorInfo.getEmail() != null)
				{
					Element emailEle = authorDoc.createElement("email");
					emailEle.setTextContent(authorInfo.getEmail());
					authorEle.appendChild(emailEle);
				}
				authorDoc.getDocumentElement().appendChild(authorEle);
				insertedAuthor = true;
			}

			// If no authors were inserted then use a default value
			// Note: This should never happen but is used as a safety measure
			if (!insertedAuthor && contentSpec.getOutputStyle().equals(CSConstants.SKYNET_OUTPUT_FORMAT))
			{
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
			}
			else if (!insertedAuthor)
			{
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
		fixedAuthorGroupXml = DocBookUtilities.addXMLBoilerplate(XMLUtilities.convertNodeToString(authorDoc, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent", "authorgroup");
		try
		{
			files.put(BOOK_LOCALE_FOLDER + "Author_Group.xml", fixedAuthorGroupXml.getBytes("UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			/* UTF-8 is a valid format so this should exception should never get thrown */
			log.error(e.getMessage());
		}
	}

	/**
	 * Builds the revision history using the requester of the build.
	 *
	 * @param requester The user who requested the build action.
	 * @param contentSpec The content spec object used to build the book.
	 * @param files The mapping of File Names/Locations to actual file content.
	 * @throws InternalProcessingException If an error occurred during a REST API call.
	 * @throws InvalidParameterException If an error occurred during a REST API call.
	 */
	private void buildRevisionHistory(final ContentSpec contentSpec, final RESTUserV1 requester, final Map<String, byte[]> files)
			throws InvalidParameterException, InternalProcessingException
	{
		log.info("\tBuilding Revision_History.xml");

		final DateFormat dateFormatter = new SimpleDateFormat(BuilderConstants.REV_DATE_STRING_FORMAT);

		// Replace the basic injection data inside the revision history
		final String revisionHistoryXml = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.REVISION_HISTORY_XML_ID, "").getValue();
		String fixedRevisionHistoryXml = revisionHistoryXml.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		fixedRevisionHistoryXml = fixedRevisionHistoryXml.replaceAll(BuilderConstants.REV_DATE_FORMAT_REGEX, dateFormatter.format(buildDate));

		final List<RESTTagV1> authorList = requester == null ? new ArrayList<RESTTagV1>() : reader.getTagsByName(requester.getName());
		final Document revHistoryDoc;

		// Check if the app should be shutdown
		if (isShuttingDown.get())
		{
			return;
		}

		// An assigned writer tag exists for the User so check if there is an AuthorInformation tuple for that writer
		if (authorList.size() == 1)
		{
			AuthorInformation authorInfo = reader.getAuthorInformation(authorList.get(0).getId());
			if (authorInfo != null)
			{
				revHistoryDoc = generateRevision(contentSpec, fixedRevisionHistoryXml, authorInfo, requester);
			}
			else
			{
				// No AuthorInformation so Use the default value
				authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME, BuilderConstants.DEFAULT_AUTHOR_LASTNAME, BuilderConstants.DEFAULT_EMAIL);
				revHistoryDoc = generateRevision(contentSpec, fixedRevisionHistoryXml, authorInfo, requester);
			}
		}
		// No assigned writer exists for the uploader so use default values
		else
		{
			final AuthorInformation authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME, BuilderConstants.DEFAULT_AUTHOR_LASTNAME, BuilderConstants.DEFAULT_EMAIL);
			revHistoryDoc = generateRevision(contentSpec, fixedRevisionHistoryXml, authorInfo, requester);
		}

		// Add the revision history to the book
		fixedRevisionHistoryXml = DocBookUtilities.addXMLBoilerplate(XMLUtilities.convertNodeToString(revHistoryDoc, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent", "appendix");
		try
		{
			files.put(BOOK_LOCALE_FOLDER + "Revision_History.xml", fixedRevisionHistoryXml.getBytes("UTF-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			/* UTF-8 is a valid format so this should exception should never get thrown */
			log.error(e.getMessage());
		}
	}

	/**
	 * Fills in the information required inside of a revision tag, for the Revision_History.xml file.
	 *
	 * @param contentSpec The content spec to generate the revisions for.
	 * @param xmlDocString An XML document represented as a string that contains key regex expressions.
	 * @param authorInfo An AuthorInformation entity object containing the details for who requested the build.
	 * @param requester The user object for the build request.
	 */
	private Document generateRevision(final ContentSpec contentSpec, final String xmlDocString, final AuthorInformation authorInfo,
			final RESTUserV1 requester)
	{
		if (authorInfo == null)
		{
			return null;
		}

		// Replace all of the regex inside the xml document
		String tempXmlDocString = new String(xmlDocString);
		tempXmlDocString = tempXmlDocString.replaceAll(BuilderConstants.AUTHOR_FIRST_NAME_REGEX, authorInfo.getFirstName());
		tempXmlDocString = tempXmlDocString.replaceAll(BuilderConstants.AUTHOR_SURNAME_REGEX, authorInfo.getLastName());
		tempXmlDocString = tempXmlDocString.replaceAll(BuilderConstants.AUTHOR_EMAIL_REGEX, authorInfo.getEmail() == null ? BuilderConstants.DEFAULT_EMAIL : authorInfo.getEmail());

		// No regex should exist so now convert it to a Document object
		Document doc = null;
		try
		{
			doc = XMLUtilities.convertStringToDocument(tempXmlDocString);
		}
		catch (SAXException ex)
		{
			/* Exit since we shouldn't fail at converting the basic revision history */
			log.debug(ExceptionUtilities.getStackTrace(ex));
			System.exit(-1);
		}
		doc.getDocumentElement().setAttribute("id", "appe-" + escapedTitle + "-Revision_History");

		final NodeList simplelistList = doc.getDocumentElement().getElementsByTagName("simplelist");
		final Element simplelist;

		// Find the first simplelist
		if (simplelistList.getLength() >= 1)
		{
			simplelist = (Element) simplelistList.item(0);
		}
		else
		{
			// The document should always have at least one revision tag inside of it.
			final Element revision = (Element) doc.getDocumentElement().getElementsByTagName("revision").item(0);
			simplelist = doc.createElement("simplelist");
			revision.appendChild(simplelist);
		}

		// Add the revision information
		final Element listMemberEle = doc.createElement("member");

		if (contentSpec.getId() > 0)
		{
			listMemberEle.setTextContent(String.format(BuilderConstants.BUILT_MSG, contentSpec.getId(), reader.getLatestCSRevById(contentSpec.getId())) + (authorInfo.getAuthorId() > 0 ? (" by " + requester.getName()) : ""));
		}
		else
		{
			listMemberEle.setTextContent(BuilderConstants.BUILT_FILE_MSG + (authorInfo.getAuthorId() > 0 ? (" by " + requester.getName()) : ""));
		}

		simplelist.appendChild(listMemberEle);
		return doc;
	}

	/**
	 * Builds the Error Chapter that contains all warnings and
	 * errors. It also builds a glossary to define most of the
	 * error messages.
	 *
	 * @param locale The locale of the book.
	 * @return A docbook formatted string representation of the
	 * error chapter.
	 */
	private String buildErrorChapter(final String locale)
	{
		log.info("\tBuilding Error Chapter");

		String errorItemizedLists = "";

		if (errorDatabase.hasItems(locale))
		{	
			for (final TopicErrorData<T, U> topicErrorData : errorDatabase.getErrors(locale))
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get())
				{
					return null;
				}

				final T topic = topicErrorData.getTopic();

				final List<String> topicErrorItems = new ArrayList<String>();

				final String tags;
				final String url;
				if (topic instanceof RESTTranslatedTopicV1)
				{
					tags = ComponentTranslatedTopicV1.getCommaSeparatedTagList(topic);
					url = ComponentTranslatedTopicV1.returnSkynetURL((RESTTranslatedTopicV1) topic);
				}
				else
				{
					tags = ComponentTopicV1.getCommaSeparatedTagList(topic);
					url = ComponentTopicV1.returnSkynetURL((RESTTopicV1) topic);
				}

				topicErrorItems.add(DocBookUtilities.buildListItem("INFO: " + tags));
				topicErrorItems.add(DocBookUtilities.buildListItem("INFO: <ulink url=\"" + url + "\">Topic URL</ulink>"));

				for (final String error : topicErrorData.getItemsOfType(ErrorLevel.ERROR))
				{
					topicErrorItems.add(DocBookUtilities.buildListItem("ERROR: " + error));
				}

				for (final String warning : topicErrorData.getItemsOfType(ErrorLevel.WARNING))
				{
					topicErrorItems.add(DocBookUtilities.buildListItem("WARNING: " + warning));
				}

				/*
				 * this should never be false, because a topic will only be
				 * listed in the errors collection once a error or warning has
				 * been added. The count of 2 comes from the standard list items
				 * we added above for the tags and url.
				 */
				if (topicErrorItems.size() > 2)
				{
					final String title;
					if (topic instanceof RESTTranslatedTopicV1)
					{
						final RESTTranslatedTopicV1 translatedTopic = (RESTTranslatedTopicV1) topic;
						title = "Topic ID " + translatedTopic.getTopicId() + ", Revision " + translatedTopic.getTopicRevision();
					}
					else
					{
						title = "Topic ID " + topic.getId();
					}
					final String id;
					if (topic instanceof RESTTranslatedTopicV1)
					{
						id = ComponentTranslatedTopicV1.returnErrorXRefID((RESTTranslatedTopicV1) topic);
					}
					else
					{
						id = ComponentTopicV1.returnErrorXRefID((RESTTopicV1) topic);
					}

					errorItemizedLists += DocBookUtilities.wrapListItems(topicErrorItems, title, id);
				}
			}
			
			// Create the glossary
			final String errorGlossary = buildErrorChapterGlossary("Compiler Glossary");
			if (errorGlossary != null)
			{
				errorItemizedLists += errorGlossary;
			}
		}
		else
		{
			errorItemizedLists = "<para>No Errors Found</para>";
		}

		return DocBookUtilities.buildChapter(errorItemizedLists, "Compiler Output");
	}
	
	/**
	 * Builds the Glossary used in the Error Chapter.
	 * 
	 * @param The title for the glossary.
	 * @return A docbook formatted string representation of the
	 * glossary.
	 */
	private String buildErrorChapterGlossary(final String title)
	{
		final StringBuilder glossary = new StringBuilder("<glossary>");

		// Add the title of the glossary
		glossary.append("<title>");
		if (title != null)
		{
			glossary.append(title);
		}
		glossary.append("</title>");

		// Add generic error messages

		// No Content Warning
		glossary.append(DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_EMPTY_TOPIC_XML + "\""),
				DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_NO_CONTENT_TOPIC_DEFINTIION)));

		// Invalid XML entity or element
		glossary.append(DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_INVALID_XML_CONTENT + "\""),
				DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_INVALID_XML_CONTENT_DEFINTIION)));

		// No Content Error
		glossary.append(DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_BAD_XML_STRUCTURE + "\""),
				DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_BAD_XML_STRUCTURE_DEFINTIION)));

		// Invalid Docbook XML Error
		glossary.append(DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_INVALID_TOPIC_XML + "\""),
				DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_INVALID_TOPIC_XML_DEFINTIION)));
		
		// Invalid Injections Error
		glossary.append(DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.ERROR_INVALID_INJECTIONS + "\""),
				DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.ERROR_INVALID_INJECTIONS_DEFINTIION)));

		// Add the glossary terms and definitions
		if (clazz.equals(RESTTranslatedTopicV1.class))
		{
			// Incomplete translation warning
			glossary.append(DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_INCOMPLETE_TRANSLATION + "\""),
					DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_INCOMPLETE_TRANSLATED_TOPIC_DEFINTIION)));
			
			// Untranslated Content warning
			glossary.append(DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_UNTRANSLATED_TOPIC + "\""),
					DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_UNTRANSLATED_TOPIC_DEFINTIION)));
			
			// Non Pushed Translation Content warning
			glossary.append(DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_NONPUSHED_TOPIC + "\""),
					DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_NONPUSHED_TOPIC_DEFINTIION)));
			
			// Old Translation Content warning
			glossary.append(DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_OLD_TRANSLATED_TOPIC + "\""),
					DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_OLD_TRANSLATED_TOPIC_DEFINTIION)));
			
			// Old Untranslated Content warning
			glossary.append(DocBookUtilities.wrapInGlossEntry(DocBookUtilities.wrapInGlossTerm("\"" + BuilderConstants.WARNING_OLD_UNTRANSLATED_TOPIC + "\""),
					DocBookUtilities.wrapInItemizedGlossDef(null, BuilderConstants.WARNING_OLD_UNTRANSLATED_TOPIC_DEFINTIION)));
		}
		
		glossary.append("</glossary>");
		
		return glossary.toString();
	}

	/**
	 * Builds a Report Chapter to be included in the book that
	 * displays a count of different types of errors and then a
	 * table to list the errors, providing links and basic topic
	 * data.
	 *
	 * @param locale The locale to build the report chapter for.
	 * @return The Docbook Report Chapter formatted as a String.
	 */
	private String buildReportChapter(final String locale)
	{
		log.info("\tBuilding Report Chapter");

		String reportChapter = "";

		final List<TopicErrorData<T, U>> noContentTopics = errorDatabase.getErrorsOfType(locale, ErrorType.NO_CONTENT);
		final List<TopicErrorData<T, U>> invalidInjectionTopics = errorDatabase.getErrorsOfType(locale, ErrorType.INVALID_INJECTION);
		final List<TopicErrorData<T, U>> invalidContentTopics = errorDatabase.getErrorsOfType(locale, ErrorType.INVALID_CONTENT);
		final List<TopicErrorData<T, U>> invalidImageTopics = errorDatabase.getErrorsOfType(locale, ErrorType.INVALID_IMAGES);
		final List<TopicErrorData<T, U>> untranslatedTopics = errorDatabase.getErrorsOfType(locale, ErrorType.UNTRANSLATED);
		final List<TopicErrorData<T, U>> incompleteTranslatedTopics = errorDatabase.getErrorsOfType(locale, ErrorType.INCOMPLETE_TRANSLATION);
		final List<TopicErrorData<T, U>> notPushedTranslatedTopics = errorDatabase.getErrorsOfType(locale, ErrorType.NOT_PUSHED_FOR_TRANSLATION);
		final List<TopicErrorData<T, U>> oldTranslatedTopics = errorDatabase.getErrorsOfType(locale, ErrorType.OLD_TRANSLATION);
		final List<TopicErrorData<T, U>> oldUntranslatedTopics = errorDatabase.getErrorsOfType(locale, ErrorType.OLD_UNTRANSLATED);

		final List<String> list = new LinkedList<String>();
		list.add(DocBookUtilities.buildListItem("Total Number of Errors: " + this.getNumErrors()));
		list.add(DocBookUtilities.buildListItem("Total Number of Warnings: " + this.getNumWarnings()));
		list.add(DocBookUtilities.buildListItem("Number of Topics with No Content: " + noContentTopics.size()));
		list.add(DocBookUtilities.buildListItem("Number of Topics with Invalid Injection points: " + invalidInjectionTopics.size()));
		list.add(DocBookUtilities.buildListItem("Number of Topics with Invalid Content: " + invalidContentTopics.size()));
		list.add(DocBookUtilities.buildListItem("Number of Topics with Invalid Image references: " + invalidImageTopics.size()));

		if (clazz.equals(RESTTranslatedTopicV1.class))
		{
			list.add(DocBookUtilities.buildListItem("Number of Topics that haven't been pushed for Translation: " + notPushedTranslatedTopics.size()));
			list.add(DocBookUtilities.buildListItem("Number of Topics that haven't been Translated: " + untranslatedTopics.size()));
			list.add(DocBookUtilities.buildListItem("Number of Topics that have incomplete Translations: " + incompleteTranslatedTopics.size()));
			list.add(DocBookUtilities.buildListItem("Number of Topics that haven't been Translated but are using previous revisions: " + oldUntranslatedTopics.size()));
			list.add(DocBookUtilities.buildListItem("Number of Topics that have been Translated using a previous revision: " + oldTranslatedTopics.size()));
		}

		reportChapter += DocBookUtilities.wrapListItems(list, "Build Statistics");

		final boolean showEditorLinks = this.docbookBuildingOptions.getInsertEditorLinks();

		/* Create the Report Tables */
		reportChapter += ReportUtilities.buildReportTable(noContentTopics, "Topics that have no Content", showEditorLinks, zanataDetails);

		reportChapter += ReportUtilities.buildReportTable(invalidContentTopics, "Topics that have Invalid XML Content", showEditorLinks, zanataDetails);

		reportChapter += ReportUtilities.buildReportTable(invalidInjectionTopics, "Topics that have Invalid Injection points in the XML", showEditorLinks, zanataDetails);

		reportChapter += ReportUtilities.buildReportTable(invalidImageTopics, "Topics that have Invalid Image references in the XML", showEditorLinks, zanataDetails);

		if (clazz.equals(RESTTranslatedTopicV1.class))
		{
			reportChapter += ReportUtilities.buildReportTable(notPushedTranslatedTopics, "Topics that haven't been pushed for Translation", showEditorLinks, zanataDetails);

			reportChapter += ReportUtilities.buildReportTable(untranslatedTopics, "Topics that haven't been Translated", showEditorLinks, zanataDetails);

			reportChapter += ReportUtilities.buildReportTable(incompleteTranslatedTopics, "Topics that have Incomplete Translations", showEditorLinks, zanataDetails);
			
			reportChapter += ReportUtilities.buildReportTable(oldUntranslatedTopics, "Topics that haven't been Translated but are using previous revisions", showEditorLinks, zanataDetails);
			
			reportChapter += ReportUtilities.buildReportTable(oldTranslatedTopics, "Topics that have been Translated using a previous revision", showEditorLinks, zanataDetails);
		}

		return DocBookUtilities.buildChapter(reportChapter, "Status Report");
	}

	/**
	 * Processes the Topics in the SpecDatabase and builds up the
	 * images found within the topics XML. If the image reference
	 * is blank or invalid it is replaced by the fail penguin image.
	 */
	@SuppressWarnings("unchecked")
	private void processImageLocations()
	{
		final List<Integer> topicIds = specDatabase.getTopicIds();
		for (final Integer topicId : topicIds)
		{
			final SpecTopic specTopic = specDatabase.getSpecTopicsForTopicID(topicId).get(0);
			final T topic = (T) specTopic.getTopic();

			/*
			 * Images have to be in the image folder in Publican. Here we loop
			 * through all the imagedata elements and fix up any reference to an
			 * image that is not in the images folder.
			 */
			final List<Node> images = this.getImages(specTopic.getXmlDocument());

			for (final Node imageNode : images)
			{
				final NamedNodeMap attributes = imageNode.getAttributes();
				if (attributes != null)
				{
					final Node fileRefAttribute = attributes.getNamedItem("fileref");

					if (fileRefAttribute != null && (fileRefAttribute.getNodeValue() == null || fileRefAttribute.getNodeValue().isEmpty()))
					{
						fileRefAttribute.setNodeValue("images/failpenguinPng.jpg");
						imageLocations.add(new TopicImageData<T, U>(topic, fileRefAttribute.getNodeValue()));
					}
					else if (fileRefAttribute != null)
					{
						if (specTopic.getRevision() == null)
						{
							if (fileRefAttribute != null && !fileRefAttribute.getNodeValue().startsWith("images/"))
							{
								fileRefAttribute.setNodeValue("images/" + fileRefAttribute.getNodeValue());
							}
							
							imageLocations.add(new TopicImageData<T, U>(topic, fileRefAttribute.getNodeValue()));
						}
						else
						{
							if (fileRefAttribute != null && !fileRefAttribute.getNodeValue().startsWith("images/"))
							{
								fileRefAttribute.setNodeValue("images/" + fileRefAttribute.getNodeValue());
							}
							
							/* Add the revision number to the name */
							final String imageFileRef = fileRefAttribute.getNodeValue();
							final int extensionIndex = imageFileRef.lastIndexOf(".");
							final String fixedImageFileRef;
							if (extensionIndex != -1)
							{
								fixedImageFileRef = imageFileRef.substring(0, extensionIndex) + "-" + specTopic.getRevision() + imageFileRef.substring(extensionIndex);
							}
							else
							{
								fixedImageFileRef = imageFileRef + "-" + specTopic.getRevision();
							}
							
							fileRefAttribute.setNodeValue(fixedImageFileRef);
							
							imageLocations.add(new TopicImageData<T, U>(topic, fileRefAttribute.getNodeValue(), specTopic.getRevision()));
						}
					}
				}
			}
		}
	}

	/**
	 * @param node
	 *            The node to search for imagedata elements in
	 * @return Search any imagedata elements found in the supplied node
	 */
	private List<Node> getImages(final Node node)
	{
		final List<Node> images = new ArrayList<Node>();

		/* Ensure that the node isn't null */
		if (node == null)
		{
			return images;
		}

		final NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); ++i)
		{
			final Node child = children.item(i);

			if (child.getNodeName().equals("imagedata"))
			{
				images.add(child);
			}
			else
			{
				images.addAll(getImages(child));
			}
		}
		return images;
	}

	/**
	 * Validates the XML after the first set of injections have been processed.
	 *
	 * @param specTopic The topic that is being validated.
	 * @param topicDoc A Document object that holds the Topic's XML
	 * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
	 * @return The validate document or a template if it failed validation.
	 */
	@SuppressWarnings("unchecked")
	private boolean validateTopicXML(final SpecTopic specTopic, final Document topicDoc, final boolean useFixedUrls)
	{
		final T topic = (T) specTopic.getTopic();

		// Validate the topic against its DTD/Schema
		final SAXXMLValidator validator = new SAXXMLValidator();
		if (!validator.validateXML(topicDoc, BuilderConstants.ROCBOOK_45_DTD, rocbookdtd.getValue()))
		{
			final String topicXMLErrorTemplate = buildTopicErrorTemplate(topic, errorInvalidValidationTopic.getValue());

			final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(XMLUtilities.convertNodeToString(topicDoc, verbatimElements, inlineElements, contentsInlineElements, true));
			errorDatabase.addError(topic, ErrorType.INVALID_CONTENT, BuilderConstants.ERROR_INVALID_TOPIC_XML + " The error is <emphasis>" + validator.getErrorText() + "</emphasis>. The processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");
			setSpecTopicXMLForError(specTopic, topicXMLErrorTemplate, useFixedUrls);

			return false;
		}

		return true;
	}

	/**
	 * Build up an error template by replacing key pointers in
	 * the template. The pointers that get replaced are:
	 *
	 * {@code
	 * <!-- Inject TopicTitle -->
	 * <!-- Inject TopicID -->
	 * <!-- Inject ErrorXREF -->}
	 *
	 * @param topic The topic to generate the error template for.
	 * @param errorTemplate The pre processed error template.
	 * @return The input error template with the pointers replaced
	 * with values from the topic.
	 */
	private String buildTopicErrorTemplate(final T topic, final String errorTemplate)
	{
		String topicXMLErrorTemplate = new String(errorTemplate);
		topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_TITLE_REGEX, topic.getTitle());

		// Set the topic id in the error
		final String errorXRefID;
		if (topic instanceof RESTTranslatedTopicV1)
		{
			final Integer topicId = ((RESTTranslatedTopicV1) topic).getTopicId();
			final Integer topicRevision = ((RESTTranslatedTopicV1) topic).getTopicRevision();
			topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, topicId + ", Revision " + topicRevision);
			errorXRefID = ComponentTranslatedTopicV1.returnErrorXRefID((RESTTranslatedTopicV1) topic);
		}
		else
		{
			topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topic.getId()));
			errorXRefID = ComponentTopicV1.returnErrorXRefID((RESTTopicV1) topic);
		}

		// Add the link to the errors page. If the errors page is suppressed then remove the injection point.
		if (!docbookBuildingOptions.getSuppressErrorsPage())
		{
			topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "<para>Please review the compiler error "
					+ "for <xref linkend=\"" + errorXRefID + "\"/> for more detailed information.</para>");
		}
		else
		{
			topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
		}

		return topicXMLErrorTemplate;
	}

	/**
	 * Sets the XML of the topic to the specified error template.
	 *
	 * @param topic The topic to be updated as having an error.
	 * @param template The template for the Error Message.
	 * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
	 * @return The Document Object that is intialised using the topic and error template.
	 */
	private Document setTopicXMLForError(final T topic, final String template, final boolean useFixedUrls)
	{
		Document doc = null;
		try
		{
			doc = XMLUtilities.convertStringToDocument(template);
		}
		catch (SAXException ex)
		{
			/* Exit since we shouldn't fail at converting a basic template */
			log.debug(ExceptionUtilities.getStackTrace(ex));
			System.exit(-1);
		}
		DocBookUtilities.setSectionTitle(topic.getTitle(), doc);
		processTopicID(topic, doc, useFixedUrls);
		return doc;
	}

	/**
	 * Sets the XML of the topic in the content spec to the
	 * error template provided.
	 *
	 * @param specTopic The spec topic to be updated as having an error.
	 * @param template The template for the Error Message.
	 * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
	 */
	@SuppressWarnings("unchecked")
	private void setSpecTopicXMLForError(final SpecTopic specTopic, final String template, final boolean useFixedUrls)
	{
		final T topic = (T) specTopic.getTopic();
		
		Document doc = null;
		try
		{
			doc = XMLUtilities.convertStringToDocument(template);
		}
		catch (SAXException ex)
		{
			/* Exit since we shouldn't fail at converting a basic template */
			log.debug(ExceptionUtilities.getStackTrace(ex));
			System.exit(-1);
		}
		specTopic.setXmlDocument(doc);
		DocBookUtilities.setSectionTitle(topic.getTitle(), doc);
		processTopicID(topic, doc, useFixedUrls);
	}

	/**
	 * Sets the topic xref id to the topic database id.
	 *
	 * @param topic The topic to be used to set the id attribute.
	 * @param doc The document object for the topics XML.
	 * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
	 */
	private void processTopicID(final T topic, final Document doc, final boolean useFixedUrls)
	{
		if (useFixedUrls)
		{
			final String errorXRefID;
			if (topic instanceof RESTTranslatedTopicV1)
			{
				errorXRefID = ComponentTranslatedTopicV1.returnXrefPropertyOrId((RESTTranslatedTopicV1) topic, CommonConstants.FIXED_URL_PROP_TAG_ID);
			}
			else
			{
				errorXRefID = ComponentTopicV1.returnXrefPropertyOrId((RESTTopicV1) topic, CommonConstants.FIXED_URL_PROP_TAG_ID);
			}
			doc.getDocumentElement().setAttribute("id", errorXRefID);
		}
		else
		{
			final String errorXRefID;
			if (topic instanceof RESTTranslatedTopicV1)
			{
				errorXRefID = ComponentTranslatedTopicV1.returnXRefID((RESTTranslatedTopicV1) topic);
			}
			else
			{
				errorXRefID = ComponentTopicV1.returnXRefID((RESTTopicV1) topic);
			}
			doc.getDocumentElement().setAttribute("id", errorXRefID);
		}

		final Integer topicId;
		if (topic instanceof RESTTranslatedTopicV1)
		{
			topicId = ((RESTTranslatedTopicV1) topic).getTopicId();
		}
		else
		{
			topicId = topic.getId();
		}
		doc.getDocumentElement().setAttribute("remap", "TID_" + topicId);
	}

	/**
	 * This method does a pass over all the topics returned by the query and
	 * attempts to create unique Fixed URL if one does not already exist.
	 *
	 * @param topics The list of topics to set the Fixed URL's for.
	 * @return True if the fixed url property tags were able to be created for
	 * 			all topics, and false otherwise.
	 */
	private boolean setFixedURLsPass(final RESTTopicCollectionV1 topics)
	{
		log.info("Doing Fixed URL Pass");

		int tries = 0;
		boolean success = false;

		while (tries < BuilderConstants.MAXIMUM_SET_PROP_TAGS_RETRY && !success)
		{

			++tries;

			try
			{
				final RESTTopicCollectionV1 updateTopics = new RESTTopicCollectionV1();

				final Set<String> processedFileNames = new HashSet<String>();

				for (final RESTTopicV1 topic : topics.getItems())
				{
					// Check if the app should be shutdown
					if (isShuttingDown.get())
					{
						return false;
					}

					final RESTPropertyTagV1 existingUniqueURL = ComponentTopicV1.returnProperty(topic, CommonConstants.FIXED_URL_PROP_TAG_ID);

					if (existingUniqueURL == null || !existingUniqueURL.getValid())
					{
						/*
						 * generate the base url
						 */
						String baseUrlName = createURLTitle(topic.getTitle());

						/* generate a unique fixed url */
						String postFix = "";

						for (int uniqueCount = 1; uniqueCount <= BuilderConstants.MAXIMUM_SET_PROP_TAG_NAME_RETRY; ++uniqueCount)
						{
							final String query = "query;propertyTag1=" + CommonConstants.FIXED_URL_PROP_TAG_ID + URLEncoder.encode(" " + baseUrlName + postFix, "UTF-8");
							final RESTTopicCollectionV1 queryTopics = restManager.getRESTClient().getJSONTopicsWithQuery(new PathSegmentImpl(query, false), "");

							if (queryTopics.getSize() != 0)
							{
								postFix = uniqueCount + "";
							}
							else
							{
								break;
							}
						}

						// Check if the app should be shutdown
						if (isShuttingDown.get())
						{
							return false;
						}

						/*
						 * persist the new fixed url, as long as we are not
						 * looking at a landing page topic
						 */
						if (topic.getId() >= 0)
						{
							final RESTPropertyTagV1 propertyTag = new RESTPropertyTagV1();
							propertyTag.setId(CommonConstants.FIXED_URL_PROP_TAG_ID);
							propertyTag.setValue(baseUrlName + postFix);
							propertyTag.setAddItem(true);

							final RESTPropertyTagCollectionV1 updatePropertyTags = new RESTPropertyTagCollectionV1();
							updatePropertyTags.addItem(propertyTag);

							/* remove any old fixed url property tags */
							if (topic.getProperties() != null && topic.getProperties().getItems() != null)
							{
								for (final RESTPropertyTagV1 existing : topic.getProperties().getItems())
								{
									if (existing.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID))
									{
										final RESTPropertyTagV1 removePropertyTag = new RESTPropertyTagV1();
										removePropertyTag.setId(CommonConstants.FIXED_URL_PROP_TAG_ID);
										removePropertyTag.setValue(existing.getValue());
										removePropertyTag.setRemoveItem(true);
										updatePropertyTags.addItem(removePropertyTag);
									}
								}
							}

							final RESTTopicV1 updateTopic = new RESTTopicV1();
							updateTopic.setId(topic.getId());
							updateTopic.explicitSetProperties(updatePropertyTags);

							updateTopics.addItem(updateTopic);
							processedFileNames.add(baseUrlName + postFix);
						}
					}
				}

				if (updateTopics.getItems() != null && updateTopics.getItems().size() != 0)
				{
					restManager.getRESTClient().updateJSONTopics("", updateTopics);
				}

				// Check if the app should be shutdown
				if (isShuttingDown.get())
				{
					return false;
				}

				/* If we got here, then the REST update went ok */
				success = true;

				/* copy the topics fixed url properties to our local collection */
				if (updateTopics.getItems() != null && updateTopics.getItems().size() != 0)
				{
					for (final RESTTopicV1 topicWithFixedUrl : updateTopics.getItems())
					{
						for (final RESTTopicV1 topic : topics.getItems())
						{
							final RESTPropertyTagV1 fixedUrlProp = ComponentTopicV1.returnProperty(topicWithFixedUrl, CommonConstants.FIXED_URL_PROP_TAG_ID);

							if (topic != null && topicWithFixedUrl.getId().equals(topic.getId()))
							{
								RESTPropertyTagCollectionV1 properties = topic.getProperties();
								if (properties == null)
								{
									properties = new RESTPropertyTagCollectionV1();
								}
								else if (properties.getItems() != null)
								{
									// remove any current url's
									final List<RESTPropertyTagV1> propertyTags = new ArrayList<RESTPropertyTagV1>(properties.getItems());
									for (final RESTPropertyTagV1 prop : propertyTags)
									{
										if (prop.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID))
										{
											properties.getItems().remove(prop);
										}
									}
								}

								if (fixedUrlProp != null)
								{
									properties.addItem(fixedUrlProp);
								}
							}

							/*
							 * we also have to copy the fixed urls into the
							 * related topics
							 */
							for (final RESTTopicV1 relatedTopic : topic.getOutgoingRelationships().getItems())
							{
								if (topicWithFixedUrl.getId().equals(relatedTopic.getId()))
								{
									RESTPropertyTagCollectionV1 relatedTopicProperties = relatedTopic.getProperties();
									if (relatedTopicProperties == null)
									{
										relatedTopicProperties = new RESTPropertyTagCollectionV1();
									}
									else if (relatedTopicProperties.getItems() != null)
									{
										// remove any current url's
										final List<RESTPropertyTagV1> relatedTopicPropertyTags = new ArrayList<RESTPropertyTagV1>(relatedTopicProperties.getItems());
										for (final RESTPropertyTagV1 prop : relatedTopicPropertyTags)
										{
											if (prop.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID))
											{
												relatedTopicProperties.getItems().remove(prop);
											}
										}
									}

									if (fixedUrlProp != null)
									{
										relatedTopicProperties.addItem(fixedUrlProp);
									}
								}
							}
						}
					}
				}
			}
			catch (final Exception ex)
			{
				/*
				 * Dump the exception to the command prompt, and restart the
				 * loop
				 */
				log.error(ExceptionUtilities.getStackTrace(ex));
			}
		}

		/* did we blow the try count? */
		return success;
	}

	/**
	 * Creates the URL specific title for a topic or level.
	 *
	 * @param title The title that will be used to create the URL Title.
	 * @return The URL representation of the title.
	 */
	private String createURLTitle(final String title)
	{
		String baseTitle = new String(title);
		/* Remove XML Elements from the Title. */
		baseTitle =  baseTitle.replaceAll("</(.*?)>", "").replaceAll("<(.*?)>", "");

		/*
		 * Check if the title starts with an invalid sequence
		 */
		final NamedPattern invalidSequencePattern = NamedPattern.compile(STARTS_WITH_INVALID_SEQUENCE_RE);
		final NamedMatcher invalidSequenceMatcher = invalidSequencePattern.matcher(baseTitle);

		if (invalidSequenceMatcher.find())
		{
			baseTitle = invalidSequenceMatcher.group("EverythingElse");
		}

		/*
		 * start by removing any prefixed numbers (you can't
		 * start an xref id with numbers)
		 */
		final NamedPattern pattern = NamedPattern.compile(STARTS_WITH_NUMBER_RE);
		final NamedMatcher matcher = pattern.matcher(baseTitle);

		if (matcher.find())
		{
			try
			{
				final String numbers = matcher.group("Numbers");
				final String everythingElse = matcher.group("EverythingElse");

				if (numbers != null && everythingElse != null)
				{
					final NumberFormat formatter = new RuleBasedNumberFormat(RuleBasedNumberFormat.SPELLOUT);
					final String numbersSpeltOut = formatter.format(Integer.parseInt(numbers));
					baseTitle = numbersSpeltOut + everythingElse;

					// Capitalize the first character
					if (baseTitle.length() > 0)
					{
						baseTitle = baseTitle.substring(0, 1).toUpperCase() + baseTitle.substring(1, baseTitle.length());
					}
				}
			}
			catch (final Exception ex)
			{
				log.error(ExceptionUtilities.getStackTrace(ex));
			}
		}

		// Escape the title
		String escapedTitle = DocBookUtilities.escapeTitle(baseTitle);
		while (escapedTitle.indexOf("__") != -1)
		{
			escapedTitle = escapedTitle.replaceAll("__", "_");
		}

		return escapedTitle;
	}
}
