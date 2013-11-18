package org.jboss.pressgang.ccms.contentspec.builder;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.util.Map;

import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuilderCreationException;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildData;
import org.jboss.pressgang.ccms.contentspec.builder.utils.DocBookBuildUtilities;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.w3c.dom.Document;

public class PublicanDocBookBuilder extends DocBookBuilder {

    public PublicanDocBookBuilder(final DataProviderFactory providerFactory) throws BuilderCreationException {
        super(providerFactory);
    }

    /**
     * Checks if the conditional pass should be done by checking to see if the publican.cfg has it's own condition set. If none are set
     * then the conditions are processed.
     *
     * @param buildData Information and data structures for the build.
     * @param specTopic The spec topic the conditions should be processed for,
     * @param doc       The DOM Document to process the conditions against.
     */
    @Override
    protected void processConditions(final BuildData buildData, final SpecTopic specTopic, final Document doc) {
        if (buildData.getContentSpec().getPublicanCfg() == null || !buildData.getContentSpec().getPublicanCfg().contains("condition:")) {
            super.processConditions(buildData, specTopic, doc);
        }
    }

    @Override
    protected void buildBookAdditions(final BuildData buildData) throws BuildProcessingException {
        super.buildBookAdditions(buildData);

        final String publicanCfg = stringConstantProvider.getStringConstant(
                buildData.getServerEntities().getPublicanCfgStringConstantId()).getValue();

        // Setup publican.cfg
        final String fixedPublicanCfg = buildCorePublicanCfgFile(buildData, publicanCfg);
        addToZip(buildData.getRootBookFolder() + "publican.cfg", fixedPublicanCfg, buildData);

        // Setup the additional publican.cfg files
        buildAdditionalPublicanCfg(buildData);
    }

    /**
     * Builds the core publican.cfg file that is a basic requirement to build the publican book.
     *
     * @param buildData           Information and data structures for the build.
     * @param publicanCfgTemplate The publican.cfg template to add content to.
     * @return The publican.cfg file filled with content from the Content Spec.
     */
    protected String buildCorePublicanCfgFile(final BuildData buildData, final String publicanCfgTemplate) {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, String> overrides = buildData.getBuildOptions().getOverrides();

        final String brandOverride = overrides.containsKey(CSConstants.BRAND_OVERRIDE) ? overrides.get(
                CSConstants.BRAND_OVERRIDE) : (overrides.containsKey(CSConstants.BRAND_ALT_OVERRIDE) ? overrides.get(
                CSConstants.BRAND_ALT_OVERRIDE) : null);
        final String brand = brandOverride != null ? brandOverride : (contentSpec.getBrand() == null ? BuilderConstants.DEFAULT_BRAND :
                contentSpec.getBrand());

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
            publicanCfg += DocBookBuildUtilities.cleanUserPublicanCfg(contentSpec.getPublicanCfg());
        }

        if (buildData.getBuildOptions().getPublicanShowRemarks()) {
            // Remove any current show_remarks definitions
            if (publicanCfg.contains("show_remarks")) {
                publicanCfg = publicanCfg.replaceAll("show_remarks:\\s*\\d+\\s*(\\r)?(\\n)?", "");
            }
            publicanCfg += "show_remarks: 1\n";
        }

        publicanCfg += "docname: " + buildData.getEscapedBookTitle().replaceAll("_", " ") + "\n";
        publicanCfg += "product: " + escapeProduct(buildData.getOriginalBookProduct()) + "\n";

        // Add a version if one wasn't specified
        if (!publicanCfg.contains("version:")) {
            String version = contentSpec.getBookVersion();
            if (isNullOrEmpty(version)) {
                version = contentSpec.getVersion();
            }
            if (isNullOrEmpty(version)) {
                version = BuilderConstants.DEFAULT_VERSION;
            }
            publicanCfg += "version: " + escapeVersion(version) + "\n";
        }

