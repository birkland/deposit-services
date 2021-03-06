/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.pass.deposit.assembler.shared;

import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.builder.InvalidModel;
import org.dataconservancy.pass.deposit.builder.fs.SharedSubmissionUtil;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import static java.util.function.Function.*;
import static java.util.stream.Collectors.toMap;
import static org.dataconservancy.pass.deposit.DepositTestUtil.composeSubmission;
import static org.dataconservancy.pass.deposit.DepositTestUtil.openArchive;
import static org.dataconservancy.pass.deposit.DepositTestUtil.tmpFile;
import static org.junit.Assert.assertTrue;

/**
 * Abstract integration test for {@link AbstractAssembler} implementations.
 * <p>
 * Creates and extracts a package using the {@link #assemblerUnderTest() assembler under test}.  Subclasses have access
 * to the extracted package directory as {@link #extractedPackageDir}, and to the contents being packaged (in various
 * forms):
 * <ul>
 *     <li>{@link #custodialResources}: a simple {@code List} of Spring {@link Resource}s</li>
 *     <li>{@link #custodialResourcesMap}: a {@code Map} of Spring {@link Resource}s, keyed by resource name</li>
 * </ul>
 * </p>
 */
public abstract class BaseAssemblerIT {

    @Rule
    public TestName testName = new TestName();

    protected static final Logger LOG = LoggerFactory.getLogger(BaseAssemblerIT.class);

    protected SharedSubmissionUtil submissionUtil;

    /**
     * The custodial resources that are to be packaged up by {@link #setUp()}.  They should be present in the extracted
     * package.
     * <p>
     * It should be noted that some implementations (notably the {@code NihmsZippedPackageStream}) will remediate the
     * filenames of the custodial resources.  For example, if the name of a custodial resource conflicts with the name
     * of a file that is required by a packaging specification, then the assembler implementation may remediate the
     * conflict by re-naming or moving the custodial resource to a new location within the package.  <strong>The
     * custodial resource will <em>not</em> be updated with the remediated file location</strong>.  Subclasses of this
     * integration test will need to be aware that the resources in this list, and any other data structures that
     * contain custodial resource (e.g. {@link #custodialResourcesMap}) will not
     * contain remediated resource names; the resources will be known by their original names..
     * </p>
     */
    protected List<DepositFile> custodialResources;

    /**
     * The custodial resources that are to be packaged up by {@link #setUp()}, keyed by file name.  They should be
     * present in the extracted package
     */
    protected Map<String, DepositFile> custodialResourcesMap;

    /**
     * The package generated by {@link #setUp()} is extracted to this directory
     */
    protected File extractedPackageDir;

    /**
     * The {@link ResourceBuilderFactory} used by the {@link AbstractAssembler} to create {@link
     * PackageStream.Resource}s from the {@link #custodialResources custodial resources}
     */
    protected ResourceBuilderFactory rbf;

    /**
     * The {@link MetadataBuilderFactory} used by the {@link AbstractAssembler} to create {@link
     * PackageStream.Metadata}
     */
    protected MetadataBuilderFactory mbf;

    /**
     * The submission that the {@link #extractedPackageDir extracted package} is composed from
     */
    protected DepositSubmission submission;

    /**
     * Mocks a submission, and invokes the assembler to create a package based on the resources under the
     * {@code sample1/} resource path.  Sets the {@link #extractedPackageDir} to the base directory of the newly created
     * and extracted package.
     *
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception {
        submissionUtil = new SharedSubmissionUtil();
        mbf = metadataBuilderFactory();
        rbf = resourceBuilderFactory();
        AbstractAssembler underTest = assemblerUnderTest();

        prepareSubmission();

        prepareCustodialResources();

        PackageStream stream = underTest.assemble(submission);

        File packageArchive = savePackage(stream);

        verifyStreamMetadata(stream.metadata());

        extractPackage(packageArchive, stream.metadata().archive(), stream.metadata().compression());
    }

    protected void prepareSubmission() throws InvalidModel {
        prepareSubmission(URI.create("fake:submission1"));
    }

    protected void prepareSubmission(URI submissionUri) throws InvalidModel {
        submission = submissionUtil.asDepositSubmission(submissionUri);
    }

    /**
     * Obtains a List of Resources from the classpath, stores them in {@link #custodialResources}.
     *
     * Creates a convenience {@code Map}, mapping file names to their corresponding Resources in {@link
     * #custodialResourcesMap}.  Every Resource in {@code custodialResources} should be represented in {@code
     * custodialResourcesMap}, and vice-versa.
     *
     * @return a {@code Map} of custodial resources to be packaged, and their corresponding {@code DepositFileType}
     */
    protected List<DepositFile> prepareCustodialResources() {
        // Insure we're packaging something
        assertTrue("Refusing to create an empty package!",submission.getFiles().size() > 0);
        custodialResources = submission.getFiles();
        custodialResourcesMap = submission.getFiles().stream().collect(toMap(DepositFile::getName, identity()));
        return submission.getFiles();
    }

    /**
     * Extracts the supplied package archive file (.zip, .gzip, etc) to the {@link #extractedPackageDir}.
     *
     * @param packageArchive the package archive file to open
     * @throws IOException if there is an error opening the package
     */
    protected void extractPackage(File packageArchive, PackageStream.ARCHIVE archive, PackageStream.COMPRESSION compression) throws IOException {
        extractedPackageDir = openArchive(packageArchive, archive, compression);

        LOG.debug(">>>> Extracted package to '{}'", extractedPackageDir);
    }

    /**
     * Saves the supplied {@link PackageStream} to a temporary file.
     *
     * @param stream the {@code PackageStream} generated by the assembler under test
     * @return the {@code File} representing the saved package
     * @throws IOException if there is an error saving the package
     */
    protected File savePackage(PackageStream stream) throws IOException {
        StringBuilder ext = new StringBuilder();

        switch (stream.metadata().archive()) {
            case TAR:
                ext.append(".").append(Extension.TAR.getExt());
                break;
            case ZIP:
                ext.append(".").append(Extension.ZIP.getExt());
                break;
        }

        switch (stream.metadata().compression()) {
            case GZIP:
                ext.append(".").append(Extension.GZ.getExt());
                break;
            case BZIP2:
                ext.append(".").append(Extension.BZ2.getExt());
                break;
        }

        File tmpOut = tmpFile(this.getClass(), testName, ext.toString());

        try (InputStream in = stream.open()) {
            Files.copy(in, tmpOut.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        LOG.debug(">>>> Wrote package to '{}'", tmpOut);
        return tmpOut;
    }

    /**
     * Returns a new instance of the {@link DefaultMetadataBuilderFactory}
     *
     * @return
     */
    protected static MetadataBuilderFactory metadataBuilderFactory() {
        return new DefaultMetadataBuilderFactory();
    }

    /**
     * Returns a new instance of the {@link DefaultResourceBuilderFactory}
     *
     * @return
     */
    protected static ResourceBuilderFactory resourceBuilderFactory() {
        return new DefaultResourceBuilderFactory();
    }

    /**
     * To be implemented by sub-classes: must return a fully functional instance of the {@link AbstractAssembler} to be
     * tested.
     *
     * @return the {@code AbstractAssembler} under test
     */
    protected abstract AbstractAssembler assemblerUnderTest();

    /**
     * To be implemented by sub-classes: must verify expected values found in the {@link PackageStream.Metadata}.
     *
     * @param metadata the package stream metadata
     */
    protected abstract void verifyStreamMetadata(PackageStream.Metadata metadata);
}
