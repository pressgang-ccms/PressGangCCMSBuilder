/*
  Copyright 2011-2014 Red Hat, Inc

  This file is part of PressGang CCMS.

  PressGang CCMS is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  PressGang CCMS is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with PressGang CCMS.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.jboss.pressgang.ccms.contentspec.builder.structures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;

/**
 * Provides a central location for storing and adding messages that are
 * generated while compiling to docbook.
 */
public class TopicErrorDatabase {
    public static enum ErrorLevel {ERROR, WARNING}

    public static enum ErrorType {
        NO_CONTENT, INVALID_INJECTION, POSSIBLE_INVALID_INJECTION, INVALID_CONTENT, UNTRANSLATED,
        NOT_PUSHED_FOR_TRANSLATION, INCOMPLETE_TRANSLATION, INVALID_IMAGES, OLD_TRANSLATION, OLD_UNTRANSLATED, FUZZY_TRANSLATION
    }

    public static final List<ErrorType> TRANSLATION_ERROR_TYPES = Arrays.asList(ErrorType.OLD_TRANSLATION, ErrorType.OLD_UNTRANSLATED,
            ErrorType.INCOMPLETE_TRANSLATION, ErrorType.NOT_PUSHED_FOR_TRANSLATION, ErrorType.FUZZY_TRANSLATION, ErrorType.UNTRANSLATED);

    public static final List<ErrorType> FATAL_ERROR_TYPES = Arrays.asList(ErrorType.NO_CONTENT, ErrorType.INVALID_INJECTION,
            ErrorType.INVALID_CONTENT);

    private Map<String, List<TopicErrorData>> errors = new HashMap<String, List<TopicErrorData>>();

    public int getErrorCount(final String locale) {
        return errors.containsKey(locale) ? errors.get(locale).size() : 0;
    }

    public boolean hasItems() {
        return errors.size() != 0;
    }

    public boolean hasItems(final String locale) {
        return errors.containsKey(locale) ? errors.get(locale).size() != 0 : false;
    }

    public void addError(final BaseTopicWrapper<?> topic, final ErrorType errorType, final String error) {
        addItem(topic, error, ErrorLevel.ERROR, errorType);
    }

    public void addWarning(final BaseTopicWrapper<?> topic, final ErrorType errorType, final String error) {
        addItem(topic, error, ErrorLevel.WARNING, errorType);
    }

    public void addError(final BaseTopicWrapper<?> topic, final String error) {
        addItem(topic, error, ErrorLevel.ERROR, null);
    }

    public void addWarning(final BaseTopicWrapper<?> topic, final String error) {
        addItem(topic, error, ErrorLevel.WARNING, null);
    }

    /**
     * Add a error for a topic that was included in the TOC
     *
     * @param topic
     * @param error
     */
    public void addTocError(final BaseTopicWrapper<?> topic, final ErrorType errorType, final String error) {
        addItem(topic, error, ErrorLevel.ERROR, errorType);
    }

    public void addTocWarning(final BaseTopicWrapper<?> topic, final ErrorType errorType, final String error) {
        addItem(topic, error, ErrorLevel.WARNING, errorType);
    }

    private void addItem(final BaseTopicWrapper<?> topic, final String item, final ErrorLevel errorLevel, final ErrorType errorType) {
        final TopicErrorData topicErrorData = addOrGetTopicErrorData(topic);        /* don't add duplicates */
        if (!(topicErrorData.getErrors().containsKey(errorLevel) && topicErrorData.getErrors().get(errorLevel).contains(item)))
            topicErrorData.addError(item, errorLevel, errorType);
    }

    public boolean hasErrorData(final BaseTopicWrapper<?> topic) {
        for (final String locale : errors.keySet())
            for (final TopicErrorData topicErrorData : errors.get(locale)) {
                if (topicErrorData.getTopic().getTopicId().equals(topic.getTopicId())) return true;
            }
        return false;
    }

    public TopicErrorData getErrorData(final BaseTopicWrapper<?> topic) {
        for (final String locale : errors.keySet())
            for (final TopicErrorData topicErrorData : errors.get(locale)) {
                if (topicErrorData.getTopic().getTopicId().equals(topic.getTopicId())) return topicErrorData;
            }
        return null;
    }

    private TopicErrorData addOrGetTopicErrorData(final BaseTopicWrapper<?> topic) {
        TopicErrorData topicErrorData = getErrorData(topic);
        if (topicErrorData == null) {
            topicErrorData = new TopicErrorData();
            topicErrorData.setTopic(topic);
            if (!errors.containsKey(topic.getLocale().getValue())) {
                errors.put(topic.getLocale().getValue(), new ArrayList<TopicErrorData>());
            }
            errors.get(topic.getLocale().getValue()).add(topicErrorData);
        }
        return topicErrorData;
    }

    public List<String> getLocales() {
        return CollectionUtilities.toArrayList(errors.keySet());
    }

    public List<TopicErrorData> getErrors(final String locale) {
        return errors.containsKey(locale) ? errors.get(locale) : null;
    }

    public List<TopicErrorData> getErrorsOfType(final String locale, final ErrorType errorType) {
        final List<TopicErrorData> localeErrors = errors.containsKey(locale) ? errors.get(locale) : new ArrayList<TopicErrorData>();

        final List<TopicErrorData> typeErrorDatas = new ArrayList<TopicErrorData>();
        for (final TopicErrorData errorData : localeErrors) {
            if (errorData.hasErrorType(errorType)) typeErrorDatas.add(errorData);
        }

        return typeErrorDatas;
    }

    public void setErrors(final String locale, final List<TopicErrorData> errors) {
        this.errors.put(locale, errors);
    }
}
