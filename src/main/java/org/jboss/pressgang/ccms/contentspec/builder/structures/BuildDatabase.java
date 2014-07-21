package org.jboss.pressgang.ccms.contentspec.builder.structures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.pressgang.ccms.contentspec.ITopicNode;
import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.SpecNode;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;

public class BuildDatabase {
    private Map<Integer, List<ITopicNode>> topics = new HashMap<Integer, List<ITopicNode>>();
    private Map<String, List<ITopicNode>> topicsKeys = new HashMap<String, List<ITopicNode>>();
    private List<Level> levels = new ArrayList<Level>();

    /**
     * Add a SpecTopic to the database.
     *
     * @param specTopic The SpecTopic object to be added.
     * @param key       A key that represents the Topic mapped to the SpecTopic
     */
    public void add(final ITopicNode specTopic, final String key) {
        if (specTopic == null) return;

        final Integer topicId = specTopic.getDBId();
        if (!topics.containsKey(topicId)) {
            topics.put(topicId, new LinkedList<ITopicNode>());
        }

        // Make sure the key exists
        if (!topicsKeys.containsKey(key)) {
            topicsKeys.put(key, new LinkedList<ITopicNode>());
        }

        topics.get(topicId).add(specTopic);
        topicsKeys.get(key).add(specTopic);
    }

    /**
     * Add a Level to the database.
     *
     * @param level        The Level object to be added.
     */
    public void add(final Level level) {
        if (level == null) return;

        levels.add(level);
    }

    /**
     * Get a List of all the Topic IDs for the topics in the database.
     *
     * @return A List of Integer objects that represent the Topic IDs.
     */
    public List<Integer> getTopicIds() {
        return CollectionUtilities.toArrayList(topics.keySet());
    }

    /**
     * Checks if a topic is unique in the database.
     *
     * @param topic The Topic to be checked to see if it's unique.
     * @return True if the topic exists in the database and it is unique, otherwise false.
     */
    public boolean isUniqueSpecTopic(final SpecTopic topic) {
        return topics.containsKey(topic.getDBId()) ? topics.get(topic.getDBId()).size() == 1 : false;
    }

    /**
     * Get a List of all the SpecTopics in the Database for a Topic ID.
     *
     * @param topicId The Topic ID to find SpecTopics for.
     * @return A List of SpecTopic objects whose Topic ID matches.
     */
    public List<ITopicNode> getTopicNodesForTopicID(final Integer topicId) {
        if (topics.containsKey(topicId)) {
            return topics.get(topicId);
        }

        return new LinkedList<ITopicNode>();
    }

    /**
     * Get a List of all the SpecTopics in the Database for a Topic ID.
     *
     * @param key The Topic Key to find SpecTopics for.
     * @return A List of SpecTopic objects whose Key matches.
     */
    public List<ITopicNode> getTopicNodesForKey(final String key) {
        if (topicsKeys.containsKey(key)) {
            return topicsKeys.get(key);
        }

        return new LinkedList<ITopicNode>();
    }

    /**
     * Get a List of all the SpecTopics in the Database.
     *
     * @return A list of SpecTopic objects.
     */
    public List<SpecTopic> getAllSpecTopics() {
        final ArrayList<SpecTopic> specTopics = new ArrayList<SpecTopic>();
        for (final Entry<Integer, List<ITopicNode>> topicEntry : topics.entrySet()) {
            for (final ITopicNode topic : topicEntry.getValue()) {
                if (topic instanceof SpecTopic) {
                    specTopics.add((SpecTopic) topic);
                }
            }
        }

        return specTopics;
    }

    /**
     * Get a List of all the Topic nodes in the Database.
     *
     * @return A list of ITopicNode objects.
     */
    public List<ITopicNode> getAllTopicNodes() {
        final ArrayList<ITopicNode> topicNodes = new ArrayList<ITopicNode>();
        for (final Entry<Integer, List<ITopicNode>> topicEntry : topics.entrySet()) {
            topicNodes.addAll(topicEntry.getValue());
        }

        return topicNodes;
    }

    /**
     * Get a List of all the levels in the Database.
     *
     * @return A list of Level objects.
     */
    public List<Level> getAllLevels() {
        return Collections.unmodifiableList(levels);
    }

    /**
     * Get a List of all the SpecNodes in the Database.
     *
     * @return A list of Level objects.
     */
    public List<SpecNode> getAllSpecNodes() {
        final ArrayList<SpecNode> retValue = new ArrayList<SpecNode>();

        // Add all the levels
        retValue.addAll(levels);

        // Add all the topics
        for (final Entry<Integer, List<ITopicNode>> topicEntry : topics.entrySet()) {
            for (final ITopicNode topic : topicEntry.getValue()) {
                if (topic instanceof SpecNode) {
                    retValue.add((SpecNode) topic);
                }
            }
        }

        return retValue;
    }

    /**
     * Get a list of all the ID Attributes of all the topics and levels held in the database.
     *
     * @param buildData
     * @return A List of IDs that exist for levels and topics in the database.
     */
    public Set<String> getIdAttributes(final BuildData buildData) {
        final Set<String> ids = new HashSet<String>();

        // Add all the level id attributes
        for (final Level level : levels) {
            ids.add(level.getUniqueLinkId(buildData.isUseFixedUrls()));
        }

        // Add all the topic id attributes
        for (final Entry<Integer, List<ITopicNode>> topicEntry : topics.entrySet()) {
            final List<ITopicNode> topics = topicEntry.getValue();
            for (final ITopicNode topic : topics) {
                if (topic instanceof SpecTopic) {
                    final SpecTopic specTopic = (SpecTopic) topic;
                    ids.add(specTopic.getUniqueLinkId(buildData.isUseFixedUrls()));
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
    public <T extends BaseTopicWrapper<T>> List<T> getAllTopics() {
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
    public <T extends BaseTopicWrapper<T>> List<T> getAllTopics(boolean ignoreRevisions) {
        final List<T> topics = new ArrayList<T>();
        for (final Entry<Integer, List<ITopicNode>> entry : this.topics.entrySet()) {
            final Integer topicId = entry.getKey();
            if (!this.topics.get(topicId).isEmpty()) {
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
     * @param topics The list of SpecTopic object to get the topics from.
     * @return A Unique list of Topics.
     */
    @SuppressWarnings("unchecked")
    protected <T extends BaseTopicWrapper<T>> List<T> getUniqueTopicsFromSpecTopics(final List<ITopicNode> topics) {
        // Find all the unique topics first
        final Map<Integer, T> revisionToTopic = new HashMap<Integer, T>();
        for (final ITopicNode specTopic : topics) {
            final T topic = (T) specTopic.getTopic();

            // Find the Topic Revision
            final Integer topicRevision = topic.getTopicRevision();

            if (!revisionToTopic.containsKey(topicRevision)) {
                revisionToTopic.put(topicRevision, topic);
            }
        }

        // Convert the revision to topic mapping to just a list of topics
        final List<T> retValue = new ArrayList<T>();
        for (final Entry<Integer, T> entry : revisionToTopic.entrySet()) {
            retValue.add(entry.getValue());
        }

        return retValue;
    }
}
