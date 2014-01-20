package org.jboss.pressgang.ccms.contentspec.builder.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;

/**
 * Stores information on the errors and warnings that were detected in a topic.
 */
public class TopicErrorData {
    private BaseTopicWrapper<?> topic;
    private Map<TopicErrorDatabase.ErrorLevel, Set<String>> errors = new HashMap<TopicErrorDatabase.ErrorLevel, Set<String>>();
    private List<TopicErrorDatabase.ErrorType> errorTypes = new ArrayList<TopicErrorDatabase.ErrorType>();

    public BaseTopicWrapper<?> getTopic() {
        return topic;
    }

    public void setTopic(final BaseTopicWrapper<?> topic) {
        this.topic = topic;
    }

    public Map<TopicErrorDatabase.ErrorLevel, Set<String>> getErrors() {
        return errors;
    }

    public void setErrors(final Map<TopicErrorDatabase.ErrorLevel, Set<String>> errors) {
        this.errors = errors;
    }

    public void addError(final String item, final TopicErrorDatabase.ErrorLevel level, final TopicErrorDatabase.ErrorType errorType) {
        if (!errors.containsKey(level)) errors.put(level, new HashSet<String>());
        errors.get(level).add(item);

        if (errorType != null) {
            if (!errorTypes.contains(errorType)) errorTypes.add(errorType);
        }
    }

    public boolean hasItemsOfType(final TopicErrorDatabase.ErrorLevel level) {
        return errors.containsKey(level);
    }

    public Set<String> getItemsOfType(final TopicErrorDatabase.ErrorLevel level) {
        if (hasItemsOfType(level)) return errors.get(level);
        return new HashSet<String>();
    }

    public boolean hasErrorType(final TopicErrorDatabase.ErrorType errorType) {
        return errorTypes.contains(errorType);
    }

    /**
     * Checks to see if the Topic has any translation based errors set against it.
     *
     * @return True if there are any errors for the Topic, that are of a translation type.
     */
    public boolean hasTranslationErrors() {
        for (final TopicErrorDatabase.ErrorType type : errorTypes) {
            if (TopicErrorDatabase.TRANSLATION_ERROR_TYPES.contains(type)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks to see if the Topic has any regular errors set against it.
     *
     * @return True if there are any errors for the Topic, that are of a basic/normal type.
     */
    public boolean hasFatalErrors() {
        for (final TopicErrorDatabase.ErrorType type : errorTypes) {
            if (TopicErrorDatabase.FATAL_ERROR_TYPES.contains(type)) {
                return true;
            }
        }

        return false;
    }
}
