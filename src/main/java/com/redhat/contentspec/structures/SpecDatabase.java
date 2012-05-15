package com.redhat.contentspec.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;

import com.redhat.contentspec.Level;
import com.redhat.contentspec.SpecTopic;
import com.redhat.ecs.commonutils.CollectionUtilities;
import com.redhat.ecs.commonutils.DocBookUtilities;

public class SpecDatabase {

	private Map<Integer, List<SpecTopic>> specTopics = new HashMap<Integer, List<SpecTopic>>();
	private Map<String, List<Level>> specLevels = new HashMap<String, List<Level>>();
	
	public void add(final SpecTopic topic)
	{
		add(topic, null);
	}
	
	public void add(final SpecTopic topic, final Document doc)
	{
		if (topic == null) return;
		
		final Integer topicId = topic.getDBId();
		if (!specTopics.containsKey(topicId))
			specTopics.put(topicId, new LinkedList<SpecTopic>());
		
		if (specTopics.get(topicId).size() > 0)
			topic.setDuplicateId(Integer.toString(specTopics.get(topicId).size()));
		
		specTopics.get(topicId).add(topic);
		topic.setXmlDocument(doc);
	}
	
	public void add(final Level level)
	{
		if (level == null) return;
		
		final String levelTitle = DocBookUtilities.escapeTitle(level.getTitle());
		if (!specLevels.containsKey(levelTitle))
			specLevels.put(levelTitle, new LinkedList<Level>());
		
		if (specLevels.get(levelTitle).size() > 0)
			level.setDuplicateId(Integer.toString(specLevels.get(levelTitle).size()));
		
		specLevels.get(levelTitle).add(level);
	}
	
	public List<Integer> getTopicIds()
	{
		return CollectionUtilities.toArrayList(specTopics.keySet());
	}
	
	public boolean isUniqueSpecTopic(final SpecTopic topic)
	{
		return specTopics.containsKey(topic.getDBId()) ? specTopics.get(topic.getDBId()).size() == 1 : false;
	}
	
	public List<SpecTopic> getSpecTopicsForTopicID(final Integer topicId)
	{
		if (specTopics.containsKey(topicId))
		{
			return specTopics.get(topicId);
		}
		
		return new LinkedList<SpecTopic>();
	}
	
	public List<SpecTopic> getAllSpecTopics()
	{
		final ArrayList<SpecTopic> specTopics = new ArrayList<SpecTopic>();
		for (final Integer topicId: this.specTopics.keySet())
		{
			specTopics.addAll(this.specTopics.get(topicId));
		}
		
		return specTopics;
	}
	
	public List<Level> getAllLevels()
	{
		final ArrayList<Level> levels = new ArrayList<Level>();
		for (final String levelTitle : this.specLevels.keySet())
		{
			levels.addAll(this.specLevels.get(levelTitle));
		}
		
		return levels;
	}
}
