package org.jboss.pressgang.ccms.contentspec.builder;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.ITopicNode;
import org.jboss.pressgang.ccms.contentspec.InitialContent;
import org.jboss.pressgang.ccms.contentspec.KeyValueNode;
import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.Node;
import org.jboss.pressgang.ccms.contentspec.SpecNode;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuilderCreationException;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.DocBookBuildingOptions;
import org.jboss.pressgang.ccms.contentspec.builder.structures.InjectionListData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.POBuildData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.TopicErrorData;
import org.jboss.pressgang.ccms.contentspec.builder.structures.TranslationDetails;
import org.jboss.pressgang.ccms.contentspec.builder.utils.DocBookBuildUtilities;
import org.jboss.pressgang.ccms.contentspec.entities.Relationship;
import org.jboss.pressgang.ccms.contentspec.entities.TargetRelationship;
import org.jboss.pressgang.ccms.contentspec.entities.TopicRelationship;
import org.jboss.pressgang.ccms.contentspec.enums.BookType;
import org.jboss.pressgang.ccms.contentspec.enums.LevelType;
import org.jboss.pressgang.ccms.contentspec.enums.TopicType;
import org.jboss.pressgang.ccms.contentspec.exceptions.BugLinkException;
import org.jboss.pressgang.ccms.contentspec.utils.EntityUtilities;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.jboss.pressgang.ccms.provider.TranslatedContentSpecProvider;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.utils.common.StringUtilities;
import org.jboss.pressgang.ccms.utils.common.XMLUtilities;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.utils.constants.CommonFilterConstants;
import org.jboss.pressgang.ccms.utils.structures.Pair;
import org.jboss.pressgang.ccms.utils.structures.StringToNodeCollection;
import org.jboss.pressgang.ccms.wrapper.CSNodeWrapper;
import org.jboss.pressgang.ccms.wrapper.TagWrapper;
import org.jboss.pressgang.ccms.wrapper.TopicWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedCSNodeStringWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedCSNodeWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedContentSpecWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedTopicStringWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.collection.CollectionWrapper;
import org.jboss.pressgang.ccms.zanata.ZanataDetails;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class PublicanPODocBookBuilder extends PublicanDocBookBuilder {
    private static SimpleDateFormat POT_DATE_FORMAT = new SimpleDateFormat("YYYY-MM-dd HH:mmZ");

    public PublicanPODocBookBuilder(final DataProviderFactory providerFactory) throws BuilderCreationException {
        super(providerFactory);
    }

    /**
     * Builds a DocBook Formatted Book using a Content Specification to define the structure and contents of the book.
     *
     * @param contentSpec     The content specification to build from.
     * @param requester       The user who requested the build.
     * @param buildingOptions The options to be used when building.
     * @param zanataDetails   The Zanata server details to be used when populating links
     * @return Returns a mapping of file names/locations to files. This HashMap can be used to build a ZIP archive.
     * @throws BuilderCreationException Thrown if the builder is unable to start due to incorrect passed variables.
     * @throws BuildProcessingException Any build issue that should not occur under normal circumstances. Ie a Template can't be
     *                                  converted to a DOM Document.
     */
    @Override
    public HashMap<String, byte[]> buildTranslatedBook(final ContentSpec contentSpec, final String requester,
            final DocBookBuildingOptions buildingOptions,
            final ZanataDetails zanataDetails) throws BuilderCreationException, BuildProcessingException {
        buildingOptions.setResolveEntities(true);
        // Translation builds ignore the publican.cfg condition parameter
        if (contentSpec.getPublicanCfg() != null) {
            contentSpec.setPublicanCfg(contentSpec.getPublicanCfg().replaceAll("condition:\\s*.*($|\\r\\n|\\n)", ""));
        }
        // For PO builds just do a normal build initially and then add the POT/PO files later
        return buildBook(contentSpec, requester, buildingOptions, new HashMap<String, byte[]>());
    }

    /**
     * Builds a DocBook Formatted Book using a Content Specification to define the structure and contents of the book.
     *
     * @param contentSpec     The content specification to build from.
     * @param requester       The user who requested the build.
     * @param buildingOptions The options to be used when building.
     * @param overrideFiles
     * @param zanataDetails   The Zanata server details to be used when populating links
     * @return Returns a mapping of file names/locations to files. This HashMap can be used to build a ZIP archive.
     * @throws BuilderCreationException Thrown if the builder is unable to start due to incorrect passed variables.
     * @throws BuildProcessingException Any build issue that should not occur under normal circumstances. Ie a Template can't be
     *                                  converted to a DOM Document.
     */
    @Override
    public HashMap<String, byte[]> buildTranslatedBook(final ContentSpec contentSpec, final String requester,
            final DocBookBuildingOptions buildingOptions, final Map<String, byte[]> overrideFiles,
            final ZanataDetails zanataDetails) throws BuilderCreationException, BuildProcessingException {
        buildingOptions.setResolveEntities(true);
        // Translation builds ignore the publican.cfg condition parameter
        if (contentSpec.getPublicanCfg() != null) {
            contentSpec.setPublicanCfg(contentSpec.getPublicanCfg().replaceAll("condition:\\s*.*($|\\r\\n|\\n)", ""));
        }
        // For PO builds just do a normal build initially and then add the POT/PO files later
        return buildBook(contentSpec, requester, buildingOptions, overrideFiles);
    }

    @Override
    protected BuildData createBuildData(final String fixedRequester, final ContentSpec contentSpec,
            final DocBookBuildingOptions buildingOptions, final ZanataDetails zanataDetails, final DataProviderFactory providerFactory,
            final boolean translationBuild) {
        return new POBuildData(fixedRequester, contentSpec, buildingOptions, zanataDetails, providerFactory,
                translationBuild);
    }

    /**
     * Wrap all of the topics, images, common content, etc... files into a ZIP Archive.
     *
     * @param buildData Information and data structures for the build.
     * @return A ZIP Archive containing all the information to build the book.
     * @throws BuildProcessingException Any build issue that should not occur under normal circumstances. Ie a Template can't be
     *                                  converted to a DOM Document.
     */
    @Override
    protected HashMap<String, byte[]> doBuildZipPass(final BuildData buildData) throws BuildProcessingException {
        final POBuildData poBuildData = (POBuildData) buildData;

        // Create the mapping required for the PO files
        if (buildData.getBuildLocale().equals(poBuildData.getPOBuildLocale())) {
            doPOTPopulatePass(poBuildData);
        } else {
            doPOPopulatePass(poBuildData);
        }

        // Build the core of the book
        final HashMap<String, byte[]> files = super.doBuildZipPass(buildData);

        // Add PO files
        buildPOPass(poBuildData);

        return files;
    }

    protected void doPOTPopulatePass(final POBuildData buildData) throws BuildProcessingException {
        for (final BaseTopicWrapper<?> topic : buildData.getBuildDatabase().getAllTopics()) {
            // Find the actual content spec node relating to the DB cs node
            final List<ITopicNode> topicNodes = buildData.getBuildDatabase().getTopicNodesForTopicID(topic.getTopicId());

            // Make sure we will have a topic node to process
            if (topicNodes.isEmpty()) {
                continue;
            }

            final ITopicNode topicNode = topicNodes.get(0);

            final Map<String, TranslationDetails> topicTranslations = new HashMap<String, TranslationDetails>();
            getTranslationStringsFromOriginalTopic(buildData, topicNode, topicTranslations);
            buildData.getTranslationMap().put(topic.getTopicId().toString(), topicTranslations);
        }
    }

    protected void doPOPopulatePass(final POBuildData buildData) throws BuildProcessingException {
        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        final ContentSpec contentSpec = buildData.getContentSpec();
        final String locale = buildData.getPOBuildLocale();
        log.info("Doing " + locale + " Populate PO Pass");

        final CollectionWrapper<TranslatedContentSpecWrapper> translatedContentSpecs = providerFactory.getProvider(
                TranslatedContentSpecProvider.class).getTranslatedContentSpecsWithQuery("query;" +
                CommonFilterConstants.ZANATA_IDS_FILTER_VAR + "=CS" + contentSpec.getId() + "-" + contentSpec.getRevision());

        // Ensure that the passed content spec has a translation
        if (translatedContentSpecs == null || translatedContentSpecs.isEmpty()) {
            throw new BuildProcessingException(
                    "Unable to find any translations for Content Spec " + contentSpec.getId() + (contentSpec.getRevision() == null ? "" :
                            (", Revision " + contentSpec.getRevision())));
        }

        final TranslatedContentSpecWrapper translatedContentSpec = translatedContentSpecs.getItems().get(0);
        if (translatedContentSpec.getTranslatedNodes() != null) {
            final int showPercent = 10;
            final float total = translatedContentSpec.getTranslatedNodes().size();
            float current = 0;
            int lastPercent = 0;

            final Map<String, TranslationDetails> translations = new HashMap<String, TranslationDetails>();

            // Iterate over each translated node and build up the list of translated strings for the content spec.
            final List<TranslatedCSNodeWrapper> translatedCSNodes = translatedContentSpec.getTranslatedNodes().getItems();
            for (final TranslatedCSNodeWrapper translatedCSNode : translatedCSNodes) {
                // Check if the app should be shutdown
                if (isShuttingDown.get()) {
                    return;
                }

                // Log the percentage complete
                ++current;
                final int percent = Math.round(current / total * 100);
                if (percent - lastPercent >= showPercent) {
                    lastPercent = percent;
                    log.info("\tPopulate PO Pass " + percent + "% Done");
                }

                final CSNodeWrapper csNode = translatedCSNode.getCSNode();

                // Only process nodes that have content pushed to Zanata
                if (!isNullOrEmpty(translatedCSNode.getOriginalString())) {
                    if (translatedCSNode.getTranslatedStrings() != null) {
                        final List<TranslatedCSNodeStringWrapper> translatedCSNodeStrings = translatedCSNode.getTranslatedStrings()
                                .getItems();
                        for (final TranslatedCSNodeStringWrapper translatedCSNodeString : translatedCSNodeStrings) {
                            if (translatedCSNodeString.getLocale().equals(locale)) {
                                final TranslationDetails translationDetails = new TranslationDetails(
                                        translatedCSNodeString.getTranslatedString(), translatedCSNodeString.isFuzzy(),
                                        getCSNodeTagName(csNode));
                                translations.put(translatedCSNode.getOriginalString(), translationDetails);
                            }
                        }
                    }
                } else {
                    // Get the translations for the topic
                    if (EntityUtilities.isNodeATopic(csNode)) {
                        getTranslationsForTopic(buildData, locale, csNode.getId().toString(), csNode.getEntityId(),
                                csNode.getEntityRevision(), translatedCSNode);
                    }
                }

                // Add in any info topic translation strings
                if (csNode.getInfoTopicNode() != null) {
                    getTranslationsForTopic(buildData, locale, csNode.getId().toString(), csNode.getInfoTopicNode().getTopicId(),
                            csNode.getInfoTopicNode().getTopicRevision(), translatedCSNode);
                }
            }

            buildData.getTranslationMap().put("CS" + contentSpec.getId(), translations);
        }
    }

    protected String getCSNodeTagName(final CSNodeWrapper csNode) {
        if (EntityUtilities.isNodeALevel(csNode)) {
            return "title";
        } else if (csNode.getNodeType() == CommonConstants.CS_NODE_META_DATA) {
            if (CommonConstants.CS_TITLE_TITLE.equalsIgnoreCase(csNode.getTitle())) {
                    return "title";
            } else if (CommonConstants.CS_PRODUCT_TITLE.equalsIgnoreCase(csNode.getTitle())) {
                return "productname";
            } else if (CommonConstants.CS_SUBTITLE_TITLE.equalsIgnoreCase(csNode.getTitle())) {
                return "subtitle";
            } else if (CommonConstants.CS_ABSTRACT_TITLE.equalsIgnoreCase(csNode.getTitle())) {
                return "para";
            }
        }

        return null;
    }

    protected void getTranslationsForTopic(final POBuildData buildData, final String locale, final String uniqueId, final Integer id,
            final Integer revision, final TranslatedCSNodeWrapper translatedCSNode) {
        // Find the actual content spec node relating to the DB cs node
        final List<ITopicNode> topicNodes = buildData.getBuildDatabase().getTopicNodesForTopicID(id);

        // Make sure we will have a topic node to process
        if (topicNodes.isEmpty()) {
            return;
        }

        ITopicNode topicNode = topicNodes.get(0);
        for (final ITopicNode node : topicNodes) {
            if (uniqueId.equals(node.getUniqueId())) {
                topicNode = node;
                break;
            }
        }

        final Map<String, TranslationDetails> topicTranslations = new HashMap<String, TranslationDetails>();
        final TopicErrorData errorData = buildData.getErrorDatabase().getErrorData(topicNode.getTopic());
        if (errorData != null && errorData.hasFatalErrors()) {
            // Topics with errors won't have any translations, so just use the original source content
            getTranslationStringsFromOriginalTopic(buildData, topicNode, topicTranslations);
        } else {
            // Get the latest translation and the latest pushed translation
            final Pair<TranslatedTopicWrapper, TranslatedTopicWrapper> latestTranslation;
            if (translatedCSNode.getTranslatedTopics() == null || translatedCSNode.getTranslatedTopics().isEmpty()) {
                final TopicWrapper topic = (TopicWrapper) topicNode.getTopic();
                latestTranslation = getLatestTranslations(buildData, topic.getTranslatedTopics(), revision, locale);
            } else {
                latestTranslation = getLatestTranslations(buildData,
                        translatedCSNode.getTranslatedTopics(), revision, locale);
            }

            final TranslatedTopicWrapper latestTranslatedTopic = latestTranslation.getSecond();
            final TranslatedTopicWrapper latestPushedTranslatedTopic = latestTranslation.getFirst();

            // Add the strings for the topic

            if (latestPushedTranslatedTopic != null) {
                getTranslationStringsFromTranslatedTopic(buildData, topicNode, latestTranslatedTopic,
                        latestPushedTranslatedTopic, topicTranslations);
            } else {
                getTranslationStringsFromOriginalTopic(buildData, topicNode, topicTranslations);
            }
        }
        buildData.getTranslationMap().put(id.toString(), topicTranslations);
    }

    protected void getTranslationStringsFromTranslatedTopic(final POBuildData buildData, final ITopicNode topicNode,
            final TranslatedTopicWrapper latestTranslatedTopic, final TranslatedTopicWrapper latestPushedTranslatedTopic,
            final Map<String, TranslationDetails> topicTranslations) {
        final Map<String, TranslationDetails> currentTopicTranslations = new HashMap<String, TranslationDetails>();

        if (latestTranslatedTopic != null && latestPushedTranslatedTopic.getTopicRevision().equals
                (latestTranslatedTopic.getTopicRevision())) {
            // Get any strings that have been translated
            for (final TranslatedTopicStringWrapper translatedTopicString : latestTranslatedTopic
                    .getTranslatedTopicStrings().getItems()) {
                // Run the formatter over the string so that it will be the same as the XML stored in PressGang.
                final String fixedOriginalString = formatTranslationString(translatedTopicString.getOriginalString());

                final TranslationDetails translationDetails = new TranslationDetails(
                        translatedTopicString.getTranslatedString(), translatedTopicString.isFuzzy());
                currentTopicTranslations.put(fixedOriginalString, translationDetails);
            }

            // Setup the additional xml for rev history/author group
            if (topicNode.getTopic().hasTag(buildData.getServerEntities().getAuthorGroupTagId())) {
                buildData.setTranslatedAuthorGroup(latestTranslatedTopic.getTranslatedAdditionalXML());
            } else if (topicNode.getTopic().hasTag(buildData.getServerEntities().getRevisionHistoryTagId())) {
                buildData.setTranslatedRevisionHistory(latestTranslatedTopic.getTranslatedAdditionalXML());
            }
        }

        // Get any untranslated strings from the XML
        try {
            final Document doc = XMLUtilities.convertStringToDocument(latestPushedTranslatedTopic.getXml());
            final List<StringToNodeCollection> stringToNodeCollections = DocBookUtilities.getTranslatableStringsV3(doc,
                    false);

            for (final StringToNodeCollection stringToNode : stringToNodeCollections) {
                final org.w3c.dom.Node parentNode = stringToNode.getNodeCollections().get(0).get(0).getParentNode();
                final String tagName = parentNode.getNodeName();

                // Check that we haven't already picked up the translation string
                if (currentTopicTranslations.containsKey(stringToNode.getTranslationString())) {
                    final TranslationDetails translationDetails = currentTopicTranslations.get(stringToNode.getTranslationString());
                    translationDetails.setTagName(tagName);
                    topicTranslations.put(stringToNode.getTranslationString(), translationDetails);
                } else {
                    // See if it was a V2 translation and fix it if possible
                    if (DocBookUtilities.VERBATIM_ELEMENTS.contains(tagName)) {
                        checkAndFixV2Translations(stringToNode.getTranslationString(), parentNode, currentTopicTranslations,
                                topicTranslations);
                    }

                    // See if it was a V1 translation and fix it if possible
                    checkAndFixV1Translations(doc, currentTopicTranslations, topicTranslations);

                    // Check to see if it was added as part of the V1 or V2 fix. If not then add it as having no translation
                    if (!topicTranslations.containsKey(stringToNode.getTranslationString())) {
                        final TranslationDetails translationDetails = new TranslationDetails(null, false, tagName);
                        topicTranslations.put(stringToNode.getTranslationString(), translationDetails);
                    }
                }
            }

            // Add in any list injection information
            checkAndAddListInjections(buildData, doc, topicNode, topicTranslations);
        } catch (Exception e) {
            log.debug("Failed to convert " + latestPushedTranslatedTopic.getZanataId() + "'s xml into a DOM Document");

            // If this failed then add the strings from the source xml
            getTranslationStringsFromOriginalTopic(buildData, topicNode, topicTranslations);
        }
    }

    protected void getTranslationStringsFromOriginalTopic(final BuildData buildData, final ITopicNode topicNode,
            final Map<String, TranslationDetails> topicTranslations) {
        // Get the source strings from the current topic XML
        final List<StringToNodeCollection> stringToNodeCollections = DocBookUtilities.getTranslatableStringsV3(topicNode.getXMLDocument(),
                false);

        for (final StringToNodeCollection stringToNode : stringToNodeCollections) {
            topicTranslations.put(stringToNode.getTranslationString(), null);
        }

        // Add in any list injection information
        checkAndAddListInjections(buildData, topicNode.getXMLDocument(), topicNode, topicTranslations);
    }

    /**
     * Checks to see if any list injections have been used and if so adds the translation xrefs.
     *
     * @param buildData    Information and data structures for the build.
     * @param doc          The DOM Document to look for list injections in.
     * @param topicNode    The topic to get the injections for.
     * @param translations The mapping of original strings to translation strings, that will be used to build the po/pot files.
     */
    protected void checkAndAddListInjections(final BuildData buildData, final Document doc, final ITopicNode topicNode,
            final Map<String, TranslationDetails> translations) {
        final DocBookXMLPreProcessor preProcessor = buildData.getXMLPreProcessor();
        final List<Integer> types =
                Arrays.asList(DocBookXMLPreProcessor.ORDEREDLIST_INJECTION_POINT, DocBookXMLPreProcessor.ITEMIZEDLIST_INJECTION_POINT,
                        DocBookXMLPreProcessor.LIST_INJECTION_POINT);

        final HashMap<org.w3c.dom.Node, InjectionListData> customInjections = new HashMap<org.w3c.dom.Node, InjectionListData>();
        preProcessor.collectInjectionData(buildData.getContentSpec(), topicNode, new ArrayList<String>(), doc, buildData.getBuildDatabase(),
                new ArrayList<String>(), customInjections, buildData.isUseFixedUrls(),
                buildData.getServerEntities().getFixedUrlPropertyTagId(), types);

        // Now convert the custom injection points
        for (final org.w3c.dom.Node customInjectionCommentNode : customInjections.keySet()) {
            final InjectionListData injectionListData = customInjections.get(customInjectionCommentNode);

            /*
             * this may not be true if we are not building all related topics
             */
            if (injectionListData.listItems.size() != 0) {
                for (final List<Element> elements : injectionListData.listItems) {
                    final Element para = doc.createElement("para");
                    for (final Element element : elements) {
                        para.appendChild(element);
                    }

                    final String translationString = XMLUtilities.convertNodeToString(para, false);
                    translations.put(translationString, new TranslationDetails(translationString, false, "para"));
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    protected void checkAndFixV2Translations(final String v3OriginalString, final org.w3c.dom.Node parentNode,
            final Map<String, TranslationDetails> currentTopicTranslations,
            final Map<String, TranslationDetails> topicTranslations) {
        final List<StringToNodeCollection> stringToNodeCollections = new ArrayList<StringToNodeCollection>();
        DocBookUtilities.getTranslatableStringsFromNodeV2(parentNode, stringToNodeCollections, false, new DocBookUtilities.XMLProperties());

        for (final StringToNodeCollection stringToNode : stringToNodeCollections) {
            if (currentTopicTranslations.containsKey(stringToNode.getTranslationString())) {
                final TranslationDetails translationDetails = currentTopicTranslations.get(stringToNode.getTranslationString());
                translationDetails.setTagName(parentNode.getNodeName());
                topicTranslations.put(v3OriginalString, translationDetails);
            }
        }
    }

    @SuppressWarnings("deprecation")
    protected void checkAndFixV1Translations(final Document doc, final Map<String, TranslationDetails> currentTopicTranslations,
            final Map<String, TranslationDetails> topicTranslations) {
        /*
         * Since V1 of the getTranslatableStrings() method wasn't breaking down content correctly,
         * we need to break it down further using the fixed V2/V3 method and then add the strings.
         */
        final List<StringToNodeCollection> stringToNodeCollections = DocBookUtilities.getTranslatableStringsV1(doc, false);

        for (final StringToNodeCollection stringToNode : stringToNodeCollections) {
            final String originalString = stringToNode.getTranslationString();
            // Check if a V1 translation exists. If it does then fix it up by breaking both the original and the
            if (currentTopicTranslations.containsKey(originalString)) {
                try {
                    final TranslationDetails translatedString = currentTopicTranslations.get(originalString);

                    // wrap the original/translation in a root element
                    final String wrappedOriginalString = "<tempRoot>" + originalString + "</tempRoot>";
                    final String wrappedTranslation = "<tempRoot>" + translatedString.getTranslation() + "</tempRoot>";

                    // Break down the original and translated strings
                    final Document originalDocument = XMLUtilities.convertStringToDocument(wrappedOriginalString);
                    final List<StringToNodeCollection> originalStrings = DocBookUtilities.getTranslatableStringsV3
                            (originalDocument, false);

                    // The original string size will be zero if the string contains no translatable elements
                    if (originalStrings.size() > 0) {
                        final Document translationDocument = XMLUtilities.convertStringToDocument(wrappedTranslation);
                        final List<StringToNodeCollection> translationStrings = DocBookUtilities.getTranslatableStringsV3
                                (translationDocument, false);

                        // Add the broken down content to the topic translation map
                        for (int i = 0; i < originalStrings.size(); i++) {
                            final StringToNodeCollection original = originalStrings.get(i);
                            final StringToNodeCollection translation = translationStrings.get(i);

                            final String tagName = original.getNodeCollections().get(0).get(0).getParentNode().getNodeName();
                            final TranslationDetails translationDetails = new TranslationDetails(translation.getTranslationString(),
                                    translatedString.isFuzzy(), tagName);
                            topicTranslations.put(original.getTranslationString(), translationDetails);
                        }
                    }
                } catch (Exception ex) {
                    // Do Nothing
                }
            }
        }
    }

    protected void buildPOPass(final POBuildData buildData) throws BuildProcessingException {
        if (!buildData.getDefaultLocale().equals(buildData.getPOBuildLocale())) {
            log.info("\tBuilding the POT Files");
        } else {
            log.info("\tBuilding the POT/PO Files");
        }

        buildBookBasePOPass(buildData);

        buildBookAdditionsPOPass(buildData);

        if (!buildData.getDefaultLocale().equals(buildData.getPOBuildLocale())) {
            // Add the translated images to the book
            final String imageFolder = buildData.getRootBookFolder() + buildData.getPOOutputLocale() + "/images/";
            addImagesToBook(buildData, buildData.getPOBuildLocale(), imageFolder, false, false);
        }
    }

    protected void buildBookAdditionsPOPass(final POBuildData buildData) throws BuildProcessingException {
        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        final ContentSpec contentSpec = buildData.getContentSpec();

        // en-US Author_Group
        if (contentSpec.getAuthorGroup() != null) {
            final TopicErrorData errorData = buildData.getErrorDatabase().getErrorData(contentSpec.getAuthorGroup().getTopic());
            if (errorData != null && errorData.hasFatalErrors()) {
                buildPODefaultFile(buildData, AUTHOR_GROUP_FILE_NAME);
            } else {
                buildPOTopic(buildData, contentSpec.getAuthorGroup(), "Author_Group");
            }
        } else {
            buildPODefaultFile(buildData, AUTHOR_GROUP_FILE_NAME);
        }

        // en-US Revision_History
        if (contentSpec.getRevisionHistory() != null) {
            final TopicErrorData errorData = buildData.getErrorDatabase().getErrorData(contentSpec.getRevisionHistory().getTopic());
            if (errorData != null && errorData.hasFatalErrors()) {
                buildPODefaultRevisionHistory(buildData);
            } else {
                buildPOTopic(buildData, contentSpec.getRevisionHistory(), "Revision_History");
            }
        } else {
            buildPODefaultRevisionHistory(buildData);
        }

        // If we are building for another locale add in the additional information
        if (!buildData.getBuildLocale().equals(buildData.getPOBuildLocale())) {
            // Translated Author_Group
            buildLocaleAuthorGroup(buildData);

            // Translated Revision_History
            buildLocaleRevisionHistory(buildData);
        }

        // Book_Info
        buildBookInfoPO(buildData);
        // Preface
        buildBookPrefacePO(buildData);
        // Feedback
        if (contentSpec.getFeedback() != null) {
            buildPOTopic(buildData, contentSpec.getFeedback(), "Feedback");
        }
        // Legal Notice
        if (contentSpec.getLegalNotice() != null) {
            buildPOTopic(buildData, contentSpec.getLegalNotice(), "Legal_Notice");
        }
    }

    protected void buildLocaleRevisionHistory(final POBuildData buildData) throws BuildProcessingException {
        if (!isNullOrEmpty(buildData.getTranslatedRevisionHistory())) {
            final SpecTopic revisionTopic = buildData.getContentSpec().getRevisionHistory();

            Document doc = null;
            try {
                doc = XMLUtilities.convertStringToDocument(buildData.getTranslatedRevisionHistory());
            } catch (Exception e) {
                // TODO Work out what to do with invalid translated XML
                return;
            }
            // Process the conditional statements
            processConditions(buildData, revisionTopic, doc);
            processSpecTopicInjections(buildData, revisionTopic, buildData.getXMLPreProcessor());

            final String revisionHistory = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(),
                    doc, "appendix", buildData.getEntityFileName(), getXMLFormatProperties());

            // Add the XML to the ZIP
            addToZip(buildData.getRootBookFolder() + buildData.getPOOutputLocale() + "/" + REVISION_HISTORY_FILE_NAME,
                    revisionHistory, buildData);
        }
    }

    protected void buildLocaleAuthorGroup(final POBuildData buildData) throws BuildProcessingException {
        if (!isNullOrEmpty(buildData.getTranslatedAuthorGroup())) {
            final SpecTopic authorGroupTopic = buildData.getContentSpec().getAuthorGroup();

            Document doc = null;
            try {
                doc = XMLUtilities.convertStringToDocument(buildData.getTranslatedAuthorGroup());
            } catch (Exception e) {
                // TODO Work out what to do with invalid translated XML
                return;
            }
            // Process the conditional statements
            processConditions(buildData, authorGroupTopic, doc);
            processSpecTopicInjections(buildData, authorGroupTopic, buildData.getXMLPreProcessor());

            final String revisionHistory = DocBookBuildUtilities.convertDocumentToDocBookFormattedString(buildData.getDocBookVersion(),
                    doc, "authorgroup", buildData.getEntityFileName(), getXMLFormatProperties());

            // Add the XML to the ZIP
            addToZip(buildData.getRootBookFolder() + buildData.getPOOutputLocale() + "/" + AUTHOR_GROUP_FILE_NAME,
                    revisionHistory, buildData);
        }
    }

    protected void buildPODefaultRevisionHistory(final POBuildData buildData) throws BuildProcessingException {
        final Map<String, TranslationDetails> translations = new LinkedHashMap<String, TranslationDetails>();

        // Get any untranslated strings from the XML
        try {
            final String revisionHistoryXML = new String(buildData.getOutputFiles().get(buildData.getBookLocaleFolder() +
                    REVISION_HISTORY_FILE_NAME), ENCODING);
            final Document doc = XMLUtilities.convertStringToDocument(revisionHistoryXML);
            final List<StringToNodeCollection> stringToNodeCollections = DocBookUtilities.getTranslatableStringsV3(doc, false);

            for (final StringToNodeCollection stringToNode : stringToNodeCollections) {
                final String tagName = stringToNode.getNodeCollections().get(0).get(0).getParentNode().getNodeName();
                translations.put(stringToNode.getTranslationString(), new TranslationDetails(null, false, tagName));
            }
        } catch (Exception e) {
            // Do nothing, as at this point it should be valid
        }

        addStringsForConstant(buildData, "REVISION_HISTORY", "title", translations);

        createPOFile(buildData, "Revision_History", translations);
    }

    protected void buildPODefaultFile(final POBuildData buildData, final String fileName) throws BuildProcessingException {
        final Map<String, TranslationDetails> translations = new LinkedHashMap<String, TranslationDetails>();

        // Get any untranslated strings from the XML
        try {
            final String xml = new String(buildData.getOutputFiles().get(buildData.getBookLocaleFolder() +
                    fileName), ENCODING);
            final Document doc = XMLUtilities.convertStringToDocument(xml);
            final List<StringToNodeCollection> stringToNodeCollections = DocBookUtilities.getTranslatableStringsV3(doc, false);

            for (final StringToNodeCollection stringToNode : stringToNodeCollections) {
                final String tagName = stringToNode.getNodeCollections().get(0).get(0).getParentNode().getNodeName();
                translations.put(stringToNode.getTranslationString(), new TranslationDetails(null, false, tagName));
            }
        } catch (Exception e) {
            // Do nothing, as at this point it should be valid
        }

        createPOFile(buildData, fileName.replace(".xml", ""), translations);
    }

    protected void buildBookInfoPO(final POBuildData buildData) throws BuildProcessingException {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, TranslationDetails> translations = new LinkedHashMap<String, TranslationDetails>();

        final List<String> TRANSLATABLE_METADATA = Arrays.asList(CommonConstants.CS_TITLE_TITLE, CommonConstants.CS_PRODUCT_TITLE,
                CommonConstants.CS_SUBTITLE_TITLE, CommonConstants.CS_ABSTRACT_TITLE);

        addStringsForMetaData(buildData.getTranslationMap(), contentSpec, TRANSLATABLE_METADATA, translations);

        if (contentSpec.getAbstractTopic() != null) {
            addStringsForTopic(buildData, contentSpec.getAbstractTopic(), translations);
        } else if (contentSpec.getAbstractNode() == null) {
            translations.put(BuilderConstants.DEFAULT_ABSTRACT_TEXT, new TranslationDetails(null, false, "para"));
        }

        if (contentSpec.getSubtitleNode() == null) {
            translations.put(BuilderConstants.SUBTITLE_DEFAULT, new TranslationDetails(null, false, "subtitle"));
        }

        createPOFile(buildData, "Book_Info", translations);
    }

    protected void buildBookPrefacePO(final POBuildData buildData) throws BuildProcessingException {
        final Map<String, TranslationDetails> translations = new LinkedHashMap<String, TranslationDetails>();

        addStringsForConstant(buildData, "PREFACE", "title", translations);

        createPOFile(buildData, "Preface", translations);
    }

    protected void buildBookBasePOPass(final POBuildData buildData) throws BuildProcessingException {
        // Check if the app should be shutdown
        if (isShuttingDown.get()) {
            return;
        }

        final ContentSpec contentSpec = buildData.getContentSpec();
        boolean flattenStructure = buildData.getBuildOptions().isServerBuild() || buildData.getBuildOptions().getFlatten();
        final List<Node> levelData = contentSpec.getBaseLevel().getChildNodes();

        final Map<String, TranslationDetails> containerTranslations = new LinkedHashMap<String, TranslationDetails>();

        for (final org.jboss.pressgang.ccms.contentspec.Node node : levelData) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            if (node instanceof Level) {
                final Level level = (Level) node;

                if (level instanceof InitialContent && level.hasSpecTopics()) {
                    addStringsForInitialContent(buildData, (InitialContent) level, containerTranslations);
                } else if (level.hasSpecTopics()) {
                    if (contentSpec.getBookType() == BookType.ARTICLE || contentSpec.getBookType() == BookType.ARTICLE_DRAFT) {
                        addStringsForContainer(buildData, level, containerTranslations, "topics/", flattenStructure);
                    } else {
                        addStringsForRootContainer(buildData, level, flattenStructure);
                    }
                }
            } else if (node instanceof SpecTopic) {
                final SpecTopic specTopic = (SpecTopic) node;

                if (flattenStructure) {
                    addStringsForTopic(buildData, specTopic, containerTranslations);
                } else {
                    final String topicFileName = specTopic.getUniqueLinkId(buildData.getServerEntities().getFixedUrlPropertyTagId(),
                            buildData.isUseFixedUrls());

                    buildPOTopic(buildData, specTopic, "topics/" + topicFileName);
                }
            }
        }

        createPOFile(buildData, buildData.getEscapedBookTitle(), containerTranslations);

        // Add the default error content
        if (!buildData.getBuildOptions().getSuppressErrorsPage() && buildData.getErrorDatabase().hasItems(buildData.getBuildLocale())) {
            buildPODefaultFile(buildData, "Errors.xml");
        }

        // Add the default report chapter
        if (buildData.getBuildOptions().getShowReportPage()) {
            buildPODefaultFile(buildData, "Report.xml");
        }

        // Build the content specification page
        if (!buildData.getBuildOptions().getSuppressContentSpecPage()) {
            buildPODefaultFile(buildData, "Build_Content_Specification.xml");
        }
    }

    protected void addStringsForRootContainer(final POBuildData buildData, final Level level, final boolean flattenStructure)
            throws BuildProcessingException {
        final Map<String, TranslationDetails> containerTranslations = new LinkedHashMap<String, TranslationDetails>();

        // Get the level file name
        final String containerName = level.getUniqueLinkId(buildData.getServerEntities().getFixedUrlPropertyTagId(),
                buildData.isUseFixedUrls());

        // Add the contents of the level
        addStringsForContainer(buildData, level, containerTranslations, "topics/" + containerName + "/", flattenStructure);

        // Create the PO and POT files
        createPOFile(buildData, containerName, containerTranslations);
    }

    protected void addStringsForSubRootContainer(final POBuildData buildData, final Level level, final String parentFileDirectory,
            final boolean flattenStructure) throws BuildProcessingException {
        final Map<String, TranslationDetails> containerTranslations = new LinkedHashMap<String, TranslationDetails>();

        // Get the level file name
        final String containerName = level.getUniqueLinkId(buildData.getServerEntities().getFixedUrlPropertyTagId(),
                buildData.isUseFixedUrls());

        // Add the contents of the level
        addStringsForContainer(buildData, level, containerTranslations, parentFileDirectory + containerName + "/", flattenStructure);

        // Create the PO and POT files
        createPOFile(buildData, containerName, containerTranslations);
    }

    protected void addStringsForContainer(final POBuildData buildData, final Level container,
            final Map<String, TranslationDetails> containerTranslations, final String parentFileLocation,
            final boolean flattenStructure) throws BuildProcessingException {
        // Add the container title
        if (buildData.getTranslationMap().containsKey("CS" + buildData.getContentSpec().getId())) {
            final Map<String, TranslationDetails> translations = buildData.getTranslationMap().get(
                    "CS" + buildData.getContentSpec().getId());
            if (translations.containsKey(container.getTitle())) {
                containerTranslations.put(container.getTitle(), translations.get(container.getTitle()));
            }
        }

        final List<org.jboss.pressgang.ccms.contentspec.Node> levelData = container.getChildNodes();

        // Add the section and topics for this level to the chapter.xml
        for (final org.jboss.pressgang.ccms.contentspec.Node node : levelData) {
            // Check if the app should be shutdown
            if (isShuttingDown.get()) {
                return;
            }

            if (node instanceof Level && node.getParent() != null && (((Level) node).getParent().getLevelType() == LevelType.BASE || (
                    (Level) node).getParent().getLevelType() == LevelType.PART) && ((Level) node).getLevelType() != LevelType
                    .INITIAL_CONTENT) {
                final Level childLevel = (Level) node;

                // Create a new file for the Chapter/Appendix
                addStringsForSubRootContainer(buildData, childLevel, parentFileLocation,
                        flattenStructure);
            } else if (node instanceof Level && ((Level) node).getLevelType() == LevelType.INITIAL_CONTENT) {
                addStringsForInitialContent(buildData, (InitialContent) node, containerTranslations);
            } else if (node instanceof Level) {
                final Level childLevel = (Level) node;

                // Ignore sections that have no spec topics
                if (childLevel.hasSpecTopics()) {
                    addStringsForContainer(buildData, childLevel, containerTranslations,
                            parentFileLocation, flattenStructure);
                }
            } else if (node instanceof SpecTopic) {
                final SpecTopic specTopic = (SpecTopic) node;

                if (flattenStructure) {
                    addStringsForTopic(buildData, specTopic, containerTranslations);
                } else {
                    final String topicFileName = specTopic.getUniqueLinkId(buildData.getServerEntities().getFixedUrlPropertyTagId(),
                            buildData.isUseFixedUrls());
                    final String fixedParentFileLocation = buildData.getBuildOptions().getFlattenTopics() ? "topics/" : parentFileLocation;

                    buildPOTopic(buildData, specTopic, fixedParentFileLocation + topicFileName);
                }
            }
        }
    }

    protected void addStringsForInitialContent(final POBuildData buildData, final InitialContent initialContent,
            final Map<String, TranslationDetails> containerTranslations) throws BuildProcessingException {

        // Add each topics translations
        for (final SpecTopic specTopic : initialContent.getSpecTopics()) {
            if (buildData.getTranslationMap().containsKey(specTopic.getDBId().toString())) {
                addStringsForTopic(buildData, specTopic, containerTranslations, Arrays.asList(specTopic.getTitle()));
            }
        }

        // Add the report a bug link
        if (buildData.getBuildOptions().getInsertBugLinks()) {
            processPOInitialContentBugLink(buildData, initialContent, containerTranslations);
        }
    }

    /**
     * Builds the POT/PO files for a topic.
     *
     * @param buildData               Information and data structures for the build.
     * @param specTopic               The spec topic to create the POT/PO files for.
     * @param filePathAndName         The path and filename for the topic relative to the locale directory.
     * @throws BuildProcessingException
     */
    protected void buildPOTopic(final POBuildData buildData, final SpecTopic specTopic, final String filePathAndName)
            throws BuildProcessingException {
        if (specTopic != null) {
            final Map<String, TranslationDetails> translations = new LinkedHashMap<String, TranslationDetails>();

            addStringsForTopic(buildData, specTopic, translations);

            createPOFile(buildData, filePathAndName, translations);
        }
    }

    protected void addStringsForTopic(final POBuildData buildData, final SpecTopic specTopic, final Map<String,
            TranslationDetails> translations) throws BuildProcessingException {
        addStringsForTopic(buildData, specTopic, translations, null);
    }

    /**
     * Add all the source/translation strings for a topic.
     *
     * @param buildData               Information and data structures for the build.
     * @param specTopic               The spec topic to add translated strings for.
     * @param translations            The mapping of original strings to translation strings, that will be used to build the po/pot files.
     * @param ignoredSourceStrings    A list of strings that should be ignored when adding the source/translation strings.
     * @throws BuildProcessingException
     */
    protected void addStringsForTopic(final POBuildData buildData, final SpecTopic specTopic,
            final Map<String, TranslationDetails> translations, final List<String> ignoredSourceStrings) throws BuildProcessingException {
        if (buildData.getTranslationMap().containsKey(specTopic.getDBId().toString())) {
            final Map<String, TranslationDetails> translatedTopicStrings = buildData.getTranslationMap().get(specTopic.getDBId().toString());

            for (final Map.Entry<String, TranslationDetails> entry : translatedTopicStrings.entrySet()) {
                if (ignoredSourceStrings != null && ignoredSourceStrings.contains(entry.getKey())) {
                    continue;
                } else {
                    final String cleanedOriginalString = resolveInjectionsAndCleanComments(buildData, specTopic, entry.getKey());
                    if (entry.getValue() == null) {
                        translations.put(cleanedOriginalString, null);
                    } else {
                        final String cleanedTranslationString = resolveInjectionsAndCleanComments(buildData, specTopic,
                                entry.getValue().getTranslation());
                        translations.put(cleanedOriginalString, new TranslationDetails(cleanedTranslationString,
                                entry.getValue().isFuzzy(), entry.getValue().getTagName()));
                    }
                }
            }
        }

        // Add additional content
        if (DocBookXMLPreProcessor.shouldAddAdditionalInfo(buildData, specTopic)) {
            // Add the report a bug link
            if (buildData.getBuildOptions().getInsertBugLinks() && specTopic.getTopicType() == TopicType.NORMAL) {
                processPOTopicBugLink(buildData, specTopic, translations);
            }

            // Add editor links
            if (buildData.getBuildOptions().getInsertEditorLinks() && specTopic.getTopicType() != TopicType.AUTHOR_GROUP) {
                processPOTopicEditorLink(buildData, specTopic, translations);
            }
        }

        // Add any injection strings from the content spec
        processPOTopicInjections(buildData, specTopic, translations);

        // Add the keyword source strings (Note: These won't ever be translated)
        if (specTopic.getTopicType() != TopicType.INITIAL_CONTENT) {
            final CollectionWrapper<TagWrapper> tags = specTopic.getTopic().getTags();
            final List<Integer> seoCategoryIds = buildData.getServerSettings().getSEOCategoryIds();

            if (seoCategoryIds != null && !seoCategoryIds.isEmpty() && tags != null && tags.getItems() != null && tags.getItems().size() > 0) {
                final List<TagWrapper> tagItems = tags.getItems();
                for (final TagWrapper tag : tagItems) {
                    if (tag.getName() == null || tag.getName().isEmpty()) continue;

                    if (tag.containedInCategories(seoCategoryIds)) {
                        translations.put(tag.getName(), new TranslationDetails(null, false, "keyword"));
                    }
                }
            }
        }
    }

    /**
     * Resolves the injections and removes any left over comments from source/translation strings.
     *
     * @param buildData         Information and data structures for the build.
     * @param specTopic         The topic to resolve the injections for.
     * @param translationString The string to have its injections resolved and comments cleaned.
     * @return The cleaned string that can be used in a POT/PO file.
     */
    protected String resolveInjectionsAndCleanComments(final BuildData buildData, final SpecTopic specTopic,
            final String translationString) {
        if (translationString == null) return null;

        try {
            final Document doc = XMLUtilities.convertStringToDocument("<temp>" + translationString + "</temp>");

            // Resolve the injections
            final List<Integer> types = Arrays.asList(DocBookXMLPreProcessor.XREF_INJECTION_POINT);
            final DocBookXMLPreProcessor preProcessor = buildData.getXMLPreProcessor();
            preProcessor.processInjections(buildData.getContentSpec(), specTopic, new ArrayList<String>(), doc,
                    buildData.getBuildOptions(), buildData.getBuildDatabase(), buildData.isUseFixedUrls(),
                    buildData.getServerEntities().getFixedUrlPropertyTagId(), types);

            // Remove any comments
            final List<org.w3c.dom.Node> comments = XMLUtilities.getComments(doc.getDocumentElement());
            for (final org.w3c.dom.Node comment : comments) {
                // When removing comments we need to trim the next string as well
                if (comment.getNextSibling() != null && comment.getNextSibling().getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                    final org.w3c.dom.Node nextNode = comment.getNextSibling();
                    nextNode.setNodeValue(StringUtilities.ltrim(nextNode.getNodeValue()));
                }
                // We also need to trim the previous string if the comment is the last node
                if (comment.getNextSibling() == null && comment.getPreviousSibling() != null
                        && comment.getPreviousSibling().getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                    final org.w3c.dom.Node prevNode = comment.getPreviousSibling();
                    prevNode.setNodeValue(StringUtilities.rtrim(prevNode.getNodeValue()));
                }

                // Remove the comment from the DOM
                comment.getParentNode().removeChild(comment);
            }

            return XMLUtilities.convertNodeToString(doc.getDocumentElement(), false);
        } catch (Exception e) {
            return translationString;
        }
    }

    /**
     * Formats a string so that it will be compatible with publicans expectations.
     *
     * @param translationString The string to be formatted.
     * @return The formatted string.
     */
    protected String formatTranslationString(final String translationString) {
        try {
            final Document doc = XMLUtilities.convertStringToDocument("<temp>" + translationString + "</temp>");

            return XMLUtilities.convertNodeToString(doc.getDocumentElement(), false);
        } catch (Exception e) {
            return translationString;
        }
    }

    /**
     * Process a spec topic and add any translation strings for it.
     *
     * @param buildData               Information and data structures for the build.
     * @param specTopic               The spec topic to process any injections for.
     * @param translations            The mapping of original strings to translation strings, that will be used to build the po/pot files.
     */
    protected void processPOTopicInjections(final POBuildData buildData, final SpecTopic specTopic,
            final Map<String, TranslationDetails> translations) {
        // Prerequisites
        addStringsFromTopicRelationships(buildData, specTopic.getPrerequisiteRelationships(),
                DocBookXMLPreProcessor.PREREQUISITE_PROPERTY, DocBookXMLPreProcessor.ROLE_PREREQUISITE, translations);

        // See also
        addStringsFromTopicRelationships(buildData, specTopic.getRelatedRelationships(),
                DocBookXMLPreProcessor.SEE_ALSO_PROPERTY, DocBookXMLPreProcessor.ROLE_SEE_ALSO, translations);

        // Link List
        addStringsFromTopicRelationships(buildData, specTopic.getLinkListRelationships(), null,
                DocBookXMLPreProcessor.ROLE_LINK_LIST, translations);

        // Previous
        final List<Relationship> prevRelationships = specTopic.getPreviousRelationships();
        if (prevRelationships.size() > 1) {
            addStringsFromProcessRelationships(buildData, specTopic, prevRelationships,
                    DocBookXMLPreProcessor.PREVIOUS_STEPS_PROPERTY, DocBookXMLPreProcessor.ROLE_PROCESS_PREVIOUS_LINK,
                    DocBookXMLPreProcessor.ROLE_PROCESS_PREVIOUS_TITLE_LINK, translations);
        } else {
            addStringsFromProcessRelationships(buildData, specTopic, prevRelationships,
                    DocBookXMLPreProcessor.PREVIOUS_STEP_PROPERTY, DocBookXMLPreProcessor.ROLE_PROCESS_PREVIOUS_LINK,
                    DocBookXMLPreProcessor.ROLE_PROCESS_PREVIOUS_TITLE_LINK, translations);
        }

        // Next
        final List<Relationship> nextRelationships = specTopic.getNextRelationships();
        if (nextRelationships.size() > 1) {
            addStringsFromProcessRelationships(buildData, specTopic, nextRelationships,
                    DocBookXMLPreProcessor.NEXT_STEPS_PROPERTY, DocBookXMLPreProcessor.ROLE_PROCESS_NEXT_LINK,
                    DocBookXMLPreProcessor.ROLE_PROCESS_NEXT_TITLE_LINK, translations);
        } else {
            addStringsFromProcessRelationships(buildData, specTopic, nextRelationships,
                    DocBookXMLPreProcessor.NEXT_STEP_PROPERTY, DocBookXMLPreProcessor.ROLE_PROCESS_NEXT_LINK,
                    DocBookXMLPreProcessor.ROLE_PROCESS_NEXT_TITLE_LINK, translations);
        }
    }

    /**
     * Add the translation strings from a list of relationships for a specific topic.
     *
     * @param buildData               Information and data structures for the build.
     * @param relationships           The list of relationships to add strings for.
     * @param key                     The constants key to be used for the relationship list.
     * @param roleName                The role name to be used on the relationship xrefs.
     * @param translations            The mapping of original strings to translation strings, that will be used to build the po/pot files.
     */
    protected void addStringsFromTopicRelationships(final POBuildData buildData, final List<Relationship> relationships,
            final String key, final String roleName,
            final Map<String, TranslationDetails> translations) {
        if (!relationships.isEmpty()) {
            if (key != null) {
                addStringsForConstant(buildData, key, "title", translations);
            }

            addStringsFromRelationships(buildData, relationships, roleName, translations);
        }
    }

    /**
     * Add the translation strings from a list of relationships for a specific topic in a process.
     *
     * @param buildData               Information and data structures for the build.
     * @param specTopic               The spec topic that the relationships belong to.
     * @param relationships           The list of relationships to add strings for.
     * @param key                     The constants key to be used for the relationship list.
     * @param roleName                The role name to be used on the relationship xrefs.
     * @param titleRoleName           The role name to be used on the process relationship titles xref.
     * @param translations            The mapping of original strings to translation strings, that will be used to build the po/pot files.
     */
    protected void addStringsFromProcessRelationships(final POBuildData buildData, final SpecTopic specTopic,
            final List<Relationship> relationships, final String key, final String roleName, final String titleRoleName,
            final Map<String, TranslationDetails> translations) {
        if (!relationships.isEmpty()) {
            if (key != null) {
                final Integer fixedUrlPropertyTagId = buildData.getServerEntities().getFixedUrlPropertyTagId();
                final boolean useFixedUrls = buildData.isUseFixedUrls();

                // Get the process title name
                final Level container = (Level) specTopic.getParent();
                String translatedTitle = container.getTitle();
                boolean fuzzy = true;
                if (buildData.getTranslationMap().containsKey("CS" + buildData.getContentSpec().getId())) {
                    final Map<String, TranslationDetails> containerTranslations = buildData.getTranslationMap().get(
                            "CS" + buildData.getContentSpec().getId());
                    if (containerTranslations.containsKey(container.getTitle())) {
                        final TranslationDetails translationDetails = containerTranslations.get(container.getTitle());
                        translatedTitle = translationDetails.getTranslation();
                        if (translatedTitle != null) {
                            fuzzy = translationDetails.isFuzzy();
                        }
                    }
                }

                // Build up the link
                final String titleLinkId = ((Level) specTopic.getParent()).getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls);
                final String originalTitleLink = DocBookUtilities.buildLink(titleLinkId, titleRoleName, container.getTitle());

                final String originalString = buildData.getConstants().getString(key);
                final String translationString = buildData.getTranslationConstants().getString(key);
                final String fixedOriginalString = String.format(originalString, originalTitleLink);

                // Add the translation
                if (!originalString.equals(translationString)) {
                    final String translatedTitleLink = DocBookUtilities.buildLink(titleLinkId, titleRoleName, translatedTitle);
                    final String fixedTranslationString = String.format(translationString, translatedTitleLink);
                    translations.put(fixedOriginalString, new TranslationDetails(fixedTranslationString, fuzzy, "title"));
                } else {
                    translations.put(fixedOriginalString, new TranslationDetails(null, fuzzy, "title"));
                }
            }

            addStringsFromRelationships(buildData, relationships, roleName, translations);
        }
    }

    /**
     * Add the translation strings from a list of relationships.
     *
     * @param buildData     Information and data structures for the build.
     * @param relationships The list of relationships to add strings for.
     * @param roleName      The role name to be used on the relationship xrefs.
     * @param translations  The mapping of original strings to translation strings, that will be used to build the po/pot files.
     */
    protected void addStringsFromRelationships(final BuildData buildData, final List<Relationship> relationships, final String roleName,
            final Map<String, TranslationDetails> translations) {
        final Integer fixedUrlPropertyTagId = buildData.getServerEntities().getFixedUrlPropertyTagId();
        final boolean useFixedUrls = buildData.isUseFixedUrls();

        for (final Relationship relationship : relationships) {
            final SpecNode relatedNode;
            if (relationship instanceof TopicRelationship) {
                relatedNode = ((TopicRelationship) relationship).getSecondaryRelationship();
            } else {
                relatedNode = ((TargetRelationship) relationship).getSecondaryRelationship();
            }

            final String xrefString = DocBookUtilities.buildXRef(relatedNode.getUniqueLinkId(fixedUrlPropertyTagId, useFixedUrls), roleName);
            translations.put(xrefString, new TranslationDetails(xrefString, false, "para"));
        }
    }

    /**
     * Add any bug link strings for a specific topic.
     *
     * @param buildData                 Information and data structures for the build.
     * @param specTopic                 The spec topic to create any bug link translation strings for.
     * @param translations              The mapping of original strings to translation strings, that will be used to build the po/pot files.
     * @throws BuildProcessingException Thrown if the bug links cannot be created.
     */
    protected void processPOTopicBugLink(final POBuildData buildData, final SpecTopic specTopic,
            final Map<String, TranslationDetails> translations) throws BuildProcessingException {
        try {
            final DocBookXMLPreProcessor preProcessor = buildData.getXMLPreProcessor();
            final String bugLinkUrl = preProcessor.getBugLinkUrl(buildData, specTopic);

            processPOBugLinks(buildData, preProcessor, bugLinkUrl, translations);
        } catch (BugLinkException e) {
            throw new BuildProcessingException(e);
        } catch (Exception e) {
            throw new BuildProcessingException("Failed to insert Bug Links into the DOM Document", e);
        }
    }

    /**
     * Add any bug link strings for a specific initial content container.
     *
     * @param buildData                 Information and data structures for the build.
     * @param initialContent            The initial content to create any bug link translation strings for.
     * @param translations              The mapping of original strings to translation strings, that will be used to build the po/pot files.
     * @throws BuildProcessingException Thrown if the bug links cannot be created.
     */
    protected void processPOInitialContentBugLink(final POBuildData buildData, final InitialContent initialContent,
            final Map<String, TranslationDetails> translations) throws BuildProcessingException {
        try {
            final DocBookXMLPreProcessor preProcessor = buildData.getXMLPreProcessor();
            final String bugLinkUrl = preProcessor.getBugLinkUrl(buildData, initialContent);

            processPOBugLinks(buildData, preProcessor, bugLinkUrl, translations);
        } catch (BugLinkException e) {
            throw new BuildProcessingException(e);
        } catch (Exception e) {
            throw new BuildProcessingException("Failed to insert Bug Links into the DOM Document", e);
        }
    }

    /**
     * Creates the strings for a specific bug link url.
     *
     * @param buildData    Information and data structures for the build.
     * @param preProcessor The {@link DocBookXMLPreProcessor} instance to use to create the bug links.
     * @param bugLinkUrl   The bug link url.
     * @param translations The mapping of original strings to translation strings, that will be used to build the po/pot files.
     */
    protected void processPOBugLinks(final POBuildData buildData, final DocBookXMLPreProcessor preProcessor, final String bugLinkUrl,
            final Map<String, TranslationDetails> translations) {
        final String originalString = buildData.getConstants().getString("REPORT_A_BUG");
        final String translationString = buildData.getTranslationConstants().getString("REPORT_A_BUG");

        final String originalLink = preProcessor.createExternalLinkElement(buildData.getDocBookVersion(), originalString,
                bugLinkUrl);

        // Check to see if the report a bug text has translations
        if (originalString.equals(translationString)) {
            translations.put(originalLink, new TranslationDetails(null, false, "para"));
        } else {
            final String translatedLink = preProcessor.createExternalLinkElement(buildData.getDocBookVersion(),
                    translationString, bugLinkUrl);
            translations.put(originalLink, new TranslationDetails(translatedLink, false, "para"));
        }
    }

    // Note: Editor strings haven't been requested to be translated, so we just use the source strings.
    // TODO Look at how to do editor links for translation builds
    protected void processPOTopicEditorLink(final BuildData buildData, final SpecTopic specTopic,
            final Map<String, TranslationDetails> translations) {
        // EDITOR LINK
        if (buildData.getBuildOptions().getInsertEditorLinks()) {
            final DocBookXMLPreProcessor preProcessor = buildData.getXMLPreProcessor();
            final BaseTopicWrapper<?> topic = specTopic.getTopic();
            final String editorUrl = topic.getEditorURL(buildData.getZanataDetails());

            if (editorUrl != null) {
                final String editorLink = preProcessor.createExternalLinkElement(buildData.getDocBookVersion(), "Edit this topic",
                        editorUrl);
                translations.put(editorLink, new TranslationDetails(null, false, "para"));
            } else {
                /*
                 * Since the getEditorURL method only returns null for translations we don't need to check the topic
                 * type.
                 */
                translations.put("No editor available for this topic, as it hasn't been pushed for translation.",
                        new TranslationDetails(null, false, "para"));
            }

            // Add a link for additional translated content for Revision Histories and author groups
            if (topic instanceof TranslatedTopicWrapper && (specTopic.getTopicType() == TopicType.REVISION_HISTORY || specTopic
                    .getTopicType() == TopicType.AUTHOR_GROUP)) {
                final String additionalXMLEditorUrl = topic.getPressGangURL();

                if (additionalXMLEditorUrl != null) {
                    final String editorLink = preProcessor.createExternalLinkElement(buildData.getDocBookVersion(),
                            "Edit the Additional Translated XML", editorUrl);
                    translations.put(editorLink, new TranslationDetails(null, false, "para"));
                }
            }
        }
    }

    protected void addStringsForMetaData(final Map<String, Map<String, TranslationDetails>> mapping, final ContentSpec contentSpec,
            final List<String> metaDataKeys, final Map<String, TranslationDetails> translations) {
        if (mapping.containsKey("CS" + contentSpec.getId())) {
            final Map<String, TranslationDetails> translatedTopicStrings = mapping.get("CS" + contentSpec.getId());

            for (final Node node : contentSpec.getNodes()) {
                if (node instanceof KeyValueNode) {
                    final KeyValueNode<String> keyValueNode = (KeyValueNode<String>) node;
                    if (metaDataKeys.contains(keyValueNode.getKey())) {
                        final String value = keyValueNode.getValue();
                        if (translatedTopicStrings.containsKey(value)) {
                            translations.put(value, translatedTopicStrings.get(value));
                        } else {
                            translations.put(value, null);
                        }
                    }
                }
            }
        }
    }

    /**
     * Add the required source/translation strings for a build constant.
     *
     * @param buildData    Information and data structures for the build.
     * @param key          The constant key to use.
     * @param translations The mapping of original strings to translation strings, that will be used to build the po/pot files.
     */
    protected void addStringsForConstant(final POBuildData buildData, final String key, final String tagName,
            final Map<String, TranslationDetails> translations) {
        final String originalString = buildData.getConstants().getString(key);
        final String translationString = buildData.getTranslationConstants().getString(key);

        if (!originalString.equals(translationString)) {
            translations.put(originalString, new TranslationDetails(translationString, false, tagName));
        } else {
            translations.put(originalString, new TranslationDetails(null, false, tagName));
        }
    }

    /**
     * Creates the actual PO/POT files for a specific file.
     *
     * @param buildData
     * @param filePathAndName The
     * @param translations    The mapping of original strings to translation strings, that will be used to build the po/pot files.
     * @throws BuildProcessingException
     */
    protected void createPOFile(final POBuildData buildData, final String filePathAndName,
            final Map<String, TranslationDetails> translations) throws BuildProcessingException {
        // Don't create files where there is nothing to translate
        if (!translations.isEmpty()) {
            final StringBuilder potFile = createBasePOFile(buildData);
            final StringBuilder poFile = createBasePOFile(buildData);

            for (final Map.Entry<String, TranslationDetails> entry : translations.entrySet()) {
                final String tagName = entry.getValue() == null ? null : entry.getValue().getTagName();
                addPOTEntry(tagName, entry.getKey(), potFile);
                if (entry.getValue() == null) {
                    addPOEntry(tagName, entry.getKey(), null, false, poFile);
                } else {
                    addPOEntry(tagName, entry.getKey(), entry.getValue().getTranslation(), entry.getValue().isFuzzy(), poFile);
                }
            }

            addPOTToZip(filePathAndName + ".pot", potFile.toString(), buildData);
            if (!buildData.getDefaultLocale().equals(buildData.getPOBuildLocale())) {
                addPOToZip(filePathAndName + ".po", poFile.toString(), buildData);
            }
        }
    }

    /**
     * Create the base initial content required for all PO and POT files.
     *
     * @param buildData Information and data structures for the build.
     * @return A {@link StringBuilder} instance initialised with the base content required for a PO/POT file.
     */
    protected StringBuilder createBasePOFile(final BuildData buildData) {
        final String formattedDate = POT_DATE_FORMAT.format(buildData.getBuildDate());

        return new StringBuilder("# \n")
                .append("# AUTHOR <EMAIL@ADDRESS>, YEAR.\n")
                .append("#\n")
                .append("msgid \"\"\n")
                .append("msgstr \"\"\n")
                .append("\"Project-Id-Version: 0\\n\"\n")
                .append("\"POT-Creation-Date: ").append(formattedDate).append("\\n\"\n")
                .append("\"PO-Revision-Date: ").append(formattedDate).append("\\n\"\n")
                .append("\"Last-Translator: Automatically generated\\n\"\n")
                .append("\"Language-Team: None\\n\"\n")
                .append("\"MIME-Version: 1.0\\n\"\n")
                .append("\"Content-Type: application/x-pressgang-ccms; charset=").append(ENCODING).append("\\n\"\n")
                .append("\"Content-Transfer-Encoding: 8bit\\n\"\n");
    }

    /**
     * Add an entry to a POT file.
     *
     * @param tag     The XML element name.
     * @param source  The original source string.
     * @param potFile The POT file to add to.
     */
    protected void addPOTEntry(final String tag, final String source, final StringBuilder potFile) {
        addPOEntry(tag, source, "", false, potFile);
    }

    /**
     * Add an entry to a PO file.
     *
     * @param tag         The XML element name.
     * @param source      The original source string.
     * @param translation The translated string.
     * @param fuzzy       If the translation is fuzzy.
     * @param poFile      The PO file to add to.
     */
    protected void addPOEntry(final String tag, final String source, final String translation, final boolean fuzzy,
            final StringBuilder poFile) {
        poFile.append("\n");
        if (!isNullOrEmpty(tag)) {
            poFile.append("#. Tag: ").append(tag).append("\n");
        }
        if (fuzzy) {
            poFile.append("#, fuzzy\n");
        }
        poFile.append("#, no-c-format\n")
                .append("msgid \"").append(escapeForPOFile(source, true)).append("\"\n")
                .append("msgstr \"").append(escapeForPOFile(translation, false)).append("\"\n");
    }

    /**
     * Escape a string so that it can be added to a PO/POT file.
     *
     * @param input The string to be escaped.
     * @param fix   If the string should have greater/lesser than symbols fixed.
     * @return The escaped string, or an empty string if the input is null.
     */
    private String escapeForPOFile(final String input, boolean fix) {
        if (input == null) return "";

        String escapedString = input;

        // Publican escapes greater/lesser than to XML entities
        if (fix && (input.contains(" < ") || input.contains(" > "))) {
            // Loop over and find all the XML Elements as they should remain untouched.
            final LinkedList<String> elements = new LinkedList<String>();
            if (input.indexOf('<') != -1) {
                int index = -1;
                while ((index = input.indexOf('<', index + 1)) != -1) {
                    int endIndex = input.indexOf('>', index);
                    int nextIndex = input.indexOf('<', index + 1);

                    /*
                      * If the next opening tag is less than the next ending tag, than the current opening tag isn't a match for the next
                      * ending tag, so continue to the next one
                      */
                    if (endIndex == -1 || (nextIndex != -1 && nextIndex < endIndex)) {
                        continue;
                    } else if (index + 1 == endIndex) {
                        // This is a <> sequence, so it should be ignored as well.
                        continue;
                    } else {
                        elements.add(input.substring(index, endIndex + 1));
                    }

                }
            }

            // Find all the elements and replace them with a marker
            for (int count = 0; count < elements.size(); count++) {
                escapedString = escapedString.replace(elements.get(count), "###" + count + "###");
            }

            // Perform the replacements on what's left
            escapedString = escapedString.replace("<", "&lt;").replace(">", "&gt;");

            // Replace the markers
            for (int count = 0; count < elements.size(); count++) {
                escapedString = escapedString.replace("###" + count + "###", elements.get(count));
            }
        }

        return escapedString.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    protected void addPOTToZip(final String path, final String file, final POBuildData buildData) throws BuildProcessingException {
        addToZip(buildData.getRootBookFolder() + "pot/" + path, file, buildData);
    }

    protected void addPOToZip(final String path, final String file, final POBuildData buildData) throws BuildProcessingException {
        addToZip(buildData.getRootBookFolder() + buildData.getPOOutputLocale() + "/" + path, file, buildData);
    }
}