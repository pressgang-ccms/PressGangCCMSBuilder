package org.jboss.pressgang.ccms.contentspec.builder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Map;

import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.builder.constants.BuilderConstants;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuilderCreationException;
import org.jboss.pressgang.ccms.contentspec.builder.structures.BuildData;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.utils.common.FileUtilities;
import org.jboss.pressgang.ccms.wrapper.BlobConstantWrapper;

public class JDocbookBuilder extends DocbookBuilder {
    public JDocbookBuilder(final DataProviderFactory providerFactory, final BlobConstantWrapper rocbookDtd,
            final String defaultLocale) throws BuilderCreationException {
        super(providerFactory, rocbookDtd, defaultLocale);
    }

    @Override
    protected void buildBookAdditions(final BuildData buildData, final boolean useFixedUrls) throws BuildProcessingException {
        super.buildBookAdditions(buildData, useFixedUrls);

        final Map<String, String> overrides = buildData.getBuildOptions().getOverrides();

        // Add any common content files that need to be included locally
        addPublicanCommonContentToBook(buildData);

        // Add the pom.xml file for the maven build
        if (overrides.containsKey(CSConstants.POM_OVERRIDE)) {
            final File pom = new File(overrides.get(CSConstants.POM_OVERRIDE));
            if (pom.exists() && pom.isFile()) {
                try {
                    final FileInputStream fis = new FileInputStream(pom);
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                    final StringBuilder buffer = new StringBuilder();
                    String line = "";
                    while ((line = reader.readLine()) != null) {
                        buffer.append(line + "\n");
                    }

                    // Add the parsed file to the book
                    addToZip(buildData.getRootBookFolder() + "pom.xml", buffer.toString(), buildData);
                } catch (Exception e) {
                    log.error(e);
                    buildPom(buildData);
                }
            } else {
                log.error("pom.xml override is an invalid file. Using the default pom.xml instead.");
                buildPom(buildData);
            }
        } else {
            buildPom(buildData);
        }
    }

    /**
     * Adds the Publican Common_Content files specified by the brand, locale and directory location build options . If the
     * Common_Content files don't exist at the directory, brand and locale specified then the "common" brand will be used
     * instead. If the file still don't exist then the files are skipped and will rely on XML XI Include Fallbacks.
     *
     * @param buildData
     */
    protected void addPublicanCommonContentToBook(final BuildData buildData) throws BuildProcessingException {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final String commonContentLocale = buildData.getOutputLocale();
        final String commonContentDirectory = buildData.getBuildOptions().getCommonContentDirectory() == null ? BuilderConstants
                .LINUX_PUBLICAN_COMMON_CONTENT : buildData.getBuildOptions().getCommonContentDirectory();

        final String brand = contentSpec.getBrand() == null ? BuilderConstants.DEFAULT_BRAND : contentSpec.getBrand();

        final String brandDir = commonContentDirectory + (commonContentDirectory.endsWith(
                "/") ? "" : "/") + brand + File.separator + commonContentLocale + File.separator;
        final String commonBrandDir = commonContentDirectory + (commonContentDirectory.endsWith(
                "/") ? "" : "/") + BuilderConstants.DEFAULT_BRAND + File.separator + commonContentLocale + File.separator;
        final String commonEnglishBrandDir = commonContentDirectory + (commonContentDirectory.endsWith(
                "/") ? "" : "/") + BuilderConstants.DEFAULT_BRAND + File.separator + "en-US" + File.separator;

        /*
         * We need to pull the Conventions.xml, Feedback.xml & Legal_Notice.xml from the publican Common_Content directory.
         * First we need to check if the files exist for the brand, if they don't then we need to check the common directory,
         * starting with the specified locale and defaulting to english.
         */
        for (final String fileName : BuilderConstants.COMMON_CONTENT_FILES) {
            final File brandFile = new File(brandDir + fileName);

            // Find the required file
            final String file;
            if (brandFile.exists() && brandFile.isFile()) {
                file = FileUtilities.readFileContents(brandFile);
            } else {
                final File commonBrandFile = new File(commonBrandDir + fileName);
                if (commonBrandFile.exists() && commonBrandFile.isFile()) {
                    file = FileUtilities.readFileContents(commonBrandFile);
                } else {
                    final File commonEnglishBrandFile = new File(commonEnglishBrandDir + fileName);
                    if (commonEnglishBrandFile.exists() && commonEnglishBrandFile.isFile()) {
                        file = FileUtilities.readFileContents(commonEnglishBrandFile);
                    } else {
                        continue;
                    }
                }
            }

            if (file != null) {
                // Set the root element name based on the file
                final String rootElementName;
                if (fileName.equals("Program_Listing.xml")) {
                    rootElementName = "programlisting";
                } else if (fileName.equals("Legal_Notice.xml")) {
                    rootElementName = "legalnotice";
                } else {
                    rootElementName = "section";
                }

                // Fix the Doctype
                final String entityFileName = "../" + buildData.getEscapedBookTitle() + ".ent";
                final String fixedFile = DocBookUtilities.addDocbook45XMLDoctype(file, entityFileName, rootElementName);

                // Add the file to the book
                addToZip(buildData.getBookLocaleFolder() + "Common_Content/" + fileName, fixedFile, buildData);
            }
        }
    }

