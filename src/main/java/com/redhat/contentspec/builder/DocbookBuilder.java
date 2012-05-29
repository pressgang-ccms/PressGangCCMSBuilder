package com.redhat.contentspec.builder;

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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.resteasy.logging.Logger;
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
import com.redhat.contentspec.ContentSpec;
import com.redhat.contentspec.Level;
import com.redhat.contentspec.SpecTopic;
import com.redhat.contentspec.builder.constants.BuilderConstants;
import com.redhat.contentspec.builder.exception.BuilderCreationException;
import com.redhat.contentspec.builder.utils.DocbookUtils;
import com.redhat.contentspec.builder.utils.SAXXMLValidator;
import com.redhat.contentspec.builder.utils.XMLUtilities;
import com.redhat.contentspec.constants.CSConstants;
import com.redhat.contentspec.entities.AuthorInformation;
import com.redhat.contentspec.entities.InjectionOptions;
import com.redhat.contentspec.enums.LevelType;
import com.redhat.contentspec.interfaces.ShutdownAbleApp;
import com.redhat.contentspec.rest.RESTManager;
import com.redhat.contentspec.rest.RESTReader;
import com.redhat.contentspec.structures.CSDocbookBuildingOptions;
import com.redhat.contentspec.structures.SpecDatabase;
import com.redhat.ecs.commonstructures.Pair;
import com.redhat.ecs.commonutils.CollectionUtilities;
import com.redhat.ecs.commonutils.DocBookUtilities;
import com.redhat.ecs.commonutils.ExceptionUtilities;
import com.redhat.ecs.commonutils.StringUtilities;
import com.redhat.ecs.constants.CommonConstants;
import com.redhat.ecs.services.docbookcompiling.DocbookBuilderConstants;
import com.redhat.topicindex.component.docbookrenderer.structures.TopicErrorData;
import com.redhat.topicindex.component.docbookrenderer.structures.TopicErrorDatabase;
import com.redhat.topicindex.component.docbookrenderer.structures.TopicImageData;
import com.redhat.topicindex.rest.collections.BaseRestCollectionV1;
import com.redhat.topicindex.rest.entities.BaseTopicV1;
import com.redhat.topicindex.rest.entities.BlobConstantV1;
import com.redhat.topicindex.rest.entities.ImageV1;
import com.redhat.topicindex.rest.entities.PropertyTagV1;
import com.redhat.topicindex.rest.entities.StringConstantV1;
import com.redhat.topicindex.rest.entities.TagV1;
import com.redhat.topicindex.rest.entities.TopicV1;
import com.redhat.topicindex.rest.entities.TranslatedTopicV1;
import com.redhat.topicindex.rest.entities.UserV1;
import com.redhat.topicindex.rest.exceptions.InternalProcessingException;
import com.redhat.topicindex.rest.exceptions.InvalidParameterException;

