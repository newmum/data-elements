package com.linewell.dataelement.platform.tenant.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.ControlDataSourceAspect;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantRoutingDataSource;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.mapper.SymTenantTMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TenantAccountPolicyTest {

    private JdbcTemplate control;
    private TenantDatabaseProperties properties;
    private TenantDataSourceRegistry registry;
    private TenantAccountPolicy policy;

    @BeforeEach
    void setUp() throws Exception {
        var controlSource = source();
        var businessSource = source();
        control = new JdbcTemplate(controlSource);
        var business = new JdbcTemplate(businessSource);
        String schema = "create table sym_tenant_t(tid varchar(32) primary key,json_config varchar(100),status int,is_del int)";
        control.execute(schema);
        business.execute(schema);
        control.update("insert into sym_tenant_t values('ga','{\"accountSource\":\"LOCAL\"}',1,0),('legacy','{}',1,0)");
        // A conflicting tenant copy must never decide the account source.
        business.update("insert into sym_tenant_t values('ga','{\"accountSource\":\"CONTROL\"}',1,0)");

        properties = new TenantDatabaseProperties();
        properties.setTenantBindings(Map.of("ga", "public-security"));
        registry = mock(TenantDataSourceRegistry.class);
        when(registry.targets()).thenReturn(Map.of("public-security", businessSource));

        var routing = new TenantRoutingDataSource(properties);
        routing.setDefaultTargetDataSource(controlSource);
        routing.setTargetDataSources(Map.of("control", controlSource, "public-security", businessSource));
        routing.afterPropertiesSet();

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setConfiguration(configuration);
        factory.setDataSource(routing);
        factory.setMapperLocations(new ClassPathResource("mapper/platform/tenant/SymTenantTMapper.xml"));
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(SymTenantTMapper.class);
        var target = new TenantAccountPolicy(mapper, new ObjectMapper(), properties, registry);
        org.springframework.test.util.ReflectionTestUtils.setField(target,"legacyControlAccountsEnabled",true);
        var proxy = new AspectJProxyFactory(target);
        proxy.addAspect(new ControlDataSourceAspect());
        policy = proxy.getProxy();
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void onlyExplicitLocalTenantSelectsBusinessSource() {
        assertTrue(policy.isLocal("ga"));
        assertFalse(policy.isLocal("legacy"));
        assertCode("TENANT-NOT-AVAILABLE", "missing");
        properties.setTenantBindings(Map.of());
        assertCode("TENANT-ACCOUNT-CONFIG-INVALID", "ga");
        control.update("update sym_tenant_t set json_config='{\"accountSource\":\"INVALID\"}' where tid='legacy'");
        assertCode("TENANT-ACCOUNT-CONFIG-INVALID", "legacy");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void absentTenantRetainsControlAccountCompatibility(String tenantId) {
        assertFalse(policy.isLocal(tenantId));
        verifyNoInteractions(registry);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "{}", "{\"accountSource\":\"CONTROL\"}"})
    void absentOrControlAccountConfigUsesControlAccounts(String config) {
        control.update("update sym_tenant_t set json_config=? where tid='legacy'", config);
        assertFalse(policy.isLocal("legacy"));
        verifyNoInteractions(registry);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"accountSource\":\"INVALID\"}", "not-json", "{\"accountSource\":"})
    void invalidAccountConfigFailsClosed(String config) {
        control.update("update sym_tenant_t set json_config=? where tid='legacy'", config);
        assertCode("TENANT-ACCOUNT-CONFIG-INVALID", "legacy");
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "deleted"})
    void inactiveAndDeletedTenantsAreUnavailable(String tenantId) {
        control.update("insert into sym_tenant_t values(?,?,?,?)", tenantId, "{}",
                "inactive".equals(tenantId) ? 0 : 1, "deleted".equals(tenantId) ? 1 : 0);
        assertCode("TENANT-NOT-AVAILABLE", tenantId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unbound", "control", "unregistered", "disabled"})
    void localAccountsRequireEnabledExplicitBusinessBinding(String condition) {
        switch (condition) {
            case "unbound" -> properties.setTenantBindings(Map.of());
            case "control" -> properties.setTenantBindings(Map.of("ga", "control"));
            case "unregistered" -> when(registry.targets()).thenReturn(Map.of());
            case "disabled" -> properties.setEnabled(false);
            default -> fail("Unexpected condition");
        }
        assertCode("TENANT-ACCOUNT-CONFIG-INVALID", "ga");
    }

    @Test
    void readsControlConfigInsideBusinessTenantScopeAndRestoresContext() {
        try (var ignored = TenantContext.use("ga")) {
            assertFalse(TenantContext.usesControlDatabase());
            assertTrue(policy.isLocal("ga"));
            assertFalse(TenantContext.usesControlDatabase());
            assertEquals("ga", TenantContext.getTenantId());
            assertCode("TENANT-NOT-AVAILABLE", "missing");
            assertFalse(TenantContext.usesControlDatabase());
            assertEquals("ga", TenantContext.getTenantId());
        }
        assertNull(TenantContext.getTenantId());
    }

    private void assertCode(String code, String tenantId) {
        var error = assertThrows(TenantAccessException.class, () -> policy.isLocal(tenantId));
        assertEquals(code, error.getCode());
    }

    @Test
    void finalStageRejectsSharedAccountFallbackWhenLegacyTablesAreGone() {
        org.springframework.test.util.ReflectionTestUtils.setField(policy,"legacyControlAccountsEnabled",false);
        assertTrue(policy.isLocal("ga"));
        assertCode("TENANT-ACCOUNT-SOURCE-RETIRED","legacy");
        assertCode("TENANT-ID-MISSING",null);
    }

    private DriverManagerDataSource source() {
        return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }
}
