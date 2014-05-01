package org.jboss.pressgang.ccms.contentspec.builder.structures;

public class TranslationDetails {
    private final String translation;
    private final boolean fuzzy;
    private String tagName;

    public TranslationDetails(final String translation, final boolean fuzzy) {
        this.translation = translation;
        this.fuzzy = fuzzy;
        tagName = null;
    }

    public TranslationDetails(final String translation, final boolean fuzzy, final String tagName) {
        this.translation = translation;
        this.fuzzy = fuzzy;
        this.tagName = tagName;
    }

    public boolean isFuzzy() {
        return fuzzy;
    }

    public String getTranslation() {
        return translation;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(final String tagName) {
        this.tagName = tagName;
    }
}
