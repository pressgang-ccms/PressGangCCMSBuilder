package org.jboss.pressgang.ccms.contentspec.builder.structures;

import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;

public class BuildLevel {

    private final Level level;
    protected String duplicateId = null;

    public BuildLevel(final Level level) {
        assert level != null;

        this.level = level;
    }

    public Level getLevel() {
        return level;
    }

    public String getUniqueLinkId(final boolean useFixedUrls) {
        // Get the pre link string
        final String preFix;
        switch (level.getType()) {
            case APPENDIX:
                preFix = "appe-";
                break;
            case SECTION:
                preFix = "sect-";
                break;
            case PROCESS:
                preFix = "proc-";
                break;
            case CHAPTER:
                preFix = "chap-";
                break;
            case PART:
                preFix = "part-";
                break;
            default:
                preFix = "";
        }

        // Get the xref id
        final String levelXRefId;
        if (useFixedUrls) {
            levelXRefId = DocBookUtilities.escapeTitle(level.getTitle());
        } else {
            levelXRefId = "ChapterID" + level.getStep();
        }

        return preFix + levelXRefId + (duplicateId == null ? "" : ("-" + duplicateId));
    }

    public String getDuplicateId() {
        return duplicateId;
    }

    public void setDuplicateId(final String duplicateId) {
        this.duplicateId = duplicateId;
    }
}
