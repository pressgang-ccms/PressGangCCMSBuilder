package com.redhat.contentspec.builder;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jboss.resteasy.logging.Logger;
import org.jboss.resteasy.specimpl.PathSegmentImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.code.regexp.NamedMatcher;
import com.google.code.regexp.NamedPattern;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import com.redhat.contentspec.builder.constants.BuilderConstants;
import com.redhat.contentspec.builder.exception.BuilderCreationException;
import com.redhat.contentspec.builder.utils.BuilderOptions;
import com.redhat.contentspec.builder.utils.DocbookUtils;
import com.redhat.contentspec.builder.utils.SAXXMLValidator;
import com.redhat.contentspec.builder.utils.XMLUtilities;
import com.redhat.contentspec.builder.TopicInjector;
import com.redhat.contentspec.constants.CSConstants;
import com.redhat.contentspec.entities.AuthorInformation;
import com.redhat.contentspec.ContentSpec;
import com.redhat.contentspec.Level;
import com.redhat.contentspec.SpecTopic;
import com.redhat.contentspec.Part;
import com.redhat.contentspec.enums.LevelType;
import com.redhat.contentspec.rest.RESTManager;
import com.redhat.contentspec.rest.RESTReader;
import com.redhat.contentspec.entities.InjectionOptions;
import com.redhat.contentspec.interfaces.ShutdownAbleApp;
import com.redhat.contentspec.utils.ExceptionUtilities;
import com.redhat.contentspec.utils.ResourceUtilities;
import com.redhat.contentspec.utils.StringUtilities;
import com.redhat.ecs.commonutils.CollectionUtilities;
import com.redhat.ecs.commonutils.ZipUtilities;
import com.redhat.ecs.constants.CommonConstants;
import com.redhat.topicindex.component.docbookrenderer.structures.TopicErrorData;
import com.redhat.topicindex.component.docbookrenderer.structures.TopicErrorDatabase;
import com.redhat.topicindex.rest.collections.BaseRestCollectionV1;
import com.redhat.topicindex.rest.entities.*;
import com.redhat.topicindex.rest.exceptions.InternalProcessingException;
import com.redhat.topicindex.rest.exceptions.InvalidParameterException;

/**
 * 
 * A class that provides the ability to build a book from content specifications.
 * 
 * @author lnewson
 * @author alabbas
 */
public class ContentSpecBuilder implements ShutdownAbleApp {
	
	private static final Logger log = Logger.getLogger(ContentSpecBuilder.class);
	
	private static final String STARTS_WITH_NUMBER_RE = "^(?<Numbers>\\d+)(?<EverythingElse>.*)$";
	
	private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
	private final AtomicBoolean shutdown = new AtomicBoolean(false);
	
	private String BOOK_FOLDER;
	private String BOOK_EN_US_FOLDER;
	private String BOOK_TOPICS_FOLDER;
	private String BOOK_IMAGES_FOLDER;
	
	private String escapedTitle;
	private int CSId;
	private String RESOURCE_LOCATION;
	private HashMap<String, byte[]> files = new HashMap<String, byte[]>();
	private String entFile;
	private TopicInjector injector;
	private final InjectionOptions injectionOptions;
	private final boolean injectBugzillaLinks;
	
	private ArrayList<String> verbatimElements;
	private ArrayList<String> inlineElements;
	private ArrayList<String> contentsInlineElements;
	
	private HashMap<Integer, TopicV1> bookTopics = new HashMap<Integer, TopicV1>();
	private HashMap<Integer, HashMap<Integer, SpecTopic>> topicMappings = new HashMap<Integer, HashMap<Integer, SpecTopic>>();
	
	private final RESTReader reader;
	private final RESTManager restManager;
	private final BuilderOptions builderOptions;
	private final BlobConstantV1 dtd;
	private ContentSpec contentSpec;
	
	private HashMap<String, Document> inlineTopics = new HashMap<String, Document>();
	private HashMap<Integer, String> topicFileNames = new HashMap<Integer, String>();
	private HashMap<Integer, String> topicIdNames = new HashMap<Integer, String>();
	private HashMap<Level, String> levelFileNames = new HashMap<Level, String>();
	private HashSet<String> bookFileNames = new HashSet<String>();
	
	/**
	 * Holds the compiler errors that form the Errors.xml file in the compiled
	 * docbook
	 */
	private TopicErrorDatabase errorDatabase = new TopicErrorDatabase();

