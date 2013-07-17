package com.redhat.contentspec.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.enums.TopicType;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTTranslatedTopicV1;
import org.jboss.pressgang.ccms.rest.v1.entities.base.RESTBaseTopicV1;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;

import com.redhat.contentspec.builder.utils.DocbookBuildUtilities;

public class SpecDatabase {
    private Map<Integer, List<SpecTopic>> specTopics = new HashMap<Integer, List<SpecTopic>>();
    private Map<String, List<SpecTopic>> specTopicsTitles = new HashMap<String, List<SpecTopic>>();
    private Map<String, List<Level>> specLevels = new HashMap<String, List<Level>>();

    /**
     * Add a SpecTopic to the database.
     * 
     * @param topic The SpecTopic object to be added.
     * @param escapedTitle The escaped title of the SpecTopic.
     */
    public void add(final SpecTopic topic, final String escapedTitle) {
        if (topic == null)
            return;

        final Integer topicId = topic.getDBId();
        if (!specTopics.containsKey(topicId))
            specTopics.put(topicId, new LinkedList<SpecTopic>());

        if (!specTopicsTitles.containsKey(escapedTitle))
            specTopicsTitles.put(escapedTitle, new LinkedList<SpecTopic>());

        if (specTopics.get(topicId).size() > 0 || specTopicsTitles.get(escapedTitle).size() > 0) {
            topic.setDuplicateId(Integer.toString(specTopics.get(topicId).size()));
        }

        specTopics.get(topicId).add(topic);
        specTopicsTitles.get(escapedTitle).add(topic);
    }

    /**
     * Add a Level to the database.
     * 
     * @param level The Level object to be added.
     * @param escapedTitle The escaped title of the Level.
     */
    public void add(final Level level, final String escapedTitle) {
        if (level == null)
            return;

        if (!specLevels.containsKey(escapedTitle))
            specLevels.put(escapedTitle, new LinkedList<Level>());

        if (specLevels.get(escapedTitle).size() > 0)
            level.setDuplicateId(Integer.toString(specLevels.get(escapedTitle).size()));

        specLevels.get(escapedTitle).add(level);
    }

    /**
     * Sets the Duplicate IDs for all the SpecTopics in the Database.
     * 
     * @param usedIdAttributes A mapping of IDs to topics that exist for a book.
     */
    public void setDatabaseDuplicateIds(final Map<SpecTopic, Set<String>> usedIdAttributes) {
        // Topics
        for (final String topicTitle : specTopicsTitles.keySet()) {
            final List<SpecTopic> specTopics = specTopicsTitles.get(topicTitle);
            for (int i = 0; i < specTopics.size(); i++) {
                final SpecTopic specTopic = specTopics.get(i);
                String fixedIdAttributeValue = null;

                // Ignore inner level topics
                if (specTopic.getTopicType() == TopicType.LEVEL) continue;

                if (i != 0) {
                    fixedIdAttributeValue = Integer.toString(i);
                }

                if (!DocbookBuildUtilities.isUniqueAttributeId(topicTitle, specTopic.getDBId(), usedIdAttributes)) {
                    if (fixedIdAttributeValue == null) {
                        fixedIdAttributeValue = Integer.toString(specTopic.getStep());
                    } else {
                        fixedIdAttributeValue += "-" + specTopic.getStep();
                    }
                }

                specTopic.setDuplicateId(fixedIdAttributeValue);
            }
        }

        // Levels
        for (final String levelTitle : specLevels.keySet()) {
            final List<Level> levels = specLevels.get(levelTitle);
            for (int i = 0; i < levels.size(); i++) {
                if (i != 0)
                    levels.get(i).setDuplicateId(Integer.toString(i));
                else
                    levels.get(i).setDuplicateId(null);
            }
        }
    }

    /**
     * Get a List of all the Topic IDs for the topics in the database.
     * 
     * @return A List of Integer objects that represent the Topic IDs.
     */
    public List<Integer> getTopicIds() {
        return CollectionUtilities.toArrayList(specTopics.keySet());
    }

    /**
     * Checks if a topic is unique in the database.
     * 
     * @param topic The Topic to be checked to see if it's unique.
     * @return True if the topic exists in the database and it is unique, otherwise false.
     */
    public boolean isUniqueSpecTopic(final SpecTopic topic) {
        return specTopics.containsKey(topic.getDBId()) ? specTopics.get(topic.getDBId()).size() == 1 : false;
    }

    /**
     * Get a List of all the SpecTopics in the Database for a Topic ID.
     * 
     * @param topicId The Topic ID to find SpecTopics for.
     * @return A List of SpecTopic objects whose Topic ID matches.
     */
    public List<SpecTopic> getSpecTopicsForTopicID(final Integer topicId) {
        if (specTopics.containsKey(topicId)) {
            return specTopics.get(topicId);
        }

        return new LinkedList<SpecTopic>();
    }

