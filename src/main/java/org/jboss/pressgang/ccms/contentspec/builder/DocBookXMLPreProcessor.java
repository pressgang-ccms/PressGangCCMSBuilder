package org.jboss.pressgang.ccms.contentspec.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;

import com.google.code.regexp.Matcher;
import com.google.code.regexp.Pattern;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.InitialContent;
import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.SpecNode;
import org.jboss.pressgang.ccms.contentspec.SpecNodeWithRelationships;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.buglinks.BaseBugLinkStrategy;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.sort.NodeTitleSorter;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildDatabase;
import org.jboss.pressgang.ccms.contentspec.builder.structures.DocBookBuildingOptions;
import org.jboss.pressgang.ccms.contentspec.builder.structures.InjectionData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.InjectionListData;
import org.jboss.pressgang.ccms.contentspec.entities.Relationship;
import org.jboss.pressgang.ccms.contentspec.entities.TargetRelationship;
import org.jboss.pressgang.ccms.contentspec.entities.TopicRelationship;
import org.jboss.pressgang.ccms.contentspec.enums.TopicType;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.utils.common.XMLUtilities;
import org.jboss.pressgang.ccms.utils.sort.ExternalListSort;
import org.jboss.pressgang.ccms.utils.structures.DocBookVersion;
import org.jboss.pressgang.ccms.wrapper.TranslatedTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class takes the XML from a topic and modifies it to include and injected content.
 */