	public ContentSpecBuilder(RESTManager dbManager, BuilderOptions builderOptions) throws InvalidParameterException, InternalProcessingException {
		reader = dbManager.getReader();
		this.restManager = dbManager;
		this.injectBugzillaLinks = builderOptions.getInjectBugzillaLinks();
		this.builderOptions = builderOptions;
		this.dtd = dbManager.getRESTClient().getJSONBlobConstant(BuilderConstants.ROCBOOK_DTD_BLOB_ID, "");
		
		// Add the options that were passed to the builder
		injectionOptions = new InjectionOptions();
		
		// Get the injection mode
		InjectionOptions.UserType injectionType = InjectionOptions.UserType.NONE;
		Boolean injection = builderOptions.getInjection();
		if (injection != null && !injection) injectionType = InjectionOptions.UserType.OFF;
		else if (injection != null && injection) injectionType = InjectionOptions.UserType.ON;
		
		// Add the strict injection types
		if (builderOptions.getInjectionTypes() != null) {
			for (String injectType: builderOptions.getInjectionTypes()) {
				injectionOptions.addStrictTopicType(injectType.trim());
			}
			if (injection != null && injection) {
				injectionType = InjectionOptions.UserType.STRICT;
			}
		}
		
		// Set the injection mode
		injectionOptions.setClientType(injectionType);
		
		/*
		 * Get the XML formatting details. These are used to pretty-print
		 * the XML when it is converted into a String.
		 */
		final String verbatimElementsString = BuilderConstants.VERBATIM_XML_ELEMENTS;
		final String inlineElementsString = BuilderConstants.INLINE_XML_ELEMENTS;
		final String contentsInlineElementsString = BuilderConstants.CONTENTS_INLINE_XML_ELEMENTS;
		
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

	public String getEscapedName() {
		return escapedTitle;
	}
	
	public int getNumWarnings() {
		int numWarnings = 0;
		for (TopicErrorData errorData: errorDatabase.getErrors()) {
			numWarnings += errorData.getItemsOfType(TopicErrorDatabase.WARNING).size();
		}
		return numWarnings;
	}

	public int getNumErrors() {
		int numErrors = 0;
		for (TopicErrorData errorData: errorDatabase.getErrors()) {
			numErrors += errorData.getItemsOfType(TopicErrorDatabase.ERROR).size();
		}
		return numErrors;
	}
	
	/**
	 * Builds a book into a zip file for the passed Content Specification
	 * 
	 * @param contentSpec The content specification that is to be built. It should have already been validated, if not errors may occur.
	 * @param requester The user who requested the book to be built.
	 * @return A byte array that is the zip file
	 * @throws Exception 
	 */
	public byte[] buildBook(ContentSpec contentSpec, UserV1 requester) throws Exception {
		if (contentSpec == null) throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
		if (requester == null) throw new BuilderCreationException("A user must be specified as the user who requested the build.");
		
		boolean ignoreErrors = builderOptions.getIgnoreErrors();
		this.CSId = contentSpec.getId();
		this.contentSpec = contentSpec;
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return null;
		}
		
		// Create the mapping of skynet topics that are in the content spec
		List<SpecTopic> specTopics = contentSpec.getSpecTopics();
		for (SpecTopic specTopic: specTopics) {
			TopicV1 topic = null;
			if (builderOptions.getSnapshotId() != null) {
				/*SnapshotV1 snapshot = reader.getTopicSnapshotById(builderOptions.getSnapshotId());
				if (snapshot != null) {
					for (SnapshotTopicV1 snapshotTopic: snapshot.getSnaphotTopics().getItems()) {
						if (snapshotTopic.getTopicId().equals(specTopic.getDBId())) {
							topic = reader.getTopicById(specTopic.getDBId(), snapshotTopic.getTopicRevision());
							break;
						}
					}
				}*/
			} else {
				topic = reader.getTopicById(specTopic.getDBId(), null);
			}
			if (topic != null) {
				bookTopics.put(topic.getId(), topic);
			}
			
			// Setup the mappings
			if (!topicMappings.containsKey(specTopic.getDBId())) topicMappings.put(specTopic.getDBId(), new HashMap<Integer, SpecTopic>());
			topicMappings.get(specTopic.getDBId()).put(topicMappings.get(specTopic.getDBId()).size(), specTopic);
		}
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return null;
		}
		
		// Set the injection options for the content spec
		if (contentSpec.getInjectionOptions() != null) {
			injectionOptions.setContentSpecType(contentSpec.getInjectionOptions().getContentSpecType());
			injectionOptions.addStrictTopicTypes(contentSpec.getInjectionOptions().getStrictTopicTypes());
		}
		
		injector = new TopicInjector(bookTopics, reader, injectionOptions, errorDatabase);
		
		// Setup the constants
		escapedTitle = StringUtilities.escapeTitle(contentSpec.getTitle());
		BOOK_FOLDER = escapedTitle + "/";
		BOOK_EN_US_FOLDER = BOOK_FOLDER + "en-US/";
		BOOK_TOPICS_FOLDER = BOOK_EN_US_FOLDER + "topics/";
		BOOK_IMAGES_FOLDER = BOOK_EN_US_FOLDER + "images/";
		
