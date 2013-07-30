package com.redhat.contentspec.builder;

import java.util.Map;

import com.redhat.contentspec.builder.constants.BuilderConstants;
import com.redhat.contentspec.builder.exception.BuildProcessingException;
import com.redhat.contentspec.builder.exception.BuilderCreationException;
import com.redhat.contentspec.builder.utils.DocbookBuildUtilities;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.contentspec.rest.RESTManager;
import org.jboss.pressgang.ccms.rest.v1.collections.base.RESTBaseCollectionItemV1;
import org.jboss.pressgang.ccms.rest.v1.collections.base.RESTBaseCollectionV1;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTBlobConstantV1;
import org.jboss.pressgang.ccms.rest.v1.entities.base.RESTBaseTopicV1;

public class PublicanDocbookBuilder<T extends RESTBaseTopicV1<T, U, V>, U extends RESTBaseCollectionV1<T, U, V>,
        V extends RESTBaseCollectionItemV1<T, U, V>> extends DocbookBuilder<T, U, V> {

    public PublicanDocbookBuilder(final RESTManager restManager, final RESTBlobConstantV1 rocbookDtd,
            final String defaultLocale) throws BuilderCreationException {
        super(restManager, rocbookDtd, defaultLocale);
    }

    @Override
    protected void buildBookAdditions(final ContentSpec contentSpec, final String requester, final boolean useFixedUrls,
            final Map<String, byte[]> files) throws BuildProcessingException {
        super.buildBookAdditions(contentSpec, requester, useFixedUrls, files);

        final String publicanCfg = restManager.getRESTClient().getJSONStringConstant(BuilderConstants.PUBLICAN_CFG_ID,
                "").getValue();

        // Setup publican.cfg
        final String fixedPublicanCfg = buildPublicanCfgFile(publicanCfg, contentSpec);
        addToFilesZip(BOOK_FOLDER + "publican.cfg", fixedPublicanCfg, files);
    }

    /**
     * Builds the publican.cfg file that is a basic requirement to build the publican book.
     *
     * @param publicanCfgTemplate The publican.cfg template to add content to.
     * @param contentSpec         The content specification object to be built.
     * @return The publican.cfg file filled with content from the Content Spec.
     */
    protected String buildPublicanCfgFile(final String publicanCfgTemplate, final ContentSpec contentSpec) {
        final Map<String, String> overrides = docbookBuildingOptions.getOverrides();

        final String brand = overrides.containsKey(CSConstants.BRAND_OVERRIDE) ? overrides.get(
                CSConstants.BRAND_OVERRIDE) : (contentSpec.getBrand() == null ? BuilderConstants.DEFAULT_BRAND : contentSpec.getBrand());

        // Setup publican.cfg
        String publicanCfg = publicanCfgTemplate.replaceAll(BuilderConstants.BRAND_REGEX, brand);
        publicanCfg = publicanCfg.replaceFirst("type\\:\\s*.*($|\\r\\n|\\n)",
                "type: " + contentSpec.getBookType().toString().replaceAll("-Draft", "") + "\n");
        publicanCfg = publicanCfg.replaceAll("xml_lang\\:\\s*.*?($|\\r\\n|\\n)", "xml_lang: " + outputLocale + "\n");
        if (!publicanCfg.matches(".*\n$")) {
            publicanCfg += "\n";
        }

        // Remove the image width for CSP output
        publicanCfg = publicanCfg.replaceFirst("max_image_width:\\s*\\d+\\s*(\\r)?\\n", "");
        publicanCfg = publicanCfg.replaceFirst("toc_section_depth:\\s*\\d+\\s*(\\r)?\\n", "");

        if (contentSpec.getPublicanCfg() != null) {
            // Remove the git_branch if the content spec contains a git_branch
            if (contentSpec.getPublicanCfg().indexOf("git_branch") != -1) {
                publicanCfg = publicanCfg.replaceFirst("git_branch:\\s*.*(\\r)?(\\n)?", "");
            }

            // Add the cleaned user defined publican.cfg
            publicanCfg += DocbookBuildUtilities.cleanUserPublicanCfg(contentSpec.getPublicanCfg());

            if (!publicanCfg.matches(".*\n$")) {
                publicanCfg += "\n";
            }
        }

        if (docbookBuildingOptions.getPublicanShowRemarks()) {
            // Remove any current show_remarks definitions
            if (publicanCfg.indexOf("show_remarks") != -1) {
                publicanCfg = publicanCfg.replaceAll("show_remarks:\\s*\\d+\\s*(\\r)?(\\n)?", "");
            }
            publicanCfg += "show_remarks: 1\n";
        }

        publicanCfg += "docname: " + escapedTitle.replaceAll("_", " ") + "\n";
        publicanCfg += "product: " + originalProduct + "\n";

        if (docbookBuildingOptions.getCvsPkgOption() != null) {
            publicanCfg += "cvs_pkg: " + docbookBuildingOptions.getCvsPkgOption() + "\n";
        }

        // Add a version if one wasn't specified
        if ((contentSpec.getVersion() == null || contentSpec.getVersion().isEmpty()) && !publicanCfg.contains("version:")) {
            String version = contentSpec.getBookVersion() != null ? contentSpec.getBookVersion() : BuilderConstants.DEFAULT_VERSION;
            publicanCfg += "version: " + version + "\n";
        }

        return publicanCfg;
    }
}
