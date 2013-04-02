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
import org.jboss.pressgang.ccms.contentspec.builder.utils.DocbookBuildUtilities;
import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;

public class BuildDatabase {
    private Map<Integer, List<BuildTopic>> topics = new HashMap<Integer, List<BuildTopic>>();
    private Map<String, List<BuildTopic>> topicsTitles = new HashMap<String, List<BuildTopic>>();
    private Map<String, List<BuildLevel>> levelTitles = new HashMap<String, List<BuildLevel>>();

    /**
     * Add a SpecTopic to the database.
     *
     * @param topic        The SpecTopic object to be added.
     * @param escapedTitle The escaped title of the SpecTopic.
     */
    public void add(final SpecTopic topic, final String escapedTitle) {
        add(new BuildTopic(topic), escapedTitle);
    }

    /**
     * Add a BuildTopic to the database.
     *
     * @param topic        The BuildTopic object to be added.
     * @param escapedTitle The escaped title of the SpecTopic.
     */
    public void add(final BuildTopic topic, final String escapedTitle) {
        if (topic == null) return;

        final SpecTopic specTopic = topic.getSpecTopic();
        final Integer topicId = specTopic.getDBId();
        if (!topics.containsKey(topicId)) topics.put(topicId, new LinkedList<BuildTopic>());

        if (!topicsTitles.containsKey(escapedTitle)) topicsTitles.put(escapedTitle, new LinkedList<BuildTopic>());

        if (topics.get(topicId).size() > 0 || topicsTitles.get(escapedTitle).size() > 0) {
            topic.setDuplicateId(Integer.toString(topics.get(topicId).size()));
        }

        topics.get(topicId).add(topic);
        topicsTitles.get(escapedTitle).add(topic);
    }

    /**
     * Add a Level to the database.
     *
     * @param level        The Level object to be added.
     * @param escapedTitle The escaped title of the Level.
     */
    public void add(final Level level, final String escapedTitle) {
        add(new BuildLevel(level), escapedTitle);
    }