public class DocbookBuilder<T extends BaseTopicV1<T>> implements ShutdownAbleApp
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
	private final BlobConstantV1 rocbookdtd;
	private final String defaultLocale;
	
	private final StringConstantV1 errorEmptyTopic;
	private final StringConstantV1 errorInvalidInjectionTopic;
	private final StringConstantV1 errorInvalidValidationTopic;
	
	private CSDocbookBuildingOptions docbookBuildingOptions;
	private InjectionOptions injectionOptions;
	private Date buildDate;
	
	private String escapedTitle;
	private String locale;
	
	private String BOOK_FOLDER;
	private String BOOK_LOCALE_FOLDER;
	private String BOOK_TOPICS_FOLDER;
	private String BOOK_IMAGES_FOLDER;
	private String BOOK_FILES_FOLDER;
		
	/**
	 * Holds the compiler errors that form the Errors.xml file in the compiled
	 * docbook
	 */
	private TopicErrorDatabase<T> errorDatabase;;
	
	/**
	 * Holds the SpecTopics and their XML that exist within the content specification
	 */
	private SpecDatabase specDatabase;
	
	/**
	 * Holds information on file url locations, which will be downloaded and
	 * included in the docbook zip file
	 */
	private final ArrayList<TopicImageData<T>> imageLocations = new ArrayList<TopicImageData<T>>();
	
	public DocbookBuilder(final RESTManager restManager, final BlobConstantV1 rocbookDtd, final String defaultLocale) throws InvalidParameterException, InternalProcessingException
	{
		reader = restManager.getReader();
		this.restManager = restManager;
		this.rocbookdtd = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.ROCBOOK_DTD_BLOB_ID, "");
		this.errorEmptyTopic = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.CSP_EMPTY_TOPIC_ERROR_XML_ID, "");
		this.errorInvalidInjectionTopic = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.CSP_INVALID_INJECTION_TOPIC_ERROR_XML_ID, "");
		this.errorInvalidValidationTopic = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.CSP_INVALID_VALIDATION_TOPIC_ERROR_XML_ID, "");
		
		this.defaultLocale = defaultLocale;
		
		/*
		 * Get the XML formatting details. These are used to pretty-print
		 * the XML when it is converted into a String.
		 */
		final String verbatimElementsString = System.getProperty(CommonConstants.VERBATIM_XML_ELEMENTS_SYSTEM_PROPERTY) == null ? BuilderConstants.VERBATIM_XML_ELEMENTS : System.getProperty(CommonConstants.VERBATIM_XML_ELEMENTS_SYSTEM_PROPERTY);
		final String inlineElementsString = System.getProperty(CommonConstants.INLINE_XML_ELEMENTS_SYSTEM_PROPERTY) == null ? BuilderConstants.INLINE_XML_ELEMENTS : System.getProperty(CommonConstants.INLINE_XML_ELEMENTS_SYSTEM_PROPERTY);;
		final String contentsInlineElementsString = System.getProperty(CommonConstants.CONTENTS_INLINE_XML_ELEMENTS_SYSTEM_PROPERTY) == null ? BuilderConstants.CONTENTS_INLINE_XML_ELEMENTS : System.getProperty(CommonConstants.CONTENTS_INLINE_XML_ELEMENTS_SYSTEM_PROPERTY);
		
		verbatimElements = CollectionUtilities.toArrayList(verbatimElementsString.split(","));
		inlineElements = CollectionUtilities.toArrayList(inlineElementsString.split(","));
		contentsInlineElements = CollectionUtilities.toArrayList(contentsInlineElementsString.split(","));
	}
	
	@Override
	public void shutdown() {
		isShuttingDown.set(true);
	}

	@Override
	public boolean isShutdown() {
		return shutdown.get();
	}
	
	public int getNumWarnings() {
		int numWarnings = 0;
		if (errorDatabase != null && errorDatabase.getErrors(locale) != null) 
		{
			for (TopicErrorData<T> errorData: errorDatabase.getErrors(locale)) {
				numWarnings += errorData.getItemsOfType(TopicErrorDatabase.WARNING).size();
			}
		}
		return numWarnings;
	}

	public int getNumErrors() {
		int numErrors = 0;
		if (errorDatabase != null && errorDatabase.getErrors(locale) != null) 
		{
			for (TopicErrorData<T> errorData: errorDatabase.getErrors(locale)) {
				numErrors += errorData.getItemsOfType(TopicErrorDatabase.ERROR).size();
			}
		}
		return numErrors;
	}
	
	public HashMap<String, byte[]> buildBook(final ContentSpec contentSpec, final UserV1 requester, final CSDocbookBuildingOptions buildingOptions, final String searchTagsUrl) throws Exception
	{
		if (contentSpec == null) throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
		
		errorDatabase = new TopicErrorDatabase<T>();
		specDatabase = new SpecDatabase();
		
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
		Boolean injection = buildingOptions.getInjection();
		if (injection != null && !injection) injectionType = InjectionOptions.UserType.OFF;
		else if (injection != null && injection) injectionType = InjectionOptions.UserType.ON;
		
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
		
		// second topic pass to set the ids and process injections
		doSpecTopicPass(contentSpec, searchTagsUrl, usedIdAttributes, fixedUrlsSuccess, BuilderConstants.BUILD_NAME);
		
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
	 * Populates the SpecTopicDatabase with the SpecTopics inside the content specification.
	 * It also adds the equivalent real topics to each SpecTopic.
	 * 
	 * @param contentSpec The content spec to populate the database from
	 */
	@SuppressWarnings("unchecked")
	private boolean doPopulateDatabasePass(final ContentSpec contentSpec, final Map<Integer, Set<String>> usedIdAttributes)
	{
		log.info("Doing " + locale + " Populate Database Pass");
		
		/* Calculate the ids of all the topics to get */
		final Set<Integer> topicIds = getTopicIdsFromLevel(contentSpec.getBaseLevel());
		
		final BaseRestCollectionV1<T> topics;
		final boolean fixedUrlsSuccess;
		if (contentSpec.getLocale() == null || contentSpec.getLocale().equals(defaultLocale))
		{
			final BaseRestCollectionV1<TopicV1> normalTopics = reader.getTopicsByIds(CollectionUtilities.toArrayList(topicIds));
			
			/*
			 * assign fixed urls property tags to the topics. If
			 * fixedUrlsSuccess is true, the id of the topic sections,
			 * xfref injection points and file names in the zip file
			 * will be taken from the fixed url property tag, defaulting
			 * back to the TopicID## format if for some reason that
			 * property tag does not exist.
			 */
			fixedUrlsSuccess = setFixedURLsPass(normalTopics);
			
			topics = (BaseRestCollectionV1<T>) normalTopics;
		}
		else
		{
			final BaseRestCollectionV1<TranslatedTopicV1> translatedTopics = reader.getTranslatedTopicsByTopicIds(CollectionUtilities.toArrayList(topicIds), contentSpec.getLocale());
			List<Integer> dummyTopicIds = new ArrayList<Integer>(topicIds);
			
			/* Remove any topic ids for translated topics that were found */
			if (translatedTopics != null && translatedTopics.getItems() != null)
			{
				for (final TranslatedTopicV1 topic : translatedTopics.getItems())
				{
					// Check if the app should be shutdown
					if (isShuttingDown.get()) {
						return false;
					}
					
					dummyTopicIds.remove(topic.getTopicId());
				}
			}
			
			/* Create the dummy translated topics */
			populateDummyTranslatedTopicsPass(translatedTopics, dummyTopicIds);
			
			/* 
			 * Translations should reference an existing historical topic with
			 * the fixed urls set, so we assume this to be the case
			 */
			fixedUrlsSuccess = true;
			
			/* set the topics variable now all initialisation is done */
			topics = (BaseRestCollectionV1<T>) translatedTopics;
		}
		
		/* Add all the levels and topics to the database first */
		addLevelAndTopicsToDatabase(contentSpec.getBaseLevel(), fixedUrlsSuccess);
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return false;
		}
		
		/* Set the duplicate id's for each topic */
		specDatabase.setDatabaseDulicateIds();
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return false;
		}
		
		/* Pass the topics to make sure they are valid */
		doTopicPass(topics, fixedUrlsSuccess, usedIdAttributes);
		
		return fixedUrlsSuccess;
	}
	
	private void addLevelAndTopicsToDatabase(final Level level, final boolean useFixedUrls)
	{
		/* Add the level to the database */
		specDatabase.add(level, createURLTitle(level.getTitle()));
		
		/* Add the topics at this level to the database */
		for (final SpecTopic specTopic : level.getSpecTopics())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return;
			}
			
			specDatabase.add(specTopic, specTopic.getUniqueLinkId(useFixedUrls));
		}
		
		/* Add the child levels to the database */
		
		for (final Level childLevel : level.getChildLevels())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return;
			}
			
			addLevelAndTopicsToDatabase(childLevel, useFixedUrls);
		}
	}
	
	private Set<Integer> getTopicIdsFromLevel(final Level level)
	{
		/* Add the topics at this level to the database */
		final Set<Integer> topicIds = new HashSet<Integer>();
		for (final SpecTopic specTopic : level.getSpecTopics())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return topicIds;
			}
			
			if (specTopic.getDBId() != 0)
				topicIds.add(specTopic.getDBId());
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
	 */
	private void populateDummyTranslatedTopicsPass(final BaseRestCollectionV1<TranslatedTopicV1> topics, final List<Integer> dummyTopicIds) 
	{
		log.info("\tDoing dummy Translated Topic pass");
		
		final BaseRestCollectionV1<TopicV1> dummyTopics = reader.getTopicsByIds(dummyTopicIds);
		
		/* Only continue if we found dummy topics */
		if (dummyTopics == null || dummyTopics.getItems() == null || dummyTopics.getItems().isEmpty()) return;
		
		/* Split the topics up into their different locales */
		final Map<String, Map<Integer, TranslatedTopicV1>> groupedLocaleTopics = new HashMap<String, Map<Integer, TranslatedTopicV1>>();
		
		if (topics != null && topics.getItems() != null)
		{
			for (final TranslatedTopicV1 topic: topics.getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return;
				}
				
				if (!groupedLocaleTopics.containsKey(topic.getLocale()))
					groupedLocaleTopics.put(topic.getLocale(), new HashMap<Integer, TranslatedTopicV1>());
				groupedLocaleTopics.get(topic.getLocale()).put(topic.getTopicId(), topic);
			}
		}
		
		/* create and add the dummy topics per locale */
		for (final String locale : groupedLocaleTopics.keySet())
		{
			final Map<Integer, TranslatedTopicV1> translatedTopicsMap = groupedLocaleTopics.get(locale);
			for (final TopicV1 topic: dummyTopics.getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return;
				}
				
				if (!translatedTopicsMap.containsKey(topic.getId()))
				{
					final TranslatedTopicV1 dummyTopic = createDummyTranslatedTopic(translatedTopicsMap, topic, true, locale);
					
					topics.addItem(dummyTopic);
				}
			}
		}
	}
	
	/**
	 * Creates a dummy translated topic so that a book
	 * can be built using the same relationships as a 
	 * normal build.
	 * 
	 * @param translatedTopicsMap A map of topic ids to translated topics
	 * @param topic The topic to create the dummy topic from
	 * @param expandRelationships Whether the relationships should be expanded for the dummy topic
	 * @return The dummy translated topic
	 */
	private TranslatedTopicV1 createDummyTranslatedTopic(final Map<Integer, TranslatedTopicV1> translatedTopicsMap, final TopicV1 topic, final boolean expandRelationships, final String locale)
	{	
		final TranslatedTopicV1 translatedTopic = new TranslatedTopicV1();
		
		translatedTopic.setId(topic.getId() * -1);
		translatedTopic.setTopicId(topic.getId());
		translatedTopic.setTopicRevision(topic.getRevision().intValue());
		translatedTopic.setTopic(topic);
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
			final BaseRestCollectionV1<TranslatedTopicV1> outgoingRelationships = new BaseRestCollectionV1<TranslatedTopicV1>();
			for (final TopicV1 relatedTopic : topic.getOutgoingRelationships().getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
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
			final BaseRestCollectionV1<TranslatedTopicV1> incomingRelationships = new BaseRestCollectionV1<TranslatedTopicV1>();
			for (final TopicV1 relatedTopic : topic.getIncomingRelationships().getItems())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
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
	
	private void doTopicPass(final BaseRestCollectionV1<T> topics, final boolean fixedUrlsSuccess, final Map<Integer, Set<String>> usedIdAttributes)
	{
		log.info("Doing " + locale + " First topic pass");
		log.info("\tProcessing " + topics.getItems().size() + " Topics");
		
		/* Check that we have some topics to process */
		if (topics != null && topics.getItems() != null)
		{
			
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
				if (topic instanceof TranslatedTopicV1)
				{
					topicId = ((TranslatedTopicV1) topic).getTopicId();
				}
				else
				{
					topicId = topic.getId();
				}
				
				Document topicDoc = null;
				final String topicXML = topic == null ? null : topic.getXml();
				
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return;
				}
				
				boolean xmlValid = true;
				
				// Check that the Topic XML exists and isn't empty
				if (topicXML == null || topicXML.equals(""))
				{
					// Create an empty topic with the topic title from the resource file
					String topicXMLErrorTemplate = errorEmptyTopic.getValue();
					topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_TITLE_REGEX, topic.getTitle());
					
					// Set the topic id in the error
					if (topic instanceof TranslatedTopicV1)
					{
						topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, topicId + ", Revision " + ((TranslatedTopicV1) topic).getTopicRevision());
					}
					else
					{
						topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topicId));
					}
					
					errorDatabase.addWarning(topic, BuilderConstants.EMPTY_TOPIC_XML);
					topicDoc = setTopicXMLForError(topic, topicXMLErrorTemplate, fixedUrlsSuccess);
					xmlValid = false;
				}
				
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return;
				}
				
				/* make sure we have valid XML */
				if (xmlValid)
				{
					try {
						topicDoc = XMLUtilities.convertStringToDocument(topic.getXml());
						
						if (topicDoc != null)
						{
							/* Ensure the topic is wrapped in a section and the title matches the topic */
							DocbookUtils.wrapDocumentInSection(topicDoc);
							DocbookUtils.setSectionTitle(topic.getTitle(), topicDoc);
						}
						else
						{
							String topicXMLErrorTemplate = errorInvalidValidationTopic.getValue();
							topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_TITLE_REGEX, topic.getTitle());
							
							// Set the topic id in the error
							if (topic instanceof TranslatedTopicV1)
							{
								topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, topicId + ", Revision " + ((TranslatedTopicV1) topic).getTopicRevision());
							}
							else
							{
								topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topicId));
							}
							
							// Add the link to the errors page. If the errors page is suppressed then remove the injection point.
							if (!docbookBuildingOptions.getSuppressErrorsPage())
							{
								topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "<para>Please review the compiler error for <xref linkend=\"" + topic.getErrorXRefID() + "\"/> for more detailed information.</para>");
							}
							else
							{
								topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
							}
							
							errorDatabase.addError(topic, BuilderConstants.INVALID_XML_CONTENT);
							topicDoc = setTopicXMLForError(topic, topicXMLErrorTemplate, fixedUrlsSuccess);
						}
					} catch (SAXException ex) {
						String topicXMLErrorTemplate = errorInvalidValidationTopic.getValue();
						topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_TITLE_REGEX, topic.getTitle());
						
						// Set the topic id in the error
						if (topic instanceof TranslatedTopicV1)
						{
							topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, topicId + ", Revision " + ((TranslatedTopicV1) topic).getTopicRevision());
						}
						else
						{
							topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topicId));
						}
						
						// Add the link to the errors page. If the errors page is suppressed then remove the injection point.
						if (!docbookBuildingOptions.getSuppressErrorsPage())
						{
							topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "<para>Please review the compiler error for <xref linkend=\"" + topic.getErrorXRefID() + "\"/> for more detailed information.</para>");
						}
						else
						{
							topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
						}
						
						errorDatabase.addError(topic, BuilderConstants.BAD_XML_STRUCTURE + " " + StringUtilities.escapeForXML(ex.getMessage()));
						topicDoc = setTopicXMLForError(topic, topicXMLErrorTemplate, fixedUrlsSuccess);
					}
				}
				
				/*
				 * Extract the id attributes used in this topic. We'll use this data
				 * in the second pass to make sure that individual topics don't
				 * repeat id attributes.
				 */
				collectIdAttributes(topicId, topicDoc, usedIdAttributes);
				
				processTopicID(topic, topicDoc, fixedUrlsSuccess);
				
				/* Add the document & topic to the database spec topics */
				List<SpecTopic> specTopics = specDatabase.getSpecTopicsForTopicID(topicId);
				for (final SpecTopic specTopic : specTopics)
				{
					// Check if the app should be shutdown
					if (isShuttingDown.get()) {
						return;
					}
					
					specTopic.setTopic(topic.clone(false));
					specTopic.setXmlDocument((Document) topicDoc.cloneNode(true));
				}
	
			}
		}
	}
	
	/**
	 * Loops through each of the spec topics in the database and sets the injections and unique ids for
	 * each id attribute in the Topics XML.
	 * 
	 * @param usedIdAttributes The set of ids that have been used in the set of topics in the content spec.
	 * @param fixedUrlsSuccess If during processing the fixed urls should be used.
	 */
	@SuppressWarnings("unchecked")
	private void doSpecTopicPass(final ContentSpec contentSpec, final String searchTagsUrl, final Map<Integer, Set<String>> usedIdAttributes, final boolean fixedUrlsSuccess, final String buildName)
	{	
		log.info("Doing " + locale + " Spec Topic Pass");
		log.info("\tProcessing " + specDatabase.getAllSpecTopics().size() + " Spec Topics");
		
		final int showPercent = 5;
		final float total = specDatabase.getAllSpecTopics().size();
		float current = 0;
		int lastPercent = 0;
		
		for (final SpecTopic specTopic : specDatabase.getAllSpecTopics())
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
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
			
			final XMLPreProcessor<T> xmlPreProcessor = new XMLPreProcessor<T>();
			boolean valid = true;
			
			if (doc != null)
			{
				/* process the injection points */
				if (injectionOptions.isInjectionAllowed())
				{
	
					final ArrayList<Integer> customInjectionIds = new ArrayList<Integer>();
					final List<Integer> genericInjectionErrors;
					
					final List<Integer> customInjectionErrors = xmlPreProcessor.processInjections(baseLevel, specTopic, customInjectionIds, doc, docbookBuildingOptions, fixedUrlsSuccess);
					
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
						
						genericInjectionErrors = xmlPreProcessor.processGenericInjections(baseLevel, specTopic, doc, customInjectionIds, topicTypeTagDetails, docbookBuildingOptions, fixedUrlsSuccess);
					}
					else
					{
						xmlPreProcessor.processPrerequisiteInjections(specTopic, doc, fixedUrlsSuccess);
						xmlPreProcessor.processPrevRelationshipInjections(specTopic, doc, fixedUrlsSuccess);
						xmlPreProcessor.processNextRelationshipInjections(specTopic, doc, fixedUrlsSuccess);
						xmlPreProcessor.processSeeAlsoInjections(specTopic, doc, fixedUrlsSuccess);
						
						genericInjectionErrors = new ArrayList<Integer>();
					}
					
					final List<Integer> topicContentFragmentsErrors = xmlPreProcessor.processTopicContentFragments(specTopic, doc, docbookBuildingOptions);
					final List<Integer> topicTitleFragmentsErrors = xmlPreProcessor.processTopicTitleFragments(specTopic, doc, docbookBuildingOptions);
					
					// Check if the app should be shutdown
					if (isShuttingDown.get()) {
						return;
					}
	
					if (!customInjectionErrors.isEmpty())
					{
						final String message = "Topic has referenced Topic(s) " + CollectionUtilities.toSeperatedString(customInjectionErrors) + " in a custom injection point that was either not related, or not included in the filter used to build this book.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, message);
						}
						else
						{
							errorDatabase.addError(topic, message);
							valid = false;
						}
					}
					

					if (!genericInjectionErrors.isEmpty())
					{
						final String message = "Topic has related Topic(s) " + CollectionUtilities.toSeperatedString(CollectionUtilities.toAbsIntegerList(genericInjectionErrors)) + " that were not included in the filter used to build this book.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, message);
						}
						else
						{
							errorDatabase.addError(topic, message);
							valid = false;
						}
					}
	
					if (!topicContentFragmentsErrors.isEmpty())
					{
						final String message = "Topic has injected content from Topic(s) " + CollectionUtilities.toSeperatedString(topicContentFragmentsErrors) + " that were not related.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, message);
						}
						else
						{
							errorDatabase.addError(topic, message);
							valid = false;
						}
					}
	
					if (!topicTitleFragmentsErrors.isEmpty())
					{
						final String message = "Topic has injected a title from Topic(s) " + CollectionUtilities.toSeperatedString(topicTitleFragmentsErrors) + " that were not related.";
						if (docbookBuildingOptions.getIgnoreMissingCustomInjections())
						{
							errorDatabase.addWarning(topic, message);
						}
						else
						{
							errorDatabase.addError(topic, message);
							valid = false;
						}
					}
					
					/* check for dummy topics */
					if (topic instanceof TranslatedTopicV1)
					{
						/* Add the warning for the topics relationships that haven't been translated */
						if (topic.getOutgoingRelationships() != null && topic.getOutgoingRelationships().getItems() != null)
						{
							for (T relatedTopic: topic.getOutgoingRelationships().getItems())
							{
								// Check if the app should be shutdown
								if (isShuttingDown.get()) {
									return;
								}
								
								final TranslatedTopicV1 relatedTranslatedTopic = (TranslatedTopicV1) relatedTopic;
								
								/* Only show errors for topics that weren't included in the injections */
								if (!customInjectionErrors.contains(relatedTranslatedTopic.getTopicId()) && !genericInjectionErrors.contains(relatedTopic.getId()))
								{
									if ((!baseLevel.isSpecTopicInLevelByTopicID(relatedTranslatedTopic.getTopicId()) && !docbookBuildingOptions.getIgnoreMissingCustomInjections()) || baseLevel.isSpecTopicInLevelByTopicID(relatedTranslatedTopic.getTopicId()))
									{
										if (relatedTopic.isDummyTopic() && relatedTranslatedTopic.hasBeenPushedForTranslation())
											errorDatabase.addWarning(topic, "Topic ID " + relatedTranslatedTopic.getTopicId() + ", Revision " + relatedTranslatedTopic.getTopicRevision() + ", Title \"" + relatedTopic.getTitle() + "\" is an untranslated topic.");
										else if (relatedTopic.isDummyTopic())
											errorDatabase.addWarning(topic, "Topic ID " + relatedTranslatedTopic.getTopicId() + ", Revision " + relatedTranslatedTopic.getTopicRevision() + ", Title \"" + relatedTopic.getTitle() + "\" hasn't been pushed for translation.");
									}
								}
							}
						}
						
						/* Check the topic itself isn't a dummy topic */
						if (topic.isDummyTopic() && ((TranslatedTopicV1) topic).hasBeenPushedForTranslation())
							errorDatabase.addWarning(topic, "This topic is an untranslated topic.");
						else if (topic.isDummyTopic())
							errorDatabase.addWarning(topic, "This topic hasn't been pushed for translation.");
					}
				}
				
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return;
				}

				if (!valid)
				{
					String topicXMLErrorTemplate = errorInvalidInjectionTopic.getValue();
					topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_TITLE_REGEX, topic.getTitle());
					
					// Set the topic id in the error
					if (topic instanceof TranslatedTopicV1)
					{
						topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, ((TranslatedTopicV1) topic).getTopicId() + ", Revision " + ((TranslatedTopicV1) topic).getTopicRevision());
					}
					else
					{
						topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topic.getId()));
					}
					
					// Add the link to the errors page. If the errors page is suppressed then remove the injection point.
					if (!docbookBuildingOptions.getSuppressErrorsPage())
					{
						topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "<para>Please review the compiler error for <xref linkend=\"TagErrorXRef" + topic.getId() + "\"/> for more detailed information.</para>");
					}
					else
					{
						topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
					}
					
					final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(XMLUtilities.convertNodeToString(doc, verbatimElements, inlineElements, contentsInlineElements, true));
					errorDatabase.addError(topic, "Topic has invalid Injection Points. The processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");
					
					setSpecTopicXMLForError(specTopic, topicXMLErrorTemplate, fixedUrlsSuccess);
				}
				else
				{
					/* add the standard boilerplate xml */
					xmlPreProcessor.processTopicAdditionalInfo(specTopic, doc, contentSpec.getBugzillaOptions(), docbookBuildingOptions, buildName, searchTagsUrl, buildDate);
					
					/*
					 * make sure the XML is valid docbook after the standard
					 * processing has been done
					 */
					validateTopicXML(specTopic, doc, fixedUrlsSuccess);
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
		if (isShuttingDown.get()) {
			return;
		}
		
		final NamedNodeMap attributes = node.getAttributes();
		if (attributes != null)
		{
			final Node idAttribute = attributes.getNamedItem("id");
			if (idAttribute != null)
			{
				String idAttributeValue = idAttribute.getNodeValue();
				
				if (specTopic.getDuplicateId() != null)
					idAttributeValue += "-" + specTopic.getDuplicateId();
				
				if (!isUniqueAttributeId(idAttributeValue, specTopic.getDBId(), usedIdAttributes))
					idAttributeValue += "-" + specTopic.getStep();
				
				setUniqueIdReferences(node, idAttribute.getNodeValue(), idAttributeValue);
				
				idAttribute.setNodeValue(idAttributeValue);
			}
		}

		final NodeList elements = node.getChildNodes();
		for (int i = 0; i < elements.getLength(); ++i)
			setUniqueIds(specTopic, elements.item(i), usedIdAttributes);
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
		if (isShuttingDown.get()) {
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
			setUniqueIdReferences(elements.item(i), id, fixedId);
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
			final Set<String> ids1 = usedIdAttributes.get(topicId);

			for (final Integer topicId2 : usedIdAttributes.keySet())
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return false;
				}
				
				if (topicId2.equals(topicId))
					continue;

				if (usedIdAttributes.containsKey(topicId2))
				{
					final Set<String> ids2 = usedIdAttributes.get(topicId2);

					for (final String id1 : ids1)
					{
						// Check if the app should be shutdown
						if (isShuttingDown.get()) {
							return false;
						}
						
						if (ids2.contains(id1))
						{
							retValue = false;
						}
					}
				}
			}
		}

		return retValue;
	}
	
	/**
	 * This function scans the supplied XML node and it's children for id
	 * attributes, collecting them in the usedIdAttributes parameter
	 * 
	 * @param node
	 *            The current node being processed (will be the document root to
	 *            start with, and then all the children as this function is
	 *            recursively called)
	 */
	private void collectIdAttributes(final Integer topicId, final Node node, final Map<Integer, Set<String>> usedIdAttributes)
	{
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
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
					usedIdAttributes.put(topicId, new HashSet<String>());
				usedIdAttributes.get(topicId).add(idAttibuteValue);
			}
		}

		final NodeList elements = node.getChildNodes();
		for (int i = 0; i < elements.getLength(); ++i)
			collectIdAttributes(topicId, elements.item(i), usedIdAttributes);
	}
	
	private HashMap<String, byte[]> doBuildZipPass(final ContentSpec contentSpec, final UserV1 requester, final boolean fixedUrlsSuccess) throws InvalidParameterException, InternalProcessingException
	{
		log.info("Building the ZIP file");
		
		final StringBuffer bookXIncludes = new StringBuffer();
		
		/* Add the base book information */
		final HashMap<String, byte[]> files = new HashMap<String, byte[]>();
		final String bookBase = buildBookBase(contentSpec, requester, files);
		
		/* add the images to the book */
		addImagesToBook(files);
		
		LinkedList<com.redhat.contentspec.Node> levelData = contentSpec.getBaseLevel().getChildNodes();

		// Loop through and create each chapter and the topics inside those chapters
		for (com.redhat.contentspec.Node node: levelData) {
		
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return null;
			}
			
			if (node instanceof Level) {
				final Level level = (Level) node;
				
				if (level.hasSpecTopics())
				{
					createRootElementXML(files, bookXIncludes, level, fixedUrlsSuccess);
				}
				else if (docbookBuildingOptions.isAllowEmptySections())
				{
					bookXIncludes.append(DocbookUtils.wrapInPara("No Content"));
				}
			}
		}
		
		
		/* add any compiler errors */
		if (!docbookBuildingOptions.getSuppressErrorsPage() && errorDatabase.hasItems(locale))
		{
			final String compilerOutput = buildErrorChapter(locale);
			files.put(BOOK_LOCALE_FOLDER + "Errors.xml", StringUtilities.getStringBytes(StringUtilities.cleanTextForXML(compilerOutput == null ? "" : compilerOutput)));
			bookXIncludes.append("	<xi:include href=\"Errors.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
		}
		
		/* build the content specification page */
		if (!docbookBuildingOptions.getSuppressContentSpecPage())
		{
			files.put(BOOK_LOCALE_FOLDER + "Build_Content_Specification.xml", DocbookUtils.buildAppendix(DocbookUtils.wrapInPara("<programlisting>" + XMLUtilities.wrapStringInCDATA(contentSpec.toString()) + "</programlisting>"), "Build Content Specification").getBytes());
			bookXIncludes.append("	<xi:include href=\"Build_Content_Specification.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
		}
		
		final String book = bookBase.replace(BuilderConstants.XIINCLUDES_INJECTION_STRING, bookXIncludes);
		files.put(BOOK_LOCALE_FOLDER + escapedTitle + ".xml", book.getBytes());
		
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
	 * @param vairables A mapping of variables that are used as override parameters
	 * @param requester The User who requested the book be built
	 * @return A Document object to be used in generating the book.xml
	 * @throws InternalProcessingException 
	 * @throws InvalidParameterException 
	 */
	private String buildBookBase(final ContentSpec contentSpec, final UserV1 requester, final Map<String, byte[]> files) throws InvalidParameterException, InternalProcessingException
	{
		log.info("\tAdding standard files to Publican ZIP file");
		
		final Map<String, String> variables = docbookBuildingOptions.getOverrides();
		
		final String bookInfo = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.BOOK_INFO_XML_ID, "").getValue();
		final String bookXml = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.BOOK_XML_ID, "").getValue();
		final String publicanCfg = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.PUBLICAN_CFG_ID, "").getValue();
		final String bookEnt = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.BOOK_ENT_ID, "").getValue();
		final String prefaceXml = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.CSP_PREFACE_XML_ID, "").getValue();
		
		final String brand = contentSpec.getBrand() == null ? "common" : contentSpec.getBrand();
		
		// Setup the basic book.xml
		String basicBook = bookXml.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		basicBook = basicBook.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
		basicBook = basicBook.replaceAll(BuilderConstants.VERSION_REGEX, contentSpec.getVersion());
		
		// Setup publican.cfg
		String fixedPublicanCfg = publicanCfg.replaceAll(BuilderConstants.BRAND_REGEX, brand);
		fixedPublicanCfg = fixedPublicanCfg.replaceFirst("xml_lang\\: .*(\\r\\n|\\n)", "xml_lang: " + locale + "\n");
		if (contentSpec.getPublicanCfg() != null) {
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
		
		files.put(BOOK_FOLDER + "publican.cfg", fixedPublicanCfg.getBytes());
		
		// Setup Book_Info.xml
		final String pubsNumber = (variables.containsKey("pubsnumber") && variables.containsKey("pubsnumber")) ? variables.get("pubsnumber") : (contentSpec.getPubsNumber() == null ? BuilderConstants.PUBSNUMBER_DEFAULT : contentSpec.getPubsNumber().toString());
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
			fixedBookInfo = fixedBookInfo.replaceAll(BuilderConstants.LEGAL_NOTICE_REGEX, "<xi:include href=\"Common_Content/Legal_Notice.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />");
		}
		
		files.put(BOOK_LOCALE_FOLDER + "Book_Info.xml", fixedBookInfo.getBytes());
		
		// Setup Author_Group.xml
		buildAuthorGroup(contentSpec, files);
		
		if (!contentSpec.getOutputStyle().equals(CSConstants.SKYNET_OUTPUT_FORMAT))
		{
			// Setup Preface.xml
			String fixedPrefaceXml = prefaceXml.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
			files.put(BOOK_LOCALE_FOLDER + "Preface.xml", fixedPrefaceXml.getBytes());
			
			// Add the preface to the book.xml
			basicBook = basicBook.replaceAll(BuilderConstants.PREFACE_REGEX, "<xi:include href=\"Preface.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />");
		}
		
		// Setup Revision_History.xml
		buildRevisionHistory(contentSpec, requester, files);
		
		// Setup the <<contentSpec.title>>.ent file
		String entFile = bookEnt.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		entFile = entFile.replaceAll(BuilderConstants.PRODUCT_REGEX, contentSpec.getProduct());
		entFile = entFile.replaceAll(BuilderConstants.TITLE_REGEX, contentSpec.getTitle());
		entFile = entFile.replaceAll(BuilderConstants.YEAR_FORMAT_REGEX, Integer.toString(Calendar.getInstance().get(Calendar.YEAR)));
		entFile = entFile.replaceAll(BuilderConstants.CONTENT_SPEC_COPYRIGHT_REGEX, contentSpec.getCopyrightHolder());
		entFile = entFile.replaceAll(BuilderConstants.BZPRODUCT_REGEX, contentSpec.getBugzillaProduct() == null ? contentSpec.getProduct() : contentSpec.getBugzillaProduct());
		entFile = entFile.replaceAll(BuilderConstants.BZCOMPONENT_REGEX, contentSpec.getBugzillaComponent() == null ? BuilderConstants.DEFAULT_BZCOMPONENT : contentSpec.getBugzillaComponent());
		entFile = entFile.replaceAll(BuilderConstants.CONTENT_SPEC_BUGZILLA_URL_REGEX, "https://bugzilla.redhat.com/");
		files.put(BOOK_LOCALE_FOLDER + escapedTitle + ".ent", entFile.getBytes());
		
		// Setup the images and files folders
		final String iconSvg = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.ICON_SVG_ID, "").getValue();
		files.put(BOOK_IMAGES_FOLDER + "icon.svg", iconSvg.getBytes());
		
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
	 * Creates all the chapters/appendixes for a book and generates the section/topic data inside of each chapter.
	 * 
	 * @param bookXIncludes The string based list of XIncludes to be used in the book.xml
	 * @param level The level to build the chapter from.
	 */
	protected void createRootElementXML(final Map<String, byte[]> files, final StringBuffer bookXIncludes, final Level level, final boolean fixedUrlsSuccess) {
			
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
			System.exit(-1);
		}
		
		// Create the title
		String chapterName = level.getUniqueLinkId(fixedUrlsSuccess) + ".xml";
		
		// Add to the list of XIncludes that will get set in the book.xml
		bookXIncludes.append("\t<xi:include href=\"" + chapterName + "\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
		
		//Create the chapter.xml
		Element titleNode = chapter.createElement("title");
		titleNode.setTextContent(level.getTitle());
		chapter.getDocumentElement().appendChild(titleNode);
		chapter.getDocumentElement().setAttribute("id", level.getUniqueLinkId(fixedUrlsSuccess));
		createSectionXML(files, level, chapter, chapter.getDocumentElement(), fixedUrlsSuccess, elementName);
		
		// Add the boiler plate text and add the chapter to the book
		String chapterString = DocbookUtils.addXMLBoilerplate(XMLUtilities.convertNodeToString(chapter, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent", elementName);
		files.put(BOOK_LOCALE_FOLDER + chapterName, chapterString.getBytes());
	}
	
	/**
	 * Creates all the chapters/appendixes for a book that are contained within another part/chapter/appendix and generates the section/topic data inside of each chapter.
	 * 
	 * @param bookXIncludes The string based list of XIncludes to be used in the book.xml
	 * @param level The level to build the chapter from.
	 */
	protected void createSubRootElementXML(final Map<String, byte[]> files, final Document doc, final Node parentNode, final Level level, final boolean fixedUrlsSuccess) {
			
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
			System.exit(-1);
		}
		
		// Create the title
		String chapterName = level.getUniqueLinkId(fixedUrlsSuccess) + ".xml";
		
		// Add to the list of XIncludes that will get set in the book.xml
		Element xiInclude = doc.createElement("xi:include");
		xiInclude.setAttribute("href", chapterName);
		xiInclude.setAttribute("xmlns:xi", "http://www.w3.org/2001/XInclude");
		parentNode.appendChild(xiInclude);
		
		//Create the chapter.xml
		Element titleNode = chapter.createElement("title");
		titleNode.setTextContent(level.getTitle());
		chapter.getDocumentElement().appendChild(titleNode);
		chapter.getDocumentElement().setAttribute("id", level.getUniqueLinkId(fixedUrlsSuccess));
		createSectionXML(files, level, chapter, chapter.getDocumentElement(), fixedUrlsSuccess, elementName);
		
		// Add the boiler plate text and add the chapter to the book
		String chapterString = DocbookUtils.addXMLBoilerplate(XMLUtilities.convertNodeToString(chapter, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent", elementName);
		files.put(BOOK_LOCALE_FOLDER + chapterName, chapterString.getBytes());
	}
	
	/**
	 * Creates the section component of a chapter.xml for a specific ContentLevel.
	 * 
	 * @param levelData A map containing the data for this Section's level ordered by a step.
	 * @param chapter The chapter document object that this section is to be added to.
	 * @param parentNode The parent XML node of this section.
	 */
	protected void createSectionXML(final Map<String, byte[]> files, final Level level, final Document chapter, final Element parentNode, final boolean fixedUrlsSuccess, final String rootElementName)
	{
		LinkedList<com.redhat.contentspec.Node> levelData = level.getChildNodes();
		
		// Add the section and topics for this level to the chapter.xml
		for (com.redhat.contentspec.Node node: levelData)
		{
			
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return;
			}
			
			if (node instanceof Level && node.getParent() != null && (((Level)node).getParent().getType() == LevelType.BASE || ((Level)node).getParent().getType() == LevelType.PART)) {
				Level childLevel = (Level)node;
				
				// Create a new file for the Chapter/Appendix
				createSubRootElementXML(files, chapter, parentNode, childLevel, fixedUrlsSuccess);
			}
			else if (node instanceof Level)
			{
				Level childLevel = (Level)node;
				
				// Create the section and its title
				Element sectionNode = chapter.createElement("section");
				Element sectionTitleNode = chapter.createElement("title");
				sectionTitleNode.setTextContent(childLevel.getTitle());
				sectionNode.appendChild(sectionTitleNode);
				sectionNode.setAttribute("id", childLevel.getUniqueLinkId(fixedUrlsSuccess));
				
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
					createSectionXML(files, childLevel, chapter, sectionNode, fixedUrlsSuccess, rootElementName);
				}
				
				parentNode.appendChild(sectionNode);
			} else if (node instanceof SpecTopic) {
				SpecTopic specTopic = (SpecTopic)node;
				String topicFileName;
				
				if (fixedUrlsSuccess)
					topicFileName = specTopic.getTopic().getXrefPropertyOrId(CommonConstants.FIXED_URL_PROP_TAG_ID);
				else
					topicFileName = specTopic.getTopic().getXRefID();
				
				if (specTopic.getDuplicateId() != null)
					topicFileName += "-" + specTopic.getDuplicateId();			
					
				topicFileName += ".xml";
				
				Element topicNode = chapter.createElement("xi:include");
				topicNode.setAttribute("href", "topics/" + topicFileName);
				topicNode.setAttribute("xmlns:xi", "http://www.w3.org/2001/XInclude");
				parentNode.appendChild(topicNode);
				
				String topicXML = DocbookUtils.addXMLBoilerplate(XMLUtilities.convertNodeToString(specTopic.getXmlDocument(), verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent", rootElementName);
				files.put(BOOK_TOPICS_FOLDER + topicFileName, topicXML.getBytes());
			}
		}
	}
	
	private void addImagesToBook(final HashMap<String, byte[]> files) throws InvalidParameterException, InternalProcessingException
	{
		/* Load the database constants */
		final byte[] failpenguinPng = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.FAILPENGUIN_PNG_ID, "").getValue();

		/* download the image files that were identified in the processing stage */
		int imageProgress = 0;
		final int imageTotal = this.imageLocations.size();

		for (final TopicImageData<T> imageLocation : this.imageLocations)
		{
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				return;
			}
			
			boolean success = false;

			final int extensionIndex = imageLocation.getImageName().lastIndexOf(".");
			final int pathIndex = imageLocation.getImageName().lastIndexOf("/");

			if (/* characters were found */
			extensionIndex != -1 && pathIndex != -1 &&
			/* the path character was found before the extension */
			extensionIndex > pathIndex)
			{
				try
				{
					/*
					 * The file name minus the extension should be an integer
					 * that references an ImageFile record ID.
					 */
					final String topicID = imageLocation.getImageName().substring(pathIndex + 1, extensionIndex);
					
					/* 
					 * If the image is the failpenguin the that means that an error has
					 * already occured most likely from not specifying an image file at
					 * all. 
					 */
					if (topicID.equals("failpenguinPng"))
					{
						success = false;
						errorDatabase.addError(imageLocation.getTopic(), "No image filename specified. Must be in the format [ImageFileID].extension e.g. 123.png, or images/321.jpg");
					}
					else
					{
						final ImageV1 imageFile = restManager.getRESTClient().getJSONImage(Integer.parseInt(topicID), "");
	
						if (imageFile != null)
						{
							success = true;
							files.put(BOOK_LOCALE_FOLDER + imageLocation.getImageName(), imageFile.getImageData());
						}
						else
						{
							errorDatabase.addError(imageLocation.getTopic(), "ImageFile ID " + topicID + " from image location " + imageLocation + " was not found!");
							log.error("ImageFile ID " + topicID + " from image location " + imageLocation + " was not found!");
						}
					}
				}
				catch (final Exception ex)
				{
					success = false;
					errorDatabase.addError(imageLocation.getTopic(), imageLocation.getImageName() + " is not a valid image. Must be in the format [ImageFileID].extension e.g. 123.png, or images/321.jpg");
					log.error(ExceptionUtilities.getStackTrace(ex));
				}
			}

			/* put in a place holder */
			if (!success)
			{
				files.put(BOOK_LOCALE_FOLDER + imageLocation.getImageName(), failpenguinPng);
			}

			final float progress = (float) imageProgress / (float) imageTotal * 100;
			log.info("\tDownloading Images " + Math.round(progress) + "% done");

			++imageProgress;
		}
	}
	
	/**
	 * Builds the Author_Group.xml using the assigned writers for topics inside of the content specification.
	 * @throws InternalProcessingException 
	 * @throws InvalidParameterException 
	 */
	@SuppressWarnings("unchecked")
	private void buildAuthorGroup(final ContentSpec contentSpec, final Map<String, byte[]> files) throws InvalidParameterException, InternalProcessingException
	{
		log.info("\tBuilding Author_Group.xml");
		
		// Setup Author_Group.xml
		final String authorGroupXml = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.AUTHOR_GROUP_XML_ID, "").getValue();
		String fixedAuthorGroupXml = authorGroupXml;
		Document authorDoc = null;
		try {
			authorDoc = XMLUtilities.convertStringToDocument(fixedAuthorGroupXml);
		} catch (SAXException ex) {
			/* Exit since we shouldn't fail at converting the basic author group */
			log.debug(ExceptionUtilities.getStackTrace(ex));
			System.exit(-1);
		}
		LinkedHashMap<Integer, TagV1> authors = new LinkedHashMap<Integer, TagV1>();
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return;
		}
		
		// Get the mapping of authors using the topics inside the content spec
		for (Integer topicId: specDatabase.getTopicIds()) {
			final T topic = (T) specDatabase.getSpecTopicsForTopicID(topicId).get(0).getTopic();
			final List<TagV1> authorTags = topic.getTagsInCategoriesByID(CollectionUtilities.toArrayList(CSConstants.WRITER_CATEGORY_ID));
			if (authorTags.size() > 0) {
				for (TagV1 author: authorTags) {
					if (!authors.containsKey(author.getId())) {
						authors.put(author.getId(), author);
					}
				}
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
			for (Integer authorId: authors.keySet()) {
				
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					shutdown.set(true);
					return;
				}
				
				AuthorInformation authorInfo = reader.getAuthorInformation(authorId);
				if (authorInfo == null) continue;
				Element authorEle = authorDoc.createElement("author");
				Element firstNameEle = authorDoc.createElement("firstname");
				firstNameEle.setTextContent(authorInfo.getFirstName());
				authorEle.appendChild(firstNameEle);
				Element lastNameEle = authorDoc.createElement("surname");
				lastNameEle.setTextContent(authorInfo.getLastName());
				authorEle.appendChild(lastNameEle);
				
				// Add the affiliation information
				if (authorInfo.getOrganization() != null) {
					Element affiliationEle = authorDoc.createElement("affiliation");
					Element orgEle = authorDoc.createElement("orgname");
					orgEle.setTextContent(authorInfo.getOrganization());
					affiliationEle.appendChild(orgEle);
					if (authorInfo.getOrgDivision() != null) {
						Element orgDivisionEle = authorDoc.createElement("orgdiv");
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
			if (!insertedAuthor && contentSpec.getOutputStyle().equals(CSConstants.SKYNET_OUTPUT_FORMAT))
			{
				// Use the author "Skynet Alpha Build System"
				Element authorEle = authorDoc.createElement("author");
				Element firstNameEle = authorDoc.createElement("firstname");
				firstNameEle.setTextContent("SkyNet");
				authorEle.appendChild(firstNameEle);
				Element lastNameEle = authorDoc.createElement("surname");
				lastNameEle.setTextContent("Alpha Build System");
				authorEle.appendChild(lastNameEle);
				authorDoc.getDocumentElement().appendChild(authorEle);
				
				// Add the affiliation
				Element affiliationEle = authorDoc.createElement("affiliation");
				Element orgEle = authorDoc.createElement("orgname");
				orgEle.setTextContent("Red Hat");
				affiliationEle.appendChild(orgEle);
				Element orgDivisionEle = authorDoc.createElement("orgdiv");
				orgDivisionEle.setTextContent("Enigineering Content Services");
				affiliationEle.appendChild(orgDivisionEle);
				authorEle.appendChild(affiliationEle);
			}
			else if (!insertedAuthor)
			{
				// Use the author "Staff Writer"
				Element authorEle = authorDoc.createElement("author");
				Element firstNameEle = authorDoc.createElement("firstname");
				firstNameEle.setTextContent("Staff");
				authorEle.appendChild(firstNameEle);
				Element lastNameEle = authorDoc.createElement("surname");
				lastNameEle.setTextContent("Writer");
				authorEle.appendChild(lastNameEle);
				authorDoc.getDocumentElement().appendChild(authorEle);
			}
		}
		
		// Add the Author_Group.xml to the book
		fixedAuthorGroupXml = DocbookUtils.addXMLBoilerplate(XMLUtilities.convertNodeToString(authorDoc, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent", "authorgroup");
		files.put(BOOK_LOCALE_FOLDER + "Author_Group.xml", fixedAuthorGroupXml.getBytes());
	}
	
	/**
	 * Builds the revision history using the requester of the build.
	 * 
	 * @param requester The user who requested the build action
	 * @throws InternalProcessingException 
	 * @throws InvalidParameterException 
	 */
	private void buildRevisionHistory(final ContentSpec contentSpec, final UserV1 requester, final Map<String, byte[]> files) throws InvalidParameterException, InternalProcessingException 
	{
		log.info("\tBuilding Revision_History.xml");
		
		final DateFormat dateFormatter = new SimpleDateFormat(BuilderConstants.REV_DATE_STRING_FORMAT);
		
		// Replace the basic injection data inside the revision history
		final String revisionHistoryXml = restManager.getRESTClient().getJSONStringConstant(DocbookBuilderConstants.REVISION_HISTORY_XML_ID, "").getValue();
		String fixedRevisionHistoryXml = revisionHistoryXml.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		fixedRevisionHistoryXml = fixedRevisionHistoryXml.replaceAll(BuilderConstants.REV_DATE_FORMAT_REGEX, dateFormatter.format(buildDate));
		
		final List<TagV1> authorList = requester == null ? new ArrayList<TagV1>() : reader.getTagsByName(requester.getName());
		final Document revHistoryDoc;
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			return;
		}
		
		// An assigned writer tag exists for the User so check if there is an AuthorInformation tuple for that writer
		if (authorList.size() == 1) {
			AuthorInformation authorInfo = reader.getAuthorInformation(authorList.get(0).getId());
			if (authorInfo != null) {
				revHistoryDoc = generateRevision(contentSpec, fixedRevisionHistoryXml, authorInfo, requester);
			} else {
				// No AuthorInformation so Use the default value
				authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME, BuilderConstants.DEFAULT_AUTHOR_LASTNAME, BuilderConstants.DEFAULT_EMAIL);
				revHistoryDoc = generateRevision(contentSpec, fixedRevisionHistoryXml, authorInfo, requester);
			}
		// No assigned writer exists for the uploader so use default values
		} else {
			AuthorInformation authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME, BuilderConstants.DEFAULT_AUTHOR_LASTNAME, BuilderConstants.DEFAULT_EMAIL);
			revHistoryDoc = generateRevision(contentSpec, fixedRevisionHistoryXml, authorInfo, requester);
		}
		
		// Add the revision history to the book
		fixedRevisionHistoryXml = DocbookUtils.addXMLBoilerplate(XMLUtilities.convertNodeToString(revHistoryDoc, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent", "appendix");
		files.put(BOOK_LOCALE_FOLDER + "Revision_History.xml", fixedRevisionHistoryXml.getBytes());
	}
	
	/**
	 * Fills in the information required inside of a revision tag, for the Revision_History.xml file.
	 * 
	 * @param xmlDocString An XML document represented as a string that contains key regex expressions.
	 * @param authorInfo An AuthorInformation entity object containing the details for who requested the build
	 * @param requester The user object for the build request.
	 */
	private Document generateRevision(final ContentSpec contentSpec, String xmlDocString, final AuthorInformation authorInfo, final UserV1 requester) {
		if (authorInfo == null) return null;
		
		// Replace all of the regex inside the xml document
		xmlDocString = xmlDocString.replaceAll(BuilderConstants.AUTHOR_FIRST_NAME_REGEX, authorInfo.getFirstName());
		xmlDocString = xmlDocString.replaceAll(BuilderConstants.AUTHOR_SURNAME_REGEX, authorInfo.getLastName());
		xmlDocString = xmlDocString.replaceAll(BuilderConstants.AUTHOR_EMAIL_REGEX, authorInfo.getEmail() == null ? BuilderConstants.DEFAULT_EMAIL : authorInfo.getEmail());
		
		// No regex should exist so now convert it to a Document object
		Document doc = null;
		try {
			doc = XMLUtilities.convertStringToDocument(xmlDocString);
		} catch (SAXException ex) {
			/* Exit since we shouldn't fail at converting the basic revision history */
			log.debug(ExceptionUtilities.getStackTrace(ex));
			System.exit(-1);
		}
		doc.getDocumentElement().setAttribute("id", "appe-" + escapedTitle + "-Revision_History");
		
		final NodeList simplelistList = doc.getDocumentElement().getElementsByTagName("simplelist");
		final Element simplelist;
		
		// Find the first simplelist
		if (simplelistList.getLength() >= 1) {
			simplelist = (Element)simplelistList.item(0);
		} else {
			// The document should always have at least one revision tag inside of it.
			Element revision = (Element)doc.getDocumentElement().getElementsByTagName("revision").item(0);
			simplelist = doc.createElement("simplelist");
			revision.appendChild(simplelist);
		}
		
		// Add the revision information
		final Element listMemberEle = doc.createElement("member");
		
		if (contentSpec.getId() > 0)
			listMemberEle.setTextContent(String.format(BuilderConstants.BUILT_MSG, contentSpec.getId(), reader.getLatestCSRevById(contentSpec.getId())) + (authorInfo.getAuthorId() > 0 ? " by " + requester.getName() : ""));
		else
			listMemberEle.setTextContent(BuilderConstants.BUILT_FILE_MSG + (authorInfo.getAuthorId() > 0 ? " by " + requester.getName() : ""));
		simplelist.appendChild(listMemberEle);
		return doc;
	}
	
	private String buildErrorChapter(final String locale)
	{
		log.info("\tBuilding Error Chapter");
		
		String errorItemizedLists = "";

		if (errorDatabase.hasItems(locale))
		{
			for (final TopicErrorData<T> topicErrorData : errorDatabase.getErrors(locale))
			{
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					return null;
				}
				
				final T topic = topicErrorData.getTopic();

				final List<String> topicErrorItems = new ArrayList<String>();

				final String tags = topic.getCommaSeparatedTagList();
				final String url = topic.getSkynetURL();

				topicErrorItems.add(DocbookUtils.buildListItem("INFO: " + tags));
				topicErrorItems.add(DocbookUtils.buildListItem("INFO: <ulink url=\"" + url + "\">Topic URL</ulink>"));

				for (final String error : topicErrorData.getItemsOfType(TopicErrorDatabase.ERROR))
					topicErrorItems.add(DocbookUtils.buildListItem("ERROR: " + error));

				for (final String warning : topicErrorData.getItemsOfType(TopicErrorDatabase.WARNING))
					topicErrorItems.add(DocbookUtils.buildListItem("WARNING: " + warning));

				/*
				 * this should never be false, because a topic will only be
				 * listed in the errors collection once a error or warning has
				 * been added. The count of 2 comes from the standard list items
				 * we added above for the tags and url.
				 */
				if (topicErrorItems.size() > 2)
				{
					final String title;
					if (topic instanceof TranslatedTopicV1)
					{
						final TranslatedTopicV1 translatedTopic = (TranslatedTopicV1) topic;
						title = "Topic ID " + translatedTopic.getTopicId() + ", Revision " + translatedTopic.getTopicRevision();
					}
					else
					{
						title = "Topic ID " + topic.getId();
					}
					final String id = topic.getErrorXRefID();

					errorItemizedLists += DocbookUtils.wrapListItems(topicErrorItems, title, id);
				}
			}
		}
		else
		{
			errorItemizedLists = "<para>No Errors Found</para>";
		}

		return DocbookUtils.buildChapter(errorItemizedLists, "Compiler Output");
	}
	
	@SuppressWarnings("unchecked")
	private void processImageLocations()
	{
		for (final Integer topicId : specDatabase.getTopicIds())
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
						imageLocations.add(new TopicImageData<T>(topic, fileRefAttribute.getNodeValue()));
					}
					else if (fileRefAttribute != null)
					{
						if (fileRefAttribute != null && !fileRefAttribute.getNodeValue().startsWith("images/"))
						{
							fileRefAttribute.setNodeValue("images/" + fileRefAttribute.getNodeValue());
						}
		
						imageLocations.add(new TopicImageData<T>(topic, fileRefAttribute.getNodeValue()));
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
	 * @return The validate document or a template if it failed validation.
	 */
	@SuppressWarnings("unchecked")
	private boolean validateTopicXML(final SpecTopic specTopic, final Document topicDoc, final boolean fixedUrlsSuccess)
	{
		final T topic = (T) specTopic.getTopic();
		
		// Validate the topic against its DTD/Schema
		SAXXMLValidator validator = new SAXXMLValidator();
		if (!validator.validateXML(topicDoc, BuilderConstants.ROCBOOK_45_DTD, rocbookdtd.getValue()))
		{
			String topicXMLErrorTemplate = errorInvalidValidationTopic.getValue();
			topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_TITLE_REGEX, topic.getTitle());
			
			// Set the topic id in the error
			if (topic instanceof TranslatedTopicV1)
			{
				topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, ((TranslatedTopicV1) topic).getTopicId() + ", Revision " + ((TranslatedTopicV1) topic).getTopicRevision());
			}
			else
			{
				topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topic.getId()));
			}
			
			// Add the link to the errors page. If the errors page is suppressed then remove the injection point.
			if (!docbookBuildingOptions.getSuppressErrorsPage())
			{
				topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "<para>Please review the compiler error for <xref linkend=\"" + topic.getErrorXRefID() + "\"/> for more detailed information.</para>");
			}
			else
			{
				topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
			}
			
			final String xmlStringInCDATA = XMLUtilities.wrapStringInCDATA(XMLUtilities.convertNodeToString(topicDoc, verbatimElements, inlineElements, contentsInlineElements, true));
			errorDatabase.addError(topic, "Topic has invalid Docbook XML. The error is <emphasis>" + validator.getErrorText() + "</emphasis>. The processed XML is <programlisting>" + xmlStringInCDATA + "</programlisting>");
			setSpecTopicXMLForError(specTopic, topicXMLErrorTemplate, fixedUrlsSuccess);
			
			return false;
		}
		
		return true;
	}
	
	/**
	 * Sets the XML of the topic to the specified error template
	 */
	private Document setTopicXMLForError(final T topic, final String template, final boolean fixedUrlsSuccess)
	{
		Document doc = null;
		try {
			doc = XMLUtilities.convertStringToDocument(template);
		} catch (SAXException ex) {
			/* Exit since we shouldn't fail at converting a basic template */
			log.debug(ExceptionUtilities.getStackTrace(ex));
			System.exit(-1);
		}
		DocbookUtils.setSectionTitle(topic.getTitle(), doc);
		processTopicID(topic, doc, fixedUrlsSuccess);
		return doc;
	}
	
	
	/**
	 * Sets the XML of the topic in the content spec
	 */
	@SuppressWarnings("unchecked")
	private void setSpecTopicXMLForError(final SpecTopic topic, final String template, final boolean fixedUrlsSuccess)
	{
		Document doc = null;
		try {
			doc = XMLUtilities.convertStringToDocument(template);
		} catch (SAXException ex) {
			/* Exit since we shouldn't fail at converting a basic template */
			log.debug(ExceptionUtilities.getStackTrace(ex));
			System.exit(-1);
		}
		topic.setXmlDocument(doc);
		DocbookUtils.setSectionTitle(topic.getTitle(), doc);
		processTopicID((T) topic.getTopic(), doc, fixedUrlsSuccess);
	}

	/**
	 * Sets the topic xref id to the topic database id
	 */
	private void processTopicID(final T topic, final Document doc, final boolean fixedUrlsSuccess)
	{
		if (fixedUrlsSuccess)
		{
			doc.getDocumentElement().setAttribute("id", topic.getXrefPropertyOrId(CommonConstants.FIXED_URL_PROP_TAG_ID));
		}
		else
		{
			doc.getDocumentElement().setAttribute("id", topic.getXRefID());
		}
	}
	
	/**
	 * This method does a pass over all the topics returned by the query and
	 * attempts to create unique Fixed URL if one does not already exist.
	 * 
	 * @return true if fixed url property tags were able to be created for all
	 *         topics, and false otherwise   
	 */
	private boolean setFixedURLsPass(final BaseRestCollectionV1<TopicV1> topics)
	{
		log.info("Doing Fixed URL Pass");
		
		int tries = 0;
		boolean success = false;

		while (tries < BuilderConstants.MAXIMUM_SET_PROP_TAGS_RETRY && !success)
		{
			
			++tries;

			try
			{
				final BaseRestCollectionV1<TopicV1> updateTopics = new BaseRestCollectionV1<TopicV1>();
				
				final Set<String> processedFileNames = new HashSet<String>();

				for (final TopicV1 topic : topics.getItems())
				{
					
					// Check if the app should be shutdown
					if (isShuttingDown.get()) {
						return false;
					}

					final PropertyTagV1 existingUniqueURL = topic.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);

					if (existingUniqueURL == null || !existingUniqueURL.isValid())
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
							final BaseRestCollectionV1<TopicV1> queryTopics = restManager.getRESTClient().getJSONTopicsWithQuery(new PathSegmentImpl(query, false), "");

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
						if (isShuttingDown.get()) {
							return false;
						}
						
						/*
						 * persist the new fixed url, as long as we are not
						 * looking at a landing page topic
						 */
						if (topic.getId() >= 0)
						{
							final PropertyTagV1 propertyTag = new PropertyTagV1();
							propertyTag.setId(CommonConstants.FIXED_URL_PROP_TAG_ID);
							propertyTag.setValue(baseUrlName + postFix);
							propertyTag.setAddItem(true);

							final BaseRestCollectionV1<PropertyTagV1> updatePropertyTags = new BaseRestCollectionV1<PropertyTagV1>();
							updatePropertyTags.addItem(propertyTag);

							/* remove any old fixed url property tags */
							if (topic.getProperties() != null && topic.getProperties().getItems() != null)
							{
								for (final PropertyTagV1 existing : topic.getProperties().getItems())
								{
									if (existing.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID))
									{
										final PropertyTagV1 removePropertyTag = new PropertyTagV1();
										removePropertyTag.setId(CommonConstants.FIXED_URL_PROP_TAG_ID);
										removePropertyTag.setValue(existing.getValue());
										removePropertyTag.setRemoveItem(true);
										updatePropertyTags.addItem(removePropertyTag);
									}
								}
							}

							final TopicV1 updateTopic = new TopicV1();
							updateTopic.setId(topic.getId());
							updateTopic.setPropertiesExplicit(updatePropertyTags);

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
				if (isShuttingDown.get()) {
					return false;
				}

				/* If we got here, then the REST update went ok */
				success = true;

				/* copy the topics fixed url properties to our local collection */
				if (updateTopics.getItems() != null && updateTopics.getItems().size() != 0)
				{
					for (final TopicV1 topicWithFixedUrl : updateTopics.getItems())
					{
						for (final TopicV1 topic : topics.getItems())
						{
							final PropertyTagV1 fixedUrlProp = topicWithFixedUrl.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);
							
							if (topic != null && topicWithFixedUrl.getId().equals(topic.getId()))
							{
								BaseRestCollectionV1<PropertyTagV1> properties = topic.getProperties();
								if (properties == null) {
									properties = new BaseRestCollectionV1<PropertyTagV1>();
								} else if (properties.getItems() != null) {
									// remove any current url's
									for (PropertyTagV1 prop: properties.getItems()) {
										if (prop.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID)) {
											properties.getItems().remove(prop);
										}
									}
								}
								
								if (fixedUrlProp != null)
									properties.addItem(fixedUrlProp);
							}
							
							/*
							 * we also have to copy the fixed urls into the
							 * related topics
							 */
							for (final TopicV1 relatedTopic : topic.getOutgoingRelationships().getItems())
							{
								if (topicWithFixedUrl.getId().equals(relatedTopic.getId()))
								{
									BaseRestCollectionV1<PropertyTagV1> relatedTopicProperties = relatedTopic.getProperties();
									if (relatedTopicProperties == null) {
										relatedTopicProperties = new BaseRestCollectionV1<PropertyTagV1>();
									} else if (relatedTopicProperties.getItems() != null) {
										// remove any current url's
										for (PropertyTagV1 prop: relatedTopicProperties.getItems()) {
											if (prop.getId().equals(CommonConstants.FIXED_URL_PROP_TAG_ID)) {
												relatedTopicProperties.getItems().remove(prop);
											}
										}
									}

									if (fixedUrlProp != null)
										relatedTopicProperties.addItem(fixedUrlProp);
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
	 * Creates the URL specific title for a topic or level
	 * 
	 * @param title The title that will be used to create the URL Title
	 * @return The URL representation of the title;
	 */
	private String createURLTitle(final String title) {
		String baseTitle = new String(title);
		
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
						baseTitle = baseTitle.substring(0, 1).toUpperCase() + baseTitle.substring(1, baseTitle.length());
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
			escapedTitle = escapedTitle.replaceAll("__", "_");
		
		return escapedTitle;
	}
}
