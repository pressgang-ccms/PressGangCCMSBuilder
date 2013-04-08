package org.jboss.pressgang.ccms.contentspec.builder.structures;

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
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;

public class BuildDatabase {
    private Map<Integer, List<SpecTopic>> topics = new HashMap<Integer, List<SpecTopic>>();
    private Map<String, List<SpecTopic>> topicsKeys = new HashMap<String, List<SpecTopic>>();
    private Map<String, List<Level>> levelTitles = new HashMap<String, List<Level>>();

    /**
     * Add a SpecTopic to the database.
     *
     * @param specTopic    The SpecTopic object to be added.
     * @param key          A key that represents the Topic mapped to the SpecTopic
     */
    public void add(final SpecTopic specTopic, final String key) {
        if (specTopic == null) return;

        final Integer topicId = specTopic.getDBId();
        if (!topics.containsKey(topicId)) {
            topics.put(topicId, new LinkedList<SpecTopic>());
        }

        // Make sure the key exists
        if (!topicsKeys.containsKey(key)) {
            topicsKeys.put(key, new LinkedList<SpecTopic>());
        }

        topics.get(topicId).add(specTopic);
        topicsKeys.get(key).add(specTopic);
    }

    /**
     * Add a Level to the database.
     *
     * @param level        The Level object to be added.
     * @param escapedTitle The escaped title of the Level.
     */
    public void add(final Level level, final String escapedTitle) {
        if (level == null) return;

        if (!levelTitles.containsKey(escapedTitle)) {
            levelTitles.put(escapedTitle, new LinkedList<Level>());
        }

        if (levelTitles.get(escapedTitle).size() > 0) {
            level.setDuplicateId(Integer.toString(levelTitles.get(escapedTitle).size()));
        }

        levelTitles.get(escapedTitle).add(level);
    }

    /**
     * Sets the Duplicate IDs for all the SpecTopics in the Database.
     *
     * @param usedIdAttributes A mapping of IDs to topics that exist for a book.
     */
    public void setDatabaseDuplicateIds(final Map<Integer, Set<String>> usedIdAttributes, final boolean useFixedURLs) {
        // Create the mapping of topic titles to spec topics
        final Map<String, List<SpecTopic>> topicsTitles = new HashMap<String, List<SpecTopic>>();
        for (final Entry<Integer, List<SpecTopic>> topicEntry : topics.entrySet()) {
            final List<SpecTopic> specTopics = topicEntry.getValue();
            for (final SpecTopic specTopic : specTopics) {
                String topicTitle = specTopic.getUniqueLinkId(useFixedURLs);

                if (!topicsTitles.containsKey(topicTitle)) {
                    topicsTitles.put(topicTitle, new LinkedList<SpecTopic>());
                }

                topicsTitles.get(topicTitle).add(specTopic);
            }
        }

        // Set the spec topic duplicate ids based on topic title
        for (final Entry<String, List<SpecTopic>> topicTitleEntry : topicsTitles.entrySet()) {
            final List<SpecTopic> specTopics = topicTitleEntry.getValue();
            if (specTopics.size() > 1) {
                for (int i = 1; i < specTopics.size(); i++) {
                    specTopics.get(i).setDuplicateId(Integer.toString(i));
                }
            }
        }

        // Levels
        for (final Entry<String, List<Level>> levelTitleEntry : levelTitles.entrySet()) {
            final List<Level> levels = levelTitleEntry.getValue();
            for (int i = 0; i < levels.size(); i++) {
                if (i != 0) {
                    levels.get(i).setDuplicateId(Integer.toString(i));
                } else {
                    levels.get(i).setDuplicateId(null);
                }
            }
        }
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
    public List<SpecTopic> getSpecTopicsForTopicID(final Integer topicId) {
        if (topics.containsKey(topicId)) {
            return topics.get(topicId);
        }

        return new LinkedList<SpecTopic>();
    }

    /**
     * Get a List of all the SpecTopics in the Database for a Topic ID.
     *
     * @param key The Topic Key to find SpecTopics for.
     * @return A List of SpecTopic objects whose Key matches.
     */
    public List<SpecTopic> getSpecTopicsForKey(final String key) {
        if (topicsKeys.containsKey(key)) {
            return topicsKeys.get(key);
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
        for (final Entry<Integer, List<SpecTopic>> topicEntry : topics.entrySet()) {
            specTopics.addAll(topicEntry.getValue());
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
        for (final Entry<String, List<Level>> levelTitleEntry : levelTitles.entrySet()) {
            levels.addAll(levelTitleEntry.getValue());
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
        for (final Entry<String, List<Level>> levelTitleEntry : levelTitles.entrySet()) {
            final List<Level> levels = levelTitleEntry.getValue();
            for (final Level level : levels) {
                ids.add(level.getUniqueLinkId(useFixedUrls));
            }
        }

        // Add all the topic id attributes
        for (final Entry<Integer, List<SpecTopic>> topicEntry : topics.entrySet()) {
            final List<SpecTopic> topics = topicEntry.getValue();
            for (final SpecTopic topic : topics) {
                ids.add(topic.getUniqueLinkId(useFixedUrls));
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
        for (final Entry<Integer, List<SpecTopic>> entry : this.topics.entrySet()) {
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
    protected <T extends BaseTopicWrapper<T>> List<T> getUniqueTopicsFromSpecTopics(final List<SpecTopic> topics) {
        // Find all the unique topics first
        final Map<Integer, T> revisionToTopic = new HashMap<Integer, T>();
        for (final SpecTopic specTopic : topics) {
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
