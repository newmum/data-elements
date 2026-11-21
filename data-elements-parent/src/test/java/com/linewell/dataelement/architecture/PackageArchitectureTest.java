package com.linewell.dataelement.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.linewell.dataelement.DataElementApplication;
import com.linewell.dataelement.testsupport.BackendTestSources;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

class PackageArchitectureTest {

    private static final Path JAVA_ROOT = BackendTestSources.javaPackageRoot();

    @Test
    void flowServeNodeProtocolUsesIntegrationPackageAndConfiguredPaths() {
        Path controller = JAVA_ROOT.resolve("integration/flowserve/FlowServeRuntimeController.java");
        assertThat(controller).exists();
        assertThat(JAVA_ROOT.resolve("dataservice/flowserve/FlowServeRuntimeController.java")).doesNotExist();
        assertThat(read(controller)).contains("${flowserve.runtime.protocol-base-path}").doesNotContain("/api/v1/runtime");
        assertThat(read(JAVA_ROOT.resolve("dataservice/flowserve/FlowServeRuntimeAdapter.java"))).doesNotContain("/api/v1/runtime");
    }

    @Test
    void logMagicAdapterUsesItsModulePackageAndComponentQueriesStayInMagic() {
        assertThat(JAVA_ROOT.resolve("magic/module/LogManagementMagicModule.java")).exists();
        assertThat(JAVA_ROOT.resolve("feature/logmanagement/LogManagementMagicModule.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("feature/logmanagement/LogComponentService.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("platform/web/download/LogDownloadController.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("feature/logmanagement/PlatformLogDownloadController.java")).doesNotExist();
    }

    @Test
    void niFiRuntimeStreamUsesMagicAndPlatformAsyncTransport() {
        assertThat(JAVA_ROOT.resolve("magic/module/TaskRuntimeMagicModule.java")).exists();
        Path oldPackage = JAVA_ROOT.resolve("integration/nifi/canvas/runtime");
        assertThat(oldPackage.resolve("TaskRuntimeMagicModule.java")).doesNotExist();
        assertThat(oldPackage.resolve("TaskRuntimeController.java")).doesNotExist();
        assertThat(oldPackage.resolve("TaskRuntimeAsyncConfiguration.java")).doesNotExist();
        assertThat(read(JAVA_ROOT.resolve("platform/web/config/WebConfiguration.java")))
                .doesNotContain("/nifi/", "task-runtime");
    }

    @Test
    void niFiNodeQueriesAndStartStateStayInMagic() {
        Path integration = JAVA_ROOT.resolve("integration/nifi/canvas");
        assertThat(integration.resolve("lifecycle/PipelineRuntimeStatusResolver.java")).doesNotExist();
        assertThat(integration.resolve("lifecycle/PipelineTaskLifecycleSynchronizer.java")).doesNotExist();
        assertThat(read(integration.resolve("config/NifiNodeRuntimeResolver.java")))
                .contains("RegisteredMagicFunctionInvoker")
                .doesNotContain("JdbcTemplate", "select tid", "ConfiguredNode");
    }

    @Test
    void tenantAndReconciliationDoNotReturnToLegacyRootPackages() throws Exception {
        assertThat(javaFiles(JAVA_ROOT.resolve("tenant"))).isEmpty();
        assertThat(javaFiles(JAVA_ROOT.resolve("reconciliation"))).isEmpty();
        assertThat(JAVA_ROOT.resolve("feature/identity/api/IdentityAccountAdminMagicModule.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("feature/identity/api/ControlIdentityMagicModule.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("feature/identity/application/IdentityAccountAdminService.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("feature/identity/application/IdentityTenantRelationService.java")).doesNotExist();
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

        assertThat(domainFiles).isNotEmpty();
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

        assertThat(apiFiles).isNotEmpty();
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
    void retiredPipelineControllersAreAbsentFromProductionSources() {
        Path controllers = JAVA_ROOT.resolve("integration/nifi/canvas/controller");
        assertThat(controllers.resolve("PipelineController.java")).doesNotExist();
        assertThat(controllers.resolve("PipelineRunController.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("integration/nifi/canvas/lifecycle/NifiTaskChainRuntime.java")).doesNotExist();
    }

    @Test
    void retiredPipelineFileImporterAndItsPersistenceTypesAreAbsent() {
        Path persistence = JAVA_ROOT.resolve("feature/dataaccess/infrastructure/persistence");
        assertThat(JAVA_ROOT.resolve("integration/nifi/canvas/migration/PipelineFileToDbMigrationRunner.java"))
                .doesNotExist();
        assertThat(persistence.resolve("entity/NifiPipelineMigrationMarkT.java")).doesNotExist();
        assertThat(persistence.resolve("mapper/NifiPipelineMigrationMarkTMapper.java")).doesNotExist();
    }

    @Test
    void runtimeDiagnosticsAndSearchClientConfigurationHaveDedicatedPackages() throws Exception {
        assertThat(JAVA_ROOT.resolve("magic/runtime/diagnostics/MagicApiRuntimeProbe.java")).exists();
        assertThat(JAVA_ROOT.resolve("magic/runtime/resource/MagicResourceReloadController.java")).exists();
        assertThat(JAVA_ROOT.resolve("magic/runtime/route/RegisteredMagicApiInvoker.java")).exists();
        assertThat(JAVA_ROOT.resolve("integration/workflow/CustomGlobalListener.java")).exists();
        assertThat(JAVA_ROOT.resolve("feature/approval/application/ApprovalLifecyclePort.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("feature/approval/infrastructure/magic/MagicApprovalLifecycleAdapter.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("platform/configuration/NodeConfigurationController.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("platform/tenant/api/IdentityPolicyInvoker.java")).doesNotExist();
        assertThat(read(JAVA_ROOT.resolve("platform/tenant/protocol/IdentityProtocolBridge.java")))
                .contains("RegisteredMagicFunctionInvoker")
                .doesNotContain("MagicAPIService", "\"/idaas/");
        assertThat(JAVA_ROOT.resolve("magic/runtime/result/CustomJsonValueProvider.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("magic/runtime/result/MagicApiResultProvider.java")).exists();
        assertThat(JAVA_ROOT.resolve("integration/nifi/canvas/controller/NifiProxyController.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("magic/module/MagicResourceLinkModule.java")).exists();
        assertThat(JAVA_ROOT.resolve("magic/module/SystemConfigMagicModule.java")).exists();
        assertThat(JAVA_ROOT.resolve("magic/module/UiComponentCacheMagicModule.java")).exists();
        assertThat(JAVA_ROOT.resolve("platform/cache/UiComponentCacheService.java")).exists();
        assertThat(JAVA_ROOT.resolve("magic/module/MetadataScanMagicModule.java")).exists();
        assertThat(JAVA_ROOT.resolve("integration/metadata/TableInventoryProbe.java")).exists();
        assertThat(JAVA_ROOT.resolve("metautil/controller/TableMetadataCollectionController.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("metautil/service/TableMetadataCollectionService.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("metautil/model/dto/DirectPageQueryRequest.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("platform/configuration/SystemConfigMagicModule.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("platform/configuration/UiComponentCacheController.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("platform/configuration/UiComponentCacheMagicModule.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("platform/configuration/UiComponentCacheService.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("platform/tenant/api/TenantController.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("integration/search/config/ElasticsearchCustomRestClient.java")).exists();
        assertThat(JAVA_ROOT.resolve("integration/search/config/EsIndexInit.java")).exists();
        assertThat(JAVA_ROOT.resolve("platform/magic/diagnostics/MagicApiRuntimeProbe.java")).doesNotExist();
        assertThat(javaFiles(JAVA_ROOT.resolve("platform/magic/result"))).isEmpty();
        assertThat(JAVA_ROOT.resolve("platform/magic/module/MagicResourceLinkModule.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("elasticsearch/ElasticsearchCustomRestClient.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("integration/elasticsearch/config/EsIndexInit.java")).doesNotExist();
    }

    @Test
    void ftpsTransportFactoryLivesWithProtocolAdapters() {
        assertThat(JAVA_ROOT.resolve("integration/transfer/FtpsClientFactory.java")).exists();
        assertThat(JAVA_ROOT.resolve("metautil/structured/FtpsClientFactory.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("integration/transfer/haitong/HaitongStandaloneRegistryClient.java")).exists();
        assertThat(JAVA_ROOT.resolve("integration/transfer/haitong/HaitongPushCredentialClient.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("integration/transfer/haitong/DataPushFjghccDeliveryService.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("integration/transfer/haitong/RelayRequestAuthentication.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("config/DataPushRegistryConfiguration.java")).exists();
        assertThat(JAVA_ROOT.resolve("dataservice/push/DataPushRegistryController.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("dataservice/push/DataPushSchemaService.java")).doesNotExist();
    }

    @Test
    void sharedBackendHasNoLegacyRouteRewriter() throws IOException {
        assertThat(read(JAVA_ROOT.resolve("platform/web/config/WebConfiguration.java")))
                .doesNotContain("HttpServletRequestWrapper", "legacyResourceRoutes", "LegacyRouteConfiguration");
        assertThat(javaFiles(JAVA_ROOT.resolve("platform/web/compat"))).isEmpty();
        assertThat(JAVA_ROOT.resolve("integration/nifi/canvas/config/CorsConfig.java")).doesNotExist();
        assertThat(JAVA_ROOT.resolve("integration/workflow/WarmFlowDesignerAccess.java")).exists();
        assertThat(JAVA_ROOT.resolve("integration/workflow/HandlerSelectServiceImpl.java")).exists();
        assertThat(javaFiles(JAVA_ROOT.resolve("feature/approval/infrastructure/warmflow/designer"))).isEmpty();
    }

    @Test
    void productionJavaSourcesAreNotEmptyOrDisabledSpringShells() throws Exception {
        List<Path> sources = javaFiles(JAVA_ROOT);

        assertThat(sources)
                .isNotEmpty()
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
