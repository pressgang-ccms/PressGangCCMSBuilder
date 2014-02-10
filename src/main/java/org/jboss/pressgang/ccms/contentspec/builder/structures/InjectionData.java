package org.jboss.pressgang.ccms.contentspec.builder.structures;

/**
 * This class represents a topic that was included in a custom injection point.
 */
public class InjectionData {
    /**
     * The topic/target ID
     */
    public String id;
    /**
     * whether this topic was marked as optional
     */
    public boolean optional;

    public InjectionData(final String id, final boolean optional) {
        this.id = id;
        this.optional = optional;
    }
}