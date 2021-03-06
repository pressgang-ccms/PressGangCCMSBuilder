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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.jboss.pressgang.ccms.contentspec.builder.structures.TopicErrorData;
import org.jboss.pressgang.ccms.contentspec.sort.TagWrapperNameComparator;
import org.jboss.pressgang.ccms.contentspec.utils.EntityUtilities;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.utils.structures.NameIDSortMap;
import org.jboss.pressgang.ccms.wrapper.TagWrapper;
import org.jboss.pressgang.ccms.wrapper.TranslatedTopicWrapper;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;
import org.jboss.pressgang.ccms.zanata.ZanataDetails;

public class ReportUtilities {
    /**
     * Builds a Table to be used within a report. The table
     * contains a link to the topic in Skynet, the topic Title
     * and the list of tags for the topic.
     *
     * @param topicErrorDatas The list of TopicErrorData objects
     *                        to use to build the table.
     * @param tableTitle      The title for the table.
     * @return The table as a String.
     */
    public static String buildReportTable(final List<TopicErrorData> topicErrorDatas, final String tableTitle, final boolean showEditorLink,
            final ZanataDetails zanataDetails) {
        final List<String> tableHeaders = CollectionUtilities.toArrayList(new String[]{"Topic Link", "Topic Title", "Topic Tags"});

        // Put the details into different tables
        final List<List<String>> rows = new ArrayList<List<String>>();
        for (final TopicErrorData topicErrorData : topicErrorDatas) {
            final BaseTopicWrapper<?> topic = topicErrorData.getTopic();
            final List<String> topicTitles;
            if (topic instanceof TranslatedTopicWrapper) {
                final TranslatedTopicWrapper translatedTopic = (TranslatedTopicWrapper) topic;
                if (!EntityUtilities.isDummyTopic(translatedTopic) && translatedTopic.getTopic() != null) {
                    topicTitles = CollectionUtilities.toArrayList(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(
                            "[" + translatedTopic.getTopic().getLocale().getValue() + "] " + translatedTopic.getTopic().getTitle())),
                            DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(
                                    "[" + translatedTopic.getLocale().getValue() + "] " + translatedTopic.getTitle())));
                } else {
                    topicTitles = CollectionUtilities.toArrayList(
                            DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(topic.getTitle())));
                }
            } else {
                topicTitles = CollectionUtilities.toArrayList(
                        DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(topic.getTitle())));
            }

            final String topicULink = createTopicTableLinks(topic, showEditorLink, zanataDetails);
            final String topicTitle = DocBookUtilities.wrapListItems(topicTitles);
            final String topicTags = buildItemizedTopicTagList(topic);

            rows.add(CollectionUtilities.toArrayList(new String[]{topicULink, topicTitle, topicTags}));
        }

        return rows.size() > 0 ? DocBookUtilities.wrapInTable(tableTitle, tableHeaders, rows) : "";
    }

    private static String createTopicTableLinks(final BaseTopicWrapper<?> topic, final boolean showEditorLink,
            final ZanataDetails zanataDetails) {
        final List<String> topicIdUrls = new ArrayList<String>();
        final String url;
        final String editorUrl;
        if (topic instanceof TranslatedTopicWrapper) {
            final String topicIdString;
            final TranslatedTopicWrapper translatedTopic = (TranslatedTopicWrapper) topic;
            if (EntityUtilities.isDummyTopic(translatedTopic)) {
                topicIdString = "Topic " + translatedTopic.getTopicId() + ", Revision " + translatedTopic.getTopicRevision();
                if (translatedTopic.getTopic() != null) {
                    url = translatedTopic.getTopic().getPressGangURL();
                } else {
                    url = translatedTopic.getPressGangURL();
                }
            } else {
                if (translatedTopic.getTopic() != null) {
                    final String topicUrl = translatedTopic.getTopic().getPressGangURL();
                    topicIdUrls.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(
                            DocBookUtilities.buildULink(topicUrl, "Topic " + translatedTopic.getTopic().getId()))));
                }
                topicIdString = "Translated Topic " + translatedTopic.getTranslatedTopicId();
                url = translatedTopic.getPressGangURL();
            }
            topicIdUrls.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(url, topicIdString))));

            if (showEditorLink) {
                editorUrl = translatedTopic.getEditorURL(zanataDetails);
            } else {
                editorUrl = null;
            }
        } else {
            url = topic.getPressGangURL();
            final String topicIdString = "Topic " + topic.getId() + ", Revision " + topic.getRevision();
            topicIdUrls.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(url, topicIdString))));

            if (showEditorLink) {
                editorUrl = topic.getEditorURL(zanataDetails);
            } else {
                editorUrl = null;
            }
        }

        if (editorUrl != null) {
            topicIdUrls.add(
                    DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(editorUrl, "Editor Link"))));
        }

        return DocBookUtilities.wrapListItems(topicIdUrls);
    }

    private static String buildItemizedTopicTagList(final BaseTopicWrapper<?> topic) {
        final TreeMap<NameIDSortMap, ArrayList<TagWrapper>> tags = EntityUtilities.getCategoriesMappedToTags(topic);

        /*
         * Since the sort order breaks the tag to categories grouping
         * we need to regroup the mapping.
         */
        final Map<String, List<TagWrapper>> catToTags = new TreeMap<String, List<TagWrapper>>();
        for (final Entry<NameIDSortMap, ArrayList<TagWrapper>> entry : tags.entrySet()) {
            final NameIDSortMap key = entry.getKey();

            // sort alphabetically
            Collections.sort(entry.getValue(), new TagWrapperNameComparator());

            final String categoryName = key.getName();

            if (!catToTags.containsKey(categoryName)) catToTags.put(categoryName, new LinkedList<TagWrapper>());

            catToTags.get(categoryName).addAll(tags.get(key));
        }

        /* Build the list of items to be used in the itemized lists */
        final List<String> items = new ArrayList<String>();
        for (final Entry<String, List<TagWrapper>> catEntry : catToTags.entrySet()) {
            final StringBuilder thisTagList = new StringBuilder("");

            for (final TagWrapper tag : catEntry.getValue()) {
                if (thisTagList.length() != 0) thisTagList.append(", ");

                thisTagList.append(tag.getName());
            }

            items.add(DocBookUtilities.wrapInListItem(
                    DocBookUtilities.wrapInPara("<emphasis role=\"bold\">" + catEntry.getKey() + ":</emphasis> " + thisTagList)));
        }

        /* Check that some tags exist, otherwise add a message about there being no tags */
        if (items.isEmpty()) {
            items.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara("No Tags exist for this topic")));
        }

        return DocBookUtilities.wrapListItems(items);
    }
}
