package com.redhat.contentspec.builder;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.code.regexp.NamedMatcher;
import com.google.code.regexp.NamedPattern;
import com.redhat.contentspec.builder.constants.BuilderConstants;
import com.redhat.contentspec.builder.sort.ExternalMapSort;
import com.redhat.contentspec.builder.sort.MapTopicTitleSorter;
import com.redhat.contentspec.builder.utils.DocbookUtils;
import com.redhat.contentspec.builder.utils.XMLUtilities;
import com.redhat.contentspec.constants.CSConstants;
import com.redhat.contentspec.rest.RESTReader;
import com.redhat.contentspec.Level;
import com.redhat.contentspec.entities.TargetRelationship;
import com.redhat.contentspec.SpecTopic;
import com.redhat.contentspec.entities.TopicRelationship;
import com.redhat.contentspec.entities.BugzillaOptions;
import com.redhat.contentspec.entities.InjectionOptions;
import com.redhat.ecs.commonutils.ExceptionUtilities;
import com.redhat.ecs.constants.CommonConstants;
import com.redhat.ecs.services.docbookcompiling.DocbookBuilderConstants;
import com.redhat.ecs.services.docbookcompiling.xmlprocessing.structures.InjectionTopicData;
import com.redhat.topicindex.component.docbookrenderer.structures.TopicErrorDatabase;
import com.redhat.topicindex.rest.entities.PropertyTagV1;
import com.redhat.topicindex.rest.entities.TagV1;
import com.redhat.topicindex.rest.entities.TopicV1;

/**
 * A class to process the Skynet injections that are used with in a Topic's XML data. The injections that are possible are:
 * Inject:
 * InjectSequence:
 * InjectAlphaSort:
 * InjectList:
 * InjectListItems:
 * 
 * It will also process Content Specification relationship injection.
 * 
 * @author Matt Casperson
 * @author lee
 */
public class TopicInjector {

	/*
	 * These constants define the docbook tags that are used to wrap up xrefs in
	 * custom injection points
	 */

	/**
	 * Used to identify that an <orderedlist> should be generated for the
	 * injection point
	 */
	protected static final int ORDEREDLIST_INJECTION_POINT = 1;
	/**
	 * Used to identify that an <itemizedlist> should be generated for the
	 * injection point
	 */
	protected static final int ITEMIZEDLIST_INJECTION_POINT = 2;
	/**
	 * Used to identify that an <xref> should be generated for the injection
	 * point
	 */
	protected static final int XREF_INJECTION_POINT = 3;
	/**
	 * Used to identify that an <xref> should be generated for the injection
	 * point
	 */
	protected static final int LIST_INJECTION_POINT = 4;

	/** Defines how many related tasks to show on the nav page */
	protected static final int MAX_RELATED_TASKS = 5;
	
	/*
	 * These regular expressions define the format of the custom injection
	 * points
	 */

	/** Identifies a named regular expression group */
	protected static final String TOPICIDS_RE_NAMED_GROUP = "TopicIDs";
	/** This text identifies an option task in a list */
	protected static final String OPTIONAL_MARKER = "OPT:";
	/** The text to be prefixed to a list item if a topic is optional */
	protected static final String OPTIONAL_LIST_PREFIX = "Optional: ";
	/** A regular expression that identifies a topic id */
	protected static final String TOPIC_ID_RE = "(" + OPTIONAL_MARKER + "\\s*)?\\d+";
	
	/**
	 * A regular expression that matches an InjectSequence custom injection
	 * point
	 */
	protected static final String CUSTOM_INJECTION_SEQUENCE_RE = "\\s*InjectSequence:\\s*" + // start
																								// xml
																								// comment
																								// and
																								// 'InjectSequence:'
																								// surrounded
																								// by
																								// optional
																								// white
																								// space
			"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(\\s*" + TOPIC_ID_RE + "\\s*,)*(\\s*" + TOPIC_ID_RE + ",?))" + // an
																												// optional
																												// comma
																												// separated
																												// list
																												// of
																												// digit
																												// blocks,
																												// and
																												// at
																												// least
																												// one
																												// digit
																												// block
																												// with
																												// an
																												// optional
																												// comma
			"\\s*"; // xml comment end
	/** A regular expression that matches an InjectList custom injection point */
	protected static final String CUSTOM_INJECTION_LIST_RE = "\\s*InjectList:\\s*" + // start
																						// xml
																						// comment
																						// and
																						// 'InjectList:'
																						// surrounded
																						// by
																						// optional
																						// white
																						// space
			"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(\\s*" + TOPIC_ID_RE + "\\s*,)*(\\s*" + TOPIC_ID_RE + ",?))" + // an
																												// optional
																												// comma
																												// separated
																												// list
																												// of
																												// digit
																												// blocks,
																												// and
																												// at
																												// least
																												// one
																												// digit
																												// block
																												// with
																												// an
																												// optional
																												// comma
			"\\s*"; // xml comment end
	protected static final String CUSTOM_INJECTION_LISTITEMS_RE = "\\s*InjectListItems:\\s*" + // start
																								// xml
																								// comment
																								// and
																								// 'InjectList:'
																								// surrounded
																								// by
																								// optional
																								// white
																								// space
			"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(\\s*" + TOPIC_ID_RE + "\\s*,)*(\\s*" + TOPIC_ID_RE + ",?))" + // an
																												// optional
																												// comma
																												// separated
																												// list
																												// of
																												// digit
																												// blocks,
																												// and
																												// at
																												// least
																												// one
																												// digit
																												// block
																												// with
																												// an
																												// optional
																												// comma
			"\\s*"; // xml comment end
	protected static final String CUSTOM_ALPHA_SORT_INJECTION_LIST_RE = "\\s*InjectListAlphaSort:\\s*" + // start
																											// xml
																											// comment
																											// and
																											// 'InjectListAlphaSort:'
																											// surrounded
																											// by
																											// optional
																											// white
																											// space
			"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(\\s*" + TOPIC_ID_RE + "\\s*,)*(\\s*" + TOPIC_ID_RE + ",?))" + // an
																												// optional
																												// comma
																												// separated
																												// list
																												// of
																												// digit
																												// blocks,
																												// and
																												// at
																												// least
																												// one
																												// digit
																												// block
																												// with
																												// an
																												// optional
																												// comma
			"\\s*";
	/** A regular expression that matches an Inject custom injection point */
	protected static final String CUSTOM_INJECTION_SINGLE_RE = "\\s*Inject:\\s*" + // start
																					// xml
																					// comment
																					// and
																					// 'Inject:'
																					// surrounded
																					// by
																					// optional
																					// white
																					// space
			"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(" + TOPIC_ID_RE + "))" + // one
																			// digit
																			// block
			"\\s*"; // xml comment end
	
	
	/** A regular expression that matches an Inject Content Fragment */
	protected static final String INJECT_CONTENT_FRAGMENT_RE =
	/* start xml comment and 'Inject:' surrounded by optional white space */
	"\\s*InjectText:\\s*" +
	/* one digit block */
	"(?<" + TOPICIDS_RE_NAMED_GROUP + ">(" + TOPIC_ID_RE + "))" +
	/* xml comment end */
	"\\s*";
	
