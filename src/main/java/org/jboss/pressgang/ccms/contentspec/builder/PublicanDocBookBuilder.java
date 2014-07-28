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

package org.jboss.pressgang.ccms.contentspec.builder;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.Level;
import org.jboss.pressgang.ccms.contentspec.SpecTopic;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuilderCreationException;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildData;
import org.jboss.pressgang.ccms.contentspec.builder.utils.DocBookBuildUtilities;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.contentspec.enums.LevelType;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.jboss.pressgang.ccms.utils.structures.DocBookVersion;
import org.w3c.dom.Document;

public class PublicanDocBookBuilder extends DocBookBuilder {
    protected static final Pattern DOCNAME_PATTERN = Pattern.compile("(^|\\n)( |\\t)*docname\\s*:\\s*.*($|\\r\\n|\\n)");
    protected static final Pattern PRODUCT_PATTERN = Pattern.compile("(^|\\n)( |\\t)*product\\s*:\\s*.*($|\\r\\n|\\n)");
    protected static final Pattern VERSION_PATTERN = Pattern.compile("(^|\\n)( |\\t)*version\\s*:\\s*.*($|\\r\\n|\\n)");

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
        final String brand = brandOverride != null ? brandOverride : (contentSpec.getBrand() == null ? getDefaultBrand(
                buildData) : contentSpec.getBrand());

        // Setup publican.cfg
        String publicanCfg = publicanCfgTemplate.replaceAll(BuilderConstants.BRAND_REGEX, brand);
        publicanCfg = publicanCfg.replaceFirst("type\\s*:\\s*.*($|\\r\\n|\\n)",
                "type: " + contentSpec.getBookType().toString().replaceAll("-Draft", "") + "\n");
        publicanCfg = publicanCfg.replaceAll("xml_lang\\s*:\\s*.*?($|\\r\\n|\\n)", "xml_lang: " + buildData.getOutputLocale() + "\n");

        // Remove the image width
        publicanCfg = publicanCfg.replaceFirst("max_image_width\\s*:\\s*\\d+\\s*(\\r)?\\n", "");
        publicanCfg = publicanCfg.replaceFirst("toc_section_depth\\s*:\\s*\\d+\\s*(\\r)?\\n", "");

        // Minor formatting cleanup
        publicanCfg = publicanCfg.trim() + "\n";