        return applyPublicanCfgOverrides(buildData, publicanCfg);
    }

    /**
     * Builds all of the additional publican.cfg files.
     *
     * @param buildData Information and data structures for the build.
     * @throws BuildProcessingException
     */
    protected void buildAdditionalPublicanCfg(final BuildData buildData) throws BuildProcessingException {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final Map<String, String> overrides = buildData.getBuildOptions().getOverrides();
        final Map<String, String> additionalPublicanCfgs = contentSpec.getAllAdditionalPublicanCfgs();

        for (final Map.Entry<String, String> entry : additionalPublicanCfgs.entrySet()) {
            final String brandOverride = overrides.containsKey(CSConstants.BRAND_OVERRIDE) ? overrides.get(
                    CSConstants.BRAND_OVERRIDE) : (overrides.containsKey(CSConstants.BRAND_ALT_OVERRIDE) ? overrides.get(
                    CSConstants.BRAND_ALT_OVERRIDE) : null);
            final String brand = brandOverride != null ? brandOverride : (contentSpec.getBrand() == null ? BuilderConstants.DEFAULT_BRAND
                    : contentSpec.getBrand());

            // Setup publican.cfg
            final StringBuilder publicanCfg = new StringBuilder("xml_lang: ").append(buildData.getOutputLocale()).append("\n");
            publicanCfg.append("type: ").append(contentSpec.getBookType().toString().replaceAll("-Draft", "") + "\n");
            publicanCfg.append("brand: ").append(brand).append("\n");

            // Add the custom content
            publicanCfg.append(DocBookBuildUtilities.cleanUserPublicanCfg(entry.getValue()));

            // Add docname if it wasn't specified
            if (publicanCfg.indexOf("docname:") == -1) {
                publicanCfg.append("docname: ").append(buildData.getEscapedBookTitle().replaceAll("_", " ")).append("\n");
            }

            // Add product if it wasn't specified
            if (publicanCfg.indexOf("product:") == -1) {
                publicanCfg.append("product: ").append(escapeProduct(buildData.getOriginalBookProduct())).append("\n");
            }

            // Add version if it wasn't specified
            if (publicanCfg.indexOf("version:") == -1) {
                String version = contentSpec.getBookVersion();
                if (isNullOrEmpty(version)) {
                    version = contentSpec.getVersion();
                }
                if (isNullOrEmpty(version)) {
                    version = BuilderConstants.DEFAULT_VERSION;
                }
                publicanCfg.append("version: ").append(escapeVersion(version)).append("\n");
            }

            String fixedPublicanCfg = publicanCfg.toString();

            if (buildData.getBuildOptions().getPublicanShowRemarks()) {
                // Remove any current show_remarks definitions
                if (publicanCfg.indexOf("show_remarks") != -1) {
                    fixedPublicanCfg = fixedPublicanCfg.replaceAll("show_remarks:\\s*\\d+\\s*(\\r)?(\\n)?", "");
                }
                fixedPublicanCfg += "show_remarks: 1\n";
            }

            addToZip(buildData.getRootBookFolder() + entry.getKey(), fixedPublicanCfg, buildData);
        }
    }

    private String escapeProduct(final String product) {
        return product == null ? null : product.replaceAll("[^0-9a-zA-Z_\\-\\.\\+ ]", "");
    }

    private String escapeVersion(final String version) {
        return version == null ? null : version.replaceAll("\\s+", "-");
    }

    /**
     * Applies custom user overrides to the publican.cfg file.
     *
     * @param publicanCfg
     * @return
     */
    private String applyPublicanCfgOverrides(final BuildData buildData, final String publicanCfg) {
        final Map<String, String> publicanCfgOverrides = buildData.getBuildOptions().getPublicanCfgOverrides();
        String retValue = publicanCfg;

        // Loop over each override and remove any entries that may exist and then append the new entry
        for (final Map.Entry<String, String> publicanCfgOverrideEntry : publicanCfgOverrides.entrySet()) {
            retValue = retValue.replaceFirst(publicanCfgOverrideEntry.getKey() + "\\s*:.*?(\\r)?\\n", "");
            retValue += publicanCfgOverrideEntry.getKey() + ": " + publicanCfgOverrideEntry.getValue() + "\n";
        }

        return retValue;
    }
}