    /**
     * Get a List of all the SpecTopics in the Database.
     * 
     * @return A list of SpecTopic objects.
     */
    public List<SpecTopic> getAllSpecTopics() {
        final ArrayList<SpecTopic> specTopics = new ArrayList<SpecTopic>();
        for (final Integer topicId : this.specTopics.keySet()) {
            specTopics.addAll(this.specTopics.get(topicId));
        }

        return specTopics;
    }

    /**
     * Get a List of all the levels in the Database.
     * 
     * @return A list of Level objects.
     */
    public List<Level> getAllLevels() {
        final ArrayList<Level> levels = new ArrayList<Level>();
        for (final String levelTitle : this.specLevels.keySet()) {
            levels.addAll(this.specLevels.get(levelTitle));
        }

        return levels;
    }

    /**
     * Get a list of all the ID Attributes of all the topics and levels held in the database.
     * 
     * @param useFixedUrls If Fixed URLs should be used to generate the IDs for topics.
     * @return A List of IDs that exist for levels and topics in the database.
     */
    public Set<String> getIdAttributes(final boolean useFixedUrls) {
        final Set<String> ids = new HashSet<String>();

        // Add all the level id attributes
        for (final String levelTitle : specLevels.keySet()) {
            final List<Level> levels = specLevels.get(levelTitle);
            for (final Level level : levels) {
                ids.add(level.getUniqueLinkId(useFixedUrls));
            }
        }

        // Add all the topic id attributes
        for (final Integer topicId : specTopics.keySet()) {
            final List<SpecTopic> topics = specTopics.get(topicId);
            for (final SpecTopic topic : topics) {
                if (topic.getTopicType() != TopicType.LEVEL) {
                    ids.add(topic.getUniqueLinkId(useFixedUrls));
                }
            }
        }

        return ids;
    }

    /**
     * Get all of the Unique Topics that exist in the database.
     * 
     * @return A List of all the Unique Topics that exist in the database.
     */
    public <T extends RESTBaseTopicV1<T, ?, ?>> List<T> getAllTopics() {
        return getAllTopics(false);
    }

    /**
     * Get all of the Topics that exist in the database. You can either choose to ignore revisions, meaning two topics with the
     * same ID but different revisions are classed as the same topic. Or choose to take note of revisions, meaning if two topics
     * have different revisions but the same ID, they are still classed as different topics.
     * 
     * @param ignoreRevisions If revisions should be ignored when generating the list of topics.
     * @return A List of all the Topics that exist in the database.
     */
    @SuppressWarnings("unchecked")
    public <T extends RESTBaseTopicV1<T, ?, ?>> List<T> getAllTopics(boolean ignoreRevisions) {
        final List<T> topics = new ArrayList<T>();
        for (final Entry<Integer, List<SpecTopic>> entry : specTopics.entrySet()) {
            final Integer topicId = entry.getKey();
            if (!specTopics.get(topicId).isEmpty()) {
                if (ignoreRevisions) {
                    topics.add((T) entry.getValue().get(0).getTopic());
                } else {
                    final List<T> specTopicTopics = getUniqueTopicsFromSpecTopics(entry.getValue());
                    topics.addAll(specTopicTopics);
                }
            }
        }
        return topics;
    }

    /**
     * Get a list of Unique Topics from a list of SpecTopics.
     * 
     * @param specTopics The list of SpecTopic object to get the topics from.
     * @return A Unique list of Topics.
     */
    @SuppressWarnings("unchecked")
    protected <T extends RESTBaseTopicV1<T, ?, ?>> List<T> getUniqueTopicsFromSpecTopics(final List<SpecTopic> specTopics) {
        /* Find all the unique topics first */
        final Map<Integer, T> revisionToTopic = new HashMap<Integer, T>();
        for (final SpecTopic specTopic : specTopics) {
            final T topic = (T) specTopic.getTopic();

            /* Find the Topic Revision */
            final Integer topicRevision;
            if (topic instanceof RESTTranslatedTopicV1) {
                topicRevision = ((RESTTranslatedTopicV1) topic).getTopicRevision();
            } else {
                topicRevision = topic.getRevision();
            }

            if (!revisionToTopic.containsKey(topicRevision)) {
                revisionToTopic.put(topicRevision, topic);
            }
        }

        /* Convert the revision to topic mapping to just a list of topics */
        final List<T> topics = new ArrayList<T>();
        for (final Entry<Integer, T> entry : revisionToTopic.entrySet()) {
            topics.add(entry.getValue());
        }

        return topics;
    }
}
