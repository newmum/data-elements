package com.linewell.dataelement.integration.nifi.canvas.nifi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.RuntimeNode;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NifiLinewellBundleTest {
    private static final String TYPE = "com.linewell.microservice.processors.database.LinewellPutDatabaseRecord";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void springClientNeedsNoBundleConfigurationAndIgnoresObsoleteVersionPins() {
        new ApplicationContextRunner().withBean(NifiNodeRuntimeResolver.class, () -> mock(NifiNodeRuntimeResolver.class))
                .withBean(ObjectMapper.class, () -> mapper).withBean(NifiClient.class)
                .withPropertyValues("nifi.linewell-database-record.bundle-version=2.9.0")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(NifiClient.class);
                    var client = spy(context.getBean(NifiClient.class));
                    doReturn(types("2.9.0", "2.10.0")).when(client).get("/flow/processor-types", JsonNode.class);
                    assertThat(createBundle(client).path("version").asText()).isEqualTo("2.10.0");
                });
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void preflightDiscoveryAndCreationShareOneTenantBoundSnapshot(int count) {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        when(resolver.resolve()).thenReturn(node("validation-tenant"));
        var client = spy(new NifiClient(resolver, mapper));
        var response = types("2.8.0", "2.10.0", "2.9.0");
        response.path("processorTypes").get(1).withObject("/bundle").put("extraMetadata", "ignored");
        doReturn(response).when(client).get("/flow/processor-types", JsonNode.class);
        doReturn(mock(NifiEntity.class)).when(client).post(eq("/process-groups/validation-group/processors"), any(), eq(NifiEntity.class));
        try (var tenant = TenantContext.use("validation-tenant"); var outer = client.bindRuntimeNode()) {
            client.requireLinewellDatabaseRecordExtension();
            try (var nested = client.bindRuntimeNode()) {
                for (int i = 0; i < count; i++) {
                    assertThat(client.findProcessorType("LinewellPutDatabaseRecord")).contains(TYPE);
                    client.createProcessor("validation-group", TYPE, "validation-writer-" + i, 0, 0,
                            Map.of("put-db-record-table-name", "validation_table"), null, null);
                }
            }
        }
        verify(resolver, times(1)).resolve();
        verify(client, times(1)).get("/flow/processor-types", JsonNode.class);
        var bodies = ArgumentCaptor.forClass(Object.class);
        verify(client, times(count)).post(eq("/process-groups/validation-group/processors"), bodies.capture(), eq(NifiEntity.class));
        for (Object body : bodies.getAllValues()) {
            JsonNode component = ((JsonNode) body).path("component");
            assertThat(component.path("type").asText()).isEqualTo(TYPE);
            assertThat(component.path("bundle")).isEqualTo(bundle("com.linewell.microservice", "datadevs-database-nar", "2.10.0"));
            assertThat(component.at("/config/properties/put-db-record-table-name").asText()).isEqualTo("validation_table");
        }
    }

    @ParameterizedTest
    @CsvSource({"2.9.0,2.10.0", "2.99.0,10.0.0", "2.9.9,2.9.10", "3.0.0-rc2,3.0.0-rc10",
            "3.0.0-SNAPSHOT,3.0.0", "3.0.0-rc1,3.0.0-SNAPSHOT", "3.0.0,3.0.0-sp1",
            "2.10,2.10.0", "2.9.0,3.0.0-SNAPSHOT"})
    void newestVersionUsesNumericAndQualifierOrderingIndependentOfRegistryOrder(String older, String newer) {
        var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), mapper));
        doReturn(types(older, newer, older), types(newer, older))
                .when(client).get("/flow/processor-types", JsonNode.class);
        assertThat(createBundle(client).path("version").asText()).isEqualTo(newer);
        assertThat(createBundle(client).path("version").asText()).isEqualTo(newer);
    }

    @Test void sameNameBundlesOutsideTheKnownNarFamilyCannotOverrideItsNewestVersion() {
        var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), mapper));
        var response = types("2.9.0", "3.0.0");
        response.withArray("processorTypes").addObject().put("type", TYPE)
                .set("bundle", bundle("validation.vendor", "datadevs-database-nar", "99.0.0"));
        response.withArray("processorTypes").addObject().put("type", TYPE)
                .set("bundle", bundle("com.linewell.microservice", "validation-database-nar", "99.0.0"));
        doReturn(response).when(client).get("/flow/processor-types", JsonNode.class);
        assertThat(createBundle(client)).isEqualTo(bundle("com.linewell.microservice", "datadevs-database-nar", "3.0.0"));
    }

    @Test void missingAndUnusableExtensionsFailBeforeCreationWithActualCoordinates() {
        var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), mapper));
        doReturn(types()).when(client).get("/flow/processor-types", JsonNode.class);
        assertThatThrownBy(client::requireLinewellDatabaseRecordExtension)
                .hasMessageContaining("未注册 LinewellPutDatabaseRecord")
                .hasMessageContaining("自动选择范围：com.linewell.microservice:datadevs-database-nar")
                .hasMessageContaining("当前 NiFi 已注册：无");
        doReturn(types("", " ", "not-a-version", "2.9.0\n", "1".repeat(257)))
                .when(client).get("/flow/processor-types", JsonNode.class);
        assertThatThrownBy(() -> client.createProcessor("validation-group", TYPE, "validation-writer", 0, 0, Map.of(), null, null))
                .hasMessageContaining("未注册可用的 LinewellPutDatabaseRecord Bundle");
        var wrongGroup = types("2.9.0");
        wrongGroup.path("processorTypes").get(0).withObject("/bundle").put("group", "validation.other");
        doReturn(wrongGroup).when(client).get("/flow/processor-types", JsonNode.class);
        assertThatThrownBy(client::requireLinewellDatabaseRecordExtension).hasMessageContaining("validation.other:datadevs-database-nar:2.9.0");
        verify(client, never()).post(anyString(), any(), any());
    }

    @Test void aSingleInstalledOlderVersionRemainsUsableWithoutAnyPinOrFallback() {
        var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), mapper));
        doReturn(types("2.8.0")).when(client).get("/flow/processor-types", JsonNode.class);
        assertThat(createBundle(client).path("version").asText()).isEqualTo("2.8.0");
    }

    @Test void missingOrNonTextualVersionCannotBeUsedAsABundleCoordinate() {
        var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), mapper));
        var invalid = types("2.9.0", "2.10.0");
        ((ObjectNode) invalid.path("processorTypes").get(0).path("bundle")).remove("version");
        invalid.path("processorTypes").get(1).withObject("/bundle").put("version", 3);
        doReturn(invalid).when(client).get("/flow/processor-types", JsonNode.class);
        assertThatThrownBy(client::requireLinewellDatabaseRecordExtension).hasMessageContaining("未注册可用");
        verify(client, never()).post(anyString(), any(), any());
    }

    @Test void ambiguousClassNamesInOneBundleAreRejectedInsteadOfChoosingAnArbitraryType() {
        var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), mapper));
        var response = types("2.9.0");
        response.withArray("processorTypes").addObject().put("type", "validation.other.LinewellPutDatabaseRecord")
                .set("bundle", bundle("com.linewell.microservice", "datadevs-database-nar", "2.9.0"));
        doReturn(response).when(client).get("/flow/processor-types", JsonNode.class);
        assertThatThrownBy(client::requireLinewellDatabaseRecordExtension).hasMessageContaining("多个 LinewellPutDatabaseRecord 处理器类型");
        verify(client, never()).post(anyString(), any(), any());
    }

    @Test void ambiguityInOlderVersionsDoesNotBlockTheUniqueNewestVersion() {
        var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), mapper));
        var response = types("2.9.0", "2.10.0");
        response.withArray("processorTypes").addObject().put("type", "validation.other.LinewellPutDatabaseRecord")
                .set("bundle", bundle("com.linewell.microservice", "datadevs-database-nar", "2.9.0"));
        doReturn(response).when(client).get("/flow/processor-types", JsonNode.class);
        assertThat(createBundle(client).path("version").asText()).isEqualTo("2.10.0");
    }

    @Test void newestVersionCreationFailureIsNotSilentlyRetriedWithAnOlderBundle() {
        var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), mapper));
        doReturn(types("2.9.0", "2.10.0")).when(client).get("/flow/processor-types", JsonNode.class);
        doThrow(new NifiException("extension unavailable on cluster worker", 409, "Bundle not found"))
                .when(client).post(anyString(), any(), eq(NifiEntity.class));
        assertThatThrownBy(() -> client.createProcessor("validation-group", TYPE, "validation-writer", 0, 0, Map.of(), null, null))
                .isInstanceOf(NifiException.class).hasMessageContaining("extension unavailable");
        var body = ArgumentCaptor.forClass(Object.class);
        verify(client).post(anyString(), body.capture(), eq(NifiEntity.class));
        assertThat(((JsonNode) body.getValue()).at("/component/bundle/version").asText()).isEqualTo("2.10.0");
    }

    @Test void capabilitySnapshotIsNeverReusedAcrossTenantsOrLaterDeployments() {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        when(resolver.resolve()).thenAnswer(i -> node(TenantContext.requireTenantId()));
        var client = spy(new NifiClient(resolver, mapper));
        doReturn(types("2.9.0"), types("2.8.0"), types("2.9.0", "3.0.0"))
                .when(client).get("/flow/processor-types", JsonNode.class);
        try (var tenant = TenantContext.use("validation-a"); var scope = client.bindRuntimeNode()) {
            client.requireLinewellDatabaseRecordExtension();
            assertThat(createBundle(client).path("version").asText()).isEqualTo("2.9.0");
            try (var foreign = TenantContext.use("validation-b")) {
                assertThatThrownBy(client::requireLinewellDatabaseRecordExtension).hasMessageContaining("租户上下文发生变化");
            }
        }
        try (var tenant = TenantContext.use("validation-b"); var scope = client.bindRuntimeNode()) {
            assertThat(createBundle(client).path("version").asText()).isEqualTo("2.8.0");
        }
        try (var tenant = TenantContext.use("validation-a"); var scope = client.bindRuntimeNode()) {
            assertThat(createBundle(client).path("version").asText()).isEqualTo("3.0.0");
        }
        verify(resolver, times(3)).resolve();
        verify(client, times(3)).get("/flow/processor-types", JsonNode.class);
    }

    private JsonNode createBundle(NifiClient client) {
        java.util.concurrent.atomic.AtomicReference<JsonNode> captured = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(call -> {
            captured.set(((JsonNode) call.getArgument(1)).at("/component/bundle"));
            return mock(NifiEntity.class);
        }).when(client).post(anyString(), any(), eq(NifiEntity.class));
        client.createProcessor("validation-group", TYPE, "validation-writer", 0, 0, Map.of(), null, null);
        return captured.get();
    }

    private ObjectNode types(String... versions) {
        var result = mapper.createObjectNode();
        var types = result.putArray("processorTypes");
        for (String version : versions) types.addObject().put("type", TYPE)
                .set("bundle", bundle("com.linewell.microservice", "datadevs-database-nar", version));
        return result;
    }

    private ObjectNode bundle(String group, String artifact, String version) {
        return mapper.createObjectNode().put("group", group).put("artifact", artifact).put("version", version);
    }

    private RuntimeNode node(String tenant) {
        return new RuntimeNode(tenant, "validation-node", "validation", "http://validation.invalid",
                "validation-user", "validation-password", false, "validation-root");
    }
}
