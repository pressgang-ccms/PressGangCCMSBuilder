package org.jboss.pressgang.ccms.contentspec.builder.structures;

import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;
import org.w3c.dom.Document;

public class BuildTopic {

    private final SpecTopic specTopic;
    private BaseTopicWrapper<?> topic = null;
    private Document xml = null;
    private String duplicateId = null;

    public BuildTopic(final SpecTopic specTopic) {
        assert specTopic != null;

        this.specTopic = specTopic;
    }

    public SpecTopic getSpecTopic() {
        return specTopic;
    }

    public Document getXMLDocument() {
        return xml;
    }

    public void setXMLDocument(final Document xml) {
        this.xml = xml;
    }

    public BaseTopicWrapper<?> getTopic() {
        return topic;
    }

    public void setTopic(final BaseTopicWrapper<?> topic) {
        this.topic = topic;
    }

    public String getUniqueLinkId(final boolean useFixedUrls) {
        final String topicXRefId;
        if (useFixedUrls) topicXRefId = topic.getXRefPropertyOrId(CommonConstants.FIXED_URL_PROP_TAG_ID);
        else {
            topicXRefId = topic.getXRefId();
        }

        return topicXRefId + (duplicateId == null ? "" : ("-" + duplicateId));
    }

    public String getDuplicateId() {
        return duplicateId;
    }

    public void setDuplicateId(final String duplicateId) {
        this.duplicateId = duplicateId;
    }
}