	/**
	 * The noinject value for the role attribute indicates that an element
	 * should not be included in the Topic Fragment
	 */
	protected static final String NO_INJECT_ROLE = "noinject";
	
	// A mapping of all the topics inside of a content specification/book
	protected final HashMap<Integer, TopicV1> bookTopics;
	// Whether or not to process injection as if they are in a book or individually
	private boolean bookProcessing = true;
	private Integer revision = null;
	private final RESTReader reader;
	private InjectionOptions injectionOptions;
	private final TopicErrorDatabase errorDatabase;
	
	public TopicInjector(HashMap<Integer, TopicV1> bookTopics, RESTReader reader, InjectionOptions injectionOptions, TopicErrorDatabase errorDatabase) {
		this.bookTopics = bookTopics;
		this.reader = reader;
		this.injectionOptions = injectionOptions;
		this.errorDatabase = errorDatabase;
	}
	
	public TopicInjector(Integer revision, RESTReader reader, InjectionOptions injectionOptions) {
		bookProcessing = false;
		bookTopics = new HashMap<Integer, TopicV1>();
		this.revision = revision;
		this.reader = reader;
		this.injectionOptions = injectionOptions;
		this.errorDatabase = new TopicErrorDatabase();
	}
	
	/**
	 * Process the injections in a topic to place the relevant injection sequences. This ignores all checks to see if the topics are related in skynet.
	 * 
	 * @param topic The topic entity object to be processed.
	 * @param topicDocument The topic xml initialised as a Document object
	 * @return True if everything injected without errors otherwise false
	 */
	public boolean processTopicInjections(TopicV1 topic, Document topicDocument, final boolean usedFixedUrls) {
		// Check that injection is allowed for the topic based on the topic types
		List<Integer> catIds = new ArrayList<Integer>();
		catIds.add(CSConstants.TYPE_CATEGORY_ID);
		List<TagV1> topicTypeTags = topic.getTagsInCategoriesByID(catIds);
		boolean injectionAllowed = false;
		for (TagV1 tag: topicTypeTags) {
			if (injectionOptions.isInjectionAllowedForType(tag.getName())) {
				injectionAllowed = true;
			}
		}
		if (!injectionOptions.isInjectionAllowed() || !injectionAllowed) {
			return true;
		}
		
		Integer topicID = topic.getId();
		
		/***************** PROCESS CUSTOM INJECTION POINTS *****************/

		// keep a track of the topics we inject into custom
		// locations, so we
		// don't then inject them again
		final ArrayList<Integer> customInjectionIds = new ArrayList<Integer>();
		
		// this collection keeps a track of the injection point
		// markers
		// and the docbook lists that we will be replacing them
		// with
		final HashMap<Node, InjectionListData> customInjections = new HashMap<Node, InjectionListData>();
		
		String injectionErrors = processCustomInjectionPoints(customInjectionIds, customInjections, ORDEREDLIST_INJECTION_POINT, topicID, topic, topicDocument, CUSTOM_INJECTION_SEQUENCE_RE, null, usedFixedUrls);
		injectionErrors += processCustomInjectionPoints(customInjectionIds, customInjections, XREF_INJECTION_POINT, topicID, topic, topicDocument, CUSTOM_INJECTION_SINGLE_RE, null, usedFixedUrls);
		injectionErrors += processCustomInjectionPoints(customInjectionIds, customInjections, ITEMIZEDLIST_INJECTION_POINT, topicID, topic, topicDocument, CUSTOM_INJECTION_LIST_RE, null, usedFixedUrls);
		injectionErrors += processCustomInjectionPoints(customInjectionIds, customInjections, ITEMIZEDLIST_INJECTION_POINT, topicID, topic, topicDocument, CUSTOM_ALPHA_SORT_INJECTION_LIST_RE, new MapTopicTitleSorter(), usedFixedUrls);
		injectionErrors += processCustomInjectionPoints(customInjectionIds, customInjections, LIST_INJECTION_POINT, topicID, topic, topicDocument, CUSTOM_INJECTION_LISTITEMS_RE, null, usedFixedUrls);
		injectionErrors += processTopicContentFragments(topic, topicDocument);
		
		if (injectionErrors.length() != 0)
		{
			// remove the last ", " from the error string
			injectionErrors = injectionErrors.substring(0, injectionErrors.length() - 2);
			errorDatabase.addError(topic, "references Topic(s) "+ injectionErrors
									+ " in a custom injection point, but this topic was not in the content specification.");
			return false;
		}
		else
		{
			// now make the custom injection point substitutions
			for (final Node customInjectionCommentNode : customInjections.keySet())
			{
				final InjectionListData injectionListData = customInjections.get(customInjectionCommentNode);
				List<Element> list = null;

				// this may not be true if we are not building
				// all related topics
				if (injectionListData.listItems.size() != 0)
				{
					if (injectionListData.listType == ORDEREDLIST_INJECTION_POINT)
					{
						list = DocbookUtils.wrapOrderedListItemsInPara(topicDocument, injectionListData.listItems);
					}
					else if (injectionListData.listType == XREF_INJECTION_POINT)
					{
						list = injectionListData.listItems.get(0);
					}
					else if (injectionListData.listType == ITEMIZEDLIST_INJECTION_POINT)
					{
						list = DocbookUtils.wrapItemizedListItemsInPara(topicDocument, injectionListData.listItems);
					}
					else if (injectionListData.listType == LIST_INJECTION_POINT)
					{
						list = DocbookUtils.wrapItemsInListItems(topicDocument, injectionListData.listItems);
					}
				}

				if (list != null)
				{
					for (final Element element : list)
					{
						customInjectionCommentNode.getParentNode().insertBefore(element, customInjectionCommentNode);
					}

					customInjectionCommentNode.getParentNode().removeChild(customInjectionCommentNode);
				}
			}
		}
		return true;
	}
	
