package com.redhat.contentspec.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.rest.v1.entities.base.RESTBaseTopicV1;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;

import com.redhat.contentspec.builder.utils.DocbookBuildUtilities;

public class SpecDatabase
{
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
	
	public void setDatabaseDulicateIds(final Map<Integer, Set<String>> usedIdAttributes)
	{
		/* Topics */
		for (final String topicTitle: specTopicsTitles.keySet())
		{
			final List<SpecTopic> specTopics = specTopicsTitles.get(topicTitle);
			for (int i = 0; i < specTopics.size(); i++)
			{
			    final SpecTopic specTopic = specTopics.get(i);
			    String fixedIdAttributeValue = null;
			    
			    if (i != 0)
			    {
			        fixedIdAttributeValue = Integer.toString(i);
			    }

			    if (!DocbookBuildUtilities.isUniqueAttributeId(topicTitle, specTopic.getDBId(), usedIdAttributes))
                {
			        if (fixedIdAttributeValue == null)
			        {
			            fixedIdAttributeValue = Integer.toString(specTopic.getStep());
			        }
			        else
			        {
			            fixedIdAttributeValue += "-" + specTopic.getStep();
			        }
                }

				specTopic.setDuplicateId(fixedIdAttributeValue);
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
	
	public Set<String> getIdAttributes(final boolean useFixedUrls)
	{
		final Set<String> ids = new HashSet<String>();
		
		// Add all the level id attributes
		for (final String levelTitle : this.specLevels.keySet())
		{
			final List<Level> levels = specLevels.get(levelTitle);
			for (final Level level : levels)
			{
				ids.add(level.getUniqueLinkId(useFixedUrls));
			}
		}
		
		// Add all the topic id attributes
		for (final Integer topicId : this.specTopics.keySet())
		{
			final List<SpecTopic> topics = specTopics.get(topicId);
			for (final SpecTopic topic : topics)
			{
				ids.add(topic.getUniqueLinkId(useFixedUrls));
			}
		}
		
		return ids;
	}
	
	@SuppressWarnings("unchecked")
	public <T extends RESTBaseTopicV1<T, ?, ?>> List<T> getAllTopics()
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