        // Add the dtdver property
        if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
            publicanCfg += "dtdver: \"5.0\"\n";
        }

        if (contentSpec.getPublicanCfg() != null) {
            // If the user publican.cfg doesn't contain a chunk_section_depth, then add a calculated one
            if (buildData.getBuildOptions().getCalculateChunkDepth() && !contentSpec.getPublicanCfg().contains("chunk_section_depth")) {
                publicanCfg += "chunk_section_depth: " + calcChunkSectionDepth(buildData) + "\n";
            }

            // Remove the git_branch if the content spec contains a git_branch
            if (contentSpec.getPublicanCfg().contains("git_branch")) {
                publicanCfg = publicanCfg.replaceFirst("git_branch\\s*:\\s*.*(\\r)?(\\n)?", "");
            }

            publicanCfg += DocBookBuildUtilities.cleanUserPublicanCfg(contentSpec.getPublicanCfg());
        } else if (buildData.getBuildOptions().getCalculateChunkDepth()) {
            publicanCfg += "chunk_section_depth: " + calcChunkSectionDepth(buildData) + "\n";
        }

        if (buildData.getBuildOptions().getPublicanShowRemarks()) {
            // Remove any current show_remarks definitions
            if (publicanCfg.contains("show_remarks")) {
                publicanCfg = publicanCfg.replaceAll("show_remarks\\s*:\\s*\\d+\\s*(\\r)?(\\n)?", "");
            }
            publicanCfg += "show_remarks: 1\n";
        }

        // Add docname if it wasn't specified
        Matcher m = DOCNAME_PATTERN.matcher(publicanCfg);
        if (!m.find()) {
            publicanCfg += "docname: " + buildData.getEscapedBookTitle().replaceAll("_", " ") + "\n";
        }

        // Add product if it wasn't specified
        m = PRODUCT_PATTERN.matcher(publicanCfg);
        if (!m.find()) {
            publicanCfg += "product: " + escapeProduct(buildData.getOriginalBookProduct()) + "\n";
        }

        // Add the mainfile attribute
        publicanCfg += "mainfile: " + buildData.getEscapedBookTitle() + "\n";

        // Add a version if one wasn't specified
        m = VERSION_PATTERN.matcher(publicanCfg);
        if (!m.find()) {
            String version = contentSpec.getBookVersion();
            if (isNullOrEmpty(version)) {
                version = DocBookBuildUtilities.getKeyValueNodeText(buildData, contentSpec.getVersionNode());
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
            final String brand = brandOverride != null ? brandOverride : (contentSpec.getBrand() == null ? getDefaultBrand(
                    buildData) : contentSpec.getBrand());

            // Setup publican.cfg
            final StringBuilder publicanCfg = new StringBuilder("xml_lang: ").append(buildData.getOutputLocale()).append("\n");
            publicanCfg.append("type: ").append(contentSpec.getBookType().toString().replaceAll("-Draft", "") + "\n");
            publicanCfg.append("brand: ").append(brand).append("\n");

            // Add the custom content
            publicanCfg.append(DocBookBuildUtilities.cleanUserPublicanCfg(entry.getValue()));

            // Add the dtdver property
            if (buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50) {
                publicanCfg.append("dtdver: \"5.0\"\n");
            }

            // Add docname if it wasn't specified
            Matcher m = DOCNAME_PATTERN.matcher(publicanCfg);
            if (!m.find()) {
                publicanCfg.append("docname: ").append(buildData.getEscapedBookTitle().replaceAll("_", " ")).append("\n");
            }

            // Add product if it wasn't specified
            m = PRODUCT_PATTERN.matcher(publicanCfg);
            if (!m.find()) {
                publicanCfg.append("product: ").append(escapeProduct(buildData.getOriginalBookProduct())).append("\n");
            }

            // Add the mainfile attribute
            publicanCfg.append("mainfile: ").append(buildData.getEscapedBookTitle()).append("\n");

            // Add version if it wasn't specified
            m = VERSION_PATTERN.matcher(publicanCfg);
            if (!m.find()) {
                String version = contentSpec.getBookVersion();
                if (isNullOrEmpty(version)) {
                    version = DocBookBuildUtilities.getKeyValueNodeText(buildData, contentSpec.getVersionNode());
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
                    fixedPublicanCfg = fixedPublicanCfg.replaceAll("show_remarks\\s*:\\s*\\d+\\s*(\\r)?(\\n)?", "");
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

    private String getDefaultBrand(final BuildData buildData) {
        return buildData.getDocBookVersion() == DocBookVersion.DOCBOOK_50 ? BuilderConstants.DEFAULT_DB50_BRAND : BuilderConstants
                .DEFAULT_DB45_BRAND;
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

    protected static int calcChunkSectionDepth(final BuildData buildData) {
        final int sectionDepth = calcChunkSectionDepth(buildData.getContentSpec().getBaseLevel(), 0);
        return sectionDepth > 0 ? sectionDepth - 1 : 0;
    }

    protected static int calcChunkSectionDepth(final Level level, int depth) {
        // If this level has no children then it is at its max depth
        if (level.getChildLevels().isEmpty()) return depth;

        int maxDepth = depth;
        for (final Level childLevel : level.getChildLevels()) {
            Integer childDepth = null;
            if (childLevel.getLevelType() == LevelType.SECTION) {
                childDepth = calcChunkSectionDepth(childLevel, depth + 1);
            } else if (childLevel.getLevelType() == LevelType.PROCESS) {
                // Only calc the child level for processes that are sections
                final LevelType parentLevelType = childLevel.getParent().getLevelType();
                if (parentLevelType != LevelType.BASE && parentLevelType != LevelType.PART) {
                    childDepth = calcChunkSectionDepth(childLevel, depth + 1);
                }
            } else {
                childDepth = calcChunkSectionDepth(childLevel, depth);
            }

            if (childDepth != null && childDepth > maxDepth) {
                maxDepth = childDepth;
            }
        }

        return maxDepth;
    }
}
