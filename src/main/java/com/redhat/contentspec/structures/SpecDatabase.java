package com.redhat.contentspec.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.redhat.contentspec.Level;
import com.redhat.contentspec.SpecTopic;
import com.redhat.ecs.commonutils.CollectionUtilities;
import com.redhat.topicindex.rest.entities.interfaces.RESTBaseTopicV1;

public class SpecDatabase {

	private Map<Integer, List<SpecTopic>> specTopics = new HashMap<Integer, List<SpecTopic>>();
	private Map<String, List<SpecTopic>> specTopicsTitles = new HashMap<String, List<SpecTopic>>();
	private Map<String, List<Level>> specLevels = new HashMap<String, List<Level>>();
	
	public void add(final SpecTopic topic, final String escapedTitle)
	{
		if (topic == null) return;
				
		final Integer topicId = topic.getDBId();
		if (!specTopics.containsKey(topicId))
			specTopics.put(topicId, new LinkedList<SpecTopic>());
		
		if (!specTopicsTitles.containsKey(escapedTitle))
			specTopicsTitles.put(escapedTitle, new LinkedList<SpecTopic>());
		
		if (specTopics.get(topicId).size() > 0 || specTopicsTitles.get(escapedTitle).size() > 0)
		{
			int duplicateId = specTopics.get(topicId).size();
			
			if (specTopicsTitles.get(escapedTitle).size() > duplicateId)
				duplicateId = specTopicsTitles.get(escapedTitle).size();
			
			topic.setDuplicateId(Integer.toString(specTopics.get(topicId).size()));
		}
		
		specTopics.get(topicId).add(topic);
		specTopicsTitles.get(escapedTitle).add(topic);
	}
	
	public void add(final Level level, final String escapedTitle)
	{
		if (level == null) return;
		
		if (!specLevels.containsKey(escapedTitle))
			specLevels.put(escapedTitle, new LinkedList<Level>());
		
		if (specLevels.get(escapedTitle).size() > 0)
			level.setDuplicateId(Integer.toString(specLevels.get(escapedTitle).size()));
		
		specLevels.get(escapedTitle).add(level);
	}
	
	public void setDatabaseDulicateIds()
	{
		/* Topics */
		for (final String topicTitle: specTopicsTitles.keySet())
		{
			final List<SpecTopic> specTopics = specTopicsTitles.get(topicTitle);
			for (int i = 0; i < specTopics.size(); i++)
			{
				if (i != 0)
					specTopics.get(i).setDuplicateId(Integer.toString(i));
				else
					specTopics.get(i).setDuplicateId(null);
			}
		}
		
		/* Levels */
		for (final String levelTitle: specLevels.keySet())
		{
			final List<Level> levels = specLevels.get(levelTitle);
			for (int i = 0; i < levels.size(); i++)
			{
				if (i != 0)
					levels.get(i).setDuplicateId(Integer.toString(i));
				else
					levels.get(i).setDuplicateId(null);
			}
		}
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
	
	@SuppressWarnings("unchecked")
	public <T extends RESTBaseTopicV1<T>> List<T> getAllTopics()
	{
		final List<T> topics = new ArrayList<T>();
		for (final Integer topicId : specTopics.keySet())
		{
			if (!specTopics.get(topicId).isEmpty())
				topics.add((T) specTopics.get(topicId).get(0).getTopic());
		}
		return topics;
	}
}
