package org.jboss.pressgang.ccms.contentspec.builder.utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.code.regexp.Matcher;
import com.google.code.regexp.Pattern;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import org.apache.commons.lang.time.DateUtils;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildDatabase;
import org.jboss.pressgang.ccms.contentspec.builder.structures.CSDocbookBuildingOptions;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.contentspec.structures.XMLFormatProperties;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.utils.common.StringUtilities;
import org.jboss.pressgang.ccms.utils.common.XMLUtilities;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.utils.structures.Pair;
import org.jboss.pressgang.ccms.wrapper.TopicWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedCSNodeWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A Utilities class that holds methods useful in the Docbook Builder.
 *
 * @author lnewson
 */
public class DocbookBuildUtilities {
    private static final Logger log = LoggerFactory.getLogger(DocbookBuildUtilities.class);
    private static final String STARTS_WITH_NUMBER_RE = "^(?<Numbers>\\d+)(?<EverythingElse>.*)$";
    private static final String STARTS_WITH_INVALID_SEQUENCE_RE = "^(?<InvalidSeq>[^\\w\\d]+)(?<EverythingElse>.*)$";
    private static final String[] DATE_FORMATS = new String[]{"MM-dd-yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "yyyy/MM/dd", "EEE MMM dd yyyy",
            "EEE, MMM dd yyyy", "EEE MMM dd yyyy Z", "EEE dd MMM yyyy", "EEE,dd MMM yyyy", "EEE dd MMM yyyy Z", "yyyyMMdd",
            "yyyyMMdd'T'HHmmss.SSSZ"};

    /**
     * Adds the levels in the provided Level object to the content spec database.
     *
     * @param buildDatabase
     * @param level         The content spec level to be added to the database.
     */
    public static void addLevelsToDatabase(final BuildDatabase buildDatabase, final Level level) {
        // Add the level to the database
        buildDatabase.add(level, DocbookBuildUtilities.createURLTitle(level.getTitle()));

        // Add the child levels to the database
        for (final Level childLevel : level.getChildLevels()) {
            addLevelsToDatabase(buildDatabase, childLevel);
        }
    }

    /**
     * Cleans a string to escape any characters that will break a {@link java.lang.String.replaceAll()} operation
     *
     * @param input
     * @return
     */
    public static String escapeForReplaceAll(final String input) {
        return input == null ? null : java.util.regex.Matcher.quoteReplacement(input);
    }

    public static String escapeForXMLEntity(final String input) {
        return StringUtilities.escapeForXML(input).replace("%", "&percnt;");
    }

    public static String escapeTitleForXMLEntity(final String input) {
        return DocBookUtilities.escapeTitleString(input).replace("%", "&percnt;");
    }

    /**
     * Sets the "id" attributes in the supplied XML node so that they will be
     * unique within the book.
     *
     * @param specTopic        The topic the node belongs to.
     * @param node             The node to process for id attributes.
     * @param usedIdAttributes The list of usedIdAttributes.
     */
    public static void setUniqueIds(final SpecTopic specTopic, final Node node, final Document doc,
            final Map<SpecTopic, Set<String>> usedIdAttributes) {
        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            final Node idAttribute = attributes.getNamedItem("id");
            if (idAttribute != null) {
                final String idAttributeValue = idAttribute.getNodeValue();
                String fixedIdAttributeValue = idAttributeValue;

                if (specTopic.getDuplicateId() != null) {
                    fixedIdAttributeValue += "-" + specTopic.getDuplicateId();
                }

                if (!DocbookBuildUtilities.isUniqueAttributeId(fixedIdAttributeValue, specTopic.getDBId(), usedIdAttributes)) {
                    fixedIdAttributeValue += "-" + specTopic.getStep();
                }

                setUniqueIdReferences(doc.getDocumentElement(), idAttributeValue, fixedIdAttributeValue);

                idAttribute.setNodeValue(fixedIdAttributeValue);
            }
        }