    protected void buildPom(final BuildData buildData) throws BuildProcessingException {
        final ContentSpec contentSpec = buildData.getContentSpec();
        final String outputLocale = buildData.getOutputLocale();
        final String escapedTitle = buildData.getEscapedBookTitle();
        final String originalProduct = buildData.getOriginalBookProduct();

        // Get the template from the server
        String pomXML = stringConstantProvider.getStringConstant(BuilderConstants.POM_XML_ID).getValue();

        // Replace the generic template values
        pomXML = pomXML.replaceFirst("<translation>.*</translation>", "<translation>" + outputLocale + "</translation>").replaceFirst(
                "<docname>.*</docname>", "<docname>" + escapedTitle + "</docname>").replaceFirst("<docproduct>.*</docproduct>",
                "<docproduct>" + DocBookUtilities.escapeTitle(originalProduct) +
                        "</docproduct>").replaceFirst("<bookname>.*</bookname>",
                "<bookname>" + contentSpec.getTitle() + "</bookname>").replaceFirst("<bookproduct>.*</bookproduct>",
                "<bookproduct>" + contentSpec.getProduct() + "</bookproduct>");

        // Change the GroupId
        final String groupId;
        if (contentSpec.getGroupId() != null) {
            groupId = contentSpec.getGroupId().replace("$", "\\$");
        } else {
            groupId = DocBookUtilities.escapeTitle(originalProduct).replace("_", "-").toLowerCase();
        }
        pomXML = pomXML.replaceFirst("<groupId>.*</groupId>", "<groupId>" + groupId + "</groupId>");

        // Change the ArtifactId
        final String artifactId;
        if (contentSpec.getArtifactId() != null) {
            artifactId = contentSpec.getArtifactId().replace("$", "\\$");
        } else {
            artifactId = escapedTitle.toLowerCase().replace("_", "-") + "-" + outputLocale;
        }
        pomXML = pomXML.replaceFirst("<artifactId>.*</artifactId>", "<artifactId>" + artifactId + "</artifactId>");

        // Change the Version
        pomXML = pomXML.replaceFirst("<version>.*</version>", "<version>" + contentSpec.getVersion() + "-SNAPSHOT</version>");

        addToZip(buildData.getRootBookFolder() + "pom.xml", pomXML, buildData);
    }

    @Override
    protected String buildBookInfoFile(final BuildData buildData, final String bookInfoTemplate) {
        final String bookInfo = super.buildBookInfoFile(buildData, bookInfoTemplate);

        // Remove the corpauthor from the book
        return bookInfo.replaceAll("<corpauthor>(.|\r|\n)*</corpauthor>", "");
    }
}
