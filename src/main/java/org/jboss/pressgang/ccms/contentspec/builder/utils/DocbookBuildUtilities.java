package org.jboss.pressgang.ccms.contentspec.builder.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.code.regexp.NamedMatcher;
import com.google.code.regexp.NamedPattern;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.structures.CSDocbookBuildingOptions;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.wrapper.TranslatedTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;
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

        return topicXMLErrorTemplate;
    }


    /**
     * Collects any nodes that have the "condition" attribute in the
     * passed node or any of it's children nodes.
     *
     * @param node             The node to collect condition elements from.
     * @param conditionalNodes A mapping of nodes to their conditions
     */
    public static void collectConditionalStatements(final Node node, final Map<Node, List<String>> conditionalNodes) {
        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            final Node attr = attributes.getNamedItem("condition");

            if (attr != null) {
                final String conditionStatement = attr.getNodeValue();

                final String[] conditions = conditionStatement.split("\\s*;\\s*");

                conditionalNodes.put(node, Arrays.asList(conditions));
            }
        }

        final NodeList elements = node.getChildNodes();
        for (int i = 0; i < elements.getLength(); ++i) {
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
     * @param doc       The Document to check for conditional statements.
     */
    public static void processConditionalStatements(final String condition, final Document doc) {
        final Map<Node, List<String>> conditionalNodes = new HashMap<Node, List<String>>();
        collectConditionalStatements(doc.getDocumentElement(), conditionalNodes);

        // Loop through each condition found and see if it matches
        for (final Entry<Node, List<String>> entry : conditionalNodes.entrySet()) {
            final Node node = entry.getKey();
            final List<String> nodeConditions = entry.getValue();
            boolean matched = false;

            // Check to see if the condition matches
            for (final String nodeCondition : nodeConditions) {
                if (condition != null && nodeCondition.matches(condition)) {
                    matched = true;
                } else if (condition == null && nodeCondition.matches(BuilderConstants.DEFAULT_CONDITION)) {
                    matched = true;
                }
            }

            // If there was no match then remove the node
            if (!matched) {
                final Node parentNode = node.getParentNode();
                if (parentNode != null) {
                    parentNode.removeChild(node);
                }
            }
        }
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
}