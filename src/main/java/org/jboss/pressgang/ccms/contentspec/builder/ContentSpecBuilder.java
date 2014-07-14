package org.jboss.pressgang.ccms.contentspec.builder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuildProcessingException;
import org.jboss.pressgang.ccms.contentspec.builder.exception.BuilderCreationException;
import org.jboss.pressgang.ccms.contentspec.interfaces.ShutdownAbleApp;
import org.jboss.pressgang.ccms.contentspec.builder.structures.DocBookBuildingOptions;
import org.jboss.pressgang.ccms.provider.DataProviderFactory;
import org.jboss.pressgang.ccms.utils.common.ZipUtilities;
import org.jboss.pressgang.ccms.zanata.ZanataDetails;

/**
 * A class that provides the ability to build a book from content specifications.
 *
 * @author lnewson
 */
public class ContentSpecBuilder implements ShutdownAbleApp {
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final DataProviderFactory providerFactory;
    private DocBookBuilder docbookBuilder;

    public ContentSpecBuilder(final DataProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @Override
    public void shutdown() {
        isShuttingDown.set(true);
        docbookBuilder.shutdown();
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    public int getNumWarnings() {
        return docbookBuilder == null ? 0 : docbookBuilder.getNumWarnings();
    }

    public int getNumErrors() {
        return docbookBuilder == null ? 0 : docbookBuilder.getNumErrors();
    }

    /**
     * Builds a book into a zip file for the passed Content Specification.
     *
     * @param contentSpec    The content specification that is to be built. It should have already been validated, if not errors may occur.
     * @param requester      The user who requested the book to be built.
     * @param builderOptions The set of options what are to be when building the book.
     * @param buildType
     * @return A byte array that is the zip file
     * @throws BuildProcessingException Any unexpected errors that occur during building.
     * @throws BuilderCreationException Any error that occurs while trying to setup/create the builder
     */
    public byte[] buildBook(final ContentSpec contentSpec, final String requester, final DocBookBuildingOptions builderOptions,
            final BuildType buildType) throws BuilderCreationException, BuildProcessingException {
        return buildBook(contentSpec, requester, builderOptions, new HashMap<String, byte[]>(), buildType);
    }

    /**
     * Builds a book into a zip file for the passed Content Specification.
     *
     * @param contentSpec    The content specification that is to be built. It should have already been validated, if not errors may occur.
     * @param requester      The user who requested the book to be built.
     * @param builderOptions The set of options what are to be when building the book.
     * @param overrideFiles
     * @param buildType
     * @return A byte array that is the zip file
     * @throws BuildProcessingException Any unexpected errors that occur during building.
     * @throws BuilderCreationException Any error that occurs while trying to setup/create the builder
     */
    public byte[] buildBook(final ContentSpec contentSpec, final String requester, final DocBookBuildingOptions builderOptions,
            final Map<String, byte[]> overrideFiles, final BuildType buildType) throws BuilderCreationException, BuildProcessingException {
        if (contentSpec == null) {
            throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
        } else if (requester == null) {
            throw new BuilderCreationException("A user must be specified as the user who requested the build.");
        }

        if (buildType == BuildType.PUBLICAN) {
            docbookBuilder = new PublicanDocBookBuilder(providerFactory);
        } else if (buildType == BuildType.PUBLICAN_PO) {
            docbookBuilder = new PublicanPODocBookBuilder(providerFactory);
        } else if (buildType == BuildType.JDOCBOOK) {
            docbookBuilder = new JDocBookBuilder(providerFactory);
        } else {
            docbookBuilder = new DocBookBuilder(providerFactory);
        }

        final HashMap<String, byte[]> files = docbookBuilder.buildBook(contentSpec, requester, builderOptions, overrideFiles);

        // Create the zip file
        byte[] zipFile = null;
        try {
            zipFile = ZipUtilities.createZip(files);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return zipFile;
    }

    /**
     * Builds a book into a zip file for the passed Content Specification.
     *
     * @param contentSpec    The content specification that is to be built. It should have already been validated, if not errors may occur.
     * @param requester      The user who requested the book to be built.
     * @param builderOptions The set of options what are to be when building the book.
     * @param zanataDetails  The Zanata details to be used when editor links are turned on.
     * @param buildType
     * @return A byte array that is the zip file
     * @throws BuildProcessingException Any unexpected errors that occur during building.
     * @throws BuilderCreationException Any error that occurs while trying to setup/create the builder
     */
    public byte[] buildTranslatedBook(final ContentSpec contentSpec, final String requester, final DocBookBuildingOptions builderOptions,
            final ZanataDetails zanataDetails, final BuildType buildType) throws BuilderCreationException, BuildProcessingException {
        return buildTranslatedBook(contentSpec, requester, builderOptions, new HashMap<String, byte[]>(), zanataDetails, buildType);
    }

    /**
     * Builds a book into a zip file for the passed Content Specification.
     *
     * @param contentSpec    The content specification that is to be built. It should have already been validated, if not errors may occur.
     * @param requester      The user who requested the book to be built.
     * @param builderOptions The set of options what are to be when building the book.
     * @param overrideFiles
     * @param zanataDetails  The Zanata details to be used when editor links are turned on.
     * @param buildType
     * @return A byte array that is the zip file
     * @throws BuildProcessingException Any unexpected errors that occur during building.
     * @throws BuilderCreationException Any error that occurs while trying to setup/create the builder
     */
    public byte[] buildTranslatedBook(final ContentSpec contentSpec, final String requester, final DocBookBuildingOptions builderOptions,
            final Map<String, byte[]> overrideFiles, final ZanataDetails zanataDetails,
            final BuildType buildType) throws BuilderCreationException, BuildProcessingException {
        if (contentSpec == null) {
            throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
        } else if (requester == null) {
            throw new BuilderCreationException("A user must be specified as the user who requested the build.");
        }

        if (buildType == BuildType.PUBLICAN) {
            docbookBuilder = new PublicanDocBookBuilder(providerFactory);
        } else if (buildType == BuildType.PUBLICAN_PO) {
            docbookBuilder = new PublicanPODocBookBuilder(providerFactory);
        } else if (buildType == BuildType.JDOCBOOK) {
            docbookBuilder = new JDocBookBuilder(providerFactory);
        } else {
            docbookBuilder = new DocBookBuilder(providerFactory);
        }

        // Always skip nested section validation for translation builds
        builderOptions.setSkipNestedSectionValidation(true);

        final HashMap<String, byte[]> files = docbookBuilder.buildTranslatedBook(contentSpec, requester, builderOptions, overrideFiles,
                zanataDetails);

        // Create the zip file
        byte[] zipFile = null;
        try {
            zipFile = ZipUtilities.createZip(files);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return zipFile;
    }
}