/*
  Copyright 2011-2014 Red Hat, Inc

  This file is part of PressGang CCMS.

  PressGang CCMS is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  PressGang CCMS is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with PressGang CCMS.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.jboss.pressgang.ccms.contentspec.builder.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.ITopicNode;
import org.jboss.pressgang.ccms.contentspec.KeyValueNode;
import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.SpecNode;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildDatabase;
import org.jboss.pressgang.ccms.contentspec.enums.TopicType;
import org.jboss.pressgang.ccms.contentspec.sort.RevisionNodeSort;
import org.jboss.pressgang.ccms.contentspec.structures.XMLFormatProperties;
import org.jboss.pressgang.ccms.contentspec.utils.CustomTopicXMLValidator;
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
import org.w3c.dom.Attr;
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

    /**
     * Adds the levels in the provided Level object to the content spec database.
     *
     * @param buildDatabase
     * @param level         The content spec level to be added to the database.
     */
    public static void addLevelsToDatabase(final BuildDatabase buildDatabase, final Level level) {
        // Add the level to the database
        buildDatabase.add(level);

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
     * @param buildData
     * @param topicNode        The topic the node belongs to.
     * @param node             The node to process for id attributes.
     * @param usedIdAttributes The list of usedIdAttributes.
     */
    public static void setUniqueIds(final BuildData buildData, final ITopicNode topicNode, final Node node, final Document doc,
            final Map<SpecTopic, Set<String>> usedIdAttributes) {
        // The root node needs to be handled slightly differently
        boolean isRootNode = doc.getDocumentElement() == node;

        final NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            final Node idAttribute;
            if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
                idAttribute = attributes.getNamedItem("xml:id");
            } else {
                idAttribute = attributes.getNamedItem("id");
            }
            if (idAttribute != null) {
                final String idAttributeValue = idAttribute.getNodeValue();
                String fixedIdAttributeValue = idAttributeValue;

                // Set the duplicate id incase the topic has been included twice
                if (topicNode.getDuplicateId() != null) {
                    fixedIdAttributeValue += "-" + topicNode.getDuplicateId();
                }

                if (!isRootNode) {
                    // The same id may be used across multiple topics, so add the step/line number to make it unique
                    if (!DocBookBuildUtilities.isUniqueAttributeId(buildData, fixedIdAttributeValue, topicNode, usedIdAttributes)) {
                        fixedIdAttributeValue += "-" + topicNode.getStep();
                    }
                }

                setUniqueIdReferences(doc.getDocumentElement(), idAttributeValue, fixedIdAttributeValue);

                idAttribute.setNodeValue(fixedIdAttributeValue);
            }
        }

        final NodeList elements = node.getChildNodes();
        for (int i = 0; i < elements.getLength(); ++i) {
            setUniqueIds(buildData, topicNode, elements.item(i), doc, usedIdAttributes);
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
                final Attr attr = (Attr) attributes.item(i);
                // Ignore id attributes, as we only care about references (ie linkend)
                if (!(attr.getName().equalsIgnoreCase("id") || attr.getName().equalsIgnoreCase("xml:id"))) {
                    final String attributeValue = attr.getValue();
                    if (attributeValue.equals(id)) {
                        attributes.item(i).setNodeValue(fixedId);
                    }
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
     * @param buildData
     * @param id               The Attribute id to be checked
     * @param topicNode        The id of the topic the attribute id was found in
     * @param usedIdAttributes The set of used ids calculated earlier   @return True if the id is unique otherwise false.
     */
    public static boolean isUniqueAttributeId(final BuildData buildData, final String id, final ITopicNode topicNode,
            final Map<SpecTopic, Set<String>> usedIdAttributes) {
        // Make sure the id doesn't match the spec topic's unique link
        if (topicNode instanceof SpecTopic && id.equals(
                ((SpecTopic) topicNode).getUniqueLinkId(buildData.isUseFixedUrls()))) {
            return false;
        }

        // Make sure the id isn't used else where in another topic
        for (final Entry<SpecTopic, Set<String>> entry : usedIdAttributes.entrySet()) {
            final SpecTopic topic2 = entry.getKey();
            final Integer topicId2 = topic2.getDBId();
            if (topicId2.equals(topicNode.getDBId())) {
                continue;
            }

            final Set<String> ids2 = entry.getValue();
            if (ids2.contains(id)) {
                return false;
            }
        }

        return true;
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
        // Info topics cannot use the regular templates and the information should be hidden so just make a basic one
        if (topic.hasTag(buildData.getServerEntities().getInfoTagId())) {
            if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
                return DocBookUtilities.addDocBook50Namespace("<info><keywordset><keyword></keyword></keywordset></info>");
            } else {
                return "<sectioninfo><keywordset><keyword></keyword></keywordset></sectioninfo>";
            }
        }

        // Replace the markers in the template with values
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
    /**
     * Clean a user specified publican.cfg to remove content that should be set for a build.
     *
     * @param userPublicanCfg The User publican.cfg file to be cleaned.
     * @return The cleaned publican.cfg file.
     */
    public static String cleanUserPublicanCfg(final String userPublicanCfg) {
        // Remove any xml_lang statements
        String retValue = userPublicanCfg.replaceAll("(#( |\\t)*)?xml_lang\\s*:\\s*.*?($|\\r\\n|\\n)", "");
        // Remove any type statements
        retValue = retValue.replaceAll("(#( |\\t)*)?type\\s*:\\s*.*($|\\r\\n|\\n)" + "", "");
        // Remove any brand statements
        retValue = retValue.replaceAll("(#( |\\t)*)?brand\\s*:\\s*.*($|\\r\\n|\\n)" + "", "");
        // Remove any dtdver statements
        retValue = retValue.replaceAll("(^|\\n)( |\\t)*dtdver\\s*:\\s*.*($|\\r\\n|\\n)", "");
        // BZ#1091776 Remove mainfile
        retValue = retValue.replaceAll("(^|\\n)( |\\t)*mainfile\\s*:\\s*.*($|\\r\\n|\\n)", "");

        // Remove any whitespace before the text
        retValue = retValue.replaceAll("(^|\\n)\\s*", "$1");

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
        return convertDocumentToFormattedString(doc, xmlFormatProperties, true);
    }

    /**
     * Convert a DOM Document to a Formatted String representation.
     *
     * @param doc                 The DOM Document to be converted and formatted.
     * @param xmlFormatProperties The XML Formatting Properties.
     * @return The converted XML String representation.
     */
    public static String convertDocumentToFormattedString(final Document doc, final XMLFormatProperties xmlFormatProperties,
            final boolean includeElementName) {
        return XMLUtilities.convertNodeToString(doc, includeElementName, xmlFormatProperties.getVerbatimElements(),
                xmlFormatProperties.getInlineElements(), xmlFormatProperties.getContentsInlineElements(), true);
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
     * @param topicNode     The topic to be used to set the id attribute.
     * @param doc       The document object for the topics XML.
     */
    public static void processTopicID(final BuildData buildData, final ITopicNode topicNode, final Document doc) {
        final BaseTopicWrapper<?> topic = topicNode.getTopic();

        // Ignore info topics
        if (!topic.hasTag(buildData.getServerEntities().getInfoTagId())) {
            // Get the id value
            final String errorXRefID = ((SpecNode) topicNode).getUniqueLinkId(buildData.isUseFixedUrls());

            // Set the id
            setDOMElementId(buildData.getDocBookVersion(), doc.getDocumentElement(), errorXRefID);
        }

        // Add the remap parameter
        final Integer topicId = topicNode.getDBId();
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
        if (DocBookUtilities.TOPIC_ROOT_NODE_NAME.equals(doc.getDocumentElement().getNodeName())) {
            DocBookUtilities.setSectionTitle(buildData.getDocBookVersion(), topic.getTitle(), doc);
        }

        return doc;
    }

    /**
     * Sets the XML of the topic in the content spec to the error template provided.
     *
     * @param buildData Information and data structures for the build.
     * @param topicNode The topic node to be updated as having an error.
     * @param template  The template for the Error Message.
     * @throws BuildProcessingException Thrown if an unexpected error occurs during building.
     */
    public static void setTopicNodeXMLForError(final BuildData buildData, final ITopicNode topicNode,
            final String template) throws BuildProcessingException {
        final BaseTopicWrapper<?> topic = topicNode.getTopic();
        final Document doc = setTopicXMLForError(buildData, topic, template);
        processTopicID(buildData, topicNode, doc);
        topicNode.setXMLDocument(doc);
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

    public static List<String> checkTopicForInvalidContent(final ITopicNode topicNode, final BaseTopicWrapper<?> topic,
            final Document topicDoc, final BuildData buildData) {
        // Do the base custom topic validation
        final List<String> xmlErrors = CustomTopicXMLValidator.checkTopicForInvalidContent(buildData.getServerSettings(), topic,
                topicDoc, buildData.getBuildOptions().isSkipNestedSectionValidation());

        // Check to make sure the XML can be used in the initial content
        if (topicNode.getTopicType() == TopicType.INITIAL_CONTENT && topicNode instanceof SpecTopic) {
            if (!CustomTopicXMLValidator.doesTopicHaveValidXMLForInitialContent((SpecTopic) topicNode, topic)) {
                xmlErrors.add(BuilderConstants.ERROR_TOPIC_CANNOT_BE_USED_AS_INITIAL_CONTENT);
            }
        }

        return xmlErrors;
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

    public static <T> String getKeyValueNodeText(final BuildData buildData, final KeyValueNode<T> keyValueNode) {
        if (keyValueNode == null) {
            return null;
        } else {
            if (buildData.isTranslationBuild() && keyValueNode.getTranslatedValue() != null) {
                return (String) keyValueNode.getTranslatedValue();
            } else {
                return (String) keyValueNode.getValue();
            }
        }
    }
}
