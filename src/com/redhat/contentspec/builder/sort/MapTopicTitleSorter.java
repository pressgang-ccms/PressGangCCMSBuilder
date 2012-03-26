package com.redhat.contentspec.builder.sort;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.redhat.ecs.services.docbookcompiling.xmlprocessing.structures.InjectionTopicData;
import com.redhat.topicindex.rest.entities.TopicV1;

public class MapTopicTitleSorter implements ExternalMapSort<Integer, TopicV1, InjectionTopicData>
{
	 public void sort(final Map<Integer, TopicV1> map, final List<InjectionTopicData> list) 
	    {
	        if (map == null || list == null)
	        	return;
		 
		 	Collections.sort(list, new Comparator<InjectionTopicData>() 
	        {
	            public int compare(final InjectionTopicData o1, final InjectionTopicData o2)
	            {
	            	final boolean v1Exists = map.containsKey(o1.topicId);
	            	final boolean v2Exists = map.containsKey(o2.topicId);
	            	
	            	if (!v1Exists && !v2Exists)
	            		return 0;
	            	if (!v1Exists)
	            		return -1;
	            	if (!v2Exists)
	            		return 1;
	            	
	            	final TopicV1 v1 = map.get(o1.topicId);
	            	final TopicV1 v2 = map.get(o2.topicId);
	            	
	            	if (v1 == null && v2 == null)
	            		return 0;
	            	
	            	if (v1 == null)
	            		return -1;
	            	
	            	if (v2 == null)
	            		return 1;
	            	
	            	if (v1.getTitle() == null && v2.getTitle() == null)
	            		return 0;
	            	
	            	if (v1.getTitle() == null)
	            		return -1;
	            	
	            	if (v2.getTitle() == null)
	            		return 1;
	            	
	            	return v1.getTitle().toLowerCase().compareTo(v2.getTitle().toLowerCase());
	            }
	        });
	    }
}

