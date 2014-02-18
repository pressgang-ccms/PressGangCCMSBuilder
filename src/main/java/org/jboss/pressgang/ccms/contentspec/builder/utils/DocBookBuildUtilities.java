package org.jboss.pressgang.ccms.contentspec.builder.utils;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.code.regexp.Matcher;
import com.google.code.regexp.Pattern;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildDatabase;
import org.jboss.pressgang.ccms.contentspec.builder.structures.InjectionError;
import org.jboss.pressgang.ccms.contentspec.sort.RevisionNodeSort;
import org.jboss.pressgang.ccms.contentspec.structures.XMLFormatProperties;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.utils.common.XMLUtilities;
import org.jboss.pressgang.ccms.utils.structures.DocBookVersion;
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
public class DocBookBuildUtilities {
    private static final Logger log = LoggerFactory.getLogger(DocBookBuildUtilities.class);
    private static final String STARTS_WITH_NUMBER_RE = "^(?<Numbers>\\d+)(?<EverythingElse>.*)$";
    private static final String STARTS_WITH_INVALID_SEQUENCE_RE = "^(?<InvalidSeq>[^\\w\\d]+)(?<EverythingElse>.*)$";
    private static final String[] DATE_FORMATS = new String[]{"MM-dd-yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "yyyy/MM/dd", "EEE MMM dd yyyy",
            "EEE, MMM dd yyyy", "EEE MMM dd yyyy Z", "EEE dd MMM yyyy", "EEE, dd MMM yyyy", "EEE dd MMM yyyy Z", "yyyyMMdd",
            "yyyyMMdd'T'HHmmss.SSSZ"};

