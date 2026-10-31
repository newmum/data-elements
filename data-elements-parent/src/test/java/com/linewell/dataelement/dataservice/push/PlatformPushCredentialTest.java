package com.linewell.dataelement.dataservice.push;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.model.common.BizException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformPushCredentialTest {
    RelayClientRegistry registry() {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table data_push_relay_client_t(tid varchar(32) primary key,relay_client_id varchar(128),tenant_id varchar(32),datasource_id varchar(32),api_key_sha256 char(64),previous_api_key_sha256 char(64),is_enable int,is_del int,created_time timestamp,updated_time timestamp,unique(relay_client_id,tenant_id,datasource_id))");
        return new RelayClientRegistry(jdbc);
    }
    @Test void localGenerationIsStableOnReentryAndRotationKeepsItsPredecessor() throws Exception {
        var registry = registry();
        var first = registry.prepareCredential("push-source", "tenant", "source", false);
        assertTrue(first.revealed()); assertEquals(43, first.apiKey().length());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(first.apiKey().getBytes(StandardCharsets.UTF_8))), first.hash());
        assertFalse(first.toString().contains(first.apiKey()));
        var retry = registry.prepareCredential("push-source", "tenant", "source", false);
        assertFalse(retry.revealed()); assertEquals("", retry.apiKey()); assertEquals(first.hash(), retry.hash());
        var rotated = registry.prepareCredential("push-source", "tenant", "source", true);
        assertNotEquals(first.hash(), rotated.hash()); assertEquals(first.hash(), rotated.previousHash());
        assertEquals(rotated.previousHash(), registry.prepareCredential("push-source", "tenant", "source", false).previousHash());
    }
    @Test void legacyCredentialsAreNotSilentlyReplaced() {
        var registry = registry(); registry.bindDatasource("push-source", "tenant", "source");
        var legacy = registry.prepareCredential("push-source", "tenant", "source", false);
        assertFalse(legacy.revealed()); assertTrue(legacy.hash().isEmpty());
    }
    @SuppressWarnings("unchecked")
    @Test void relayOutageStillReturnsTheLocallyGeneratedKeyAndItsConcreteReason() {
        var schemas = mock(DataPushSchemaService.class); var relay = mock(HaitongStandaloneRegistryClient.class);
        var legacyIssuer = mock(HaitongPushCredentialClient.class); var registry = registry();
        when(schemas.standaloneSnapshot("tenant", "source", "push-source")).thenAnswer(call -> new LinkedHashMap<>(Map.of("datasourceId", "source")));
        doThrow(new BizException(503,"推送登记同步服务尚未配置")).when(relay).synchronize(eq("source"), anyMap());
        var controller = new DataPushRegistryController(schemas, registry, new DataPushRegistryProperties(), legacyIssuer, relay);
        TenantContext.bind("tenant");
        try {
            var first = (Map<String,Object>)controller.issueCredential(Map.of("datasourceId","source")).get("data");
            assertEquals(false, first.get("ready")); assertEquals(true, first.get("revealed"));
            assertEquals("推送登记同步服务尚未配置",first.get("syncError")); assertNotNull(first.get("apiKey"));
            var retry = (Map<String,Object>)controller.issueCredential(Map.of("datasourceId","source")).get("data");
            assertEquals(false,retry.get("revealed")); assertFalse(retry.containsKey("apiKey"));
            verifyNoInteractions(legacyIssuer);
            doThrow(new BizException(403,"无权访问")).when(schemas).requirePushDatasource("tenant","foreign");
            assertThrows(BizException.class,()->controller.issueCredential(Map.of("datasourceId","foreign")));
            verify(relay,times(2)).synchronize(eq("source"),anyMap());
        } finally { TenantContext.clear(); }
    }
}