    /**
     * Add a Level to the database.
     *
     * @param level        The BuildLevel object to be added.
     * @param escapedTitle The escaped title of the Level.
     */
    public void add(final BuildLevel level, final String escapedTitle) {
        if (level == null) return;

        if (!levelTitles.containsKey(escapedTitle)) {
            levelTitles.put(escapedTitle, new LinkedList<BuildLevel>());
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
    public void setDatabaseDuplicateIds(final Map<Integer, Set<String>> usedIdAttributes) {
        // Topics
        for (final Entry<String, List<BuildTopic>> topicTitleEntry : topicsTitles.entrySet()) {
            final List<BuildTopic> buildTopics = topicTitleEntry.getValue();
            for (int i = 0; i < buildTopics.size(); i++) {
                final BuildTopic topic = buildTopics.get(i);
                final SpecTopic specTopic = topic.getSpecTopic();
                String fixedIdAttributeValue = null;

                if (i != 0) {
                    fixedIdAttributeValue = Integer.toString(i);
                }

                if (!DocbookBuildUtilities.isUniqueAttributeId(topicTitleEntry.getKey(), specTopic.getDBId(), usedIdAttributes)) {
                    if (fixedIdAttributeValue == null) {
                        fixedIdAttributeValue = Integer.toString(specTopic.getStep());
                    } else {
                        fixedIdAttributeValue += "-" + specTopic.getStep();
                    }
                }

                topic.setDuplicateId(fixedIdAttributeValue);
            }
        }

        // Levels
        for (final Entry<String, List<BuildLevel>> levelTitleEntry : levelTitles.entrySet()) {
            final List<BuildLevel> levels = levelTitleEntry.getValue();
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
        final LinkedList<SpecTopic> specTopics = new LinkedList<SpecTopic>();
        if (topics.containsKey(topicId)) {
            for (final BuildTopic buildTopic : topics.get(topicId)) {
                specTopics.add(buildTopic.getSpecTopic());
            }
        }

        return specTopics;
    }

    /**
     * Get a List of all the BuildTopics in the Database for a Topic ID.
     *
     * @param topicId The Topic ID to find SpecTopics for.
     * @return A List of BuildTopic objects whose Topic ID matches.
     */
    public List<BuildTopic> getBuildTopicsForTopicID(final Integer topicId) {
        if (topics.containsKey(topicId)) {
            return topics.get(topicId);
        } else {
            return new LinkedList<BuildTopic>();
        }
    }

    /**
     * Get a List of all the SpecTopics in the Database.
     *
     * @return A list of SpecTopic objects.
     */
    public List<SpecTopic> getAllSpecTopics() {
        final ArrayList<SpecTopic> specTopics = new ArrayList<SpecTopic>();
        for (final Entry<Integer, List<BuildTopic>> topicEntry : topics.entrySet()) {
            for (final BuildTopic buildTopic : topicEntry.getValue()) {
                specTopics.add(buildTopic.getSpecTopic());
            }
        }

        return specTopics;
    }

    /**
     * Get a List of all the BuildTopics in the Database.
     *
     * @return A list of BuildTopic objects.
     */
    public List<BuildTopic> getAllBuildTopics() {
        final ArrayList<BuildTopic> buildTopics = new ArrayList<BuildTopic>();
        for (final Entry<Integer, List<BuildTopic>> topicEntry : topics.entrySet()) {
            buildTopics.addAll(topicEntry.getValue());
        }

        return buildTopics;
    }

    /**
     * Get a List of all the levels in the Database.
     *
     * @return A list of Level objects.
     */
    public List<Level> getAllLevels() {
        final ArrayList<Level> levels = new ArrayList<Level>();
        for (final Entry<String, List<BuildLevel>> levelTitleEntry : levelTitles.entrySet()) {
            for (final BuildLevel level : levelTitleEntry.getValue()) {
                levels.add(level.getLevel());
            }
        }

        return levels;
    }

    /**
     * Get a List of all the build levels in the Database.
     *
     * @return A list of BuildLevel objects.
     */
    public List<BuildLevel> getAllBuildLevels() {
        final ArrayList<BuildLevel> levels = new ArrayList<BuildLevel>();
        for (final Entry<String, List<BuildLevel>> levelTitleEntry : levelTitles.entrySet()) {
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
        for (final Entry<String, List<BuildLevel>> levelTitleEntry : levelTitles.entrySet()) {
            final List<BuildLevel> levels = levelTitleEntry.getValue();
            for (final BuildLevel level : levels) {
                ids.add(level.getUniqueLinkId(useFixedUrls));
            }
        }

        // Add all the topic id attributes
        for (final Entry<Integer, List<BuildTopic>> topicEntry : topics.entrySet()) {
            final List<BuildTopic> topics = topicEntry.getValue();
            for (final BuildTopic topic : topics) {
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
        for (final Entry<Integer, List<BuildTopic>> entry : this.topics.entrySet()) {
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
    protected <T extends BaseTopicWrapper<T>> List<T> getUniqueTopicsFromSpecTopics(final List<BuildTopic> topics) {
        // Find all the unique topics first
        final Map<Integer, T> revisionToTopic = new HashMap<Integer, T>();
        for (final BuildTopic buildTopic : topics) {
            final T topic = (T) buildTopic.getTopic();

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

    /**
     * Finds the build topic for a specific SpecTopic.
     *
     * @param specTopic A SpecTopic that is used in a BuildTopic
     * @return The build topic object that wraps the passed content specification topic object.
     */
    public BuildTopic getBuildTopicForSpecTopic(final SpecTopic specTopic) {
        final List<BuildTopic> topics = getBuildTopicsForTopicID(specTopic.getDBId());
        for (final BuildTopic topic : topics) {
            if (topic.getSpecTopic() == specTopic) {
                return topic;
            }
        }

        return null;
    }

    /**
     * Finds the build level for a specific content specification Level.
     *
     * @param level A Level that is used in a BuildLevel
     * @return The build level object that wraps the passed level object.
     */
    public BuildLevel getBuildLevelForSpecLevel(final Level level) {
        final List<BuildLevel> levels = getAllBuildLevels();
        for (final BuildLevel buildLevel : levels) {
            if (buildLevel.getLevel() == level) {
                return buildLevel;
            }
        }

        return null;
    }
}
