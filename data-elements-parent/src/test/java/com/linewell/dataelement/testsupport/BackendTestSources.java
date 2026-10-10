package com.linewell.dataelement.testsupport;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Locates editable backend sources independently of the test JVM's working directory. */
public final class BackendTestSources {
    private static final String SOURCE_PROPERTY = "backend.javaSourceDirectory";
    private static final Path PACKAGE_PATH = Path.of("com/linewell/dataelement");
    private static final Path APPLICATION_PATH = PACKAGE_PATH.resolve("DataElementApplication.java");

    private BackendTestSources() {
    }

    public static Path javaPackageRoot() {
        try {
            Path classesDirectory = Path.of(BackendTestSources.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return locateJavaSourceDirectory(System.getProperty(SOURCE_PROPERTY),
                    Path.of("").toAbsolutePath(), classesDirectory).resolve(PACKAGE_PATH);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Cannot locate backend test classes", exception);
        }
    }

    static Path locateJavaSourceDirectory(String configured, Path workingDirectory, Path classesDirectory) {
        if (configured != null && !configured.isBlank()) {
            Path sourceDirectory = workingDirectory.resolve(configured).toAbsolutePath().normalize();
            if (!isSourceDirectory(sourceDirectory)) {
                throw new IllegalStateException("Backend Java source directory is missing: " + sourceDirectory
                        + "; check -D" + SOURCE_PROPERTY);
            }
            return sourceDirectory;
        }

        // IDE runs and previously generated build POMs may not supply the Maven property.
        // Prefer the checkout containing the compiled tests before considering the working directory.
        for (Path anchor : List.of(classesDirectory, workingDirectory)) {
            for (Path current = anchor.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
                for (Path module : List.of(current, current.resolve("data-elements-parent"))) {
                    Path sourceDirectory = module.resolve("src/main/java");
                    if (isSourceDirectory(sourceDirectory)) {
                        return sourceDirectory;
                    }
                }
            }
        }
        throw new IllegalStateException("Cannot find backend Java sources; set -D" + SOURCE_PROPERTY
                + " to the actual src/main/java directory");
    }

    private static boolean isSourceDirectory(Path directory) {
        return Files.isRegularFile(directory.resolve(APPLICATION_PATH));
    }
}
