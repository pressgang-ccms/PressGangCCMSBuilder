package org.jboss.pressgang.ccms.contentspec.builder.sort;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.jboss.pressgang.ccms.contentspec.SpecNode;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.structures.InjectionData;
import org.jboss.pressgang.ccms.utils.sort.ExternalListSort;

public class NodeTitleSorter implements ExternalListSort<Integer, SpecNode, InjectionData> {
    public void sort(final List<SpecNode> nodes, final List<InjectionData> list) {
        if (nodes == null || list == null) return;

        Collections.sort(list, new Comparator<InjectionData>() {
            public int compare(final InjectionData o1, final InjectionData o2) {
                SpecNode node1 = null;
                SpecNode node2 = null;

                final boolean isTopicId1 = BuilderConstants.TOPIC_ID_PATTERN.matcher(o1.id).matches();
                final boolean isTopicId2 = BuilderConstants.TOPIC_ID_PATTERN.matcher(o2.id).matches();

                // Find the nodes
                for (final SpecNode node : nodes) {
                    if (isTopicId1 && node instanceof SpecTopic) {
                        if (((SpecTopic) node).getDBId().equals(o1.id)) node1 = node;
                    } else if (!isTopicId1) {
                        if (node.getTargetId() != null && node.getTargetId().equals(o1.id)) node1 = node;
                    }
                    if (isTopicId2 && node instanceof SpecTopic) {
                        if (((SpecTopic) node).getDBId().equals(o2.id)) node2 = node;
                    } else if (!isTopicId2) {
                        if (node.getTargetId() != null && node.getTargetId().equals(o2.id)) node2 = node;
                    }

                    if (node1 != null && node2 != null) break;
                }

                final boolean v1Exists = node1 != null;
                final boolean v2Exists = node2 != null;

                if (!v1Exists && !v2Exists) return 0;
                if (!v1Exists) return -1;
                if (!v2Exists) return 1;

                final SpecNode v1 = node1;
                final SpecNode v2 = node2;

                if (v1.getTitle() == null && v2.getTitle() == null) return 0;

                if (v1.getTitle() == null) return -1;

                if (v2.getTitle() == null) return 1;

                return v1.getTitle().toLowerCase().compareTo(v2.getTitle().toLowerCase());
            }
        });
    }
}

