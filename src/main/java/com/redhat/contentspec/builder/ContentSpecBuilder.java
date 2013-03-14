package com.redhat.contentspec.builder;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.redhat.contentspec.builder.exception.BuilderCreationException;
import com.redhat.contentspec.structures.CSDocbookBuildingOptions;
import org.jboss.pressgang.ccms.contentspec.ContentSpec;
import org.jboss.pressgang.ccms.contentspec.interfaces.ShutdownAbleApp;
import org.jboss.pressgang.ccms.contentspec.rest.RESTManager;
import org.jboss.pressgang.ccms.docbook.constants.DocbookBuilderConstants;
import org.jboss.pressgang.ccms.rest.v1.collections.RESTTopicCollectionV1;
import org.jboss.pressgang.ccms.rest.v1.collections.RESTTranslatedTopicCollectionV1;
import org.jboss.pressgang.ccms.rest.v1.collections.items.RESTTopicCollectionItemV1;
import org.jboss.pressgang.ccms.rest.v1.collections.items.RESTTranslatedTopicCollectionItemV1;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTBlobConstantV1;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTTopicV1;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTTranslatedTopicV1;
import org.jboss.pressgang.ccms.rest.v1.entities.RESTUserV1;
import org.jboss.pressgang.ccms.rest.v1.exceptions.InternalProcessingException;
import org.jboss.pressgang.ccms.rest.v1.exceptions.InvalidParameterException;
import org.jboss.pressgang.ccms.utils.common.ZipUtilities;
import org.jboss.pressgang.ccms.utils.constants.CommonConstants;
import org.jboss.pressgang.ccms.zanata.ZanataDetails;

/**
 * A class that provides the ability to build a book from content specifications.
 *
 * @author lnewson
 */
public class ContentSpecBuilder implements ShutdownAbleApp {
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final RESTBlobConstantV1 rocbookdtd;
    private final RESTManager restManager;
    private DocbookBuilder<?, ?, ?> docbookBuilder;

    public ContentSpecBuilder(final RESTManager restManager) throws InvalidParameterException, InternalProcessingException {
        this.restManager = restManager;
        this.rocbookdtd = restManager.getRESTClient().getJSONBlobConstant(DocbookBuilderConstants.ROCBOOK_DTD_BLOB_ID, "");
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
     * @param contentSpec    The content specification that is to be built. It
     *                       should have already been validated, if not errors
     *                       may occur.
     * @param requester      The user who requested the book to be built.
     * @param builderOptions The set of options what are to be when building the
     *                       book.
     * @return A byte array that is the zip file
     * @throws Exception Any unexpected errors that occur during building.
     */
    public byte[] buildBook(final ContentSpec contentSpec, final RESTUserV1 requester,
            final CSDocbookBuildingOptions builderOptions) throws Exception {
        if (contentSpec == null) {
            throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
        } else if (requester == null) {
            throw new BuilderCreationException("A user must be specified as the user who requested the build.");
        }

        docbookBuilder = new DocbookBuilder<RESTTopicV1, RESTTopicCollectionV1, RESTTopicCollectionItemV1>(restManager, rocbookdtd,
                CommonConstants.DEFAULT_LOCALE);

        final HashMap<String, byte[]> files = docbookBuilder.buildBook(contentSpec, requester, builderOptions);

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
     * @param contentSpec    The content specification that is to be built. It should have already been validated, if not errors
     *                       may occur.
     * @param requester      The user who requested the book to be built.
     * @param builderOptions The set of options what are to be when building the book.
     * @param zanataDetails  The Zanata details to be used when editor links are turned on.
     * @return A byte array that is the zip file
     * @throws Exception Any unexpected errors that occur during building.
     */
    public byte[] buildTranslatedBook(final ContentSpec contentSpec, final RESTUserV1 requester,
            final CSDocbookBuildingOptions builderOptions, final ZanataDetails zanataDetails) throws Exception {
        if (contentSpec == null) {
            throw new BuilderCreationException("No content specification specified. Unable to build from nothing!");
        } else if (requester == null) {
            throw new BuilderCreationException("A user must be specified as the user who requested the build.");
        }

        docbookBuilder = new DocbookBuilder<RESTTranslatedTopicV1, RESTTranslatedTopicCollectionV1, RESTTranslatedTopicCollectionItemV1>(
                restManager, rocbookdtd, CommonConstants.DEFAULT_LOCALE);

        final HashMap<String, byte[]> files = docbookBuilder.buildBook(contentSpec, requester, builderOptions, zanataDetails);

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