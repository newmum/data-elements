package com.linewell.dataelement.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackendTestSourcesTest {
    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest
    @ValueSource(strings = {"", "data-elements-parent", "logs/task"})
    void findsSourcesFromRepositoryModuleAndLogWorkingDirectories(String relativeWorkingDirectory) throws Exception {
        Path repository = temporaryDirectory.resolve("checkout");
        Path sources = createSources(repository.resolve("data-elements-parent"));
        Path workingDirectory = repository.resolve(relativeWorkingDirectory);
        Path relocatedClasses = repository.resolve("logs/build/test-classes");

        assertThat(BackendTestSources.locateJavaSourceDirectory(null, workingDirectory, relocatedClasses))
                .isEqualTo(sources);
    }

    @Test
    void compiledTestsIdentifyTheirCheckoutEvenWhenWorkingDirectoryPointsAtAnotherCheckout() throws Exception {
        Path compiledModule = temporaryDirectory.resolve("compiled-checkout/data-elements-parent");
        Path sources = createSources(compiledModule);
        Path otherCheckout = temporaryDirectory.resolve("other-checkout");
        createSources(otherCheckout.resolve("data-elements-parent"));

        assertThat(BackendTestSources.locateJavaSourceDirectory(null, otherCheckout,
                compiledModule.resolve("target/test-classes"))).isEqualTo(sources);
    }

    @Test
    void explicitMavenSourceDirectorySupportsRelocatedBuilds() throws Exception {
        Path sources = createSources(temporaryDirectory.resolve("source-checkout/data-elements-parent"));
        Path buildDirectory = temporaryDirectory.resolve("external-build");

        assertThat(BackendTestSources.locateJavaSourceDirectory(sources.toString(),
                buildDirectory, buildDirectory.resolve("test-classes"))).isEqualTo(sources);
    }

    @Test
    void invalidExplicitSourceDirectoryFailsInsteadOfInspectingAnotherCheckout() throws Exception {
        Path repository = temporaryDirectory.resolve("checkout");
        createSources(repository.resolve("data-elements-parent"));

        assertThatIllegalStateException().isThrownBy(() -> BackendTestSources.locateJavaSourceDirectory(
                "missing-src", repository, repository.resolve("build/test-classes")))
                .withMessageContaining("Backend Java source directory is missing");
    }

    @Test
    void emptySourceDirectoryCannotSilentlyPassArchitectureChecks() throws Exception {
        Path emptySources = Files.createDirectories(temporaryDirectory.resolve("empty-src"));
        assertThatIllegalStateException().isThrownBy(() -> BackendTestSources.locateJavaSourceDirectory(
                emptySources.toString(), temporaryDirectory, temporaryDirectory.resolve("build/test-classes")))
                .withMessageContaining("Backend Java source directory is missing");
    }

    private Path createSources(Path module) throws IOException {
        Path sourceDirectory = module.resolve("src/main/java");
        Path application = sourceDirectory.resolve("com/linewell/dataelement/DataElementApplication.java");
        Files.createDirectories(application.getParent());
        Files.writeString(application, "package com.linewell.dataelement; class DataElementApplication {}\n");
        return sourceDirectory;
    }
}