		// Set the book mode and resource file locations
		// (atm Docbook 4.5 is the only supported format)
		RESOURCE_LOCATION = BuilderConstants.DOCBOOK_45_RESOURCE_LOCATION;
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return null;
		}
		
		/*
		 * assign fixed urls property tags to the topics. If
		 * fixedUrlsSuccess is true, the id of the topic sections,
		 * xfref injection points and file names in the zip file
		 * will be taken from the fixed url property tag, defaulting
		 * back to the TopicID## format if for some reason that
		 * property tag does not exist.
		 */
		final boolean fixedUrlsSuccess = setFixedURLsPass();
		
		// Create the topic and level fileNames
		createFileNames(fixedUrlsSuccess);
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return null;
		}
		
		// Create the XML for each topic in the book
		for (Integer topicId: bookTopics.keySet()) {
			createTopicXML(bookTopics.get(topicId), ignoreErrors, fixedUrlsSuccess);
			if (shutdown.get()) return null;
		}
		
		// Create the rest of the book
		createBook(requester, ignoreErrors);
		if (shutdown.get()) return null;
		
		// Create the zip file
		byte[] zipFile = null;
		try {
			zipFile = ZipUtilities.createZip(files);
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return zipFile;
	}
	
	/**
	 * Creates a Chapter for the errors and warnings
	 * 
	 * Modified from Matt Casperson's code
	 */
	private String buildErrorChapter()
	{
		String errorItemizedLists = "";

		if (errorDatabase.hasItems())
		{
			for (final TopicErrorData topicErrorData : errorDatabase.getErrors())
			{
				final TopicV1 topic = topicErrorData.getTopic();

				final List<String> topicErrorItems = new ArrayList<String>();

				final String tags = topic.getCommaSeparatedTagList();

				topicErrorItems.add(DocbookUtils.buildListItem("INFO: " + tags));

				for (final String error : topicErrorData.getItemsOfType(TopicErrorDatabase.ERROR))
					topicErrorItems.add(DocbookUtils.buildListItem("ERROR: " + error));

				for (final String warning : topicErrorData.getItemsOfType(TopicErrorDatabase.WARNING))
					topicErrorItems.add(DocbookUtils.buildListItem("WARNING: " + warning));

				/*
				 * this should never be false, because a topic will only be
				 * listed in the errors collection once a error or warning has
				 * been added. The count of 1 comes from the standard list items
				 * we added above for the tags.
				 */
				if (topicErrorItems.size() > 1)
				{
					final String title = "Topic ID " + topic.getId();
					final String id = BuilderConstants.ERROR_XREF_ID_PREFIX + topic.getId();

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
	
	/**
	 * Create the main content of the book (chapters, sections, etc...)
	 * 
	 * @param reequester The user who requested the book to be built
	 * @param ignoreErrors Whether or not errors should be ignored
	 */
	public void createBook(UserV1 requester, boolean ignoreErrors) {
		StringBuffer bookXIncludes = new StringBuffer();
		
		// Build the base of the book
		String book = buildBookBase(contentSpec, requester);
		
		// Get the initial levels items as these will be chapters.
		LinkedList<com.redhat.contentspec.Node> levelData = contentSpec.getBaseLevel().getChildNodes();
		
		// Setup the basic chapter.xml
		String basicChapter = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "Chapter.xml");

		// Loop through and create each chapter and the topics inside those chapters
		for (com.redhat.contentspec.Node node: levelData) {
		
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				shutdown.set(true);
				return;
			}
			
			if (node instanceof Part) {
				Part part = (Part)node;
				
				bookXIncludes.append("\t<part>\n");
				bookXIncludes.append("\t<title>" + part.getTitle() + "</title>\n");
				
				for (Level childLevel: part.getChildLevels()) {
					createChapterXML(bookXIncludes, childLevel, basicChapter);
				}
				
				bookXIncludes.append("\t</part>\n");
			} else if (node instanceof Level) {
				createChapterXML(bookXIncludes, (Level)node, basicChapter);
			}
		}
		
		if (errorDatabase.hasItems() && !ignoreErrors) {
			files.put(BOOK_EN_US_FOLDER + "Errors.xml", buildErrorChapter().getBytes());
			// Add the error to the book.xml
			bookXIncludes.append("\t<xi:include href=\"Errors.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
		}
		book = book.replace(BuilderConstants.XIINCLUDES_INJECTION_STRING, bookXIncludes);
		files.put(BOOK_EN_US_FOLDER + escapedTitle + ".xml", book.getBytes());
	}
	
	/**
	 * Creates all the chapters/appendixes for a book and generates the section/topic data inside of each chapter.
	 * 
	 * @param bookXIncludes The string based list of XIncludes to be used in the book.xml
	 * @param level The level to build the chapter from.
	 * @param basicChapter A string representation of a basic chapter.
	 */
	protected void createChapterXML(StringBuffer bookXIncludes, Level level, String basicChapter) {
			
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return;
		}
		
		Document chapter = XMLUtilities.convertStringToDocument(basicChapter);
		
		// Create the title
		String chapterName = levelFileNames.get(level) + ".xml";
		if (level.getType() == LevelType.APPENDIX) {
			chapter.renameNode(chapter.getDocumentElement(), null, "appendix");
		}
		
		// Add to the list of XIncludes that will get set in the book.xml
		bookXIncludes.append("\t<xi:include href=\"" + chapterName + "\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n");
		
		//Create the chapter.xml
		Element titleNode = chapter.createElement("title");
		titleNode.setTextContent(level.getTitle());
		chapter.getDocumentElement().appendChild(titleNode);
		chapter.getDocumentElement().setAttribute("id", levelFileNames.get(level));
		createSectionXML(level, chapter, chapter.getDocumentElement());
		
		// Add the boiler plate text and add the chapter to the book
		String chapterString = DocbookUtils.addXMLBoilerplate(XMLUtilities.convertNodeToString(chapter, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent");
		files.put(BOOK_EN_US_FOLDER + chapterName, chapterString.getBytes());
	}
	
	/**
	 * Creates the section component of a chapter.xml for a specific ContentLevel.
	 * 
	 * @param levelData A map containing the data for this Section's level ordered by a step.
	 * @param chapter The chapter document object that this section is to be added to.
	 * @param parentNode The parent XML node of this section.
	 */
	protected void createSectionXML(Level level, Document chapter, Element parentNode) {
		LinkedList<com.redhat.contentspec.Node> levelData = level.getChildNodes();
		
		// Add the section and topics for this level to the chapter.xml
		for (com.redhat.contentspec.Node node: levelData) {
			
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				shutdown.set(true);
				return;
			}
			
			if (node instanceof Level) {
				Level childLevel = (Level)node;
				
				// Create the section and its title
				Element sectionNode = chapter.createElement("section");
				Element sectionTitleNode = chapter.createElement("title");
				sectionTitleNode.setTextContent(childLevel.getTitle());
				sectionNode.appendChild(sectionTitleNode);
				sectionNode.setAttribute("id", levelFileNames.get(childLevel));
				
				// Add this sections child sections/topics
				createSectionXML(childLevel, chapter, sectionNode);
				parentNode.appendChild(sectionNode);
			} else if (node instanceof SpecTopic) {
				SpecTopic topic = (SpecTopic)node;
				String topicNameId = topic.getDBId() + ".xml";
				String topicFileName = topicFileNames.get(topic.getDBId());
				if (topicMappings.get(topic.getDBId()).size() > 1) {
					// The topic is duplicated in the content spec so use the appropriate topic
					for (Integer i: topicMappings.get(topic.getDBId()).keySet()) {
						if (topicMappings.get(topic.getDBId()).get(i).equals(topic)) {
							
							// Create the String that will be appended to the end of the topic xml id
							String postFix = "";
							if (i != 0) {
								postFix = "-" + i;
							}
							
							topicNameId = topic.getDBId() + postFix + ".xml";
							topicFileName = topicFileName + postFix;
							break;
						}
					}
				}
				topicFileName += ".xml";
				
				// Inject the topic either inline or as an XInclude
				if (topic.isInlineTopic()) {
					// TODO Fix inline injection
					Document topicDoc = inlineTopics.get(topicNameId);
					NodeList childNodes = topicDoc.getDocumentElement().getChildNodes();
					for (int i = 0; i < childNodes.getLength(); i++) {
						parentNode.appendChild(chapter.importNode(childNodes.item(i), true));
					}
				} else {
					Element topicNode = chapter.createElement("xi:include");
					topicNode.setAttribute("href", "topics/" + topicFileName);
					topicNode.setAttribute("xmlns:xi", "http://www.w3.org/2001/XInclude");
					parentNode.appendChild(topicNode);
				}
			}
		}
	}
	
	/**
	 * Creates the required Topic XML data for a specific topic and then validates it.
	 * 
	 * @param topic The topic entity to create the XML data for.
	 * @returns A Document object that represents the topic XML data.
	 */
	protected void createTopicXML(TopicV1 topic, boolean ignoreErrors, final boolean fixedUrlsSuccess) {
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return;
		}
		
		String topicXML = topic.getXml();
		Document topicDoc = null;
		String id = "id";
		
		// Check that the Topic XML exists and isn't empty
		if (topicXML == null || topicXML.equals("")) {
			// Create an empty topic with the topic title from the resource file
			topicXML = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "EmptyTopic.xml");
			topicXML = topicXML.replaceAll(BuilderConstants.TOPIC_TITLE_REGEX, topic.getTitle());
			topicXML = topicXML.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topic.getId()));
			if (!ignoreErrors) {
				topicXML = topicXML.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "<para>Please review the compiler error for <xref linkend=\"TagErrorXRef" + topic.getId() + "\"/> for more detailed information.</para>");
			} else {
				topicXML = topicXML.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
			}
			topicDoc = XMLUtilities.convertStringToDocument(topicXML);
			errorDatabase.addWarning(topic, BuilderConstants.EMPTY_TOPIC_XML);
		} else {
			// Converts the string into Document and removes the title node
			topicDoc = XMLUtilities.convertStringToDocument(topicXML);
			
			// Checks to ensure that the topic converted properly
			if (topicDoc == null) {
				topicXML = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "FailedValidationTopic.xml");
				topicXML = topicXML.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topic.getId()));
				if (!ignoreErrors) {
					topicXML = topicXML.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "<para>Please review the compiler error for <xref linkend=\"TagErrorXRef" + topic.getId() + "\"/> for more detailed information.</para>");
				} else {
					topicXML = topicXML.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
				}
				topicDoc = XMLUtilities.convertStringToDocument(topicXML);
				errorDatabase.addError(topic, BuilderConstants.BAD_XML_STRUCTURE);
			} else {
				
				// Create the title node for the topic
				NodeList nodes = topicDoc.getElementsByTagName("title");
				if (nodes.getLength() != 0) {
					for (int i = 0; i < nodes.getLength(); i++) {
						if (nodes.item(i).getParentNode().equals(topicDoc.getDocumentElement())) {
							nodes.item(i).getParentNode().removeChild(nodes.item(i));
						}
					}
				}
				Element topicTitleNode = topicDoc.createElement("title");
				topicTitleNode.setTextContent(topic.getTitle());
				
				// Writes the title node to the document with the topic title
				Node firstNode = topicDoc.getDocumentElement().getFirstChild();
				if (firstNode != null) {
					topicDoc.getDocumentElement().insertBefore(topicTitleNode, firstNode);
				} else {
					topicDoc.getDocumentElement().appendChild(topicTitleNode);
				}
				
				// Creates a section node for the topic if one doesn't exist
				if (!topicDoc.getDocumentElement().getNodeName().equals("section")) {
					DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
					DocumentBuilder dBuilder;
					Document doc = null;
					try {
						dBuilder = dbFactory.newDocumentBuilder();	
						doc = dBuilder.newDocument();
					} catch (ParserConfigurationException e) {
						e.printStackTrace();
					}
					Element section = topicDoc.createElement("section");
					section.appendChild(doc.importNode(topicDoc.getDocumentElement(), true));
					doc.appendChild(section);
					topicDoc = doc;
				}
				
				// Check if the app should be shutdown
				if (isShuttingDown.get()) {
					shutdown.set(true);
					return;
				}
				
				// Adds the images to the book that are required for this topic
				addImagesToBookForNode(topicDoc.getDocumentElement(), topic);
				
				// Process the injections if allowed
				boolean injectionsValid = true;
				if (injectionOptions.isInjectionAllowed()) {
					injectionsValid = injector.processTopicInjections(topic, topicDoc, fixedUrlsSuccess);
				}
				
				// Check that the injections were successful
				if (!injectionsValid) {
					topicXML = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "FailedInjectionTopic.xml");
					topicXML = topicXML.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topic.getId()));
					if (!ignoreErrors) {
						topicXML = topicXML.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "<para>Please review the compiler error for <xref linkend=\"TagErrorXRef" + topic.getId() + "\"/> for more detailed information.</para>");
					} else {
						topicXML = topicXML.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
					}
					topicDoc = XMLUtilities.convertStringToDocument(topicXML);
				} else {
					
					// Check if the app should be shutdown
					if (isShuttingDown.get()) {
						shutdown.set(true);
						return;
					}
					
					// Validate the topic
					topicDoc = validateTopicXMLFirstPass(topic, topicDoc, ignoreErrors);
				}
			}
		}
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return;
		}
		
		// Add/overwrite the topic ID attribute and add relationships to the topic
		HashMap<Integer, SpecTopic> topicMapping = topicMappings.get(topic.getId());
		if (topicMapping.size() == 1) {
			for (Integer key: topicMapping.keySet()) {
				
				// Inject the relationships and bugzilla links
				injector.processRelationshipInjections(topicDoc, topicMapping.get(key), topicMappings, topicIdNames, levelFileNames);
				if (injectBugzillaLinks && contentSpec.getBugzillaOptions().isBugzillaLinksEnabled()) {
					injector.addBugzillaLinksToTopic(topicDoc, topic, contentSpec.getBugzillaOptions(), getBookDetails());
				}
				
				// Validate the XML again after the final injections
				topicDoc = validateTopicXMLSecondPass(topic, topicDoc, ignoreErrors);
				
				// There are no duplicates of the topic so there is no need to create duplicate files or alter the filename
				topicDoc.getDocumentElement().setAttribute(id, topicIdNames.get(topic.getId()));
				
				if (topicMapping.get(key).isInlineTopic()) {
					inlineTopics.put(topic.getId() + ".xml", topicDoc);
				} else {
					/* get a formatted copy of the XML Document */
					String docString = XMLUtilities.convertNodeToString(topicDoc, verbatimElements, inlineElements, contentsInlineElements, true);
					final String topicString = StringUtilities.cleanTextForXML(DocbookUtils.addXMLBoilerplate(docString, this.escapedTitle + ".ent"));
					
					files.put(BOOK_TOPICS_FOLDER + topicFileNames.get(topic.getId()) + ".xml", topicString.getBytes());
				}
			}
		} else {
			// There are duplicates of the topic so we need to create duplicate files and alter each filename to be unique
			for (Integer i: topicMapping.keySet()) {
				
				// Create the String that will be appended to the end of the topic xml id
				String postFix = "";
				if (i != 0) {
					postFix = "-" + i;
				}
				
				// Clone the topic and add the relationships
				SpecTopic t = topicMapping.get(i);
				try {
					Document clonedDoc = XMLUtilities.cloneDocument(topicDoc);
					
					// Inject the relationships and bugzilla links
					injector.processRelationshipInjections(clonedDoc, t, topicMappings, topicIdNames, levelFileNames);
					if (injectBugzillaLinks && contentSpec.getBugzillaOptions().isBugzillaLinksEnabled()) {
						injector.addBugzillaLinksToTopic(clonedDoc, topic, contentSpec.getBugzillaOptions(), getBookDetails());
					}
					
					// Validate the XML again after the final injections
					clonedDoc = validateTopicXMLSecondPass(topic, clonedDoc, ignoreErrors);
					
					clonedDoc.getDocumentElement().setAttribute(id, topicIdNames.get(topic.getId()) + postFix);
					
					// Set other id's in the topic document so that they always stay unique
					// however leave the first one intact in case it is linked to elsewhere
					if (i > 1) {
						NodeList topicNodes = clonedDoc.getDocumentElement().getChildNodes();
						for (int j = 0; j < topicNodes.getLength(); j++) {
							if (topicNodes.item(j).getNodeType() == Node.ELEMENT_NODE) {
								Element ele = (Element) topicNodes.item(j);
								if (ele.hasAttribute("id")) {
									ele.setAttribute("id", (ele.getAttribute("id") + postFix));
								}
							}
						}
					}
					
					if (t.isInlineTopic()) {
						inlineTopics.put(topic.getId() + postFix + ".xml", clonedDoc);
					} else {
						// Get a formatted copy of the XML Document
						String docString = XMLUtilities.convertNodeToString(clonedDoc, verbatimElements, inlineElements, contentsInlineElements, true);
						final String topicString = StringUtilities.cleanTextForXML(DocbookUtils.addXMLBoilerplate(docString, this.escapedTitle + ".ent"));
	
						files.put(BOOK_TOPICS_FOLDER + topicFileNames.get(topic.getId()) + postFix + ".xml", topicString.getBytes());
					}
				} catch (Exception e) {
					log.error(ExceptionUtilities.getStackTrace(e));
				}
			}
		}
	}
	
	/**
	 * Validates the XML after the first set of injections have been processed.
	 * 
	 * @param topic The topic that is being validated.
	 * @param topicDoc A Document object that holds the Topic's XML
	 * @param ignoreErrors Whether error output should be ignored.
	 * @return The validate document or a template if it failed validation.
	 */
	private Document validateTopicXMLFirstPass(final TopicV1 topic, final Document topicDoc, boolean ignoreErrors) {
		// Validate the topic against its DTD/Schema
		SAXXMLValidator validator = new SAXXMLValidator();
		if (!validator.validateXML(topicDoc, BuilderConstants.ROCBOOK_45_DTD, dtd.getValue())) {
			String topicXML = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "FailedValidationTopic.xml");
			topicXML = topicXML.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topic.getId()));
			if (!ignoreErrors) {
				topicXML = topicXML.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "<para>Please review the compiler error for <xref linkend=\"" + BuilderConstants.ERROR_XREF_ID_PREFIX + topic.getId() + "\"/> for more detailed information.</para>");
			} else {
				topicXML = topicXML.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
			}
			errorDatabase.addError(topic, String.format(BuilderConstants.INVALID_TOPIC_XML, validator.getErrorText()));
			return XMLUtilities.convertStringToDocument(topicXML);
		}
		return topicDoc;
	}
	
	/**
	 * Validates the XML after the second set of injections have been done (Relationships and Bug Reporting Links).
	 * 
	 * @param id The unique id of the topic. Ie if there are two topics with the same id one should be "TopicID" and the other "TopicID-1"
	 * @param topicDoc A Document object that holds the Topic's XML
	 * @param ignoreErrors Whether error output should be ignored.
	 * @return The validate document or a template if it failed validation.
	 */
	private Document validateTopicXMLSecondPass(final TopicV1 topic, final Document topicDoc, final boolean ignoreErrors) {
		// Validate the topic against its DTD/Schema
		SAXXMLValidator validator = new SAXXMLValidator();
		if (!validator.validateXML(topicDoc, BuilderConstants.ROCBOOK_45_DTD, dtd.getValue())) {
			String topicXML = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "FailedInjectionTopic.xml");
			topicXML = topicXML.replaceAll(BuilderConstants.TOPIC_ID_REGEX, topic.getId().toString());
			if (!ignoreErrors) {
				topicXML = topicXML.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "<para>Please review the compiler error for <xref linkend=\"" + BuilderConstants.ERROR_XREF_ID_PREFIX + topic.getId() + "\"/> for more detailed information.</para>");
			} else {
				topicXML = topicXML.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
			}
			errorDatabase.addError(topic, String.format(BuilderConstants.FAILED_TOPIC_XML, validator.getErrorText()));
			return XMLUtilities.convertStringToDocument(topicXML);
		}
		return topicDoc;
	}
	
	/**
	 * Builds the basics of a Docbook from the resource files for a specific content specification.
	 * 
	 * @param cs The content specification object to be built.
	 * @param vairables A mapping of variables that are used as override parameters
	 * @param requester The User who requested the book be built
	 * @return A Document object to be used in generating the book.xml
	 */
	private String buildBookBase(ContentSpec cs, UserV1 requester) {
		Map<String, String> variables = builderOptions.getOverrides();
		
		String brand = cs.getBrand();
		if (brand == null) brand = "common";
		
		// Setup publican.cfg
		String publicanCfg = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "publican.cfg");
		publicanCfg = publicanCfg.replaceAll(BuilderConstants.BRAND_REGEX, brand);
		publicanCfg = publicanCfg.replaceAll(BuilderConstants.DATE_FORMAT_REGEX, getDateString());
		publicanCfg = publicanCfg.replaceAll(BuilderConstants.BUILDER_VERSION_REGEX, BuilderConstants.BUILDER_VERSION);
		if (cs.getPublicanCfg() != null) {
			publicanCfg += cs.getPublicanCfg();
		}
		files.put(BOOK_FOLDER + "publican.cfg", publicanCfg.getBytes());
		
		// Setup Book_Info.xml
		String pubsNumber = (variables.containsKey("pubsnumber") && variables.containsKey("pubsnumber")) ? variables.get("pubsnumber") : (cs.getPubsNumber() == null ? BuilderConstants.PUBSNUMBER_DEFAULT : cs.getPubsNumber().toString());
		String bookInfo = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "Book_Info.xml");
		bookInfo = bookInfo.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		bookInfo = bookInfo.replaceAll(BuilderConstants.TITLE_REGEX, cs.getTitle());
		bookInfo = bookInfo.replaceAll(BuilderConstants.SUBTITLE_REGEX, cs.getSubtitle() == null ? BuilderConstants.SUBTITLE_DEFAULT : cs.getSubtitle());
		bookInfo = bookInfo.replaceAll(BuilderConstants.PRODUCT_REGEX, cs.getProduct());
		bookInfo = bookInfo.replaceAll(BuilderConstants.VERSION_REGEX, cs.getVersion());
		bookInfo = bookInfo.replaceAll(BuilderConstants.EDITION_REGEX, cs.getEdition() == null ? BuilderConstants.EDITION_DEFAULT : cs.getEdition());
		bookInfo = bookInfo.replaceAll(BuilderConstants.PUBSNUMBER_REGEX, pubsNumber);
		bookInfo = bookInfo.replaceAll(BuilderConstants.CONTENT_SPEC_DECRIPTION_REGEX, cs.getAbstract() == null ? BuilderConstants.DEFAULT_CS_DECRIPTION : cs.getAbstract());
		files.put(BOOK_EN_US_FOLDER + "Book_Info.xml", bookInfo.getBytes());
		
		// Setup Author_Group.xml
		buildAuthorGroup();
		
		// Setup Preface.xml
		String preface = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "Preface.xml");
		preface = preface.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		files.put(BOOK_EN_US_FOLDER + "Preface.xml", preface.getBytes());
		
		// Setup Revision_History.xml
		buildRevisionHistory(requester);
		
		// Setup the <<contentSpec.title>>.ent file
		entFile = BuilderConstants.CS_NAME_ENT_FILE.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		entFile = entFile.replaceAll(BuilderConstants.PRODUCT_REGEX, cs.getProduct());
		entFile = entFile.replaceAll(BuilderConstants.TITLE_REGEX, cs.getTitle());
		entFile = entFile.replaceAll(BuilderConstants.YEAR_FORMAT_REGEX, Integer.toString(Calendar.getInstance().get(Calendar.YEAR)));
		entFile = entFile.replaceAll(BuilderConstants.CONTENT_SPEC_COPYRIGHT_REGEX, cs.getCopyrightHolder());
		entFile = entFile.replaceAll(BuilderConstants.BZPRODUCT_REGEX, cs.getBugzillaProduct() == null ? cs.getProduct() : cs.getBugzillaProduct());
		entFile = entFile.replaceAll(BuilderConstants.BZCOMPONENT_REGEX, cs.getBugzillaComponent() == null ? BuilderConstants.DEFAULT_BZCOMPONENT : cs.getBugzillaComponent());
		entFile = entFile.replaceAll(BuilderConstants.CONTENT_SPEC_BUGZILLA_URL_REGEX, "https://bugzilla.redhat.com/");
		files.put(BOOK_EN_US_FOLDER + escapedTitle + ".ent", entFile.getBytes());
		
		// Setup the icon.svg
		files.put(BOOK_IMAGES_FOLDER + "icon.svg", ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "icon.svg").getBytes());
		
		// Setup the basic book.xml
		String basicBook = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "Book.xml").replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		basicBook = basicBook.replaceAll(BuilderConstants.PRODUCT_REGEX, cs.getProduct());
		basicBook = basicBook.replaceAll(BuilderConstants.VERSION_REGEX, cs.getVersion());
		return basicBook;
	}
	
	/**
	 * Builds the Author_Group.xml using the assigned writers for topics inside of the content specification.
	 */
	private void buildAuthorGroup() {
		// Setup Author_Group.xml
		String authorGroup = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "Author_Group.xml");
		Document authorDoc = XMLUtilities.convertStringToDocument(authorGroup);
		LinkedHashMap<Integer, TagV1> authors = new LinkedHashMap<Integer, TagV1>();
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return;
		}
		
		// Get the mapping of authors using the topics inside the content spec
		for (Integer bookTopicId: bookTopics.keySet()) {
			List<TagV1> authorTags = bookTopics.get(bookTopicId).getTagsInCategoriesByID(CollectionUtilities.toArrayList(CSConstants.WRITER_CATEGORY_ID));
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
			shutdown.set(true);
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
			if (!insertedAuthor) {
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
		authorGroup = DocbookUtils.addXMLBoilerplate(XMLUtilities.convertNodeToString(authorDoc, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent");
		files.put(BOOK_EN_US_FOLDER + "Author_Group.xml", authorGroup.getBytes());
	}
	
	/**
	 * Builds the revision history using the requester of the build.
	 * 
	 * @param requester The user who requested the build action
	 */
	private void buildRevisionHistory(UserV1 requester) {
		// Replace the basic injection data inside the revision history
		String revHistory = ResourceUtilities.resourceFileToString(RESOURCE_LOCATION, "Revision_History.xml");
		revHistory = revHistory.replaceAll(BuilderConstants.ESCAPED_TITLE_REGEX, escapedTitle);
		revHistory = revHistory.replaceAll(BuilderConstants.REV_DATE_FORMAT_REGEX, getRevDateString());
		List<TagV1> authorList = reader.getTagsByName(requester.getName());
		Document revHistoryDoc;
		
		// Check if the app should be shutdown
		if (isShuttingDown.get()) {
			shutdown.set(true);
			return;
		}
		
		// An assigned writer tag exists for the User so check if there is an AuthorInformation tuple for that writer
		if (authorList.size() == 1) {
			AuthorInformation authorInfo = reader.getAuthorInformation(authorList.get(0).getId());
			if (authorInfo != null) {
				revHistoryDoc = generateRevision(revHistory, authorInfo, requester);
			} else {
				// No AuthorInformation so Use the default value
				authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME, BuilderConstants.DEFAULT_AUTHOR_LASTNAME, BuilderConstants.DEFAULT_EMAIL);
				revHistoryDoc = generateRevision(revHistory, authorInfo, requester);
			}
		// No assigned writer exists for the uploader so use default values
		} else {
			AuthorInformation authorInfo = new AuthorInformation(-1, BuilderConstants.DEFAULT_AUTHOR_FIRSTNAME, BuilderConstants.DEFAULT_AUTHOR_LASTNAME, BuilderConstants.DEFAULT_EMAIL);
			revHistoryDoc = generateRevision(revHistory, authorInfo, requester);
		}
		
		// Add the revision history to the book
		revHistory = DocbookUtils.addXMLBoilerplate(XMLUtilities.convertNodeToString(revHistoryDoc, verbatimElements, inlineElements, contentsInlineElements, true), this.escapedTitle + ".ent");
		files.put(BOOK_EN_US_FOLDER + "Revision_History.xml", revHistory.getBytes());
	}
	
	/**
	 * Fills in the information required inside of a revision tag, for the Revision_History.xml file.
	 * 
	 * @param xmlDocString An XML document represented as a string that contains key regex expressions.
	 * @param authorInfo An AuthorInformation entity object containing the details for who requested the build
	 * @param requester The user object for the build request.
	 */
	private Document generateRevision(String xmlDocString, AuthorInformation authorInfo, UserV1 requester) {
		if (authorInfo == null) return null;
		// Replace all of the regex inside the xml document
		xmlDocString = xmlDocString.replaceAll(BuilderConstants.AUTHOR_FIRST_NAME_REGEX, authorInfo.getFirstName());
		xmlDocString = xmlDocString.replaceAll(BuilderConstants.AUTHOR_SURNAME_REGEX, authorInfo.getLastName());
		xmlDocString = xmlDocString.replaceAll(BuilderConstants.AUTHOR_EMAIL_REGEX, authorInfo.getEmail() == null ? BuilderConstants.DEFAULT_EMAIL : authorInfo.getEmail());
		
		// No regex should exist so now convert it to a Document object
		Document doc = XMLUtilities.convertStringToDocument(xmlDocString);
		doc.getDocumentElement().setAttribute("id", "appe-" + escapedTitle + "-Revision_History");
		NodeList simplelistList = doc.getDocumentElement().getElementsByTagName("simplelist");
		Element simplelist;
		
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
		Element listMemberEle = doc.createElement("member");
		listMemberEle.setTextContent(String.format(BuilderConstants.BUILT_MSG, contentSpec.getId(), reader.getLatestCSRevById(CSId)) + (authorInfo.getAuthorId() > 0 ? " by " + requester.getName() : ""));
		simplelist.appendChild(listMemberEle);
		return doc;
	}
	
	/**
	 * Gets a string in the format of the current date. The format is: Day Month DD HH:MM:SS YYYY.
	 * 
	 * @return The formatted date string.
	 */
	private String getDateString() {
		Calendar cal = Calendar.getInstance();
		String output = String.format(BuilderConstants.DATE_STRING_FORMAT, cal, cal, cal, cal, cal);
		return output;
	}
	
	/**
	 * Gets a string in the format of the current date. The format is: Day Month DD HH:MM:SS YYYY.
	 * 
	 * @return The formatted date string.
	 */
	private String getRevDateString() {
		Calendar cal = Calendar.getInstance();
		String output = String.format(BuilderConstants.REV_DATE_STRING_FORMAT, cal, cal, cal, cal);
		return output;
	}
	
	/**
	 * Iterates through the node and finds any images and adds them to the books image folder
	 * 
	 * @param node The node to iterate through
	 * @return True if the images were added successfully otherwise false;
	 */
	private boolean addImagesToBookForNode(Node node, TopicV1 topic) {
		NodeList childNodes = node.getChildNodes();
		boolean valid = true;
		
		// Iterate through each child node
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node childNode = childNodes.item(i);
			
			// If the nodes name is image data then add the image
			if (childNode.getNodeName().equals("imagedata")) {
				NamedNodeMap attributes = childNode.getAttributes();
				if (attributes != null) {
					final Node fileRefAttribute = attributes.getNamedItem("fileref");
					if (fileRefAttribute != null && !fileRefAttribute.getNodeValue().startsWith("images/")) {
						fileRefAttribute.setNodeValue("images/" + fileRefAttribute.getNodeValue());
					}
					if (!addImageToBook(fileRefAttribute.getNodeValue(), topic)) return false;
				}
			} else {
				if (!addImagesToBookForNode(childNode, topic)) valid = false;
			}
		}
		return valid;
	}
	
	/**
	 * Adds an Image from the database to the books image folder
	 * 
	 * @param imageLocation The name/location of the image
	 * @return True if the image was added successfully otherwise false.
	 */
	private boolean addImageToBook(String imageLocation, TopicV1 topic) {
		final int extensionIndex = imageLocation.lastIndexOf(".");
		final int pathIndex = imageLocation.lastIndexOf("/");
		
		// check that the extension and index character was found & the path is before the extension
		if (extensionIndex != -1 && pathIndex != -1 && extensionIndex > pathIndex) {
			try {
				// The file name minus the extension should be an integer
				// that references an ImageFile record ID.
				final String imageId = imageLocation.substring(pathIndex + 1, extensionIndex);
				final ImageV1 imageFile = reader.getImageById(Integer.parseInt(imageId));

				if (imageFile != null) {
					files.put(BOOK_EN_US_FOLDER + imageLocation, imageFile.getImageData());
				} else {
					errorDatabase.addError(topic, "ImageFile ID " + imageId + " from image location " + imageLocation + " was not found!");
					return false;
				}
			} catch (Exception e) {
				errorDatabase.addError(topic, imageLocation + " is not a valid image. Must be in the format [ImageFileID].extension e.g. 123.png, or images/321.jpg");
				return false;
			}
		}
		return true;
	}
	
	/**
	 * This method does a pass over all the topics returned by the query and
	 * attempts to create unique Fixed URL if one does not already exist.
	 * 
	 * @return true if fixed url property tags were able to be created for all
	 *         topics, and false otherwise
	 *         
	 * @author mcasperson
	 */
	// Edited version of mcasperson's function
	private boolean setFixedURLsPass() {
		int tries = 0;
		boolean success = false;

		while (tries < BuilderConstants.MAXIMUM_SET_PROP_TAGS_RETRY && !success)
		{
			
			++tries;

			try
			{
				final BaseRestCollectionV1<TopicV1> updateTopics = new BaseRestCollectionV1<TopicV1>();
				
				final Set<String> processedFileNames = new HashSet<String>();

				for (Integer topicId: bookTopics.keySet())
				{
					
					// Check if the app should be shutdown
					if (isShuttingDown.get()) {
						shutdown.set(true);
						return false;
					}
					
					final TopicV1 topic = bookTopics.get(topicId);

					final PropertyTagV1 existingUniqueURL = topic.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);

					if (existingUniqueURL == null || !existingUniqueURL.isValid())
					{
						/*
						 * generate the base url
						 */
						String baseUrlName = createURLTitle(topic.getTitle());
						
						baseUrlName = baseUrlName.replaceAll("-", "_");
						while (escapedTitle.indexOf("__") != -1)
							escapedTitle = escapedTitle.replaceAll("__", "_");

						/* generate a unique fixed url */
						String postFix = "";

						for (int uniqueCount = 1; uniqueCount <= BuilderConstants.MAXIMUM_SET_PROP_TAG_NAME_RETRY; ++uniqueCount)
						{
							final String query = "query;propertyTag1=" + CommonConstants.FIXED_URL_PROP_TAG_ID + URLEncoder.encode(" " + baseUrlName + postFix, "UTF-8");
							final BaseRestCollectionV1<TopicV1> topics = restManager.getRESTClient().getJSONTopicsWithQuery(new PathSegmentImpl(query, false), "");

							if (topics.getSize() != 0 || processedFileNames.contains(baseUrlName + postFix))
							{
								postFix = uniqueCount + "";
							}
							else
							{
								break;
							}
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

				/* If we got here, then the REST update went ok */
				success = true;

				/* copy the topics fixed url properties to our local collection */
				if (updateTopics.getItems() != null && updateTopics.getItems().size() != 0)
				{
					for (final TopicV1 topicWithFixedUrl : updateTopics.getItems())
					{
						final TopicV1 topic = bookTopics.get(topicWithFixedUrl.getId());
						if (topic != null && topicWithFixedUrl.getId().equals(topic.getId()))
						{

							final PropertyTagV1 fixedUrlProp = topicWithFixedUrl.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);

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
	private String createURLTitle(String title) {
		String baseTitle = new String(title);
		
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
		String escapedTitle = StringUtilities.escapeTitle(baseTitle);
		while (escapedTitle.indexOf("__") != -1)
			escapedTitle = escapedTitle.replaceAll("__", "_");
		
		return escapedTitle;
	}
	
	/**
	 * Creates the file names that will be used when building the book
	 * 
	 * @param fixedUrlsSuccess Whether or not the topic URLs were successfully fixed.
	 */
	private void createFileNames(final boolean fixedUrlsSuccess) {
		// Create the topic file names
		for (Integer topicId: bookTopics.keySet()) {
			TopicV1 topic = bookTopics.get(topicId);
			
			/*
			 * The file names will either be the fixed url property tag, or the
			 * topic id if we could not generate unique file names
			 */
			String fileName = "";
			String topicIdName = "";
			if (fixedUrlsSuccess) {
				final PropertyTagV1 propTag = topic.getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);
				if (propTag != null) {
					fileName = propTag.getValue();
					topicIdName = fileName;
				} else {
					errorDatabase.addError(topic, "Topic does not have the fixed url property tag.");
					fileName = topic.getId().toString();
					topicIdName = "Topic" + fileName;
				}
			} else {
				fileName = topic.getId().toString();
				topicIdName = "Topic" + fileName;
			}
			topicIdNames.put(topicId, topicIdName);
			topicFileNames.put(topicId, fileName);
			bookFileNames.add(fileName);
		}
		
		// Create the level file names
		HashMap<String, List<Level>> titles = getLevelTitles(contentSpec.getBaseLevel());
		for (String title: titles.keySet()) {
			
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				shutdown.set(true);
				return;
			}
			
			// Add the title and level to the mapping of levels to titles
			if (titles.get(title).size() > 1 || bookFileNames.contains(escapedTitle)) {
				int counter = 1;
				for (Level level: titles.get(title)) {
					String escapedTitle = createURLTitle(level.getTitle());
					escapedTitle = level.getType().getTitle() + "-" + escapedTitle;
					levelFileNames.put(level, escapedTitle + "-" + counter);
					bookFileNames.add(escapedTitle + "-" + counter);
					counter++;
				}
			} else {
				Level level = titles.get(title).get(0);
				String escapedTitle = createURLTitle(level.getTitle());
				escapedTitle = level.getType().getTitle() + "-" + escapedTitle;
				levelFileNames.put(level, escapedTitle);
				bookFileNames.add(escapedTitle);
			}
		}
	}
	
	/**
	 * Gets a mapping of titles to levels for the level and all of its children.
	 * 
	 * @param level The Level to get the list of titles for.
	 * @return A Mapping of Titles to Levels.
	 */
	private HashMap<String, List<Level>> getLevelTitles(Level level) {
		HashMap<String, List<Level>> titles = new HashMap<String, List<Level>>();
		
		// Add the current title to the list of titles
		if (!titles.containsKey(level.getTitle().toLowerCase())) {
			titles.put(level.getTitle().toLowerCase(), new ArrayList<Level>());
		}
		titles.get(level.getTitle().toLowerCase()).add(level);
		
		// Add all the child levels titles to the list
		for (Level childLevel: level.getChildLevels()) {
			
			// Check if the app should be shutdown
			if (isShuttingDown.get()) {
				shutdown.set(true);
				return new HashMap<String, List<Level>>();
			}
			
			HashMap<String, List<Level>> childTitles = getLevelTitles(childLevel);
			for (String childTitle: childTitles.keySet()) {
				if (titles.containsKey(childTitle)) {
					titles.get(childTitle).addAll(childTitles.get(childTitle));
				} else {
					titles.put(childTitle, childTitles.get(childTitle));
				}
			}
		}
		return titles;
	}
	
	private String getBookDetails() {
		return "Book: " + contentSpec.getTitle() + "\nEdition: " + (contentSpec.getEdition() == null ? "" : contentSpec.getEdition()) + "\nVersion: " + contentSpec.getVersion();
	}
}
