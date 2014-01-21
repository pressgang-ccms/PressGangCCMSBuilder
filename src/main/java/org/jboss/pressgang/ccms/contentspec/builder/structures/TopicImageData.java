package org.jboss.pressgang.ccms.contentspec.builder.structures;

import org.jboss.pressgang.ccms.wrapper.TopicWrapper;
import org.jboss.pressgang.ccms.wrapper.base.BaseTopicWrapper;

/**
 * This class is used to map an image referenced inside a topic to the topic
 * itself. This is mostly for error reporting purposes.
 */
public class TopicImageData {
    private BaseTopicWrapper topic = null;
    private String imageName = null;
    private Integer revision = null;

    public BaseTopicWrapper<?> getTopic() {
        return topic;
    }

    public void setTopic(final TopicWrapper topic) {
        this.topic = topic;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(final String imageName) {
        this.imageName = imageName;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(final Integer revision) {
        this.revision = revision;
    }

    public TopicImageData(final BaseTopicWrapper<?> topic, final String imageName) {
        this.topic = topic;
        this.imageName = imageName;
    }

    public TopicImageData(final BaseTopicWrapper<?> topic, final String imageName, final Integer revision) {
        this.topic = topic;
        this.imageName = imageName;
        this.revision = revision;
    }
}