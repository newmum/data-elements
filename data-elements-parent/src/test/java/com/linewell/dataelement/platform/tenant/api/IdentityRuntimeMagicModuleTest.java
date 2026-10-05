package com.linewell.dataelement.platform.tenant.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.identity.application.ControlIdentityService;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class IdentityRuntimeMagicModuleTest {

    private final TenantAccessService access = mock(TenantAccessService.class);
    private final ControlIdentityService accounts = mock(ControlIdentityService.class);
    private final TenantDatabaseProperties properties = new TenantDatabaseProperties();
    private final TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
    private final DataSource routing = mock(DataSource.class);
    private IdentityRuntimeMagicModule runtime;

    @BeforeEach
    void setUp() {
        properties.setTenantBindings(Map.of("police", "public-security"));
        when(registry.targets()).thenReturn(Map.of("public-security", mock(DataSource.class)));
        runtime = new IdentityRuntimeMagicModule(access, accounts, properties, registry, routing);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.unbindResourceIfPossible(routing);
    }

    @Test
    void controlClosureRestoresTenantAfterSuccessAndFailure() {
        TenantContext.bind("police");
        assertEquals("ok", runtime.control(() -> {
            assertTrue(TenantContext.usesControlDatabase());
            assertEquals("police", TenantContext.requireTenantId());
            return "ok";
        }));
        assertFalse(TenantContext.usesControlDatabase());
        assertThrows(IllegalStateException.class, () -> runtime.control(() -> {
            throw new IllegalStateException("rollback");
        }));
        assertEquals("police", TenantContext.requireTenantId());
        assertFalse(TenantContext.usesControlDatabase());
    }

    @Test
    void supportsRealMagicSupplierConversion() {
        TenantContext.bind("police");
        MagicScriptContext context = new MagicScriptContext(Map.of("runtime", runtime));
        Object result = MagicScript.create("return runtime.control(() => { return 42; });", null)
                .execute(context);
        assertEquals(42, result);
        assertEquals("police", TenantContext.requireTenantId());
        assertFalse(TenantContext.usesControlDatabase());
    }

    @Test
    void refusesControlSwitchInsideTenantTransaction() {
        TenantContext.bind("police");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertEquals("IDENTITY-CROSS-DATABASE-TRANSACTION", assertThrows(
                TenantAccessException.class, () -> runtime.control(() -> "must not execute")
        ).getCode());
        assertFalse(TenantContext.usesControlDatabase());
    }

    @Test
    void refusesControlSwitchWithBoundRoutingConnection() {
        TenantContext.bind("police");
        TransactionSynchronizationManager.bindResource(routing, new Object());
        assertThrows(TenantAccessException.class, () -> runtime.control(() -> "must not execute"));
    }

    @Test
    void allowsAlreadyControlScopedTransaction() {
        try (TenantContext.Scope ignored = TenantContext.control()) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertEquals("nested", runtime.control(() -> "nested"));
            assertTrue(TenantContext.usesControlDatabase());
        }
    }

    @Test
    void explicitBindingNeverUsesConfiguredDefault() {
        TenantContext.bind("police");
        assertEquals("public-security", runtime.requireTenantBinding("police"));
        assertThrows(TenantAccessException.class, () -> runtime.requireTenantBinding("unassigned"));
        assertThrows(TenantAccessException.class, () -> runtime.requireTenantBinding(null));
        assertEquals("police", TenantContext.requireTenantId());
    }

    @Test
    void rejectsDisabledRoutingControlBindingAndAbsentDatasource() {
        properties.setEnabled(false);
        assertThrows(TenantAccessException.class, () -> runtime.requireTenantBinding("police"));
        properties.setEnabled(true);
        properties.setTenantBindings(Map.of("police", "control"));
        assertThrows(TenantAccessException.class, () -> runtime.requireTenantBinding("police"));
        properties.setTenantBindings(Map.of("police", "not-registered"));
        assertThrows(TenantAccessException.class, () -> runtime.requireTenantBinding("police"));
    }

    @Test
    void strictRechecksMembershipAndAccountOnEachRequest() {
        try (MockedStatic<StpUtil> session = login("police")) {
            when(accounts.find("police", "user-one")).thenReturn(Map.of("status", 0));
            assertEquals(Map.of("tenantId", "police", "userId", "user-one"), runtime.strict());
            verify(access).requireMembership("user-one", "police");
            when(access.requireMembership("user-one", "police"))
                    .thenThrow(new TenantAccessException("TENANT-FORBIDDEN", "membership revoked"));
            assertThrows(TenantAccessException.class, runtime::strict);
            verify(access, never()).resolve(anyString());
        }
    }

    @Test
    void strictRejectsDeletedOrDisabledAccount() {
        try (MockedStatic<StpUtil> session = login("police")) {
            when(accounts.find("police", "user-one")).thenReturn(null);
            assertThrows(TenantAccessException.class, runtime::strict);
            when(accounts.find("police", "user-one")).thenReturn(Map.of("status", 1));
            assertEquals("IDENTITY-ACCOUNT-UNAVAILABLE", assertThrows(
                    TenantAccessException.class, runtime::strict).getCode());
        }
    }

    @Test
    void strictRejectsTokenTenantMismatchBeforeReadingAccounts() {
        try (MockedStatic<StpUtil> session = login("broadcast")) {
            assertEquals("IDENTITY-TENANT-CONTEXT-MISMATCH", assertThrows(
                    TenantAccessException.class, runtime::strict).getCode());
            verifyNoInteractions(access, accounts);
        }
    }

    @Test
    void strictRequiresLoginAndRunsBeforeTransactions() {
        try (MockedStatic<StpUtil> session = mockStatic(StpUtil.class)) {
            session.when(StpUtil::isLogin).thenReturn(false);
            assertEquals("IDENTITY-LOGIN-REQUIRED", assertThrows(
                    TenantAccessException.class, runtime::strict).getCode());
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertEquals("IDENTITY-TRANSACTION-ORDER", assertThrows(
                    TenantAccessException.class, runtime::strict).getCode());
            verifyNoInteractions(access, accounts);
        }
    }

    @Test
    void inspectionUsesMysqlCatalogAndDamengSqlSchema() throws Exception {
        DataSource database = mock(DataSource.class);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        java.sql.DatabaseMetaData metadata = mock(java.sql.DatabaseMetaData.class);
        when(registry.dataSourceForTenant("police")).thenReturn(database);
        when(database.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.getCatalog()).thenReturn("baseline_ga_old");
        when(connection.getSchema()).thenReturn(null);
        assertEquals("baseline_ga_old", runtime.inspectTenantBinding("police").get("schema"));

        when(metadata.getDatabaseProductName()).thenReturn("DM DBMS");
        when(connection.getCatalog()).thenReturn(null);
        when(connection.getSchema()).thenReturn("BASELINE_BEIJING_GD");
        assertEquals("BASELINE_BEIJING_GD", runtime.inspectTenantBinding("police").get("schema"));
        when(connection.getCatalog()).thenReturn("");
        assertEquals("BASELINE_BEIJING_GD", runtime.inspectTenantBinding("police").get("schema"));
        verify(connection, times(3)).close();
    }

    @Test
    void detectsOnlyExactTableInCurrentTenantDatabase() throws Exception {
        TenantContext.bind("police");
        DataSource tenantDatabase = new DriverManagerDataSource("jdbc:h2:mem:idaas-table-check;DB_CLOSE_DELAY=-1");
        when(registry.dataSourceForTenant("police")).thenReturn(tenantDatabase);
        try (var connection = tenantDatabase.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table iam_user_profile_t (id varchar(32))");
        }
        assertTrue(runtime.hasTable("iam_user_profile_t"));
        assertFalse(runtime.hasTable("iam_audit_t"));
        assertThrows(IllegalArgumentException.class, () -> runtime.hasTable("other.iam_user_profile_t"));
        assertThrows(IllegalArgumentException.class, () -> runtime.hasTable("iam_%"));
        try (TenantContext.Scope ignored = TenantContext.control()) {
            assertThrows(TenantAccessException.class, () -> runtime.hasTable("iam_user_profile_t"));
        }
    }

    private MockedStatic<StpUtil> login(String tokenTenant) {
        TenantContext.bind("police");
        SaSession tokenSession = mock(SaSession.class);
        when(tokenSession.get("tenantId")).thenReturn(tokenTenant);
        MockedStatic<StpUtil> session = mockStatic(StpUtil.class);
        session.when(StpUtil::isLogin).thenReturn(true);
        session.when(StpUtil::getTokenSession).thenReturn(tokenSession);
        session.when(StpUtil::getLoginId).thenReturn("user-one");
        return session;
    }
}
