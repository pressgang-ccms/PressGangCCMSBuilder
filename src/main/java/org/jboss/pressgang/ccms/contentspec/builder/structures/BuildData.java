package org.jboss.pressgang.ccms.contentspec.builder.structures;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.entities.InjectionOptions;
import org.jboss.pressgang.ccms.contentspec.enums.BookType;
import org.jboss.pressgang.ccms.docbook.structures.TopicErrorDatabase;
import org.jboss.pressgang.ccms.docbook.structures.TopicImageData;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.zanata.ZanataDetails;

public class BuildData {
    /**
     * Holds the SpecTopics and their XML that exist within the content specification.
     */
    private final BuildDatabase buildDatabase = new BuildDatabase();
    /**
     * Holds the compiler errors that form the Errors.xml file in the compiled docbook.
     */
    private final TopicErrorDatabase errorDatabase = new TopicErrorDatabase();
    /**
     * The locale that the book should be saved as.
     */
    private final String outputLocale;
    /**
     * The original title of the book before any modifications are done.
     */
    private final String originalTitle;
    /**
     * The original product of the book before any modifications are done.
     */
    private final String originalProduct;
    /**
     * Holds information on file url locations, which will be downloaded and included in the docbook zip file.
     */
    private final List<TopicImageData> imageLocations = new ArrayList<TopicImageData>();

    /**
     * If the build is building a translation or just a normal book
     */
    private boolean translationBuild;
    /**
     * The date of this build.
     */
    private final Date buildDate;

    /**
     * The escaped version of the books title.
     */
    private final String escapedTitle;
    /**
     * The locale the book is to be built in.
     */
    private final String locale;
    /**
     * The Docbook/Formatting Building Options to be used when building.
     */
    private final CSDocbookBuildingOptions buildOptions;
    /**
     * A mapping of override key's to their files.
     */
    private final Map<String, byte[]> overrideFiles = new HashMap<String, byte[]>();
    /**
     * The options that specify what injections are allowed when building.
     */
    private final InjectionOptions injectionOptions = new InjectionOptions();
    /**
     * The details about the zanata server the translated topics exist on.
     */
    private final ZanataDetails zanataDetails;
    /**
     * A specific name for the build to be used in bug links.
     */
    private final String buildName;
    /**
     * The username of the user who requested the build.
     */
    private final String requester;
    /**
     * The Mapping of file names to file contents to be used to build the ZIP archive.
     */
    private final HashMap<String, byte[]> files = new HashMap<String, byte[]>();
    private final ContentSpec contentSpec;

    public BuildData(final String requester, final String buildName, final ContentSpec contentSpec, final String locale,
            final CSDocbookBuildingOptions buildOptions) {
        this(requester, buildName, contentSpec, locale, buildOptions, new ZanataDetails());
    }

    public BuildData(final String requester, final String buildName, final ContentSpec contentSpec, final String locale,
            final CSDocbookBuildingOptions buildOptions, final ZanataDetails zanataDetails) {
        this.contentSpec = contentSpec;
        this.requester = requester;
        this.buildName = buildName;
        this.locale = locale;
        this.buildOptions = buildOptions;
        this.zanataDetails = zanataDetails;
        originalProduct = contentSpec.getProduct();
        originalTitle = contentSpec.getTitle();
        outputLocale = buildOptions.getOutputLocale() == null ? locale : buildOptions.getOutputLocale();
        escapedTitle = DocBookUtilities.escapeTitle(originalTitle);
        buildDate = new Date();

        applyBuildOptionsFromSpec(contentSpec, buildOptions);
        applyInjectionOptionsFromSpec(contentSpec, buildOptions);
    }

    public ZanataDetails getZanataDetails() {
        return zanataDetails;
    }

    public BuildDatabase getBuildDatabase() {
        return buildDatabase;
    }

    public TopicErrorDatabase getErrorDatabase() {
        return errorDatabase;
    }

    public String getOutputLocale() {
        return outputLocale;
    }

    public String getOriginalBookTitle() {
        return originalTitle;
    }

    public String getOriginalBookProduct() {
        return originalProduct;
    }

    public List<TopicImageData> getImageLocations() {
        return imageLocations;
    }

    public boolean isTranslationBuild() {
        return translationBuild;
    }

    public void setTranslationBuild(boolean translationBuild) {
        this.translationBuild = translationBuild;
    }

