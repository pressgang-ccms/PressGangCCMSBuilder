package com.redhat.contentspec.builder.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.redhat.ecs.commonstructures.NameIDSortMap;
import com.redhat.ecs.commonutils.CollectionUtilities;
import com.redhat.topicindex.component.docbookrenderer.structures.TopicErrorData;
import com.redhat.topicindex.rest.collections.BaseRestCollectionV1;
import com.redhat.topicindex.rest.entities.ComponentBaseTopicV1;
import com.redhat.topicindex.rest.entities.ComponentTopicV1;
import com.redhat.topicindex.rest.entities.ComponentTranslatedTopicV1;
import com.redhat.topicindex.rest.entities.interfaces.RESTBaseTopicV1;
import com.redhat.topicindex.rest.entities.interfaces.RESTTagV1;
import com.redhat.topicindex.rest.entities.interfaces.RESTTopicV1;
import com.redhat.topicindex.rest.entities.interfaces.RESTTranslatedTopicV1;
import com.redhat.topicindex.rest.sort.TagV1NameComparator;
import com.redhat.topicindex.zanata.ZanataDetails;

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
	public static <T extends RESTBaseTopicV1<T, U>, U extends BaseRestCollectionV1<T, U>> String buildReportTable(final List<TopicErrorData<T, U>> topicErrorDatas, final String tableTitle, final boolean showEditorLink, final ZanataDetails zanataDetails)
	{
		final List<String> tableHeaders = CollectionUtilities.toArrayList(new String[]{"Topic Link", "Topic Title", "Topic Tags"});

		// Put the details into different tables
		final List<List<String>> rows = new ArrayList<List<String>>();
		for (final TopicErrorData<T, U> topicErrorData : topicErrorDatas)
		{
			final T topic = topicErrorData.getTopic();
			final String url;
			final Integer topicId;
			final Integer topicRevision;
			final String editorUrl;
			if (topic instanceof RESTTranslatedTopicV1)
			{
				url = ComponentTranslatedTopicV1.returnSkynetURL((RESTTranslatedTopicV1) topic);
				topicId = ((RESTTranslatedTopicV1) topic).getTopicId();
				topicRevision = ((RESTTranslatedTopicV1) topic).getTopicRevision();
				editorUrl = ComponentTranslatedTopicV1.returnEditorURL((RESTTranslatedTopicV1) topic, zanataDetails);
			}
			else
			{
				url = ComponentTopicV1.returnSkynetURL((RESTTopicV1) topic);
				topicId = topic.getId();
				topicRevision = topic.getRevision();
				editorUrl = ComponentTopicV1.returnEditorURL((RESTTopicV1) topic);
			}
			
			final String topicULink = createTopicTableLinks(topicId, topicRevision, url, editorUrl);
			final String topicTitle = DocBookUtilities.wrapListItems(CollectionUtilities.toArrayList(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara(topic.getTitle()))));
			final String topicTags = buildItemizedTopicTagList(topic);
			
			rows.add(CollectionUtilities.toArrayList(new String[] {topicULink, topicTitle, topicTags}));
		}
		
		return rows.size() > 0 ? DocBookUtilities.wrapInTable(tableTitle, tableHeaders, rows) : "";
	}
	
	private static String createTopicTableLinks(final Integer topicId, final Integer topicRevision, final String url, final String editorUrl)
	{
		final String topicPara = DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(url, "Topic " + topicId + ", Revision " + topicRevision));
		if (editorUrl != null)
		{
			final String editorPara = DocBookUtilities.wrapInPara(DocBookUtilities.buildULink(editorUrl, "Editor Link"));
			return DocBookUtilities.wrapListItems(CollectionUtilities.toArrayList(DocBookUtilities.wrapInListItem(topicPara), DocBookUtilities.wrapInListItem(editorPara)));
		}
		else
		{
			return DocBookUtilities.wrapListItems(CollectionUtilities.toArrayList(DocBookUtilities.wrapInListItem(topicPara)));
		}
	}
	
	private static <T extends RESTBaseTopicV1<T, U>, U extends BaseRestCollectionV1<T, U>> String buildItemizedTopicTagList(final T topic)
	{
		final TreeMap<NameIDSortMap, ArrayList<RESTTagV1>> tags = ComponentBaseTopicV1.getCategoriesMappedToTags(topic);

		/*
		 * Since the sort order breaks the tag to categories grouping
		 * we need to regroup the mapping.
		 */
		final Map<String, List<RESTTagV1>> catToTags = new TreeMap<String, List<RESTTagV1>>();
		for (final NameIDSortMap key : tags.keySet())
		{
			// sort alphabetically
			Collections.sort(tags.get(key), new TagV1NameComparator());
			
			final String categoryName = key.getName();
			
			if (!catToTags.containsKey(categoryName))
				catToTags.put(categoryName, new LinkedList<RESTTagV1>());
			
			catToTags.get(categoryName).addAll(tags.get(key));
		}
		
		/* Build the list of items to be used in the itemized lists */
		final List<String> items = new ArrayList<String>();
		for (final String catName : catToTags.keySet())
		{
			String thisTagList = "";

			for (final RESTTagV1 tag : catToTags.get(catName))
			{
				if (thisTagList.length() != 0)
					thisTagList += ", ";

				thisTagList += tag.getName();
			}
			
			items.add(DocBookUtilities.wrapInListItem(DocBookUtilities.wrapInPara("<emphasis role=\"bold\">" + catName + ":</emphasis> " + thisTagList)));
		}
		
		return DocBookUtilities.wrapListItems(items);
	}
}
