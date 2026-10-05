package com.linewell.dataelement.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.linewell.dataelement.DataElementApplication;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

class PackageArchitectureTest {

    private static final Path JAVA_ROOT =
            Path.of("src/main/java/com/linewell/dataelement");

    @Test
    void tenantAndReconciliationDoNotReturnToLegacyRootPackages() throws Exception {
        assertThat(javaFiles(JAVA_ROOT.resolve("tenant"))).isEmpty();
        assertThat(javaFiles(JAVA_ROOT.resolve("reconciliation"))).isEmpty();
    }

    @Test
    void transitionalRootPackagesStayRetired() throws Exception {
        List<String> retiredRoots = List.of(
                "integrated",
                "external",
                "systemconfig",
                "support",
                "extension",
                "configuration",
                "interceptor",
                "message",
                "task",
                "flow"
        );
        for (String root : retiredRoots) {
            assertThat(javaFiles(JAVA_ROOT.resolve(root)))
                    .as("retired root package %s", root)
                    .isEmpty();
        }

        List<Path> violations = javaFiles(JAVA_ROOT).stream()
                .filter(path -> retiredRoots.stream().anyMatch(root ->
                        read(path).contains("com.linewell.dataelement." + root)
                ))
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void huaweiHiveGatewayCompatibilityModuleIsTheOnlyRemainingRootModule() throws Exception {
        // The gateway is a live Huawei MRS Hive Magic module, not a transitional
        // package. Keep that adapter while preventing legacy utility copies from
        // being reintroduced beside it.
        assertThat(javaFiles(JAVA_ROOT.resolve("module")))
                .extracting(path -> path.getFileName().toString())
                .containsExactly("HiveModule.java");
    }

    @Test
    void domainPackagesStayFrameworkIndependent() throws Exception {
        List<Path> domainFiles = javaFiles(
                JAVA_ROOT.resolve("platform/tenant/domain"),
                JAVA_ROOT.resolve("feature/reconciliation/domain"),
                JAVA_ROOT.resolve("feature/dataquality/domain"),
                JAVA_ROOT.resolve("feature/approval/domain")
        );

        List<Path> violations = domainFiles.stream()
                .filter(path -> {
                    String source = read(path);
                    return source.contains("org.springframework")
                            || source.contains("com.baomidou")
                            || source.contains("org.apache.ibatis")
                            || source.contains("java.sql");
                })
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void apiPackagesDoNotReachIntoPersistenceOrJdbcInfrastructure() throws Exception {
        List<Path> apiFiles = javaFiles(
                JAVA_ROOT.resolve("platform/tenant/api"),
                JAVA_ROOT.resolve("feature/reconciliation/api"),
                JAVA_ROOT.resolve("feature/dataquality/api"),
                JAVA_ROOT.resolve("feature/approval/api")
        );

        List<Path> violations = apiFiles.stream()
                .filter(path -> {
                    String source = read(path);
                    return source.contains(".infrastructure.persistence")
                            || source.contains(".infrastructure.jdbc");
                })
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void warmFlowAdapterDoesNotCallMagicOrLegacyIdentityRoutes() throws Exception {
        List<Path> adapterFiles = javaFiles(
                JAVA_ROOT.resolve("feature/approval/infrastructure/warmflow")
        );
        assertThat(adapterFiles).isNotEmpty();
        assertThat(adapterFiles).allSatisfy(path -> assertThat(read(path))
                .doesNotContain("MagicAPIService")
                .doesNotContain("/sym/sso")
                .doesNotContain("db.sso"));
    }

    @Test
    void featurePersistenceMappersAreScanned() {
        MapperScan mapperScan = DataElementApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(List.of(mapperScan.value()))
                .contains(
                "com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper",
                "com.linewell.dataelement.feature.approval.infrastructure.persistence.mapper",
                "com.linewell.dataelement.feature.dataquality.infrastructure.persistence.mapper"
        );
    }

    @Test
    void pipelineControllerDoesNotOwnPersistenceOrRuntimeSynchronization() {
        Path controller = JAVA_ROOT.resolve(
                "integration/nifi/canvas/controller/PipelineRunController.java"
        );
        String source = read(controller);

        assertThat(source)
                .doesNotContain("LambdaUpdateWrapper")
                .doesNotContain("IDataAccessAggTaskTService")
                .doesNotContain("DataAssetRuntimeAdapter")
                .doesNotContain(".infrastructure.persistence");
    }

    @Test
    void productionJavaSourcesAreNotEmptyOrDisabledSpringShells() throws Exception {
        List<Path> sources = javaFiles(JAVA_ROOT);

        assertThat(sources)
                .allSatisfy(path -> assertThat(read(path).trim())
                        .as("production source %s", path)
                        .isNotEmpty());

        List<Path> disabledSpringTypes = sources.stream()
                .filter(path -> read(path).matches(
                        "(?s).*//\\s*@(RestController|Controller|Service|Component|Repository|Configuration|ControllerAdvice|RestControllerAdvice).*"
                ))
                .toList();

        assertThat(disabledSpringTypes).isEmpty();
    }

    private List<Path> javaFiles(Path... roots) throws IOException {
        List<Path> result = new java.util.ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                result.addAll(paths
                        .filter(path -> path.toString().endsWith(".java"))
                        .toList());
            }
        }
        return result;
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