    public Date getBuildDate() {
        return buildDate;
    }

    public String getEscapedBookTitle() {
        return escapedTitle;
    }

    public String getBuildLocale() {
        return locale;
    }

    public CSDocbookBuildingOptions getBuildOptions() {
        return buildOptions;
    }

    public Map<String, byte[]> getOverrideFiles() {
        return overrideFiles;
    }

    public InjectionOptions getInjectionOptions() {
        return injectionOptions;
    }

    /**
     * Get the root path for the books storage.
     */
    public String getRootBookFolder() {
        return getEscapedBookTitle() + "/";
    }

    /**
     * Get the locale path for the books storage. eg. /{@code<TITLE>}/en-US/
     */
    public String getBookLocaleFolder() {
        return getRootBookFolder() + getOutputLocale() + "/";
    }

    /**
     * Get the path where topics are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/topics/
     */
    public String getBookTopicsFolder() {
        return getBookLocaleFolder() + "topics/";
    }

    /**
     * Get the path where images are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/images/
     */
    public String getBookImagesFolder() {
        return getBookLocaleFolder() + "images/";
    }

    /**
     * Get the path where generic files are to be stored in the books storage. eg. /{@code<TITLE>}/en-US/files/
     */
    public String getBookFilesFolder() {
        return getBookLocaleFolder() + "files/";
    }

    public String getEntityFileName() {
        return getEscapedBookTitle() + ".ent";
    }

    public String getBuildName() {
        return buildName;
    }

    public ContentSpec getContentSpec() {
        return contentSpec;
    }

    protected void applyBuildOptionsFromSpec(final ContentSpec contentSpec, final CSDocbookBuildingOptions buildOptions) {
        /*
         * Apply the build options from the content spec only if the build options are true. We do this so that if the options
         * are turned off earlier then we don't re-enable them.
         */
        if (buildOptions.getInsertSurveyLink()) {
            buildOptions.setInsertSurveyLink(contentSpec.isInjectSurveyLinks());
        }
        if (buildOptions.getForceInjectBugzillaLinks()) {
            buildOptions.setInsertBugzillaLinks(true);
        } else {
            if (buildOptions.getInsertBugzillaLinks()) {
                buildOptions.setInsertBugzillaLinks(contentSpec.isInjectBugLinks());
            }
        }
        if (buildOptions.getBuildName() == null || buildOptions.getBuildName().isEmpty()) {
            buildOptions.setBuildName(
                    (contentSpec.getId() != null ? (contentSpec.getId() + ", ") : "") + contentSpec.getTitle() + "-" + contentSpec
                            .getVersion() + "-" + contentSpec.getEdition());
        }
        if (!buildOptions.getDraft()) {
            if (contentSpec.getBookType() == BookType.ARTICLE_DRAFT || contentSpec.getBookType() == BookType.BOOK_DRAFT) {
                buildOptions.setDraft(true);
            }
        }
    }

    protected void applyInjectionOptionsFromSpec(final ContentSpec contentSpec, final CSDocbookBuildingOptions buildOptions) {
        // Get the injection mode
        InjectionOptions.UserType injectionType = InjectionOptions.UserType.NONE;
        final Boolean injection = buildOptions.getInjection();
        if (injection != null && !injection) {
            injectionType = InjectionOptions.UserType.OFF;
        } else if (injection != null && injection) {
            injectionType = InjectionOptions.UserType.ON;
        }

        // Add the strict injection types
        if (buildOptions.getInjectionTypes() != null) {
            for (final String injectType : buildOptions.getInjectionTypes()) {
                injectionOptions.addStrictTopicType(injectType.trim());
            }
            if (injection != null && injection) {
                injectionType = InjectionOptions.UserType.STRICT;
            }
        }

        // Set the injection mode
        injectionOptions.setClientType(injectionType);

        // Set the injection options for the content spec
        if (contentSpec.getInjectionOptions() != null) {
            injectionOptions.setContentSpecType(contentSpec.getInjectionOptions().getContentSpecType());
            injectionOptions.addStrictTopicTypes(contentSpec.getInjectionOptions().getStrictTopicTypes());
        }
    }

    /**
     *
     *
     * @return
     */
    public HashMap<String, byte[]> getOutputFiles() {
        return files;
    }

    public String getRequester() {
        return requester;
    }
}