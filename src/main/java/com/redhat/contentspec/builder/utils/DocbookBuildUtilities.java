package com.redhat.contentspec.builder.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.code.regexp.NamedMatcher;
import com.google.code.regexp.NamedPattern;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import com.redhat.contentspec.builder.constants.BuilderConstants;
import com.redhat.contentspec.structures.CSDocbookBuildingOptions;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.rest.v1.components.ComponentBaseTopicV1;
import org.jboss.pressgang.ccms.rest.v1.components.ComponentTopicV1;
import org.jboss.pressgang.ccms.rest.v1.components.ComponentTranslatedTopicV1;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTTopicV1;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTTranslatedTopicV1;
import org.jboss.pressgang.ccms.rest.v1.entities.base.RESTBaseTopicV1;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.DateTimeParser;
import org.joda.time.format.ISODateTimeFormat;
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
    private static final String STARTS_WITH_NUMBER_RE = "^(?<Numbers>\\d+)(?<EverythingElse>.*)$";
    private static final String STARTS_WITH_INVALID_SEQUENCE_RE = "^(?<InvalidSeq>[^\\w\\d]+)(?<EverythingElse>.*)$";
    private static final DateTimeParser[] parsers = {
            DateTimeFormat.forPattern("MM/dd/yyyy").getParser(),
            DateTimeFormat.forPattern("EEE MMM dd yyyy").getParser(),
            DateTimeFormat.forPattern("EEE, MMM dd yyyy").getParser(),
            DateTimeFormat.forPattern("EEE MMM dd yyyy Z").getParser(),
            DateTimeFormat.forPattern("EEE dd MMM yyyy").getParser(),
            DateTimeFormat.forPattern("EEE, dd MMM yyyy").getParser(),
            DateTimeFormat.forPattern("EEE dd MMM yyyy Z").getParser(),
            ISODateTimeFormat.basicDateTime().getParser()};
    private static final DateTimeFormatter formatter = new DateTimeFormatterBuilder().append(null, parsers).toFormatter();

    /**
     * Sets the "id" attributes in the supplied XML node so that they will be
     * unique within the book.
     *
     * @param specTopic        The topic the node belongs to.
     * @param node             The node to process for id attributes.
     * @param usedIdAttributes The list of usedIdAttributes.
     */
    public static void setUniqueIds(final SpecTopic specTopic, final Node node, final Document doc,
            final Map<Integer, Set<String>> usedIdAttributes) {
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
                final String attibuteValue = attributes.item(i).getNodeValue();
                if (attibuteValue.equals(id)) {
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
    public static boolean isUniqueAttributeId(final String id, final Integer topicId, final Map<Integer, Set<String>> usedIdAttributes) {
        boolean retValue = true;

        if (usedIdAttributes.containsKey(topicId)) {
            for (final Entry<Integer, Set<String>> entry : usedIdAttributes.entrySet()) {
                final Integer topicId2 = entry.getKey();
                if (topicId2.equals(topicId)) {
                    continue;
                }

                final Set<String> ids2 = entry.getValue();

                if (ids2.contains(id)) {
                    retValue = false;
                }
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
                    final String idAttibuteValue = idAttribute.getNodeValue();
                    linkIds.add(idAttibuteValue);
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
        /* Remove XML Elements from the Title. */
        baseTitle = baseTitle.replaceAll("</(.*?)>", "").replaceAll("<(.*?)>", "");

        /*
         * Check if the title starts with an invalid sequence
         */
        final NamedPattern invalidSequencePattern = NamedPattern.compile(STARTS_WITH_INVALID_SEQUENCE_RE);
        final NamedMatcher invalidSequenceMatcher = invalidSequencePattern.matcher(baseTitle);

        if (invalidSequenceMatcher.find()) {
            baseTitle = invalidSequenceMatcher.group("EverythingElse");
        }

        /*
         * start by removing any prefixed numbers (you can't
         * start an xref id with numbers)
         */
        final NamedPattern pattern = NamedPattern.compile(STARTS_WITH_NUMBER_RE);
        final NamedMatcher matcher = pattern.matcher(baseTitle);

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
    public static String buildTopicErrorTemplate(final RESTBaseTopicV1<?, ?, ?> topic, final String errorTemplate,
            final CSDocbookBuildingOptions docbookBuildingOptions) {
        String topicXMLErrorTemplate = errorTemplate;
        topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_TITLE_REGEX,
                DocBookUtilities.escapeTitleString(topic.getTitle()));

        // Set the topic id in the error
        final String errorXRefID;
        if (topic instanceof RESTTranslatedTopicV1) {
            final Integer topicId = ((RESTTranslatedTopicV1) topic).getTopicId();
            final Integer topicRevision = ((RESTTranslatedTopicV1) topic).getTopicRevision();
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX,
                    topicId + ", Revision " + topicRevision);
            errorXRefID = ComponentTranslatedTopicV1.returnErrorXRefID((RESTTranslatedTopicV1) topic);
        } else {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll(BuilderConstants.TOPIC_ID_REGEX, Integer.toString(topic.getId()));
            errorXRefID = ComponentTopicV1.returnErrorXRefID((RESTTopicV1) topic);
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
        if (ComponentBaseTopicV1.hasTag(topic, CSConstants.REVISION_HISTORY_TAG_ID)) {
            topicXMLErrorTemplate = topicXMLErrorTemplate.replaceAll("(?<=<(/)?)section(?=>)", "appendix");
        } else if (ComponentBaseTopicV1.hasTag(topic, CSConstants.LEGAL_NOTICE_TAG_ID)) {
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
            rev.append(BuilderConstants.DEFAULT_EDITION + ".0");
        } else if (contentSpec.getEdition().matches("^[0-9]+\\.[0-9]+\\.[0-9]+$")) {
            rev.append(bookVersion);
        } else if (contentSpec.getEdition().matches("^[0-9]+\\.[0-9]+(\\.[0-9]+)?$")) {
            rev.append(bookVersion + ".0");
        } else {
            rev.append(bookVersion + ".0.0");
        }

        return rev.toString();
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
                if (!BuilderConstants.VALID_PROGRAM_LISTING_LANGS.contains(programListing.getAttribute("language"))) {
                    valid = false;
                }
            }
        }

        return valid;
    }

    public static <T extends RESTBaseTopicV1<T, ?, ?>> List<String> checkTopicForInvalidContent(final T topic, final Document topicDoc) {
        final List<String> xmlErrors = new ArrayList<String>();
        // Check to ensure that if the topic has programlisting elements, that the language is a valid Publican value
        if (!DocbookBuildUtilities.validateProgramListingLanguages(topicDoc)) {
            xmlErrors.add("Table column declaration doesn't match the number of entry elements.");
        }
        // Check to ensure that if the topic has a table, that the table isn't missing any entries
        if (!DocbookBuildUtilities.validateTopicTables(topicDoc)) {
            xmlErrors.add("The Program Listing language is not a valid Publican language.");
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
    protected static String checkTopicRootElement(final RESTBaseTopicV1<?, ?, ?> topic, final Document doc) {
        final StringBuilder xmlErrors = new StringBuilder();
        if (ComponentBaseTopicV1.hasTag(topic, CSConstants.REVISION_HISTORY_TAG_ID)) {
            if (!doc.getDocumentElement().getNodeName().equals("appendix")) {
                xmlErrors.append("Revision History topics must be an &lt;appendix&gt;.\n");
            }
        } else if (ComponentBaseTopicV1.hasTag(topic, CSConstants.LEGAL_NOTICE_TAG_ID)) {
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
    protected static List<String> checkTopicContentBasedOnType(final RESTBaseTopicV1<?, ?, ?> topic, final Document doc) {
        final List<String> xmlErrors = new ArrayList<String>();
        if (ComponentBaseTopicV1.hasTag(topic, CSConstants.REVISION_HISTORY_TAG_ID)) {
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
        DateTime previousDate = null;
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
                    final DateTime revisionDate = formatter.parseDateTime(date.getTextContent());
                    if (previousDate != null && revisionDate.isAfter(previousDate)) {
                        return "The revisions in the Revision History are not in decending chronological order.";
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