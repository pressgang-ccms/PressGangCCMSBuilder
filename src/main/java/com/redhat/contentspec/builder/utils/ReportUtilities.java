package com.redhat.contentspec.builder.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.jboss.pressgangccms.docbook.structures.TopicErrorData;
import org.jboss.pressgangccms.rest.v1.components.ComponentBaseTopicV1;
import org.jboss.pressgangccms.rest.v1.components.ComponentTopicV1;
import org.jboss.pressgangccms.rest.v1.components.ComponentTranslatedTopicV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTTagV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTTopicV1;
import org.jboss.pressgangccms.rest.v1.entities.RESTTranslatedTopicV1;
import org.jboss.pressgangccms.rest.v1.entities.base.RESTBaseTopicV1;
import org.jboss.pressgangccms.rest.v1.sort.TagV1NameComparator;
import org.jboss.pressgangccms.utils.common.CollectionUtilities;
import org.jboss.pressgangccms.utils.common.DocBookUtilities;
import org.jboss.pressgangccms.utils.structures.NameIDSortMap;
import org.jboss.pressgangccms.zanata.ZanataDetails;

public class ReportUtilities
{
	/**
	 * Builds a Table to be used within a report. The table
	 * contains a link to the topic in Skynet, the topic Title
	 * and the list of tags for the topic.
	 * 
	 * @param topicErrorDatas The list of TopicErrorData objects
	 * to use to build the table.
	 * @param tableTitle The title for the table.
	 * @return The table as a String.
	 */
	public static <T extends RESTBaseTopicV1<T, ?, ?>> String buildReportTable(final List<TopicErrorData<T>> topicErrorDatas, final String tableTitle, final boolean showEditorLink, final ZanataDetails zanataDetails)
	{
		final List<String> tableHeaders = CollectionUtilities.toArrayList(new String[]{"Topic Link", "Topic Title", "Topic Tags"});

		// Put the details into different tables
		final List<List<String>> rows = new ArrayList<List<String>>();
		for (final TopicErrorData<T> topicErrorData : topicErrorDatas)
		{
			final T topic = topicErrorData.getTopic();
			final List<String> topicTitles;
			if (topic instanceof RESTTranslatedTopicV1)
			{
				final RESTTranslatedTopicV1 translatedTopic = (RESTTranslatedTopicV1) topic;
				if (!ComponentTranslatedTopicV1.returnIsDummyTopic(translatedTopic) && translatedTopic.getTopic() != null)
				{
					topicTitles = CollectionUtilities.toArrayList(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara("[" + translatedTopic.getTopic().getLocale() + "] " + translatedTopic.getTopic().getTitle()))
							, DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara("[" + translatedTopic.getLocale() + "] " + translatedTopic.getTitle())));
				}
				else
				{
					topicTitles = CollectionUtilities.toArrayList(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(topic.getTitle())));
				}
			}
			else
			{
				topicTitles = CollectionUtilities.toArrayList(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(topic.getTitle())));
			}
			
			final String topicULink = createTopicTableLinks(topic, showEditorLink, zanataDetails);
			final String topicTitle = DocBookUtilities.wrapListItems(topicTitles);
			final String topicTags = buildItemizedTopicTagList(topic);
			
			rows.add(CollectionUtilities.toArrayList(new String[] {topicULink, topicTitle, topicTags}));
		}
		
		return rows.size() > 0 ? DocBookUtilities.wrapInTable(tableTitle, tableHeaders, rows) : "";
	}
	
	private static <T extends RESTBaseTopicV1<T, ?, ?>> String createTopicTableLinks(final T topic, final boolean showEditorLink, final ZanataDetails zanataDetails)
	{
		final List<String> topicIdUrls = new ArrayList<String>();
		final String url;
		final String editorUrl;
		if (topic instanceof RESTTranslatedTopicV1)
		{
			final String topicIdString;
			final RESTTranslatedTopicV1 translatedTopic = (RESTTranslatedTopicV1) topic;
			if (ComponentTranslatedTopicV1.returnIsDummyTopic(translatedTopic))
			{
				topicIdString = "Topic " + translatedTopic.getTopicId() + ", Revision " + translatedTopic.getTopicRevision();
				if (translatedTopic.getTopic() != null)
				{
					url = ComponentTopicV1.returnSkynetURL(translatedTopic.getTopic());
				}
				else
				{
					url = ComponentTranslatedTopicV1.returnSkynetURL(translatedTopic);
				}
			}
			else
			{
				if (translatedTopic.getTopic() != null)
				{
					final String topicUrl = ComponentTopicV1.returnSkynetURL(translatedTopic.getTopic());
					topicIdUrls.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(topicUrl, "Topic " + translatedTopic.getTopic().getId()))));
				}
				topicIdString = "Translated Topic " + translatedTopic.getTranslatedTopicId();
				url = ComponentTranslatedTopicV1.returnSkynetURL(translatedTopic);
			}
			topicIdUrls.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(url, topicIdString))));
			
			if (showEditorLink)
			{
				editorUrl = ComponentTranslatedTopicV1.returnEditorURL(translatedTopic, zanataDetails);
			}
			else
			{
				editorUrl = null;
			}
		}
		else
		{
			url = ComponentTopicV1.returnSkynetURL((RESTTopicV1) topic);
			final String topicIdString = "Topic " + topic.getId() + ", Revision " + topic.getRevision();
			topicIdUrls.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(url, topicIdString))));
			
			if (showEditorLink)
			{
				editorUrl = ComponentTopicV1.returnEditorURL((RESTTopicV1) topic);
			}
			else
			{
				editorUrl = null;
			}
		}
		
		if (editorUrl != null)
		{
			topicIdUrls.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(editorUrl, "Editor Link"))));
		}

		return DocBookUtilities.wrapListItems(topicIdUrls);
	}
	
	private static <T extends RESTBaseTopicV1<T, ?, ?>> String buildItemizedTopicTagList(final T topic)
	{
		final TreeMap<NameIDSortMap, ArrayList<RESTTagV1>> tags = ComponentBaseTopicV1.getCategoriesMappedToTags(topic);

		/*
		 * Since the sort order breaks the tag to categories grouping
		 * we need to regroup the mapping.
		 */
		final Map<String, List<RESTTagV1>> catToTags = new TreeMap<String, List<RESTTagV1>>();
		for (final Entry<NameIDSortMap, ArrayList<RESTTagV1>> entry : tags.entrySet())
		{
		    final NameIDSortMap key = entry.getKey();
		    
			// sort alphabetically
			Collections.sort(entry.getValue(), new TagV1NameComparator());

			final String categoryName = key.getName();
			
			if (!catToTags.containsKey(categoryName))
				catToTags.put(categoryName, new LinkedList<RESTTagV1>());
			
			catToTags.get(categoryName).addAll(tags.get(key));
		}
		
		/* Build the list of items to be used in the itemized lists */
		final List<String> items = new ArrayList<String>();
		for (final Entry<String, List<RESTTagV1>> catEntry : catToTags.entrySet())
		{
			final StringBuilder thisTagList = new StringBuilder("");

			for (final RESTTagV1 tag : catEntry.getValue())
			{
				if (thisTagList.length() != 0)
					thisTagList.append(", ");

				thisTagList.append(tag.getName());
			}
			
			items.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara("<emphasis role=\"bold\">" + catEntry.getKey() + ":</emphasis> " + thisTagList)));
		}
		
		/* Check that some tags exist, otherwise add a message about there being no tags */
		if (items.isEmpty())
		{
			items.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara("No Tags exist for this topic")));
		}
		
		return DocBookUtilities.wrapListItems(items);
	}
}