	/**
	 * Searches the given XML for an injection point, as defined by the
	 * regularExpression parameter. Those topics listed in the injection point
	 * are processed, and the injection point marker (i.e. the xml comment) is
	 * replaced with a list of xrefs.
	 * 
	 * @return a list of topics that were injected but not related
	 */
	protected String processCustomInjectionPoints(final ArrayList<Integer> customInjectionIds, final HashMap<Node, InjectionListData> customInjections, final int injectionPointType, final Integer topidID, TopicV1 topicData, Document topicDoc, final String regularExpression,
			final ExternalMapSort<Integer, TopicV1, InjectionTopicData> sortComparator, final boolean usedFixedUrls)
	{
		final Document xmlDocument = topicDoc;

		String injectionErrors = "";

		// loop over all of the comments in the document
		for (final Node comment : XMLUtilities.getComments(xmlDocument))
		{
			final String commentContent = comment.getNodeValue();

			// compile the regular expression
			final NamedPattern injectionSequencePattern = NamedPattern.compile(regularExpression);
			// find any matches
			final NamedMatcher injectionSequencematcher = injectionSequencePattern.matcher(commentContent);

			// loop over the regular expression matches
			while (injectionSequencematcher.find())
			{
				// get the list of topics from the named group in the regular
				// expression match
				final String reMatch = injectionSequencematcher.group(TOPICIDS_RE_NAMED_GROUP);

				// make sure we actually found a matching named group
				if (reMatch != null)
				{
					// get the sequence of ids
					final List<InjectionTopicData> sequenceIDs = processTopicIdList(reMatch);
					
					// If not processing a book then add the topics to the bookTopics that are used through injection
					if (!bookProcessing) {
						for (final InjectionTopicData sequenceID : sequenceIDs) {
							if (!bookTopics.containsKey(sequenceID.topicId)) {
								TopicV1 seqTopic = reader.getTopicById(sequenceID.topicId, revision);
								if (seqTopic != null) {
									bookTopics.put(seqTopic.getId(), seqTopic);
								}
							}
						}
					}
					
					// sort the InjectionTopicData list of required
					if (sortComparator != null)
						sortComparator.sort(bookTopics, sequenceIDs);

					// loop over all the topic ids in the injection point
					for (final InjectionTopicData sequenceID : sequenceIDs)
					{
						// right now it is possible to list a topic in an
						// injection point without it being
						// related in the database. if that is the case, we will
						// report an error
						boolean foundSequenceID = false;

						// look for the topic mentioned in the injection point
						// in the list of related topics
						if (bookTopics.containsKey(sequenceID.topicId))
						{
							
							// this injected topic is also related, so we don't
							// need to generate an error
							foundSequenceID = true;
	
							// topics that are injected into custom injection
							// points are excluded from the
							// generic related topic lists at the beginning and
							// end of a topic. adding the
							// topic id here means that when it comes time to
							// generate the generic related
							// topic lists, we can skip this topic
							customInjectionIds.add(sequenceID.topicId);

							// we have found the related topic, so build our
							// list
							List<List<Element>> list = new ArrayList<List<Element>>();

							// each related topic is added to a string, which is
							// stored in the
							// customInjections collection. the customInjections
							// key is the
							// custom injection text from the source xml. this
							// allows us to
							// match the xrefs we are generating for the related
							// topic with the
							// text in the xml file that these xrefs will
							// eventually replace
							if (customInjections.containsKey(comment))
								list = customInjections.get(comment).listItems;
							

							// wrap the xref up in a listitem if the injections are being processed in a book
							if (bookProcessing) {
								if (sequenceID.optional)
								{
									if (usedFixedUrls)
									{
										final PropertyTagV1 propTag = bookTopics.get(sequenceID.topicId).getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);
										if (propTag != null)
										{
											list.add(DocbookUtils.buildEmphasisPrefixedXRef(xmlDocument, OPTIONAL_LIST_PREFIX, propTag.getValue()));
										}
										else
										{
											list.add(DocbookUtils.buildEmphasisPrefixedXRef(xmlDocument, OPTIONAL_LIST_PREFIX, bookTopics.get(sequenceID.topicId).getXRefID()));
										}
									}
									else
									{
										list.add(DocbookUtils.buildEmphasisPrefixedXRef(xmlDocument, OPTIONAL_LIST_PREFIX, bookTopics.get(sequenceID.topicId).getXRefID()));
									}
								}
								else
								{
									if (usedFixedUrls)
									{
										final PropertyTagV1 propTag = bookTopics.get(sequenceID.topicId).getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);
										if (propTag != null)
										{
											list.add(DocbookUtils.buildXRef(xmlDocument, propTag.getValue()));
										}
										else
										{
											list.add(DocbookUtils.buildXRef(xmlDocument, bookTopics.get(sequenceID.topicId).getXRefID()));
										}
									}
									else
									{
										list.add(DocbookUtils.buildXRef(xmlDocument, bookTopics.get(sequenceID.topicId).getXRefID()));
									}
								}
							// Single topic injection processing
							} else {
								// wrap the topic title in <literal> xml nodes
								String topicTitle = generateTopicInjectionTitle(bookTopics.get(sequenceID.topicId).getTitle());
								if (sequenceID.optional) {
									list.add(DocbookUtils.buildEmphasisPrefixedLiteral(xmlDocument, OPTIONAL_LIST_PREFIX, topicTitle));
								} else {
									list.add(DocbookUtils.buildLiteral(xmlDocument, topicTitle));
								}
								if (sequenceID.optional)
								{
									if (usedFixedUrls)
									{
										final PropertyTagV1 propTag = bookTopics.get(sequenceID.topicId).getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);
										if (propTag != null)
										{
											list.add(DocbookUtils.buildEmphasisPrefixedXRef(xmlDocument, OPTIONAL_LIST_PREFIX, propTag.getValue()));
										}
										else
										{
											list.add(DocbookUtils.buildEmphasisPrefixedXRef(xmlDocument, OPTIONAL_LIST_PREFIX, topicTitle));
										}
									}
									else
									{
										list.add(DocbookUtils.buildEmphasisPrefixedXRef(xmlDocument, OPTIONAL_LIST_PREFIX, topicTitle));
									}
								}
								else
								{
									if (usedFixedUrls)
									{
										final PropertyTagV1 propTag = bookTopics.get(sequenceID.topicId).getProperty(CommonConstants.FIXED_URL_PROP_TAG_ID);
										if (propTag != null)
										{
											list.add(DocbookUtils.buildXRef(xmlDocument, propTag.getValue()));
										}
										else
										{
											list.add(DocbookUtils.buildXRef(xmlDocument, topicTitle));
										}
									}
									else
									{
										list.add(DocbookUtils.buildXRef(xmlDocument, topicTitle));
									}
								}
							}

							// save the changes back into the customInjections
							// collection
							customInjections.put(comment, new InjectionListData(list, injectionPointType));
						}

						if (!foundSequenceID)
						{
							// the topic referenced in the custom injection
							// point was not related in the database,
							// so report an error
							injectionErrors += sequenceID.topicId + ", ";
						}
					}
				}
			}
		}

		return injectionErrors;
	}
	
	/**
	 * Takes a comma separated list of ints, and returns an array of Integers.
	 * This is used when processing custom injection points.
	 */
	protected List<InjectionTopicData> processTopicIdList(final String list)
	{
		// find the individual topic ids
		final String[] topicIDs = list.split(",");

		List<InjectionTopicData> retValue = new ArrayList<InjectionTopicData>(topicIDs.length);

		// clean the topic ids
		for (int i = 0; i < topicIDs.length; ++i)
		{
			final String topicId = topicIDs[i].replaceAll(OPTIONAL_MARKER, "").trim();
			final boolean optional = topicIDs[i].indexOf(OPTIONAL_MARKER) != -1;

			try
			{
				final InjectionTopicData topicData = new InjectionTopicData(Integer.parseInt(topicId), optional);
				retValue.add(topicData);
			}
			catch (final Exception ex)
			{
				/*
				 * these lists are discovered by a regular expression so we
				 * shouldn't have any trouble here with Integer.parse
				 */
				ExceptionUtilities.handleException(ex);
				retValue.add(new InjectionTopicData(-1, false));
			}
		}

		return retValue;
	}
	
	/**
	 * Adds the "Report a Bug" link to the end of topics
	 * 
	 * @param document The XML document object that is being processed for injections
	 * @param topic The topic that the document was created from.
	 */
	public boolean addBugzillaLinksToTopic(final Document document, final TopicV1 topic, final BugzillaOptions bzOptions, final String bookDetails) {	
		
		final Element bugzillaSection = document.createElement("simplesect");
		final Element bugzillaSectionTitle = document.createElement("title");
		bugzillaSectionTitle.setTextContent("");
		bugzillaSection.appendChild(bugzillaSectionTitle);

		/* BUGZILLA LINK */
		try
		{
			final String instanceNameProperty = System.getProperty(CommonConstants.INSTANCE_NAME_PROPERTY);
			final String fixedInstanceNameProperty = instanceNameProperty == null ? "Not Defined" : instanceNameProperty;

			final Element bugzillaPara = document.createElement("para");
			bugzillaPara.setAttribute("role", DocbookBuilderConstants.ROLE_CREATE_BUG_PARA);

			final Element bugzillaULink = document.createElement("ulink");

			bugzillaULink.setTextContent("Report a bug");

			String bugzillaProduct = null;
			String bugzillaComponent = null;
			String bugzillaVersion = null;
			String bugzillaKeywords = null;
			String bugzillaAssignedTo = null;
			final String bugzillaBuildID = URLEncoder.encode(topic.getBugzillaBuildId(), "UTF-8");
			final String bugzillaEnvironment = URLEncoder.encode("Instance Name: " + fixedInstanceNameProperty + "\nCSProcessor Builder: " + BuilderConstants.BUILDER_VERSION + "\n" + bookDetails, "UTF-8");

			/* look for the bugzilla options */
			if (topic.getTags() != null && topic.getTags().getItems() != null)
			{
				for (final TagV1 tag : topic.getTags().getItems())
				{
					final PropertyTagV1 bugzillaProductTag = tag.getProperty(CommonConstants.BUGZILLA_PRODUCT_PROP_TAG_ID);
					final PropertyTagV1 bugzillaComponentTag = tag.getProperty(CommonConstants.BUGZILLA_COMPONENT_PROP_TAG_ID);
					final PropertyTagV1 bugzillaKeywordsTag = tag.getProperty(CommonConstants.BUGZILLA_KEYWORDS_PROP_TAG_ID);
					final PropertyTagV1 bugzillaVersionTag = tag.getProperty(CommonConstants.BUGZILLA_VERSION_PROP_TAG_ID);
					final PropertyTagV1 bugzillaAssignedToTag = tag.getProperty(CommonConstants.BUGZILLA_PROFILE_PROPERTY);

					if (bugzillaProduct == null && bugzillaProductTag != null)
						bugzillaProduct = URLEncoder.encode(bugzillaProductTag.getValue(), "UTF-8");

					if (bugzillaComponent == null && bugzillaComponentTag != null)
						bugzillaComponent = URLEncoder.encode(bugzillaComponentTag.getValue(), "UTF-8");

					if (bugzillaKeywords == null && bugzillaKeywordsTag != null)
						bugzillaKeywords = URLEncoder.encode(bugzillaKeywordsTag.getValue(), "UTF-8");

					if (bugzillaVersion == null && bugzillaVersionTag != null)
						bugzillaVersion = URLEncoder.encode(bugzillaVersionTag.getValue(), "UTF-8");

					if (bugzillaAssignedTo == null && bugzillaAssignedToTag != null)
						bugzillaAssignedTo = URLEncoder.encode(bugzillaAssignedToTag.getValue(), "UTF-8");
				}
			}
			
			/* build the bugzilla url options */
			String bugzillaURLComponents = "";
			
			/* check the content spec options first */
			if (bzOptions.getProduct() != null){
				
				bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
				bugzillaURLComponents += "product=" + URLEncoder.encode(bzOptions.getProduct(), "UTF-8");

				if (bzOptions.getComponent() != null)
				{
					bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
					bugzillaURLComponents += "component=" + URLEncoder.encode(bzOptions.getComponent(), "UTF-8");
				}

				if (bzOptions.getVersion() != null)
				{
					bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
					bugzillaURLComponents += "version=" + URLEncoder.encode(bzOptions.getVersion(), "UTF-8");
				}
				
				if (bugzillaAssignedTo != null)
				{
					bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
					bugzillaURLComponents += "assigned_to=" + bugzillaAssignedTo;
				}
				
				bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
				bugzillaURLComponents += "cf_environment=" + bugzillaEnvironment;

				bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
				bugzillaURLComponents += "cf_build_id=" + bugzillaBuildID;
			}
			/* we need at least a product*/
			else if (bugzillaProduct != null)
			{
				bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
				bugzillaURLComponents += "product=" + bugzillaProduct;

				if (bugzillaComponent != null)
				{
					bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
					bugzillaURLComponents += "component=" + bugzillaComponent;
				}

				if (bugzillaVersion != null)
				{
					bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
					bugzillaURLComponents += "version=" + bugzillaVersion;
				}

				if (bugzillaKeywords != null)
				{
					bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
					bugzillaURLComponents += "keywords=" + bugzillaKeywords;
				}

				if (bugzillaAssignedTo != null)
				{
					bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
					bugzillaURLComponents += "assigned_to=" + bugzillaAssignedTo;
				}

				bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
				bugzillaURLComponents += "cf_environment=" + bugzillaEnvironment;

				bugzillaURLComponents += bugzillaURLComponents.isEmpty() ? "?" : "&amp;";
				bugzillaURLComponents += "cf_build_id=" + bugzillaBuildID;
				
			}

			String bugzillaUrl = "https://bugzilla.redhat.com/enter_bug.cgi" + bugzillaURLComponents;
			bugzillaULink.setAttribute("url", bugzillaUrl);

			/*
			 * only add the elements to the XML DOM if there was no exception
			 * (not that there should be one
			 */
			bugzillaSection.appendChild(bugzillaPara);
			bugzillaPara.appendChild(bugzillaULink);
			document.getDocumentElement().appendChild(bugzillaSection);
		} catch (final Exception e){
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	private String processTopicContentFragments(final TopicV1 topic, final Document xmlDocument)
	{
		String injectionErrors = "";
		
		if (xmlDocument == null)
			return injectionErrors;

		final Map<Node, ArrayList<Node>> replacements = new HashMap<Node, ArrayList<Node>>();

		/* loop over all of the comments in the document */
		for (final Node comment : XMLUtilities.getComments(xmlDocument))
		{
			final String commentContent = comment.getNodeValue();

			/* compile the regular expression */
			final NamedPattern injectionSequencePattern = NamedPattern.compile(INJECT_CONTENT_FRAGMENT_RE);
			/* find any matches */
			final NamedMatcher injectionSequencematcher = injectionSequencePattern.matcher(commentContent);

			/* loop over the regular expression matches */
			while (injectionSequencematcher.find())
			{
				/*
				 * get the list of topics from the named group in the regular
				 * expression match
				 */
				final String reMatch = injectionSequencematcher.group(TOPICIDS_RE_NAMED_GROUP);

				/* make sure we actually found a matching named group */
				if (reMatch != null)
				{
					try
					{
						if (!replacements.containsKey(comment))
							replacements.put(comment, new ArrayList<Node>());

						final Integer topicID = Integer.parseInt(reMatch);

						/*
						 * make sure the topic we are trying to inject has been
						 * related
						 */
						if (topic.isRelatedTo(topicID))
						{
							final TopicV1 relatedTopic = topic.getRelatedTopicByID(topicID);
							final Document relatedTopicXML = XMLUtilities.convertStringToDocument(relatedTopic.getXml());
							if (relatedTopicXML != null)
							{
								final Node relatedTopicDocumentElement = relatedTopicXML.getDocumentElement();
								final Node importedXML = xmlDocument.importNode(relatedTopicDocumentElement, true);

								/* ignore the section title */
								final NodeList sectionChildren = importedXML.getChildNodes();
								for (int i = 0; i < sectionChildren.getLength(); ++i)
								{
									final Node node = sectionChildren.item(i);
									if (node.getNodeName().equals("title"))
									{
										importedXML.removeChild(node);
										break;
									}
								}

								/* remove all with a role="noinject" attribute */
								removeNoInjectElements(importedXML);

								/*
								 * importedXML is a now section with no title,
								 * and no child elements with the noinject value
								 * on the role attribute. We now add its
								 * children to the Array in the replacements
								 * Map.
								 */

								final NodeList remainingChildren = importedXML.getChildNodes();
								for (int i = 0; i < remainingChildren.getLength(); ++i)
								{
									final Node child = remainingChildren.item(i);
									replacements.get(comment).add(child);
								}
							}
						}
						else
						{
							injectionErrors += reMatch + ", ";
						}
					}
					catch (final Exception ex)
					{
						ExceptionUtilities.handleException(ex);
					}
				}
			}
		}

		/*
		 * The replacements map now has a keyset of the comments mapped to a
		 * collection of nodes that the comment will be replaced with
		 */

		for (final Node comment : replacements.keySet())
		{
			final ArrayList<Node> replacementNodes = replacements.get(comment);
			for (final Node replacementNode : replacementNodes)
				comment.getParentNode().insertBefore(replacementNode, comment);
			comment.getParentNode().removeChild(comment);
		}

		return injectionErrors;
	}
	
	private void removeNoInjectElements(final Node parent)
	{
		final NodeList childrenNodes = parent.getChildNodes();
		final ArrayList<Node> removeNodes = new ArrayList<Node>();

		for (int i = 0; i < childrenNodes.getLength(); ++i)
		{
			final Node node = childrenNodes.item(i);
			final NamedNodeMap attributes = node.getAttributes();
			if (attributes != null)
			{
				final Node roleAttribute = attributes.getNamedItem("role");
				if (roleAttribute != null)
				{
					final String[] roles = roleAttribute.getTextContent().split(",");
					for (final String role : roles)
					{
						if (role.equals(NO_INJECT_ROLE))
						{
							removeNodes.add(node);
							break;
						}
					}
				}
			}
		}

		for (final Node removeNode : removeNodes)
			parent.removeChild(removeNode);

		final NodeList remainingChildrenNodes = parent.getChildNodes();

		for (int i = 0; i < remainingChildrenNodes.getLength(); ++i)
		{
			final Node child = remainingChildrenNodes.item(i);
			removeNoInjectElements(child);
		}
	}
	
	private String generateTopicInjectionTitle(String title) {
		return "Injected: " + title;
	}
	
	/**
	 * Processes a specific topic for a Content Spec topic and adds in the relates, prerequisites, next & prev attributes to the XML data.
	 * 
	 * @param doc The XML Document object that represents the topic XML data.
	 * @param topic The topic the injections are being processed for.
	 * @param topicMappings A map of topics that exist in a content specification.
	 * @param relationshipHandler The RelationshipManager used with the content specification processor.
	 */
	public void processRelationshipInjections(Document doc, SpecTopic topic, HashMap<Integer, HashMap<Integer, SpecTopic>> topicMappings, HashMap<Integer, String> topicIdNames, HashMap<Level, String> levelIdNames) {
		// Process Prerequisites first since they need to be under the prev and title nodes
		processPrerequisites(doc, topic, topicMappings, topicIdNames, levelIdNames);
		// Process the previous relationships
		processPrevRelationships(doc, topic, topicMappings, topicIdNames, levelIdNames);
		// Attempt to get the next topic and process it
		processNextRelationships(doc, topic, topicMappings, topicIdNames, levelIdNames);
		// Process relationships and put them at the bottom of the XML 
		processRelationships(doc, topic, topicMappings, topicIdNames, levelIdNames);
	}
	
	private void generateLinkend(Element xrefItem, SpecTopic topic, HashMap<Integer, HashMap<Integer, SpecTopic>> topicMappings, HashMap<Integer, String> topicIdNames) {
		if (topicMappings.get(topic.getDBId()).size() > 1) {
			for (Integer i: topicMappings.get(topic.getDBId()).keySet()) {
				if (topicMappings.get(topic.getDBId()).get(i).equals(topic)) {
					
					// Create the String that will be appended to the end of the topic xml id
					String postFix = "";
					if (i != 0) {
						postFix = "-" + i;
					}
					
					xrefItem.setAttribute("linkend", topicIdNames.get(topic.getDBId()) + postFix);
					break;
				}
			}
		} else {
			xrefItem.setAttribute("linkend", topicIdNames.get(topic.getDBId()));
		}
	}
	
	private boolean processPrevRelationships(Document doc, SpecTopic topic, HashMap<Integer, HashMap<Integer, SpecTopic>> topicMappings, HashMap<Integer, String> topicIdNames, HashMap<Level, String> levelIdNames) {
		if (topic.getPrevTopicRelationships().isEmpty()) return false;
		// Get the title element so that it can be used later to add the prev topic node
		Element titleEle = null;
		NodeList titleList = doc.getDocumentElement().getElementsByTagName("title");
		for (int i = 0; i < titleList.getLength(); i++) {
			if (titleList.item(i).getParentNode().equals(doc.getDocumentElement())) {
				titleEle = (Element)titleList.item(i);
				break;
			}
		}
		if (titleEle != null) {
			// Attempt to get the previous topic and process it
			List<TopicRelationship> prevList = topic.getPrevTopicRelationships();
			// Create the paragraph/itemizedlist and list of previous relationships.
			Element rootEle = null;
			rootEle = doc.createElement("itemizedlist");
			// Create the title
			Element linkTitleEle = doc.createElement("title");
			linkTitleEle.setAttribute("role", "process-previous-title");
			if (prevList.size() > 1) {
				linkTitleEle.setTextContent("Previous Steps in ");
			} else {
				linkTitleEle.setTextContent("Previous Step in ");
			}
			Element titleXrefItem = doc.createElement("link");
			titleXrefItem.setTextContent(topic.getParent().getTitle());
			titleXrefItem.setAttribute("linkend", levelIdNames.get(topic.getParent()));
			linkTitleEle.appendChild(titleXrefItem);
			rootEle.appendChild(linkTitleEle);
			
			for (TopicRelationship prev: prevList) {
				Element prevEle = doc.createElement("para");
				SpecTopic prevTopic = prev.getSecondaryRelationship();
				prevEle.setAttribute("role", "process-previous-link");
				// Add the previous element to either the list or paragraph
				// Create the link element
				Element xrefItem = doc.createElement("xref");
				generateLinkend(xrefItem, prevTopic, topicMappings, topicIdNames);
				prevEle.appendChild(xrefItem);
				Element listitemEle = doc.createElement("listitem");
				listitemEle.appendChild(prevEle);
				rootEle.appendChild(listitemEle);
			}
			// Insert the node after the title node
			Node nextNode = titleEle.getNextSibling();
			while (nextNode.getNodeType() != Node.ELEMENT_NODE && nextNode.getNodeType() != Node.COMMENT_NODE && nextNode != null) {
				nextNode = nextNode.getNextSibling();
			}
			doc.getDocumentElement().insertBefore(rootEle, nextNode);
			return true;
		} else {
			return false;
		}
	}
	
	private boolean processNextRelationships(Document doc, SpecTopic topic, HashMap<Integer, HashMap<Integer, SpecTopic>> topicMappings, HashMap<Integer, String> topicIdNames, HashMap<Level, String> levelIdNames) {
		if (topic.getNextTopicRelationships().isEmpty()) return false;
		// Attempt to get the previous topic and process it
		List<TopicRelationship> nextList = topic.getNextTopicRelationships();
		// Create the paragraph/itemizedlist and list of next relationships.
		Element rootEle = null;
		rootEle = doc.createElement("itemizedlist");
		// Create the title
		Element linkTitleEle = doc.createElement("title");
		linkTitleEle.setAttribute("role", "process-next-title");
		if (nextList.size() > 1) {
			linkTitleEle.setTextContent("Next Steps in ");
		} else {
			linkTitleEle.setTextContent("Next Step in ");
		}
		Element titleXrefItem = doc.createElement("link");
		titleXrefItem.setTextContent(topic.getParent().getTitle());
		titleXrefItem.setAttribute("linkend", levelIdNames.get(topic.getParent()));
		linkTitleEle.appendChild(titleXrefItem);
		rootEle.appendChild(linkTitleEle);

		for (TopicRelationship next: nextList) {
			Element nextEle = doc.createElement("para");
			SpecTopic nextTopic = next.getSecondaryRelationship();
			nextEle.setAttribute("role", "process-next-link");
			// Add the next element to either the list or paragraph
			// Create the link element
			Element xrefItem = doc.createElement("xref");
			generateLinkend(xrefItem, nextTopic, topicMappings, topicIdNames);
			nextEle.appendChild(xrefItem);
			Element listitemEle = doc.createElement("listitem");
			listitemEle.appendChild(nextEle);
			rootEle.appendChild(listitemEle);
		}
		// Add the node to the end of the XML data
		doc.getDocumentElement().appendChild(rootEle);
		return true;
	}
	
	/*
	 * Process's a Content Specs Topic and adds in the prerequisite topic links
	 * 
	 * @param doc The XML Document object that represents the topic XML data.
	 * @param topic The topic the injections are being processed for.
	 * @param topicMappings A map of topics that exist in a content specification.
	 * @param relationshipHandler The RelationshipManager used with the content specification processor.
	 */
	private boolean processPrerequisites(Document doc, SpecTopic topic, HashMap<Integer, HashMap<Integer, SpecTopic>> topicMappings, HashMap<Integer, String> topicIdNames, HashMap<Level, String> levelIdNames) {
		if (topic.getPrerequisiteRelationships().isEmpty()) return false;
		// Get the title element so that it can be used later to add the prerequisite topic nodes
		Element titleEle = null;
		NodeList titleList = doc.getDocumentElement().getElementsByTagName("title");
		for (int i = 0; i < titleList.getLength(); i++) {
			if (titleList.item(i).getParentNode().equals(doc.getDocumentElement())) {
				titleEle = (Element)titleList.item(i);
				break;
			}
		}
		if (titleEle == null) return false;
		// Create the paragraph and list of prerequisites.
		Element formalParaEle = doc.createElement("formalpara");
		formalParaEle.setAttribute("role", "prereqs-list");
		Element formalParaTitleEle = doc.createElement("title");
		formalParaTitleEle.setTextContent("Prerequisites:");
		formalParaEle.appendChild(formalParaTitleEle);
		List<List<Element>> list = new ArrayList<List<Element>>();
		// Add the Topic Prerequisites
		for (TopicRelationship prereq: topic.getPrerequisiteTopicRelationships()) {
			SpecTopic relatedTopic = prereq.getSecondaryRelationship();
			if (topicMappings.get(relatedTopic.getDBId()).size() > 1) {
				for (Integer i: topicMappings.get(relatedTopic.getDBId()).keySet()) {
					if (topicMappings.get(relatedTopic.getDBId()).get(i).equals(relatedTopic)) {
						
						// Create the String that will be appended to the end of the topic xml id
						String postFix = "";
						if (i != 0) {
							postFix = "-" + i;
						}
						
						list.add(DocbookUtils.buildXRef(doc, topicIdNames.get(relatedTopic.getDBId()) + postFix));
						break;
					}
				}
			} else {
				list.add(DocbookUtils.buildXRef(doc, topicIdNames.get(relatedTopic.getDBId())));
			}
		}
		// Add the Level Prerequisites
		for (TargetRelationship prereq: topic.getPrerequisiteLevelRelationships()) {
			Level relatedLevel = (Level) prereq.getSecondaryElement();
			list.add(DocbookUtils.buildXRef(doc, levelIdNames.get(relatedLevel)));
		}
		// Wrap the items into an itemized list
		List<Element> items = DocbookUtils.wrapItemizedListItemsInPara(doc, list);
		for (Element ele: items) {
			formalParaEle.appendChild(ele);
		}
		// Add the paragraph and list after the title node
		Node nextNode = titleEle.getNextSibling();
		while (nextNode.getNodeType() != Node.ELEMENT_NODE && nextNode.getNodeType() != Node.COMMENT_NODE && nextNode != null) {
			nextNode = nextNode.getNextSibling();
		}
		doc.getDocumentElement().insertBefore(formalParaEle, nextNode);
		return true;
	}
	
	/*
	 * Process's a Content Spec Topic and adds in the related topic links
	 * 
	 * @param doc The XML Document object that represents the topic XML data.
	 * @param topic The topic the injections are being processed for.
	 * @param topicMappings A map of topics that exist in a content specification.
	 * @param relationshipHandler The RelationshipManager used with the content specification processor.
	 */
	private void processRelationships(Document doc, SpecTopic topic, HashMap<Integer, HashMap<Integer, SpecTopic>> topicMappings, HashMap<Integer, String> topicIdNames, HashMap<Level, String> levelIdNames) {
		// Create the paragraph and list of prerequisites.
		if (topic.getRelatedRelationships().isEmpty()) return;
		Element formalParaEle = doc.createElement("formalpara");
		formalParaEle.setAttribute("role", "refer-to-list");
		Element formalParaTitleEle = doc.createElement("title");
		formalParaTitleEle.setTextContent("See Also:");
		formalParaEle.appendChild(formalParaTitleEle);
		List<List<Element>> list = new ArrayList<List<Element>>();
		// Add the Topic Relationships
		for (TopicRelationship prereq: topic.getRelatedTopicRelationships()) {
			SpecTopic relatedTopic = prereq.getSecondaryRelationship();
			if (topicMappings.get(relatedTopic.getDBId()).size() > 1) {
				for (Integer i: topicMappings.get(relatedTopic.getDBId()).keySet()) {
					if (topicMappings.get(relatedTopic.getDBId()).get(i).equals(relatedTopic)) {
						
						// Create the String that will be appended to the end of the topic xml id
						String postFix = "";
						if (i != 0) {
							postFix = "-" + i;
						}
						
						list.add(DocbookUtils.buildXRef(doc, topicIdNames.get(relatedTopic.getDBId()) + postFix));
						break;
					}
				}
			} else {
				list.add(DocbookUtils.buildXRef(doc, topicIdNames.get(relatedTopic.getDBId())));
			}
		}
		// Add the Level Relationships
		for (TargetRelationship prereq: topic.getRelatedLevelRelationships()) {
			Level relatedLevel = (Level) prereq.getSecondaryElement();
			list.add(DocbookUtils.buildXRef(doc, levelIdNames.get(relatedLevel)));
		}
		// Wrap the items into an itemized list
		List<Element> items = DocbookUtils.wrapItemizedListItemsInPara(doc, list);
		for (Element ele: items) {
			formalParaEle.appendChild(ele);
		}
		// Add the paragraph and list after at the end of the xml data
		doc.getDocumentElement().appendChild(formalParaEle);
	}
	
}