        final NodeList elements = node.getChildNodes();
        for (int i = 0; i < elements.getLength(); ++i) {
            setUniqueIds(specTopic, elements.item(i), doc, usedIdAttributes);
        }
    }

    /**
     * ID attributes modified in the setUniqueIds() method may have been referenced
     * locally in the XML. When an ID is updated, and attribute that referenced
     * that ID is also updated.
     *
     * @param node    The node to check for attributes
     * @param id      The old ID attribute value
     * @param fixedId The new ID attribute
     */
    private static void setUniqueIdReferences(final Node node, final String id, final String fixedId) {
        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); ++i) {
                final String attributeValue = attributes.item(i).getNodeValue();
                if (attributeValue.equals(id)) {
                    attributes.item(i).setNodeValue(fixedId);
                }
            }
        }

        final NodeList elements = node.getChildNodes();
        for (int i = 0; i < elements.getLength(); ++i) {
            setUniqueIdReferences(elements.item(i), id, fixedId);
        }
    }

    /**
     * Checks to see if a supplied attribute id is unique within this book, based
     * upon the used id attributes that were calculated earlier.
     *
     * @param id               The Attribute id to be checked
     * @param topicId          The id of the topic the attribute id was found in
     * @param usedIdAttributes The set of used ids calculated earlier
     * @return True if the id is unique otherwise false.
     */
    public static boolean isUniqueAttributeId(final String id, final Integer topicId, final Map<SpecTopic, Set<String>> usedIdAttributes) {
        boolean retValue = true;

        for (final Entry<SpecTopic, Set<String>> entry : usedIdAttributes.entrySet()) {
            final SpecTopic topic2 = entry.getKey();
            final Integer topicId2 = topic2.getDBId();
            if (topicId2.equals(topicId)) {
                continue;
            }

            final Set<String> ids2 = entry.getValue();

            if (ids2.contains(id)) {
                retValue = false;
            }
        }

        return retValue;
    }

    /**
     * Get any ids that are referenced by a "link" or "xref"
     * XML attribute within the node. Any ids that are found
     * are added to the passes linkIds set.
     *
     * @param node    The DOM XML node to check for links.
     * @param linkIds The set of current found link ids.
     */
    public static void getTopicLinkIds(final Node node, final Set<String> linkIds) {
        // If the node is null then there isn't anything to find, so just return.
        if (node == null) {
            return;
        }

        if (node.getNodeName().equals("xref") || node.getNodeName().equals("link")) {
            final NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                final Node idAttribute = attributes.getNamedItem("linkend");
                if (idAttribute != null) {
                    final String idAttributeValue = idAttribute.getNodeValue();
                    linkIds.add(idAttributeValue);
                }
            }
        }

        final NodeList elements = node.getChildNodes();
        for (int i = 0; i < elements.getLength(); ++i) {
            getTopicLinkIds(elements.item(i), linkIds);
        }
    }

    /**
     * Creates the URL specific title for a topic or level.
     *
     * @param title The title that will be used to create the URL Title.
     * @return The URL representation of the title.
     */
    public static String createURLTitle(final String title) {
        String baseTitle = title;
        // Remove XML Elements from the Title.
        baseTitle = baseTitle.replaceAll("</(.*?)>", "").replaceAll("<(.*?)>", "");

        // Check if the title starts with an invalid sequence
        final Pattern invalidSequencePattern = Pattern.compile(STARTS_WITH_INVALID_SEQUENCE_RE);
        final Matcher invalidSequenceMatcher = invalidSequencePattern.matcher(baseTitle);

        if (invalidSequenceMatcher.find()) {
            baseTitle = invalidSequenceMatcher.group("EverythingElse");
        }

        // Start by removing any prefixed numbers (you can't start an xref id with numbers)
        final Pattern pattern = Pattern.compile(STARTS_WITH_NUMBER_RE);
        final Matcher matcher = pattern.matcher(baseTitle);

        if (matcher.find()) {
            final String numbers = matcher.group("Numbers");
            final String everythingElse = matcher.group("EverythingElse");

            if (numbers != null && everythingElse != null) {
                final NumberFormat formatter = new RuleBasedNumberFormat(RuleBasedNumberFormat.SPELLOUT);
                final String numbersSpeltOut = formatter.format(Integer.parseInt(numbers));
                baseTitle = numbersSpeltOut + everythingElse;

                // Capitalize the first character
                if (baseTitle.length() > 0) {
                    baseTitle = baseTitle.substring(0, 1).toUpperCase() + baseTitle.substring(1, baseTitle.length());
                }
            }
        }

        // Escape the title
        String escapedTitle = DocBookUtilities.escapeTitle(baseTitle);
        while (escapedTitle.indexOf("__") != -1) {
            escapedTitle = escapedTitle.replaceAll("__", "_");
        }

        return escapedTitle;
    }

    /**
     * Build up an error template by replacing key pointers in
     * the template. The pointers that get replaced are:
     * <p/>
     * {@code
     * <!-- Inject TopicTitle -->
     * <!-- Inject TopicID -->
     * <!-- Inject ErrorXREF -->}
     *
     * @param topic         The topic to generate the error template for.
     * @param errorTemplate The pre processed error template.
     * @return The input error template with the pointers replaced
     *         with values from the topic.
     */
    public static String buildTopicErrorTemplate(final BaseTopicWrapper<?> topic, final String errorTemplate,
            final CSDocbookBuildingOptions docbookBuildingOptions) {
        String topicXMLErrorTemplate = errorTemplate;
        topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_TITLE_REGEX, topic.getTitle());

        // Set the topic id in the error
        final String errorXRefID = topic.getErrorXRefId();
        if (topic instanceof TranslatedTopicWrapper) {
            final Integer topicId = topic.getTopicId();
            final Integer topicRevision = topic.getTopicRevision();
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX,
                    topicId + ", Revision " + topicRevision);
        } else {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topic.getId()));
        }

        // Add the link to the errors page. If the errors page is suppressed then remove the injection point.
        if (!docbookBuildingOptions.getSuppressErrorsPage()) {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX,
                    "<para>Please review the compiler error " + "for <xref linkend=\"" + errorXRefID + "\"/> for more detailed " +
                            "information.</para>");
        } else {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
        }

        // Replace the root section element if the topic is a revision history or a legal notice
        if (topic.hasTag(CSConstants.REVISION_HISTORY_TAG_ID)) {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll("(?<=<(/)?)section(?=>)", "appendix");
        } else if (topic.hasTag(CSConstants.LEGAL_NOTICE_TAG_ID)) {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll("(?<=<(/)?)section(?=>)", "legalnotice");
        }

        return topicXMLErrorTemplate;
    }

    /**
     * Checks to see if the Rows, in XML Tables exceed the maximum number of columns.
     *
     * @param doc The XML DOM Document to be validated.
     * @return True if the XML is valid, otherwise false.
     */
    public static boolean validateTopicTables(final Document doc) {
        final NodeList tables = doc.getElementsByTagName("table");
        for (int i = 0; i < tables.getLength(); i++) {
            final Element table = (Element) tables.item(i);
            if (!DocBookUtilities.validateTableRows(table)) {
                return false;
            }
        }

        final NodeList informalTables = doc.getElementsByTagName("informaltable");
        for (int i = 0; i < informalTables.getLength(); i++) {
            final Element informalTable = (Element) informalTables.item(i);
            if (!DocBookUtilities.validateTableRows(informalTable)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Generates the Revision Number to be used in a Revision_History.xml
     * file using the Book Version, Edition and Pubsnumber values from a content
     * specification.
     *
     * @param contentSpec the content specification to generate the revision number for.
     * @return The generated revnumber value.
     */
    public static String generateRevisionNumber(final ContentSpec contentSpec) {
        final StringBuilder rev = new StringBuilder();

        rev.append(generateRevision(contentSpec));

        // Add the separator
        rev.append("-");

        // Build the pubsnumber part of the revision number.
        final Integer pubsnum = contentSpec.getPubsNumber();
        if (pubsnum == null) {
            rev.append(BuilderConstants.DEFAULT_PUBSNUMBER);
        } else {
            rev.append(pubsnum);
        }

        return rev.toString();
    }

    /**
     * Generates the Revision component of a revnumber to be used in a Revision_History.xml
     * file using the Book Version, Edition and Pubsnumber values from a content
     * specification.
     *
     * @param contentSpec the content specification to generate the revision number for.
     * @return The generated revision number.
     */
    public static String generateRevision(final ContentSpec contentSpec) {
        final StringBuilder rev = new StringBuilder();

        // Build the BookVersion/Edition part of the revision number.
        final String bookVersion;
        if (contentSpec.getBookVersion() == null) {
            bookVersion = contentSpec.getEdition();
        } else {
            bookVersion = contentSpec.getBookVersion();
        }

        if (bookVersion == null) {
            rev.append(BuilderConstants.DEFAULT_EDITION + ".0.0");
        } else if (contentSpec.getEdition().matches("^[0-9]+\\.[0-9]+\\.[0-9]+$")) {
            rev.append(bookVersion);
        } else if (contentSpec.getEdition().matches("^[0-9]+\\.[0-9]+$")) {
            rev.append(bookVersion + ".0");
        } else {
            rev.append(bookVersion + ".0.0");
        }

        return rev.toString();
    }

    /**
     * Clean a user specified publican.cfg to remove content that should be set for a build.
     *
     * @param userPublicanCfg The User publican.cfg file to be cleaned.
     * @return The cleaned publican.cfg file.
     */
    public static String cleanUserPublicanCfg(final String userPublicanCfg) {
        // Remove any xml_lang statements
        String retValue = userPublicanCfg.replaceAll("xml_lang\\:\\s*.*?($|\\r\\n|\\n)", "");
        // Remove any type statements
        retValue = retValue.replaceAll("type\\:\\s*.*($|\\r\\n|\\n)" + "", "");
        // Remove any brand statements
        retValue = retValue.replaceAll("brand\\:\\s*.*($|\\r\\n|\\n)" + "", "");

        if (!retValue.endsWith("\n")) {
            retValue += "\n";
        }

        return retValue;
    }

    /**
     * Gets a Set of Topic ID's to Revisions from the content specification for each Spec Topic.
     *
     * @param contentSpec The content spec to scan for topics.
     * @return A Set of Topic ID/Revision Pairs that represent the topics in the content spec.
     */
    public static Set<Pair<Integer, Integer>> getTopicIdsFromContentSpec(final ContentSpec contentSpec) {
        // Add the topics at this level to the database
        final Set<Pair<Integer, Integer>> topicIds = new HashSet<Pair<Integer, Integer>>();
        final List<SpecTopic> specTopics = contentSpec.getSpecTopics();
        for (final SpecTopic specTopic : specTopics) {
            if (specTopic.getDBId() != 0) {
                topicIds.add(new Pair<Integer, Integer>(specTopic.getDBId(), specTopic.getRevision()));
            }
        }

        return topicIds;
    }

    /**
     * Convert a DOM Document to a Docbook 4.5 Formatted String representation including the XML preamble and DOCTYPE.
     *
     * @param doc                 The DOM Document to be converted and formatted.
     * @param elementName         The name that the root element should be.
     * @param entityName          The name of the local entity file, if one exists.
     * @param xmlFormatProperties The XML Formatting Properties.
     * @return The converted XML String representation.
     */
    public static String convertDocumentToDocbook45FormattedString(final Document doc, final String elementName, final String entityName,
            final XMLFormatProperties xmlFormatProperties) {
        return DocBookUtilities.addDocbook45XMLDoctype(convertDocumentToFormattedString(doc, xmlFormatProperties), entityName, elementName);
    }

    /**
     * Convert a DOM Document to a Formatted String representation and wrap it in a CDATA element.
     *
     * @param doc                 The DOM Document to be converted and formatted.
     * @param xmlFormatProperties The XML Formatting Properties.
     * @return The converted XML String representation.
     */
    public static String convertDocumentToCDATAFormattedString(final Document doc, final XMLFormatProperties xmlFormatProperties) {
        return XMLUtilities.wrapStringInCDATA(convertDocumentToFormattedString(doc, xmlFormatProperties));
    }

    /**
     * Convert a DOM Document to a Formatted String representation.
     *
     * @param doc                 The DOM Document to be converted and formatted.
     * @param xmlFormatProperties The XML Formatting Properties.
     * @return The converted XML String representation.
     */
    public static String convertDocumentToFormattedString(final Document doc, final XMLFormatProperties xmlFormatProperties) {
        return XMLUtilities.convertNodeToString(doc, xmlFormatProperties.getVerbatimElements(), xmlFormatProperties.getInlineElements(),
                xmlFormatProperties.getContentsInlineElements(), true);
    }

    /**
     * Create a Key that can be used to store a topic in a build database.
     *
     * @param topic The topic to create the key for.
     * @return The Key for the topic.
     */
    public static String getTopicBuildKey(final TopicWrapper topic) {
        return getBaseTopicBuildKey(topic);
    }

    /**
     * Create a Key that can be used to store a translated topic in a build database.
     *
     * @param translatedTopic The translated topic to create the key for.
     * @return The Key for the translated topic.
     */
    public static String getTranslatedTopicBuildKey(final TranslatedTopicWrapper translatedTopic,
            final TranslatedCSNodeWrapper translatedCSNode) {
        String topicKey = getBaseTopicBuildKey(translatedTopic);
        return translatedCSNode == null ? topicKey : (topicKey + "-" + translatedCSNode.getId());
    }

    /**
     * Create a Key that can be used to store a topic in a build database.
     *
     * @param topic The topic to create the key for.
     * @return The Key for the topic.
     */
    protected static String getBaseTopicBuildKey(final BaseTopicWrapper<?> topic) {
        return topic.getTopicId() + "-" + topic.getTopicRevision();
    }

    /**
     * Sets the topic xref id to the topic database id.
     *
     * @param topic        The topic to be used to set the id attribute.
     * @param doc          The document object for the topics XML.
     * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
     */
    public static void processTopicID(final BaseTopicWrapper<?> topic, final Document doc, final boolean useFixedUrls) {
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
     * Sets the XML of the topic to the specified error template.
     *
     * @param topic        The topic to be updated as having an error.
     * @param template     The template for the Error Message.
     * @param useFixedUrls If Fixed URL Properties should be used for topic ID attributes.
     * @return The Document Object that is initialised using the topic and error template.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    public static Document setTopicXMLForError(final BaseTopicWrapper<?> topic, final String template,
            final boolean useFixedUrls) throws BuildProcessingException {
        Document doc = null;
        try {
            doc = XMLUtilities.convertStringToDocument(template);
        } catch (Exception ex) {
            // Exit since we shouldn't fail at converting a basic template
            log.debug("Topic Error Template is not valid XML", ex);
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
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    public static void setSpecTopicXMLForError(final SpecTopic specTopic, final String template,
            final boolean useFixedUrls) throws BuildProcessingException {
        final BaseTopicWrapper<?> topic = specTopic.getTopic();

        Document doc = null;
        try {
            doc = XMLUtilities.convertStringToDocument(template);
        } catch (Exception ex) {
            // Exit since we shouldn't fail at converting a basic template
            log.debug("Topic Error Template is not valid XML", ex);
            throw new BuildProcessingException("Failed to convert the Topic Error template into a DOM document");
        }
        specTopic.setXMLDocument(doc);
        DocBookUtilities.setSectionTitle(topic.getTitle(), doc);
        processTopicID(topic, doc, useFixedUrls);
    }

    /**
     * This function scans the supplied XML node and it's children for id attributes, collecting them in the usedIdAttributes
     * parameter.
     *
     * @param node             The current node being processed (will be the document root to start with, and then all the children as this
     *                         function is recursively called)
     * @param topic            The topic that we are collecting attribute ID's for.
     * @param usedIdAttributes The set of Used ID Attributes that should be added to.
     */
    public static void collectIdAttributes(final SpecTopic topic, final Node node, final Map<SpecTopic, Set<String>> usedIdAttributes) {
        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            final Node idAttribute = attributes.getNamedItem("id");
            if (idAttribute != null) {
                final String idAttributeValue = idAttribute.getNodeValue();
                if (!usedIdAttributes.containsKey(topic)) {
                    usedIdAttributes.put(topic, new HashSet<String>());
                }
                usedIdAttributes.get(topic).add(idAttributeValue);
            }
        }

        final NodeList elements = node.getChildNodes();
        for (int i = 0; i < elements.getLength(); ++i) {
            collectIdAttributes(topic, elements.item(i), usedIdAttributes);
        }
    }

    /**
     * Validates that the Languages for all {@code<programlisting>} elements has a valid Publican language attribute.
     *
     * @param doc The DOM XML Document to be validated.
     * @return True if the document is valid, otherwise false.
     */
    public static boolean validateProgramListingLanguages(final Document doc) {
        assert doc != null;
        boolean valid = true;

        final NodeList programListings = doc.getElementsByTagName("programlisting");
        for (int i = 0; i < programListings.getLength(); i++) {
            final Element programListing = (Element) programListings.item(i);
            if (programListing.hasAttribute("language")) {
                if (!BuilderConstants.VALID_PROGRAM_LISTING_LANGS_LOWERCASE.contains(
                        programListing.getAttribute("language").toLowerCase())) {
                    valid = false;
                }
            }
        }

        return valid;
    }

    public static List<String> checkTopicForInvalidContent(final BaseTopicWrapper<?> topic, final Document topicDoc) {
        final List<String> xmlErrors = new ArrayList<String>();
        // Check to ensure that if the topic has programlisting elements, that the language is a valid Publican value
        if (!DocbookBuildUtilities.validateProgramListingLanguages(topicDoc)) {
            xmlErrors.add("The Program Listing language is not a valid Publican language.");
        }
        // Check to ensure that if the topic has a table, that the table isn't missing any entries
        if (!DocbookBuildUtilities.validateTopicTables(topicDoc)) {
            xmlErrors.add("Table column declaration doesn't match the number of entry elements.");
        }
        // Check that the root element matches the topic type
        final String rootElementErrors = checkTopicRootElement(topic, topicDoc);
        if (rootElementErrors != null) {
            xmlErrors.add(rootElementErrors);
        }
        // Check that the content matches the topic type
        xmlErrors.addAll(checkTopicContentBasedOnType(topic, topicDoc));

        return xmlErrors;
    }

    /**
     * @param topic
     * @param doc
     * @return
     */
    protected static String checkTopicRootElement(final BaseTopicWrapper<?> topic, final Document doc) {
        final StringBuilder xmlErrors = new StringBuilder();
        if (topic.hasTag(CSConstants.REVISION_HISTORY_TAG_ID)) {
            if (!doc.getDocumentElement().getNodeName().equals("appendix")) {
                xmlErrors.append("Revision History topics must be an &lt;appendix&gt;.\n");
            }
        } else if (topic.hasTag(CSConstants.LEGAL_NOTICE_TAG_ID)) {
            if (!doc.getDocumentElement().getNodeName().equals("legalnotice")) {
                xmlErrors.append("Legal Notice topics must be a &lt;legalnotice&gt;.\n");
            }
        } else {
            if (!doc.getDocumentElement().getNodeName().equals(DocBookUtilities.TOPIC_ROOT_NODE_NAME)) {
                xmlErrors.append("Topics must be a &lt;" + DocBookUtilities.TOPIC_ROOT_NODE_NAME + "&gt;.\n");
            }
        }

        return xmlErrors.length() == 0 ? null : xmlErrors.toString();
    }

    /**
     * Check a topic and return an error messages if the content doesn't match the topic type.
     *
     * @param topic
     * @param doc
     * @return
     */
    protected static List<String> checkTopicContentBasedOnType(final BaseTopicWrapper<?> topic, final Document doc) {
        final List<String> xmlErrors = new ArrayList<String>();
        if (topic.hasTag(CSConstants.REVISION_HISTORY_TAG_ID)) {
            // Check to make sure that a revhistory entry exists
            final String revHistoryErrors = validateRevisionHistory(doc);
            if (revHistoryErrors != null) {
                xmlErrors.add(revHistoryErrors);
            }
        }

        return xmlErrors;
    }

    /**
     * Validates a Revision History XML DOM Document to ensure that the content is valid for use with Publican.
     *
     * @param doc The DOM Document that represents the XML that is to be validated.
     * @return Null if there weren't any errors otherwise an error message that states what is wrong.
     */
    protected static String validateRevisionHistory(final Document doc) {
        final List<String> invalidRevNumbers = new ArrayList<String>();

        // Find each <revnumber> element and make sure it matches the publican regex
        final NodeList revisions = doc.getElementsByTagName("revision");
        Date previousDate = null;
        for (int i = 0; i < revisions.getLength(); i++) {
            final Element revision = (Element) revisions.item(i);
            final NodeList revnumbers = revision.getElementsByTagName("revnumber");
            final Element revnumber = revnumbers.getLength() == 1 ? (Element) revnumbers.item(0) : null;
            final NodeList dates = revision.getElementsByTagName("date");
            final Element date = dates.getLength() == 1 ? (Element) dates.item(0) : null;

            // Make sure the rev number is valid and the order is correct
            if (revnumber != null && !revnumber.getTextContent().matches("^([0-9.]*)-([0-9.]*)$")) {
                invalidRevNumbers.add(revnumber.getTextContent());
            } else if (revnumber == null) {
                return "Invalid revision, missing &lt;revnumber&gt; element.";
            }

            // Check the dates are in chronological order
            if (date != null) {
                try {
                    final Date revisionDate = DateUtils.parseDate(date.getTextContent(), DATE_FORMATS);
                    if (previousDate != null && revisionDate.after(previousDate)) {
                        return "The revisions in the Revision History are not in descending chronological order, " +
                                "starting from \"" + date.getTextContent() + "\".";
                    }

                    previousDate = revisionDate;
                } catch (Exception e) {
                    return "Invalid revision, the date \"" + date.getTextContent() + "\" is not in a valid format.";
                }
            } else {
                return "Invalid revision, missing &lt;date&gt; element.";
            }
        }

        if (!invalidRevNumbers.isEmpty()) {
            return "Revision History has invalid &lt;revnumber&gt; values: " + CollectionUtilities.toSeperatedString(invalidRevNumbers,
                    ", ") + ". The revnumber must match \"^([0-9.]*)-([0-9.]*)$\" to be valid.";
        } else {
            return null;
        }
    }
}