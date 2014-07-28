/*
  Copyright 2011-2014 Red Hat

  This file is part of PresGang CCMS.

  PresGang CCMS is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  PresGang CCMS is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with PresGang CCMS.  If not, see <http://www.gnu.org/licenses/>.
*/

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
