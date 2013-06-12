package com.redhat.contentspec.builder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import com.redhat.contentspec.builder.constants.BuilderConstants;
import com.redhat.contentspec.builder.exception.BuildProcessingException;
import com.redhat.contentspec.builder.exception.BuilderCreationException;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.constants.CSConstants;
import org.jboss.pressgang.ccms.contentspec.rest.RESTManager;
import org.jboss.pressgang.ccms.rest.v1.collections.base.RESTBaseCollectionItemV1;
import org.jboss.pressgang.ccms.rest.v1.collections.base.RESTBaseCollectionV1;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTBlobConstantV1;
import org.jboss.pressgang.ccms.rest.v1.entities.base.RESTBaseTopicV1;
import org.jboss.pressgang.ccms.utils.common.DocBookUtilities;
import org.jboss.pressgang.ccms.utils.common.FileUtilities;

public class JDocbookBuilder<T extends RESTBaseTopicV1<T, U, V>, U extends RESTBaseCollectionV1<T, U, V>,
        V extends RESTBaseCollectionItemV1<T, U, V>> extends DocbookBuilder<T, U, V> {
    public JDocbookBuilder(RESTManager restManager, RESTBlobConstantV1 rocbookDtd, String defaultLocale) throws BuilderCreationException {
        super(restManager, rocbookDtd, defaultLocale);
    }

    @Override
    protected void buildBookAdditions(final ContentSpec contentSpec, final String requester,
            final Map<String, byte[]> files) throws BuildProcessingException {
        super.buildBookAdditions(contentSpec, requester, files);

        final Map<String, String> overrides = docbookBuildingOptions.getOverrides();

        // Add any common content files that need to be included locally
        final String commonContentDirectory = docbookBuildingOptions.getCommonContentDirectory() == null ? BuilderConstants
                .LINUX_PUBLICAN_COMMON_CONTENT : docbookBuildingOptions.getCommonContentDirectory();
        addPublicanCommonContentToBook(contentSpec, outputLocale, commonContentDirectory, files);

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
                    files.put(BOOK_FOLDER + "pom.xml", buffer.toString().getBytes(ENCODING));
                } catch (Exception e) {
                    log.error(e);
                    buildPom(contentSpec, outputLocale, files);
                }
            } else {
                log.error("pom.xml override is an invalid file. Using the default pom.xml instead.");
                buildPom(contentSpec, outputLocale, files);
            }
        } else {
            buildPom(contentSpec, outputLocale, files);
        }
    }

    /**
     * Adds the Publican Common_Content files specified by the brand, locale and directory location build options . If the
     * Common_Content files don't exist at the directory, brand and locale specified then the "common" brand will be used
     * instead. If the file still don't exist then the files are skipped and will rely on XML XI Include Fallbacks.
     *
     * @param contentSpec            The Content Spec that is used to build the book.
     * @param commonContentLocale    The Common_Content Locale to be used.
     * @param commonContentDirectory The Common_Content directory.
     * @param files                  The Mapping of file names to file contents to be used to build the ZIP archive.
     */
    protected void addPublicanCommonContentToBook(final ContentSpec contentSpec, final String commonContentLocale,
            final String commonContentDirectory, final Map<String, byte[]> files) {
        final String brand = contentSpec.getBrand() == null ? BuilderConstants.DEFAULT_BRAND : contentSpec.getBrand();

        final String brandDir = commonContentDirectory + (commonContentDirectory.endsWith(
                "/") ? "" : "/") + brand + File.separator + commonContentLocale + File.separator;
        final String commonBrandDir = commonContentDirectory + (commonContentDirectory.endsWith(
                "/") ? "" : "/") + BuilderConstants.DEFAULT_BRAND + File.separator + commonContentLocale + File.separator;

        /*
         * We need to pull the Conventions.xml, Feedback.xml & Legal_Notice.xml from the publican Common_Content directory.
         * First we need to check if the files exist for the brand, if they don't then we need to check the common directory.
         */

        for (final String fileName : BuilderConstants.COMMON_CONTENT_FILES) {
            final File brandFile = new File(brandDir + fileName);

            try {
                final String file;
                if (brandFile.exists() && brandFile.isFile()) {
                    file = FileUtilities.readFileContents(brandFile);
                } else {
                    final File commonBrandFile = new File(commonBrandDir + fileName);
                    if (commonBrandFile.exists() && commonBrandFile.isFile()) {
                        file = FileUtilities.readFileContents(commonBrandFile);
                    } else {
                        continue;
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
                    final String entityFileName = "../" + escapedTitle + ".ent";
                    final String fixedFile = DocBookUtilities.addDocbook45XMLDoctype(file, entityFileName, rootElementName);

                    // Add the file to the book
                    files.put(BOOK_LOCALE_FOLDER + "Common_Content/" + fileName, fixedFile.getBytes(ENCODING));
                }
            } catch (UnsupportedEncodingException e) {
                log.debug(e.getMessage());
            }
        }
    }

    protected void buildPom(final ContentSpec contentSpec, final String outputLocale, final Map<String, byte[]> files) {
        String pomXML = restManager.getRESTClient().getJSONStringConstant(BuilderConstants.POM_XML_ID, "").getValue();

        pomXML = pomXML.replaceFirst("<translation>.*</translation>", "<translation>" + outputLocale + "</translation>")
                .replaceFirst("<docname>.*</docname>", "<docname>" + escapedTitle + "</docname>")
                .replaceFirst("<docproduct>.*</docproduct>", "<docproduct>" + DocBookUtilities.escapeTitle(originalProduct) +
                        "</docproduct>")
                .replaceFirst("<bookname>.*</bookname>", "<bookname>" + contentSpec.getTitle() + "</bookname>")
                .replaceFirst("<bookproduct>.*</bookproduct>", "<bookproduct>" + contentSpec.getProduct() + "</bookproduct>");

        // Change the GroupId
        final String groupId;
        if (contentSpec.getGroupId() != null) {
            groupId = contentSpec.getGroupId();
        } else {
            groupId = DocBookUtilities.escapeTitle(originalProduct).replace("_", "-").toLowerCase();
        }
        pomXML = pomXML.replaceFirst("<groupId>.*</groupId>", "<groupId>" + groupId + "</groupId>");

        // Change the ArtifactId
        final String artifactId;
        if (contentSpec.getArtifactId() != null) {
            artifactId = contentSpec.getArtifactId();
        } else {
            artifactId = escapedTitle.toLowerCase().replace("_", "-") + "-" + outputLocale;
        }
        pomXML = pomXML.replaceFirst("<artifactId>.*</artifactId>", "<artifactId>" + artifactId + "</artifactId>");

        // Change the Version
        pomXML = pomXML.replaceFirst("<version>.*</version>", "<version>" + contentSpec.getVersion() + "-SNAPSHOT</version>");

        try {
            files.put(BOOK_FOLDER + "pom.xml", pomXML.getBytes(ENCODING));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is a valid format so this should exception should never get thrown
            log.error(e);
        }
    }

    @Override
    protected String buildBookInfoFile(final String bookInfoTemplate, final ContentSpec contentSpec) {
        final String bookInfo = super.buildBookInfoFile(bookInfoTemplate, contentSpec);

        // Remove the corpauthor from the book
        return bookInfo.replaceAll("<corpauthor>(.|\r|\n)*</corpauthor>", "");
    }
}
