package org.jboss.pressgang.ccms.contentspec.builder;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuilderCreationException;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildData;
import org.jboss.pressgang.ccms.contentspec.builder.utils.DocbookBuildUtilities;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.jboss.pressgang.ccms.wrapper.BlobConstantWrapper;

public class PublicanDocbookBuilder extends DocbookBuilder {

    public PublicanDocbookBuilder(final DataProviderFactory providerFactory, final BlobConstantWrapper rocbookDtd,
            final String defaultLocale) throws BuilderCreationException {
        super(providerFactory, rocbookDtd, defaultLocale);
    }

    @Override
    protected void buildBookAdditions(final BuildData buildData) throws BuildProcessingException {
        super.buildBookAdditions(buildData);

        final String publicanCfg = stringConstantProvider.getStringConstant(BuilderConstants.PUBLICAN_CFG_ID).getValue();

        // Setup publican.cfg
        final String fixedPublicanCfg = buildPublicanCfgFile(buildData, publicanCfg);
        try {
            buildData.getOutputFiles().put(buildData.getRootBookFolder() + "publican.cfg", fixedPublicanCfg.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error(e);
        }
    }

    /**
     * Builds the publican.cfg file that is a basic requirement to build the publican book.
     *
     * @param buildData           Information and data structures for the build.
     * @param publicanCfgTemplate The publican.cfg template to add content to.
     * @return The publican.cfg file filled with content from the Content Spec.
     */
    protected String buildPublicanCfgFile(final BuildData buildData, final String publicanCfgTemplate) {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, String> overrides = buildData.getBuildOptions().getOverrides();

        final String brand = overrides.containsKey(CSConstants.BRAND_OVERRIDE) ? overrides.get(
                CSConstants.BRAND_OVERRIDE) : (contentSpec.getBrand() == null ? BuilderConstants.DEFAULT_BRAND : contentSpec.getBrand());

        // Setup publican.cfg
        String publicanCfg = publicanCfgTemplate.replaceAll(BuilderConstants.BRAND_REGEX, brand);
        publicanCfg = publicanCfg.replaceFirst("type\\:\\s*.*($|\\r\\n|\\n)",
                "type: " + contentSpec.getBookType().toString().replaceAll("-Draft", "") + "\n");
        publicanCfg = publicanCfg.replaceAll("xml_lang\\:\\s*.*?($|\\r\\n|\\n)", "xml_lang: " + buildData.getOutputLocale() + "\n");
        if (!publicanCfg.matches(".*\n$")) {
            publicanCfg += "\n";
        }

        // Remove the image width
        publicanCfg = publicanCfg.replaceFirst("max_image_width:\\s*\\d+\\s*(\\r)?\\n", "");
        publicanCfg = publicanCfg.replaceFirst("toc_section_depth:\\s*\\d+\\s*(\\r)?\\n", "");

        if (contentSpec.getPublicanCfg() != null) {
            // Remove the git_branch if the content spec contains a git_branch
            if (contentSpec.getPublicanCfg().contains("git_branch")) {
                publicanCfg = publicanCfg.replaceFirst("git_branch:\\s*.*(\\r)?(\\n)?", "");
            }
            publicanCfg += DocbookBuildUtilities.cleanUserPublicanCfg(contentSpec.getPublicanCfg());

            if (!publicanCfg.matches(".*\n$")) {
                publicanCfg += "\n";
            }
        }

        if (buildData.getBuildOptions().getPublicanShowRemarks()) {
            // Remove any current show_remarks definitions
            if (publicanCfg.contains("show_remarks")) {
                publicanCfg = publicanCfg.replaceAll("show_remarks:\\s*\\d+\\s*(\\r)?(\\n)?", "");
            }
            publicanCfg += "show_remarks: 1\n";
        }

        publicanCfg += "docname: " + buildData.getEscapedBookTitle().replaceAll("_", " ") + "\n";
        publicanCfg += "product: " + buildData.getOriginalBookProduct() + "\n";

        if (buildData.getBuildOptions().getCvsPkgOption() != null) {
            publicanCfg += "cvs_pkg: " + buildData.getBuildOptions().getCvsPkgOption() + "\n";
        }

        // Add a version if one wasn't specified
        if ((contentSpec.getVersion() == null || contentSpec.getVersion().isEmpty()) && !publicanCfg.contains("version:")) {
            String version = contentSpec.getBookVersion() != null ? contentSpec.getBookVersion() : BuilderConstants.DEFAULT_VERSION;
            publicanCfg += "version: " + version + "\n";
        }

        return publicanCfg;
    }
}