public class DocBookXMLPreProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(DocBookXMLPreProcessor.class);
    /**
     * Used to identify that an <orderedlist> should be generated for the injection point
     */
    protected static final int ORDEREDLIST_INJECTION_POINT = 1;
    /**
     * Used to identify that an <itemizedlist> should be generated for the injection point
     */
    protected static final int ITEMIZEDLIST_INJECTION_POINT = 2;
    /**
     * Used to identify that an <xref> should be generated for the injection point
     */
    protected static final int XREF_INJECTION_POINT = 3;
    /**
     * Used to identify that an <xref> should be generated for the injection point
     */
    protected static final int LIST_INJECTION_POINT = 4;
    /**
     * Identifies a named regular expression group
     */
    protected static final String IDS_RE_NAMED_GROUP = "TopicIDs";
    /**
     * This text identifies an option task in a list
     */
    protected static final String OPTIONAL_MARKER = "OPT:";
    /**
     * The text to be prefixed to a list item if a topic is optional
     */
    protected static final String OPTIONAL_LIST_PREFIX = "Optional: ";

    /**
     * A regular expression that identifies a topic id
     */
    protected static final String OPTIONAL_ID_RE = "(" + OPTIONAL_MARKER + "\\s*)?" + BuilderConstants.INJECT_ID_RE;

    /**
     * A regular expression that matches an InjectSequence custom injection point
     */
    public static final Pattern CUSTOM_INJECTION_SEQUENCE_RE =
    /*
     * start xml comment and 'InjectSequence:' surrounded by optional white space
     */
            Pattern.compile("^\\s*InjectSequence:\\s*" +
    /*
     * an optional comma separated list of digit blocks, and at least one digit block with an optional comma
     */
                    "(?<" + IDS_RE_NAMED_GROUP + ">(\\s*" + OPTIONAL_ID_RE + "\\s*,)*(\\s*" + OPTIONAL_ID_RE + ",?))" +
    /* xml comment end */
                    "\\s*$");

    /**
     * A regular expression that matches an InjectList custom injection point
     */
    public static final Pattern CUSTOM_INJECTION_LIST_RE =
    /* start xml comment and 'InjectList:' surrounded by optional white space */
            Pattern.compile("^\\s*InjectList:\\s*" +
    /*
     * an optional comma separated list of digit blocks, and at least one digit block with an optional comma
     */
                    "(?<" + IDS_RE_NAMED_GROUP + ">(\\s*" + OPTIONAL_ID_RE + "\\s*,)*(\\s*" + OPTIONAL_ID_RE + ",?))" +
    /* xml comment end */
                    "\\s*$");

    public static final Pattern CUSTOM_INJECTION_LISTITEMS_RE =
    /* start xml comment and 'InjectList:' surrounded by optional white space */
            Pattern.compile("^\\s*InjectListItems:\\s*" +
    /*
     * an optional comma separated list of digit blocks, and at least one digit block with an optional comma
     */
                    "(?<" + IDS_RE_NAMED_GROUP + ">(\\s*" + OPTIONAL_ID_RE + "\\s*,)*(\\s*" + OPTIONAL_ID_RE + ",?))" +
    /* xml comment end */
                    "\\s*$");

    public static final Pattern CUSTOM_ALPHA_SORT_INJECTION_LIST_RE =
    /*
     * start xml comment and 'InjectListAlphaSort:' surrounded by optional white space
     */
            Pattern.compile("^\\s*InjectListAlphaSort:\\s*" +
    /*
     * an optional comma separated list of digit blocks, and at least one digit block with an optional comma
     */
                    "(?<" + IDS_RE_NAMED_GROUP + ">(\\s*" + OPTIONAL_ID_RE + "\\s*,)*(\\s*" + OPTIONAL_ID_RE + ",?))" +
    /* xml comment end */
                    "\\s*$");

    /**
     * A regular expression that matches an Inject custom injection point
     */
    public static final Pattern CUSTOM_INJECTION_SINGLE_RE =
    /* start xml comment and 'Inject:' surrounded by optional white space */
            Pattern.compile("^\\s*Inject:\\s*" +
    /* one digit block */
                    "(?<" + IDS_RE_NAMED_GROUP + ">(" + OPTIONAL_ID_RE + "))" +
    /* xml comment end */
                    "\\s*$");

    /**
     * The noinject value for the role attribute indicates that an element should not be included in the Topic Fragment
     */
    protected static final String NO_INJECT_ROLE = "noinject";

    // DEFAULT STRING CONSTANTS
    protected static final String DEFAULT_PREREQUISITE = "Prerequisite:";
    protected static final String PREREQUISITE_PROPERTY = "PREREQUISITE";
    protected static final String DEFAULT_SEE_ALSO = "See Also:";
    protected static final String SEE_ALSO_PROPERTY = "SEE_ALSO";
    protected static final String DEFAULT_REPORT_A_BUG = "Report a bug";
    protected static final String REPORT_A_BUG_PROPERTY = "REPORT_A_BUG";
    protected static final String DEFAULT_PREVIOUS_STEP = "Previous Step in %s";
    protected static final String PREVIOUS_STEP_PROPERTY = "PREVIOUS_STEP";
    protected static final String DEFAULT_PREVIOUS_STEPS = "Previous Steps in %s";
    protected static final String PREVIOUS_STEPS_PROPERTY = "PREVIOUS_STEPS";
    protected static final String DEFAULT_NEXT_STEP = "Next Step in %s";
    protected static final String NEXT_STEP_PROPERTY = "NEXT_STEP";
    protected static final String DEFAULT_NEXT_STEPS = "Next Step in %s";
    protected static final String NEXT_STEPS_PROPERTY = "NEXT_STEPS";

    // ROLE/STYLE CONSTANTS
    /**
     * The Docbook role (which becomes a CSS class) for the bug link para
     */
    protected static final String ROLE_CREATE_BUG_PARA = "RoleCreateBugPara";
    protected static final String ROLE_ADDITIONAL_INFO_REFSECTION = "RoleAdditionalInfoRefSection";
    protected static final String ROLE_PREREQUISITE_LIST = "prereqs-list";
    protected static final String ROLE_PREREQUISITE = "prereq";
    protected static final String ROLE_SEE_ALSO_LIST = "see-also-list";
    protected static final String ROLE_SEE_ALSO = "see-also";
    protected static final String ROLE_LINK_LIST_LIST = "link-list-list";
    protected static final String ROLE_LINK_LIST = "link-list";
    protected static final String ROLE_PROCESS_NEXT_ITEMIZED_LIST = "process-next-itemizedlist";
    protected static final String ROLE_PROCESS_NEXT_TITLE = "process-next-title";
    protected static final String ROLE_PROCESS_NEXT_TITLE_LINK = "process-next-title-link";
    protected static final String ROLE_PROCESS_NEXT_LINK = "process-next-link";
    protected static final String ROLE_PROCESS_NEXT_LISTITEM = "process-next-listitem";
    protected static final String ROLE_PROCESS_PREVIOUS_ITEMIZED_LIST = "process-previous-itemizedlist";
    protected static final String ROLE_PROCESS_PREVIOUS_TITLE = "process-previous-title";
    protected static final String ROLE_PROCESS_PREVIOUS_TITLE_LINK = "process-previous-title-link";
    protected static final String ROLE_PROCESS_PREVIOUS_LINK = "process-previous-link";
    protected static final String ROLE_PROCESS_PREVIOUS_LISTITEM = "process-previous-listitem";

    protected static final String ENCODING = "UTF-8";

    protected final ResourceBundle translations;
    protected final BaseBugLinkStrategy bugLinkStrategy;

    public DocBookXMLPreProcessor(final ResourceBundle translationStrings, final BaseBugLinkStrategy bugLinkStrategy) {
        translations = translationStrings;
        this.bugLinkStrategy = bugLinkStrategy;
    }

    /**
     * Creates the wrapper element for bug or editor links and adds it to the document.
     *
     * @param document The document to add the wrapper/link to.
     * @param node     The specific node the wrapper/link should be added to.
     * @param cssClass The css class name to use for the wrapper.  @return The wrapper element that links can be added to.
     */
    protected Element createLinkWrapperElement(final Document document, final Node node, final String cssClass) {
        // Create the bug/editor link root element
        final Element linkElement = document.createElement("para");
        if (cssClass != null) {
            linkElement.setAttribute("role", cssClass);
        }

        node.appendChild(linkElement);

        return linkElement;
    }

    /**
     * Creates an element that represents an external link.
     *
     * @param docBookVersion The DocBook Version the link should be created for.
     * @param document       The document to create the link for.
     * @param title
     * @param url            The links url.  @return
     */
    protected Element createExternalLinkElement(final DocBookVersion docBookVersion, final Document document, String title,
            final String url) {
        final Element linkEle;
        if (docBookVersion == DocBookVersion.DOCBOOK_50) {
            linkEle = document.createElement("link");
            linkEle.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", url);
        } else {
            linkEle = document.createElement("ulink");
            linkEle.setAttribute("url", url);
        }
        linkEle.setTextContent(title);
        return linkEle;
    }

    public void processTopicBugLink(final BuildData buildData, final SpecTopic specTopic, final Document document, final Element rootEle) {
        try {
            String specifiedBuildName = "";
            if (buildData.getBuildName() != null) specifiedBuildName = buildData.getBuildName();

            // build the bug link url with the base components
            final String bugLinkUrl = bugLinkStrategy.generateUrl(buildData.getBugLinkOptions(), specTopic);
            processBugLink(buildData.getDocBookVersion(), bugLinkUrl, document, rootEle);
        } catch (final Exception ex) {
            LOG.error("Failed to insert Bug Links into the DOM Document", ex);
        }
    }

    public void processInitialContentBugLink(final BuildData buildData, final InitialContent initialContent, final Document document,
            final Element rootNode) {
        if (!buildData.getBuildOptions().getInsertBugLinks()) return;

        final List<Node> invalidNodes = XMLUtilities.getChildNodes(document.getDocumentElement(), "section");

        // Only add injections if the topic doesn't contain any invalid nodes. The reason for this is that adding any links to topics
        // that contain <section> will cause the XML to become invalid. Unfortunately there isn't any way around this.
        if (invalidNodes == null || invalidNodes.size() == 0) {
            try {
                final Element rootEle = getRootAdditionalInfoElement(document, rootNode);

                String specifiedBuildName = "";
                if (buildData.getBuildName() != null) specifiedBuildName = buildData.getBuildName();

                // build the bug link url with the base components
                final String bugLinkUrl = bugLinkStrategy.generateUrl(buildData.getBugLinkOptions(), initialContent);
                processBugLink(buildData.getDocBookVersion(), bugLinkUrl, document, rootEle);
            } catch (final Exception ex) {
                LOG.error("Failed to insert Bug Links into the DOM Document", ex);
            }
        }
    }

    protected void processBugLink(final DocBookVersion docBookVersion, final String bugLinkUrl, final Document document, final Node node) {
        final String reportBugTranslation = translations.getString(REPORT_A_BUG_PROPERTY);
        final String reportBugText = reportBugTranslation == null ? DEFAULT_REPORT_A_BUG : reportBugTranslation;
        final Element bugzillaLink = createExternalLinkElement(docBookVersion, document, reportBugText, bugLinkUrl);

        // Add the elements to the XML DOM last incase there is an exception (not that there should be one)
        final Element bugzillaSection = createLinkWrapperElement(document, node, ROLE_CREATE_BUG_PARA);
        bugzillaSection.appendChild(bugzillaLink);
    }

    public void processTopicEditorLinks(final BuildData buildData, final SpecTopic specTopic, final Document document,
            final Element rootEle) {
        // EDITOR LINK
        if (buildData.getBuildOptions().getInsertEditorLinks()) {
            final BaseTopicWrapper<?> topic = specTopic.getTopic();
            final String editorUrl = topic.getEditorURL(buildData.getZanataDetails());

            final Element editorLinkPara = createLinkWrapperElement(document, rootEle, ROLE_CREATE_BUG_PARA);

            if (editorUrl != null) {
                final Element editorLink = createExternalLinkElement(buildData.getDocBookVersion(), document, "Edit this topic", editorUrl);
                editorLinkPara.appendChild(editorLink);
            } else {
                /*
                 * Since the getEditorURL method only returns null for translations we don't need to check the topic
                 * type.
                 */
                editorLinkPara.setTextContent("No editor available for this topic, as it hasn't been pushed for translation.");
            }

            // Add a link for additional translated content for Revision Histories and author groups
            if (topic instanceof TranslatedTopicWrapper && (specTopic.getTopicType() == TopicType.REVISION_HISTORY || specTopic
                    .getTopicType() == TopicType.AUTHOR_GROUP)) {
                final String additionalXMLEditorUrl = topic.getPressGangURL();

                if (additionalXMLEditorUrl != null) {
                    final Element additionalXMLEditorLinkPara = createLinkWrapperElement(document, rootEle, ROLE_CREATE_BUG_PARA);

                    final Element editorLink = createExternalLinkElement(buildData.getDocBookVersion(), document,
                            "Edit the Additional Translated XML", additionalXMLEditorUrl);
                    additionalXMLEditorLinkPara.appendChild(editorLink);
                }
            }
        }
    }

    /**
     * Adds some debug information and links to the end of the topic
     */
    public void processTopicAdditionalInfo(final BuildData buildData, final SpecTopic specTopic, final Document document) {
        // First check if we should even bother processing any additional info based on build data
        if (!shouldAddAdditionalInfo(buildData, specTopic)) return;

        final List<Node> invalidNodes = XMLUtilities.getChildNodes(document.getDocumentElement(), "section");

        // Only add injections if the topic doesn't contain any invalid nodes. The reason for this is that adding any links to topics
        // that contain <section> will cause the XML to become invalid. Unfortunately there isn't any way around this.
        if (invalidNodes == null || invalidNodes.size() == 0) {
            final Element rootEle = getRootAdditionalInfoElement(document, document.getDocumentElement());

            if (buildData.getBuildOptions().getInsertEditorLinks() && specTopic.getTopicType() != TopicType.AUTHOR_GROUP) {
                processTopicEditorLinks(buildData, specTopic, document, rootEle);
            }

            // Only include a bugzilla link for normal topics
            if (buildData.getBuildOptions().getInsertBugLinks() && specTopic.getTopicType() == TopicType.NORMAL) {
                processTopicBugLink(buildData, specTopic, document, rootEle);
            }
        }
    }

    /**
     * Checks to see if additional info should be added based on the build options and the spec topic type.
     *
     * @param buildData
     * @param specTopic
     * @return
     */
    protected static boolean shouldAddAdditionalInfo(final BuildData buildData, final SpecTopic specTopic) {
        return (buildData.getBuildOptions().getInsertEditorLinks() && specTopic.getTopicType() != TopicType.AUTHOR_GROUP)
                || (buildData.getBuildOptions().getInsertBugLinks() && specTopic.getTopicType() == TopicType.NORMAL);
    }

    protected Element getRootAdditionalInfoElement(final Document document, final Element rootNode) {
        Element rootEle = rootNode;

        // For refentries inject the links into the last <refentry>
        final NodeList refEntryNodes = rootNode.getElementsByTagName("refentry");
        if (refEntryNodes.getLength() > 0) {
            final Element lastRefentry = (Element) refEntryNodes.item(refEntryNodes.getLength() - 1);

            // check if a <refsection> or a <refsect1> should be used
            Node lastEle = lastRefentry.getLastChild();
            while (lastEle != null && !(lastEle instanceof Element)) {
                lastEle = lastEle.getPreviousSibling();
            }

            // Check if it was created by the PreProcessor
            final String roleAttribute = lastEle == null ? null : ((Element) lastEle).getAttribute("role");
            if (roleAttribute != null && roleAttribute.contains(ROLE_ADDITIONAL_INFO_REFSECTION)) {
                rootEle = (Element) lastEle;
            } else {
                final String nodeName = lastEle == null ? "refsection" : lastEle.getNodeName();

                // Create the node with a blank title
                rootEle = document.createElement(nodeName);
                rootEle.setAttribute("role", ROLE_ADDITIONAL_INFO_REFSECTION);
                rootEle.appendChild(document.createElement("title"));
                lastRefentry.appendChild(rootEle);
            }
        }

        return rootEle;
    }

    /**
     * Takes a comma separated list of ints, and returns an array of Integers. This is used when processing custom injection
     * points.
     */
    private static List<InjectionData> processIdList(final String list) {
        /* find the individual topic ids */
        final String[] ids = list.split(",");

        List<InjectionData> retValue = new ArrayList<InjectionData>(ids.length);

        /* clean the topic ids */
        for (final String id : ids) {
            final String topicId = id.replaceAll(OPTIONAL_MARKER, "").trim();
            final boolean optional = id.contains(OPTIONAL_MARKER);

            try {
                final InjectionData topicData = new InjectionData(topicId, optional);
                retValue.add(topicData);
            } catch (final NumberFormatException ex) {
                /*
                 * these lists are discovered by a regular expression so we shouldn't have any trouble here with Integer.parse
                 */
                LOG.debug("Unable to convert Injection Point ID into a Number", ex);
                retValue.add(new InjectionData("-1", false));
            }
        }

        return retValue;
    }

    @SuppressWarnings("unchecked")
    public List<String> processInjections(final ContentSpec contentSpec, final SpecTopic topic, final ArrayList<String> customInjectionIds,
            final Document xmlDocument, final DocBookBuildingOptions docbookBuildingOptions, final BuildDatabase buildDatabase,
            final boolean usedFixedUrls, final Integer fixedUrlPropertyTagId) {
        /*
         * this collection keeps a track of the injection point markers and the docbook lists that we will be replacing them
         * with
         */
        final HashMap<Node, InjectionListData> customInjections = new HashMap<Node, InjectionListData>();

        final List<String> errorTopics = new ArrayList<String>();

        errorTopics.addAll(processInjections(contentSpec, topic, customInjectionIds, customInjections, ORDEREDLIST_INJECTION_POINT, xmlDocument,
                CUSTOM_INJECTION_SEQUENCE_RE, null, buildDatabase, usedFixedUrls, fixedUrlPropertyTagId));
        errorTopics.addAll(processInjections(contentSpec, topic, customInjectionIds, customInjections, XREF_INJECTION_POINT, xmlDocument,
                CUSTOM_INJECTION_SINGLE_RE, null, buildDatabase, usedFixedUrls, fixedUrlPropertyTagId));
        errorTopics.addAll(processInjections(contentSpec, topic, customInjectionIds, customInjections, ITEMIZEDLIST_INJECTION_POINT, xmlDocument,
                CUSTOM_INJECTION_LIST_RE, null, buildDatabase, usedFixedUrls, fixedUrlPropertyTagId));
        errorTopics.addAll(processInjections(contentSpec, topic, customInjectionIds, customInjections, ITEMIZEDLIST_INJECTION_POINT, xmlDocument,
                CUSTOM_ALPHA_SORT_INJECTION_LIST_RE, new NodeTitleSorter(), buildDatabase, usedFixedUrls, fixedUrlPropertyTagId));
        errorTopics.addAll(processInjections(contentSpec, topic, customInjectionIds, customInjections, LIST_INJECTION_POINT, xmlDocument,
                CUSTOM_INJECTION_LISTITEMS_RE, null, buildDatabase, usedFixedUrls, fixedUrlPropertyTagId));

        /*
         * If we are not ignoring errors, return the list of topics that could not be injected
         */
        if (errorTopics.size() != 0 && docbookBuildingOptions != null && !docbookBuildingOptions.getIgnoreMissingCustomInjections())
            return errorTopics;

        /* now make the custom injection point substitutions */
        for (final Node customInjectionCommentNode : customInjections.keySet()) {
            final InjectionListData injectionListData = customInjections.get(customInjectionCommentNode);
            List<Element> list = null;

            /*
             * this may not be true if we are not building all related topics
             */
            if (injectionListData.listItems.size() != 0) {
                if (injectionListData.listType == ORDEREDLIST_INJECTION_POINT) {
                    list = DocBookUtilities.wrapOrderedListItemsInPara(xmlDocument, injectionListData.listItems);
                } else if (injectionListData.listType == XREF_INJECTION_POINT) {
                    list = injectionListData.listItems.get(0);
                } else if (injectionListData.listType == ITEMIZEDLIST_INJECTION_POINT) {
                    list = DocBookUtilities.wrapItemizedListItemsInPara(xmlDocument, injectionListData.listItems);
                } else if (injectionListData.listType == LIST_INJECTION_POINT) {
                    list = DocBookUtilities.wrapItemsInListItems(xmlDocument, injectionListData.listItems);
                }
            }

            if (list != null) {
                for (final Element element : list) {
                    customInjectionCommentNode.getParentNode().insertBefore(element, customInjectionCommentNode);
                }

                customInjectionCommentNode.getParentNode().removeChild(customInjectionCommentNode);
            }
        }

        return errorTopics;
    }

    public List<String> processInjections(final ContentSpec contentSpec, final SpecTopic topic, final ArrayList<String> customInjectionIds,
            final HashMap<Node, InjectionListData> customInjections, final int injectionPointType, final Document xmlDocument,
            final Pattern regularExpression, final ExternalListSort<Integer, SpecNode, InjectionData> sortComparator,
            final BuildDatabase buildDatabase,
            final boolean usedFixedUrls, final Integer fixedUrlPropertyTagId) {
        final List<String> retValue = new ArrayList<String>();

        if (xmlDocument == null) return retValue;

        // loop over all of the comments in the document
        for (final Node comment : XMLUtilities.getComments(xmlDocument)) {
            final String commentContent = comment.getNodeValue();

            // find any matches
            final Matcher injectionSequenceMatcher = regularExpression.matcher(commentContent);

            // loop over the regular expression matches
            while (injectionSequenceMatcher.find()) {
                // Get the list of topics from the named group in the regular expression match
                final String reMatch = injectionSequenceMatcher.group(IDS_RE_NAMED_GROUP);

                // make sure we actually found a matching named group
                if (reMatch != null) {
                    // get the sequence of ids
                    final List<InjectionData> sequenceIDs = processIdList(reMatch);

                    // sort the InjectionData list if required
                    if (sortComparator != null) {
                        sortComparator.sort(buildDatabase.getAllSpecNodes(), sequenceIDs);
                    }

                    // loop over all the topic ids in the injection point
                    for (final InjectionData sequenceID : sequenceIDs) {
                        /*
                         * topics that are injected into custom injection points are excluded from the generic related topic
                         * lists at the beginning and end of a topic. adding the topic id here means that when it comes time to
                         * generate the generic related topic lists, we can skip this topic
                         */
                        customInjectionIds.add(sequenceID.id);

                        /*
                         * See if the topic/target is available in the content spec
                         */
                        final boolean isTopicId = BuilderConstants.TOPIC_ID_PATTERN.matcher(sequenceID.id).matches();
                        final boolean isInContentSpec;
                        if (isTopicId) {
                            isInContentSpec = contentSpec.getBaseLevel().isSpecTopicInLevelByTopicID(Integer.parseInt(sequenceID.id));
                        } else {
                            isInContentSpec = contentSpec.getBaseLevel().isSpecNodeInLevelByTargetID(sequenceID.id);
                        }

                        /*
                         * It is possible that the topic id referenced in the injection point has not been related, or has not
                         * been included in the list of topics to process. This is a validity error
                         */
                        if (isInContentSpec) {
                            /*
                             * build our list
                             */
                            List<List<Element>> list = new ArrayList<List<Element>>();

                            /*
                             * each related topic is added to a string, which is stored in the customInjections collection. the
                             * customInjections key is the custom injection text from the source xml. this allows us to match
                             * the xrefs we are generating for the related topic with the text in the xml file that these xrefs
                             * will eventually replace
                             */
                            if (customInjections.containsKey(comment)) list = customInjections.get(comment).listItems;

                            final SpecNode closestSpecNode;
                            if (isTopicId) {
                                closestSpecNode = topic.getClosestTopicByDBId(Integer.parseInt(sequenceID.id), true);
                            } else {
                                closestSpecNode = topic.getClosestSpecNodeByTargetId(sequenceID.id, true);
                            }
                            if (sequenceID.optional) {
                                list.add(DocBookUtilities.buildEmphasisPrefixedXRef(xmlDocument, OPTIONAL_LIST_PREFIX,
                                        closestSpecNode.getUniqueLinkId(fixedUrlPropertyTagId, usedFixedUrls)));
                            } else {
                                list.add(DocBookUtilities.buildXRef(xmlDocument,
                                        closestSpecNode.getUniqueLinkId(fixedUrlPropertyTagId, usedFixedUrls)));
                            }

                            /*
                             * save the changes back into the customInjections collection
                             */
                            customInjections.put(comment, new InjectionListData(list, injectionPointType));
                        } else {
                            retValue.add(sequenceID.id);
                        }
                    }
                }
            }
        }

        return retValue;
    }

    /**
     * Insert a itemized list into the start of the topic, below the title with any PREVIOUS relationships that exists for the
     * Spec Topic. The title for the list is set to "Previous Step(s) in <TOPIC_PARENT_NAME>".
     *
     * @param topic                 The topic to process the injection for.
     * @param doc                   The DOM Document object that represents the topics XML.
     * @param useFixedUrls          Whether fixed URL's should be used in the injected links.
     * @param fixedUrlPropertyTagId The ID for the Fixed URL Property Tag.
     */
    public void processPrevRelationshipInjections(final SpecTopic topic, final Document doc, final boolean useFixedUrls,
            final Integer fixedUrlPropertyTagId) {
        if (topic.getPrevTopicRelationships().isEmpty()) return;

        // Get the title element so that it can be used later to add the prev topic node
        Element titleEle = null;
        final NodeList titleList = doc.getDocumentElement().getElementsByTagName("title");
        for (int i = 0; i < titleList.getLength(); i++) {
            if (titleList.item(i).getParentNode().equals(doc.getDocumentElement())) {
                titleEle = (Element) titleList.item(i);
                break;
            }
        }

        if (titleEle != null) {
            // Attempt to get the previous topic and process it
            final List<TopicRelationship> prevList = topic.getPrevTopicRelationships();
            // Create the paragraph/itemizedlist and list of previous relationships.
            final Element rootEle = doc.createElement("itemizedlist");
            rootEle.setAttribute("role", ROLE_PROCESS_PREVIOUS_ITEMIZED_LIST);

            // Create the title
            final Element linkTitleEle = doc.createElement("title");
            linkTitleEle.setAttribute("role", ROLE_PROCESS_PREVIOUS_TITLE);
            final String translatedString;
            if (prevList.size() > 1) {
                final String previousStepsTranslation = translations.getString(PREVIOUS_STEPS_PROPERTY);
                translatedString = previousStepsTranslation == null ? DEFAULT_PREVIOUS_STEPS : previousStepsTranslation;
            } else {
                final String previousStepTranslation = translations.getString(PREVIOUS_STEP_PROPERTY);
                translatedString = previousStepTranslation == null ? DEFAULT_PREVIOUS_STEP : previousStepTranslation;
            }

            /*
             * The translated String will have a format marker to specify where the link should be placed. So we need to split
             * the translated string on that marker and add content where it should be.
             */
            String[] split = translatedString.split("%s");

            // Add the first part of the translated string if any exists
            if (!split[0].trim().isEmpty()) {
                linkTitleEle.appendChild(doc.createTextNode(split[0]));
            }

            // Create the title link
            final Element titleXrefItem = doc.createElement("link");
            final Level level = (Level) topic.getParent();
            if (level.getTranslatedTitle() != null && !level.getTranslatedTitle().isEmpty()) {
                titleXrefItem.setTextContent(level.getTranslatedTitle());
            } else {
                titleXrefItem.setTextContent(level.getTitle());
            }
            titleXrefItem.setAttribute("linkend", ((Level) topic.getParent()).getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls));
            titleXrefItem.setAttribute("xrefstyle", ROLE_PROCESS_PREVIOUS_TITLE_LINK);
            linkTitleEle.appendChild(titleXrefItem);

            // Add the last part of the translated string if any exists
            if (split.length > 1 && !split[1].trim().isEmpty()) {
                linkTitleEle.appendChild(doc.createTextNode(split[1]));
            }

            rootEle.appendChild(linkTitleEle);

            for (final TopicRelationship prev : prevList) {
                final Element prevEle = doc.createElement("para");
                final SpecTopic prevTopic = prev.getSecondaryRelationship();

                // Add the previous element to either the list or paragraph
                // Create the link element
                final Element xrefItem = doc.createElement("xref");
                xrefItem.setAttribute("linkend", prevTopic.getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls));
                xrefItem.setAttribute("xrefstyle", ROLE_PROCESS_PREVIOUS_LINK);
                prevEle.appendChild(xrefItem);

                final Element listitemEle = doc.createElement("listitem");
                listitemEle.setAttribute("role", ROLE_PROCESS_PREVIOUS_LISTITEM);
                listitemEle.appendChild(prevEle);
                rootEle.appendChild(listitemEle);
            }

            // Insert the node after the title node
            Node nextNode = titleEle.getNextSibling();
            while (nextNode != null && nextNode.getNodeType() != Node.ELEMENT_NODE && nextNode.getNodeType() != Node.COMMENT_NODE) {
                nextNode = nextNode.getNextSibling();
            }
            doc.getDocumentElement().insertBefore(rootEle, nextNode);
        }
    }

    /**
     * Insert a itemized list into the end of the topic with any NEXT relationships that exists for the Spec Topic. The title
     * for the list is set to "Next Step(s) in <TOPIC_PARENT_NAME>".
     *
     * @param topic                 The topic to process the injection for.
     * @param doc                   The DOM Document object that represents the topics XML.
     * @param useFixedUrls          Whether fixed URL's should be used in the injected links.
     * @param fixedUrlPropertyTagId
     */
    public void processNextRelationshipInjections(final SpecTopic topic, final Document doc, final boolean useFixedUrls,
            final Integer fixedUrlPropertyTagId) {
        if (topic.getNextTopicRelationships().isEmpty()) return;

        // Attempt to get the previous topic and process it
        final List<TopicRelationship> nextList = topic.getNextTopicRelationships();
        // Create the paragraph/itemizedlist and list of next relationships.
        final Element rootEle = doc.createElement("itemizedlist");
        rootEle.setAttribute("role", ROLE_PROCESS_NEXT_ITEMIZED_LIST);

        // Create the title
        final Element linkTitleEle = doc.createElement("title");
        linkTitleEle.setAttribute("role", ROLE_PROCESS_NEXT_TITLE);
        final String translatedString;
        if (nextList.size() > 1) {
            final String nextStepsTranslation = translations.getString(NEXT_STEPS_PROPERTY);
            translatedString = nextStepsTranslation == null ? DEFAULT_NEXT_STEPS : nextStepsTranslation;
        } else {
            final String nextStepTranslation = translations.getString(NEXT_STEP_PROPERTY);
            translatedString = nextStepTranslation == null ? DEFAULT_NEXT_STEP : nextStepTranslation;
        }

        /*
         * The translated String will have a format marker to specify where the link should be placed. So we need to split the
         * translated string on that marker and add content where it should be.
         */
        String[] split = translatedString.split("%s");

        // Add the first part of the translated string if any exists
        if (!split[0].trim().isEmpty()) {
            linkTitleEle.appendChild(doc.createTextNode(split[0]));
        }

        // Create the title link
        final Element titleXrefItem = doc.createElement("link");
        final Level level = (Level) topic.getParent();
        if (level.getTranslatedTitle() != null && !level.getTranslatedTitle().isEmpty()) {
            titleXrefItem.setTextContent(level.getTranslatedTitle());
        } else {
            titleXrefItem.setTextContent(level.getTitle());
        }
        titleXrefItem.setAttribute("linkend", ((Level) topic.getParent()).getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls));
        titleXrefItem.setAttribute("xrefstyle", ROLE_PROCESS_NEXT_TITLE_LINK);
        linkTitleEle.appendChild(titleXrefItem);

        // Add the last part of the translated string if any exists
        if (split.length > 1 && !split[1].trim().isEmpty()) {
            linkTitleEle.appendChild(doc.createTextNode(split[1]));
        }

        rootEle.appendChild(linkTitleEle);

        for (final TopicRelationship next : nextList) {
            final Element nextEle = doc.createElement("para");
            final SpecTopic nextTopic = next.getSecondaryRelationship();

            // Add the next element to either the list or paragraph
            // Create the link element
            final Element xrefItem = doc.createElement("xref");
            xrefItem.setAttribute("linkend", nextTopic.getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls));
            xrefItem.setAttribute("xrefstyle", ROLE_PROCESS_NEXT_LINK);
            nextEle.appendChild(xrefItem);

            final Element listitemEle = doc.createElement("listitem");
            listitemEle.setAttribute("role", ROLE_PROCESS_NEXT_LISTITEM);
            listitemEle.appendChild(nextEle);
            rootEle.appendChild(listitemEle);
        }

        // Add the node to the end of the XML data
        doc.getDocumentElement().appendChild(rootEle);
    }

    /**
     * Insert a itemized list into the start of the topic, below the title with any PREREQUISITE relationships that exists for
     * the Spec Topic. The title for the list is set to the "PREREQUISITE" property or "Prerequisites:" by default.
     *
     * @param specNode              The content spec node to process the injection for.
     * @param doc                   The DOM Document object that represents the topics XML.
     * @param useFixedUrls          Whether fixed URL's should be used in the injected links.
     * @param fixedUrlPropertyTagId The ID for the Fixed URL Property Tag.
     */
    public void processPrerequisiteInjections(final SpecNodeWithRelationships specNode, final Document doc, final boolean useFixedUrls,
            final Integer fixedUrlPropertyTagId) {
        processPrerequisiteInjections(specNode, doc, doc.getDocumentElement(), useFixedUrls, fixedUrlPropertyTagId);
    }

    /**
     * Insert a itemized list into the start of the topic, below the title with any PREREQUISITE relationships that exists for
     * the Spec Topic. The title for the list is set to the "PREREQUISITE" property or "Prerequisites:" by default.
     *
     * @param specNode              The content spec node to process the injection for.
     * @param doc                   The DOM Document object that represents the topics XML.
     * @param node                  The DOM Element to append the links to.
     * @param useFixedUrls          Whether fixed URL's should be used in the injected links.
     * @param fixedUrlPropertyTagId The ID for the Fixed URL Property Tag.
     */
    public void processPrerequisiteInjections(final SpecNodeWithRelationships specNode, final Document doc, final Element node,
            final boolean useFixedUrls, final Integer fixedUrlPropertyTagId) {
        // Make sure we have some links to inject
        if (specNode.getPrerequisiteRelationships().isEmpty()) return;

        // Get the title element so that it can be used later to add the prerequisite topic nodes
        Element titleEle = null;
        final NodeList titleList = node.getElementsByTagName("title");
        for (int i = 0; i < titleList.getLength(); i++) {
            if (titleList.item(i).getParentNode().equals(node)) {
                titleEle = (Element) titleList.item(i);
                break;
            }
        }

        if (titleEle != null) {
            // Create the paragraph and list of prerequisites.
            final Element itemisedListEle = doc.createElement("itemizedlist");
            itemisedListEle.setAttribute("role", ROLE_PREREQUISITE_LIST);
            final Element itemisedListTitleEle = doc.createElement("title");

            final String prerequisiteTranslation = translations.getString(PREREQUISITE_PROPERTY);
            itemisedListTitleEle.setTextContent(prerequisiteTranslation == null ? DEFAULT_PREREQUISITE : prerequisiteTranslation);

            itemisedListEle.appendChild(itemisedListTitleEle);
            final List<List<Element>> list = new LinkedList<List<Element>>();

            // Add the Relationships
            for (final Relationship prereq : specNode.getPrerequisiteRelationships()) {
                if (prereq instanceof TopicRelationship) {
                    final SpecTopic relatedTopic = ((TopicRelationship) prereq).getSecondaryRelationship();

                    list.add(DocBookUtilities.buildXRef(doc, relatedTopic.getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls),
                            ROLE_PREREQUISITE));
                } else {
                    final SpecNode relatedNode = ((TargetRelationship) prereq).getSecondaryRelationship();

                    list.add(DocBookUtilities.buildXRef(doc, relatedNode.getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls),
                            ROLE_PREREQUISITE));
                }
            }

            // Wrap the items into an itemized list
            final List<Element> items = DocBookUtilities.wrapItemsInListItems(doc, list);
            for (final Element ele : items) {
                itemisedListEle.appendChild(ele);
            }

            // Add the paragraph and list after the title node
            Node nextNode = titleEle.getNextSibling();
            while (nextNode != null && nextNode.getNodeType() != Node.ELEMENT_NODE && nextNode.getNodeType() != Node.COMMENT_NODE) {
                nextNode = nextNode.getNextSibling();
            }

            node.insertBefore(itemisedListEle, nextNode);
        }
    }

    /**
     * Insert a itemized list into the end of the topic with any RELATED relationships that exists for the Spec Topic. The title
     * for the list is set to "See Also:".
     *
     * @param specNode              The content spec node to process the injection for.
     * @param doc                   The DOM Document object that represents the topics XML.
     * @param useFixedUrls          Whether fixed URL's should be used in the injected links.
     * @param fixedUrlPropertyTagId The ID for the Fixed URL Property Tag.
     */
    public void processSeeAlsoInjections(final SpecNodeWithRelationships specNode, final Document doc, final boolean useFixedUrls,
            final Integer fixedUrlPropertyTagId) {
        processSeeAlsoInjections(specNode, doc, doc.getDocumentElement(), useFixedUrls, fixedUrlPropertyTagId);
    }

    /**
     * Insert a itemized list into the end of the topic with any RELATED relationships that exists for the Spec Topic. The title
     * for the list is set to "See Also:".
     *
     * @param specNode              The content spec node to process the injection for.
     * @param doc                   The DOM Document object that represents the topics XML.
     * @param node                  The DOM Element to append the links to.
     * @param useFixedUrls          Whether fixed URL's should be used in the injected links.
     * @param fixedUrlPropertyTagId The ID for the Fixed URL Property Tag.
     */
    public void processSeeAlsoInjections(final SpecNodeWithRelationships specNode, final Document doc, final Element node,
            final boolean useFixedUrls, final Integer fixedUrlPropertyTagId) {
        // Make sure we have some links to inject
        if (specNode.getRelatedRelationships().isEmpty()) return;

        // Create the paragraph and list of see alsos.
        final Element itemisedListEle = doc.createElement("itemizedlist");
        itemisedListEle.setAttribute("role", ROLE_SEE_ALSO_LIST);
        final Element itemisedListTitleEle = doc.createElement("title");

        final String seeAlsoTranslation = translations.getString(SEE_ALSO_PROPERTY);
        itemisedListTitleEle.setTextContent(seeAlsoTranslation == null ? DEFAULT_SEE_ALSO : seeAlsoTranslation);

        itemisedListEle.appendChild(itemisedListTitleEle);
        final List<List<Element>> list = new LinkedList<List<Element>>();

        // Add the Relationships
        for (final Relationship seeAlso : specNode.getRelatedRelationships()) {
            if (seeAlso instanceof TopicRelationship) {
                final SpecTopic relatedTopic = ((TopicRelationship) seeAlso).getSecondaryRelationship();

                list.add(DocBookUtilities.buildXRef(doc, relatedTopic.getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls), ROLE_SEE_ALSO));
            } else {
                final SpecNode relatedNode = ((TargetRelationship) seeAlso).getSecondaryRelationship();

                list.add(DocBookUtilities.buildXRef(doc, relatedNode.getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls), ROLE_SEE_ALSO));
            }
        }

        // Wrap the items into an itemized list
        final List<Element> items = DocBookUtilities.wrapItemsInListItems(doc, list);
        for (final Element ele : items) {
            itemisedListEle.appendChild(ele);
        }

        // Add the paragraph and list after at the end of the xml data
        node.appendChild(itemisedListEle);
    }

    /**
     * Insert a itemized list into the end of the topic with the any LINKLIST relationships that exists for the Spec Topic.
     *
     * @param specNode              The content spec node to process the injection for.
     * @param doc                   The DOM Document object that represents the topics XML.
     * @param useFixedUrls          Whether fixed URL's should be used in the injected links.
     * @param fixedUrlPropertyTagId The ID for the Fixed URL Property Tag.
     */
    public void processLinkListRelationshipInjections(final SpecNodeWithRelationships specNode, final Document doc,
            final boolean useFixedUrls, final Integer fixedUrlPropertyTagId) {
        processLinkListRelationshipInjections(specNode, doc, doc.getDocumentElement(), useFixedUrls, fixedUrlPropertyTagId);
    }

    /**
     * Insert a itemized list into the end of the topic with the any LINKLIST relationships that exists for the Spec Topic.
     *
     * @param specNode              The content spec node to process the injection for.
     * @param doc                   The DOM Document object that represents the topics XML.
     * @param node                  The DOM Element to append the links to.
     * @param useFixedUrls          Whether fixed URL's should be used in the injected links.
     * @param fixedUrlPropertyTagId The ID for the Fixed URL Property Tag.
     */
    public void processLinkListRelationshipInjections(final SpecNodeWithRelationships specNode, final Document doc, final Element node,
            final boolean useFixedUrls, final Integer fixedUrlPropertyTagId) {
        // Make sure we have some links to inject
        if (specNode.getLinkListRelationships().isEmpty()) return;

        // Create the paragraph and list of prerequisites.
        final Element itemisedListEle = doc.createElement("itemizedlist");
        itemisedListEle.setAttribute("role", ROLE_LINK_LIST_LIST);
        final Element itemisedListTitleEle = doc.createElement("title");
        itemisedListTitleEle.setTextContent("");
        itemisedListEle.appendChild(itemisedListTitleEle);
        final List<List<Element>> list = new LinkedList<List<Element>>();

        // Add the Relationships
        for (final Relationship linkList : specNode.getLinkListRelationships()) {
            if (linkList instanceof TopicRelationship) {
                final SpecTopic relatedTopic = ((TopicRelationship) linkList).getSecondaryRelationship();

                list.add(
                        DocBookUtilities.buildXRef(doc, relatedTopic.getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls), ROLE_LINK_LIST));
            } else {
                final SpecNode relatedNode = ((TargetRelationship) linkList).getSecondaryRelationship();

                list.add(DocBookUtilities.buildXRef(doc, relatedNode.getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls), ROLE_LINK_LIST));
            }
        }

        // Wrap the items into an itemized list
        final List<Element> items = DocBookUtilities.wrapItemsInListItems(doc, list);
        for (final Element ele : items) {
            itemisedListEle.appendChild(ele);
        }

        // Add the paragraph and list after at the end of the xml data
        node.appendChild(itemisedListEle);
    }
}