    private static final Pattern THURSDAY_DATE_RE = Pattern.compile("Thurs?(?!s?day)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final Pattern TUESDAY_DATE_RE = Pattern.compile("Tues(?!day)", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final Pattern INJECT_RE = Pattern.compile(
            "^\\s*(?<TYPE>Inject\\w*)(?<COLON>:?)\\s*(?<IDS>" + BuilderConstants.INJECT_ID_RE + ".*)\\s*$",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final Pattern INJECT_ID_RE = Pattern.compile("^[\\d ,]+$");
    private static final Pattern INJECT_SINGLE_ID_RE = Pattern.compile("^[\\d]+$");
    private static final List<String> VALID_INJECTION_TYPES = Arrays.asList("Inject", "InjectList", "InjectListItems",
            "InjectListAlphaSort", "InjectSequence");

    /**
     * Adds the levels in the provided Level object to the content spec database.
     *
     * @param buildDatabase
     * @param level         The content spec level to be added to the database.
     */
    public static void addLevelsToDatabase(final BuildDatabase buildDatabase, final Level level) {
        // Add the level to the database
        buildDatabase.add(level, DocBookBuildUtilities.createURLTitle(level.getTitle()));

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

    public static String escapeTitleForXMLEntity(final String input) {
        return DocBookUtilities.escapeForXML(input).replace("%", "&percnt;");
    }

    /**
     * Sets the "id" attributes in the supplied XML node so that they will be
     * unique within the book.
     *
     * @param specTopic        The topic the node belongs to.
     * @param node             The node to process for id attributes.
     * @param usedIdAttributes The list of usedIdAttributes.
     */
    public static void setUniqueIds(final DocBookVersion docBookVersion, final SpecTopic specTopic, final Node node, final Document doc,
            final Map<SpecTopic, Set<String>> usedIdAttributes) {
        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            final Node idAttribute;
            if (docBookVersion == DocBookVersion.DOCBOOK_50) {
                idAttribute = attributes.getNamedItem("xml:id");
            } else {
                idAttribute = attributes.getNamedItem("id");
            }
            if (idAttribute != null) {
                final String idAttributeValue = idAttribute.getNodeValue();
                String fixedIdAttributeValue = idAttributeValue;

                if (specTopic.getDuplicateId() != null) {
                    fixedIdAttributeValue += "-" + specTopic.getDuplicateId();
                }

                if (!DocBookBuildUtilities.isUniqueAttributeId(fixedIdAttributeValue, specTopic.getDBId(), usedIdAttributes)) {
                    fixedIdAttributeValue += "-" + specTopic.getStep();
                }

                setUniqueIdReferences(doc.getDocumentElement(), idAttributeValue, fixedIdAttributeValue);

                idAttribute.setNodeValue(fixedIdAttributeValue);
            }
        }

        final NodeList elements = node.getChildNodes();
        for (int i = 0; i < elements.getLength(); ++i) {
            setUniqueIds(docBookVersion, specTopic, elements.item(i), doc, usedIdAttributes);
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
     * @param buildData     Information and data structures for the build.
     * @param topic         The topic to generate the error template for.
     * @param errorTemplate The pre processed error template.
     * @return The input error template with the pointers replaced
     *         with values from the topic.
     */
    protected static String buildTopicErrorTemplate(final BuildData buildData, final BaseTopicWrapper<?> topic,
            final String errorTemplate) {
        String topicXMLErrorTemplate = errorTemplate;

        topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_TITLE_REGEX, "Invalid Topic");

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
        if (!buildData.getBuildOptions().getSuppressErrorsPage()) {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX,
                    "<para>Please review the compiler error " + "for <xref linkend=\"" + errorXRefID + "\"/> for more detailed " +
                            "information.</para>");
        } else {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.ERROR_XREF_REGEX, "");
        }

        // Replace the root section element if the topic is a revision history or a legal notice
        if (topic.hasTag(buildData.getServerEntities().getRevisionHistoryTagId())) {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll("(?<=<(/)?)section(?=>)", "appendix");
        } else if (topic.hasTag(buildData.getServerEntities().getLegalNoticeTagId())) {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll("(?<=<(/)?)section(?=>)", "legalnotice");
        } else if (topic.hasTag(buildData.getServerEntities().getAuthorGroupTagId())) {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll("(?<=<(/)?)section(?=>)", "authorgroup");
        } else if (topic.hasTag(buildData.getServerEntities().getAbstractTagId())) {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll("(?<=<(/)?)section(?=>)", "abstract");
        }

        // Add the namespaces for docbook 5
        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
            topicXMLErrorTemplate = DocBookUtilities.addDocBook50Namespace(topicXMLErrorTemplate);
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
            rev.append(BuilderConstants.DEFAULT_EDITION).append(".0.0");
        } else {
            // Remove any beta/alpha declarations
            final String removeContent = bookVersion.replaceAll("^(([0-9]+)|([0-9]+.[0-9]+)|([0-9]+.[0-9]+.[0-9]+))", "");
            final String fixedBookVersion = bookVersion.replace(removeContent, "");

            if (fixedBookVersion.matches("^[0-9]+\\.[0-9]+\\.[0-9]+$")) {
                rev.append(fixedBookVersion);
            } else if (fixedBookVersion.matches("^[0-9]+\\.[0-9]+$")) {
                rev.append(fixedBookVersion).append(".0");
            } else {
                rev.append(fixedBookVersion).append(".0.0");
            }
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
        String retValue = userPublicanCfg.replaceAll("xml_lang:\\s*.*?($|\\r\\n|\\n)", "");
        // Remove any type statements
        retValue = retValue.replaceAll("type:\\s*.*($|\\r\\n|\\n)" + "", "");
        // Remove any brand statements
        retValue = retValue.replaceAll("brand:\\s*.*($|\\r\\n|\\n)" + "", "");

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
     * Convert a DOM Document to a DocBook Formatted String representation including the XML preamble and DOCTYPE/namespaces.
     *
     * @param docBookVersion      The DocBook version to add the preamble content for.
     * @param doc                 The DOM Document to be converted and formatted.
     * @param elementName         The name that the root element should be.
     * @param entityName          The name of the local entity file, if one exists.
     * @param xmlFormatProperties The XML Formatting Properties.
     * @return The converted XML String representation.
     */
    public static String convertDocumentToDocBookFormattedString(final DocBookVersion docBookVersion, final Document doc,
            final String elementName, final String entityName, final XMLFormatProperties xmlFormatProperties) {
        final String formattedXML = convertDocumentToFormattedString(doc, xmlFormatProperties);
        return addDocBookPreamble(docBookVersion, formattedXML, elementName, entityName);
    }

    /**
     * Adds any content to the xml that is required for the specified DocBook version.
     *
     * @param docBookVersion The DocBook version to add the preamble content for.
     * @param xml            The XML to add the preamble content to.
     * @param elementName    The name that the root element should be.
     * @param entityName     The name of the local entity file, if one exists.
     * @return
     */
    public static String addDocBookPreamble(final DocBookVersion docBookVersion, final String xml, final String elementName,
            final String entityName) {
        if (docBookVersion == DocBookVersion.DOCBOOK_50) {
            final String xmlWithNamespace = DocBookUtilities.addDocBook50Namespace(xml, elementName);
            return XMLUtilities.addDoctype(xmlWithNamespace, elementName, entityName);
        } else {
            return DocBookUtilities.addDocBook45Doctype(xml, entityName, elementName);
        }
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
     * @param buildData Information and data structures for the build.
     * @param topic     The topic to be used to set the id attribute.
     * @param doc       The document object for the topics XML.
     */
    public static void processTopicID(final BuildData buildData, final BaseTopicWrapper<?> topic, final Document doc) {
        // Get the id value
        final String errorXRefID;
        if (buildData.isUseFixedUrls()) {
            errorXRefID = topic.getXRefPropertyOrId(buildData.getServerEntities().getFixedUrlPropertyTagId());
        } else {
            errorXRefID = topic.getXRefId();
        }

        // Set the id
        setDOMElementId(buildData.getDocBookVersion(), doc.getDocumentElement(), errorXRefID);

        final Integer topicId = topic.getTopicId();
        doc.getDocumentElement().setAttribute("remap", "TID_" + topicId);
    }

    public static void setDOMElementId(final DocBookVersion docBookVersion, final Element element, final String id) {
        // Remove any current ids
        element.removeAttribute("id");
        element.removeAttribute("xml:id");

        // Set the id
        if (docBookVersion == DocBookVersion.DOCBOOK_50) {
            element.setAttribute("xml:id", id);
        } else {
            element.setAttribute("id", id);
        }
    }

    /**
     * Sets the XML of the topic to the specified error template.
     *
     * @param buildData Information and data structures for the build.
     * @param topic     The topic to be updated as having an error.
     * @param template  The template for the Error Message.
     * @return The Document Object that is initialised using the topic and error template.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    public static Document setTopicXMLForError(final BuildData buildData, final BaseTopicWrapper<?> topic,
            final String template) throws BuildProcessingException {
        final String errorContent = buildTopicErrorTemplate(buildData, topic, template);

        Document doc = null;
        try {
            doc = XMLUtilities.convertStringToDocument(errorContent);
        } catch (Exception ex) {
            // Exit since we shouldn't fail at converting a basic template
            log.debug("Topic Error Template is not valid XML", ex);
            throw new BuildProcessingException("Failed to convert the Topic Error template into a DOM document");
        }
        DocBookUtilities.setSectionTitle(buildData.getDocBookVersion(), topic.getTitle(), doc);
        processTopicID(buildData, topic, doc);
        return doc;
    }

    /**
     * Sets the XML of the topic in the content spec to the error template provided.
     *
     * @param buildData Information and data structures for the build.
     * @param specTopic The spec topic to be updated as having an error.
     * @param template  The template for the Error Message.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    public static void setSpecTopicXMLForError(final BuildData buildData, final SpecTopic specTopic,
            final String template) throws BuildProcessingException {
        final BaseTopicWrapper<?> topic = specTopic.getTopic();
        final String errorContent = buildTopicErrorTemplate(buildData, topic, template);

        Document doc = null;
        try {
            doc = XMLUtilities.convertStringToDocument(errorContent);
        } catch (Exception ex) {
            // Exit since we shouldn't fail at converting a basic template
            log.debug("Topic Error Template is not valid XML", ex);
            throw new BuildProcessingException("Failed to convert the Topic Error template into a DOM document");
        }
        specTopic.setXMLDocument(doc);
        DocBookUtilities.setSectionTitle(buildData.getDocBookVersion(), topic.getTitle(), doc);
        processTopicID(buildData, topic, doc);
    }

    /**
     * This function scans the supplied XML node and it's children for id attributes, collecting them in the usedIdAttributes
     * parameter.
     *
     * @param docBookVersion
     * @param topic            The topic that we are collecting attribute ID's for.
     * @param node             The current node being processed (will be the document root to start with, and then all the children as this
     *                         function is recursively called)
     * @param usedIdAttributes The set of Used ID Attributes that should be added to.
     */
    public static void collectIdAttributes(final DocBookVersion docBookVersion, final SpecTopic topic, final Node node,
            final Map<SpecTopic, Set<String>> usedIdAttributes) {
        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            final Node idAttribute;
            if (docBookVersion == DocBookVersion.DOCBOOK_50) {
                idAttribute = attributes.getNamedItem("xml:id");
            } else {
                idAttribute = attributes.getNamedItem("id");
            }
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
            collectIdAttributes(docBookVersion, topic, elements.item(i), usedIdAttributes);
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

    public static List<String> checkTopicForInvalidContent(final BaseTopicWrapper<?> topic, final Document topicDoc,
            final BuildData buildData) {
        final List<String> xmlErrors = new ArrayList<String>();
        // Check to ensure that if the topic has programlisting elements, that the language is a valid Publican value
        if (!DocBookBuildUtilities.validateProgramListingLanguages(topicDoc)) {
            xmlErrors.add("The Program Listing language is not a valid Publican language.");
        }
        // Check to ensure that if the topic has a table, that the table isn't missing any entries
        if (!DocBookBuildUtilities.validateTopicTables(topicDoc)) {
            xmlErrors.add("Table column declaration doesn't match the number of entry elements.");
        }
        // Check that the root element matches the topic type
        final String rootElementErrors = checkTopicRootElement(buildData, topic, topicDoc);
        if (rootElementErrors != null) {
            xmlErrors.add(rootElementErrors);
        }
        // Check that the content matches the topic type
        xmlErrors.addAll(checkTopicContentBasedOnType(buildData, topic, topicDoc));

        return xmlErrors;
    }

    /**
     * @param buildData Information and data structures for the build.
     * @param topic
     * @param doc
     * @return
     */
    protected static String checkTopicRootElement(final BuildData buildData, final BaseTopicWrapper<?> topic, final Document doc) {
        final StringBuilder xmlErrors = new StringBuilder();
        if (topic.hasTag(buildData.getServerEntities().getRevisionHistoryTagId())) {
            if (!doc.getDocumentElement().getNodeName().equals("appendix")) {
                xmlErrors.append("Revision History topics must be an &lt;appendix&gt;.\n");
            }
        } else if (topic.hasTag(buildData.getServerEntities().getLegalNoticeTagId())) {
            if (!doc.getDocumentElement().getNodeName().equals("legalnotice")) {
                xmlErrors.append("Legal Notice topics must be a &lt;legalnotice&gt;.\n");
            }
        } else if (topic.hasTag(buildData.getServerEntities().getAuthorGroupTagId())) {
            if (!doc.getDocumentElement().getNodeName().equals("authorgroup")) {
                xmlErrors.append("Author Group topics must be a &lt;authorgroup&gt;.\n");
            }
        } else if (topic.hasTag(buildData.getServerEntities().getAbstractTagId())) {
            if (!doc.getDocumentElement().getNodeName().equals("abstract")) {
                xmlErrors.append("Abstract topics must be a &lt;abstract&gt;.\n");
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
     * @param buildData Information and data structures for the build.
     * @param topic
     * @param doc
     * @return
     */
    protected static List<String> checkTopicContentBasedOnType(final BuildData buildData, final BaseTopicWrapper<?> topic,
            final Document doc) {
        final List<String> xmlErrors = new ArrayList<String>();
        if (topic.hasTag(buildData.getServerEntities().getRevisionHistoryTagId())) {
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
                    final Date revisionDate = DateUtils.parseDateStrictly(cleanDate(date.getTextContent()), Locale.ENGLISH, DATE_FORMATS);
                    if (previousDate != null && revisionDate.after(previousDate)) {
                        return "The revisions in the Revision History are not in descending chronological order, " +
                                "starting from \"" + date.getTextContent() + "\".";
                    }

                    previousDate = revisionDate;
                } catch (Exception e) {
                    // Check that it is an invalid format or just an incorrect date (ie the day doesn't match)
                    try {
                        DateUtils.parseDate(cleanDate(date.getTextContent()), Locale.ENGLISH, DATE_FORMATS);
                        return "Invalid revision, the name of the day specified in \"" + date.getTextContent() + "\" doesn't match the " +
                                "date.";
                    } catch (Exception ex) {
                        return "Invalid revision, the date \"" + date.getTextContent() + "\" is not in a valid format.";
                    }
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

    /**
     * Basic method to clean a date string to fix any partial day names. It currently cleans "Thur", "Thurs" and "Tues".
     *
     * @param dateString
     * @return
     */
    private static String cleanDate(final String dateString) {
        if (dateString == null) {
            return dateString;
        }

        String retValue = dateString;
        retValue = THURSDAY_DATE_RE.matcher(retValue).replaceAll("Thu");
        retValue = TUESDAY_DATE_RE.matcher(retValue).replaceAll("Tue");

        return retValue;
    }

    public static void mergeRevisionHistories(final Document mainDoc, final Document mergeDoc) throws BuildProcessingException {
        final NodeList revhistories = mainDoc.getElementsByTagName("revhistory");
        if (revhistories.getLength() > 0) {
            final Element revhistory = (Element) revhistories.item(0);

            // Get the revision nodes
            final NodeList docRevisions = mainDoc.getElementsByTagName("revision");
            final NodeList additionalDocRevisions = mergeDoc.getElementsByTagName("revision");
            final List<Element> revisionNodes = new LinkedList<Element>();
            for (int i = 0; i < docRevisions.getLength(); i++) {
                revisionNodes.add((Element) docRevisions.item(i));
            }
            for (int i = 0; i < additionalDocRevisions.getLength(); i++) {
                revisionNodes.add((Element) additionalDocRevisions.item(i));
            }

            // Sort the revisions
            Collections.sort(revisionNodes, new RevisionNodeSort());

            // Insert the additional revisions
            final ListIterator<Element> listIterator = revisionNodes.listIterator(revisionNodes.size());

            Node prevNode = null;
            while (listIterator.hasPrevious()) {
                final Element revisionNode = listIterator.previous();

                // The node is from the additional doc
                if (!revisionNode.getOwnerDocument().equals(mainDoc)) {
                    final Node importedNode = mainDoc.importNode(revisionNode, true);

                    if (prevNode == null) {
                        revhistory.appendChild(importedNode);
                    } else {
                        prevNode.getParentNode().insertBefore(importedNode, prevNode);
                    }
                    prevNode = importedNode;
                } else {
                    prevNode = revisionNode;
                }
            }
        } else {
            throw new BuildProcessingException("The main document has no <revhistory> element and therefore can't be merged.");
        }
    }

    public static void mergeAuthorGroups(final Document mainDoc, final Document mergeDoc) throws BuildProcessingException {
        final NodeList authorGroups = mainDoc.getElementsByTagName("authorgroup");
        final NodeList mergeAuthorGroups = mergeDoc.getElementsByTagName("authorgroup");
        if (authorGroups.getLength() > 0 && mergeAuthorGroups.getLength() > 0) {
            final Element authorGroup = (Element) authorGroups.item(0);
            final Node mergeAuthorGroup = mainDoc.importNode(authorGroups.item(0), true);

            // Move all the authors/editors/othercredits to the main doc
            final NodeList mergeAuthorGroupChildren = mergeAuthorGroup.getChildNodes();
            while (mergeAuthorGroupChildren.getLength() > 0) {
                authorGroup.appendChild(mergeAuthorGroupChildren.item(0));
            }
        } else if (mergeAuthorGroups.getLength() > 0) {
            throw new BuildProcessingException("The translated document has no <authorgroup> element and therefore can't be merged.");
        } else {
            throw new BuildProcessingException("The main document has no <authorgroup> element and therefore can't be merged.");
        }
    }

    /**
     * Checks for instances of PressGang Injections that are invalid. This will check for the following problems:
     * <ul>
     * <li>Incorrect Captialisation</li>
     * <li>Invalid Injection types (eg. InjectListItem)</li>
     * <li>Missing colons</li>
     * <li>Incorrect ID list (eg referencing Topic 10 as 10.xml)</li>
     * </ul>
     *
     * @param doc The DOM document to be checked for invalid PressGang injections.
     * @return A List of {@link InjectionError} objects that contain the invalid injection and the error messages.
     */
    public static List<InjectionError> checkForInvalidInjections(final Document doc) {
        final List<InjectionError> retValue = new ArrayList<InjectionError>();

        final List<Node> comments = XMLUtilities.getComments(doc.getDocumentElement());
        for (final Node comment : comments) {
            final Matcher match = INJECT_RE.matcher(comment.getTextContent());
            if (match.find()) {
                final String type = match.group("TYPE");
                final String colon = match.group("COLON");
                final String ids = match.group("IDS");

                final InjectionError error = new InjectionError(comment.getTextContent());

                // Check the type
                if (!VALID_INJECTION_TYPES.contains(type)) {
                    error.addMessage(
                            "\"" + type + "\" is not a valid injection type. The valid types are: " + CollectionUtilities.toSeperatedString(
                                    VALID_INJECTION_TYPES, ", "));
                }

                // Check that a colon has been specified
                if (isNullOrEmpty(colon)) {
                    error.addMessage("No colon specified in the injection.");
                }

                // Check that the id(s) are valid
                if (isNullOrEmpty(ids) || !INJECT_ID_RE.matcher(ids).matches()) {
                    if (type.equalsIgnoreCase("inject")) {
                        error.addMessage(
                                "The Topic ID in the injection is invalid. Please ensure that only the Topic ID is used. eg " +
                                        "\"Inject: 1\"");
                    } else {
                        error.addMessage(
                                "The Topic ID(s) in the injection are invalid. Please ensure that only the Topic ID is used and is " +
                                        "in a comma separated list. eg \"InjectList: 1, 2, 3\"");
                    }
                } else if (type.equalsIgnoreCase("inject") && !INJECT_SINGLE_ID_RE.matcher(ids.trim()).matches()) {
                    error.addMessage(
                            "The Topic ID in the injection is invalid. Please ensure that only the Topic ID is used. eg " + "\"Inject: " +
                                    "1\"");
                }

                // Some errors were found so add the injection to the retValue
                if (!error.getMessages().isEmpty()) {
                    retValue.add(error);
                }
            }
        }

        return retValue;
    }

    public static boolean useStaticFixedURLForTopic(final BuildData buildData, final BaseTopicWrapper<?> topic) {
        return topic.hasTag(buildData.getServerEntities().getRevisionHistoryTagId()) || topic.hasTag(
                buildData.getServerEntities().getLegalNoticeTagId()) || topic.hasTag(
                buildData.getServerEntities().getAuthorGroupTagId()) || topic.hasTag(buildData.getServerEntities().getAbstractTagId());
    }

    public static String getStaticFixedURLForTopic(final BuildData buildData, final BaseTopicWrapper<?> topic) {
        if (topic.hasTag(buildData.getServerEntities().getRevisionHistoryTagId())) {
            return "appe-" + buildData.getEscapedBookTitle() + "-Revision_History";
        } else if (topic.hasTag(buildData.getServerEntities().getLegalNoticeTagId())) {
            return "Legal_Notice";
        } else if (topic.hasTag(buildData.getServerEntities().getAuthorGroupTagId())) {
            return "Author_Group";
        } else if (topic.hasTag(buildData.getServerEntities().getAbstractTagId())) {
            return "Abstract";
        } else {
            return null;
        }
    }
}