package com.linewell.dataelement.platform.tenant.api;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.identity.application.ControlIdentityService;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/**
 * Infrastructure boundaries for identity Magic scripts. Business validation,
 * persistence and orchestration stay in the editable Magic resources.
 */
@Component
@MagicModule("identityRuntime")
public class IdentityRuntimeMagicModule {

    private final TenantAccessService tenantAccessService;
    private final ControlIdentityService controlIdentityService;
    private final TenantDatabaseProperties databaseProperties;
    private final TenantDataSourceRegistry dataSourceRegistry;
    private final DataSource routingDataSource;
    @Value("${idaas.receiver.routes:}")
    private String receiverRoutes = "";

    /** Deployment-owned route lookup; message fields cannot select a schema. Trust is verified in tenant Magic. */
    public Map<String, String> receiverRoute(String keyId) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9_-]{3,64}") || receiverRoutes.isBlank())
            throw new IllegalArgumentException("接收实例未登记此密钥标识");
        var route = JSONUtil.parseObj(receiverRoutes).getJSONObject(keyId);
        if (route == null) throw new IllegalArgumentException("接收实例未登记此密钥标识");
        String tenant = route.getStr("tenantId"), app = route.getStr("appId"), instance = route.getStr("instanceId");
        if (tenant == null || app == null || instance == null) throw new IllegalStateException("接收路由配置不完整");
        requireTenantBinding(tenant);
        return Map.of("tenantId", tenant, "appId", app, "instanceId", instance);
    }

    public IdentityRuntimeMagicModule(
            TenantAccessService tenantAccessService,
            ControlIdentityService controlIdentityService,
            TenantDatabaseProperties databaseProperties,
            TenantDataSourceRegistry dataSourceRegistry,
            @Qualifier("dataSource") DataSource routingDataSource
    ) {
        this.tenantAccessService = tenantAccessService;
        this.controlIdentityService = controlIdentityService;
        this.databaseProperties = databaseProperties;
        this.dataSourceRegistry = dataSourceRegistry;
        this.routingDataSource = routingDataSource;
    }

    @Comment("在控制库上下文执行闭包；事务必须在闭包内部开启")
    public Object control(Supplier<Object> action) {
        Objects.requireNonNull(action, "action");
        if (!TenantContext.usesControlDatabase() && hasTransaction()) {
            throw new TenantAccessException(
                    "IDENTITY-CROSS-DATABASE-TRANSACTION",
                    "不能在已开启的租户事务中切换控制库，请先结束当前事务"
            );
        }
        try (TenantContext.Scope ignored = TenantContext.control()) {
            return action.get();
        }
    }

    @Comment("严格校验当前登录、成员资格和租户数据源；必须在业务事务之前调用")
    public Map<String, String> strict() {
        if (hasTransaction()) {
            throw new TenantAccessException(
                    "IDENTITY-TRANSACTION-ORDER",
                    "身份上下文校验必须在业务事务之前执行"
            );
        }
        if (!StpUtil.isLogin()) {
            throw new TenantAccessException("IDENTITY-LOGIN-REQUIRED", "请先登录统一身份管理平台");
        }
        String tenantId = TenantContext.requireTenantId();
        Object sessionTenant = StpUtil.getTokenSession().get("tenantId");
        if (TenantContext.usesControlDatabase()
                || sessionTenant == null
                || !tenantId.equals(String.valueOf(sessionTenant))) {
            throw new TenantAccessException(
                    "IDENTITY-TENANT-CONTEXT-MISMATCH", "登录租户上下文无效，请重新登录"
            );
        }
        requireTenantBinding(tenantId);
        String userId = String.valueOf(StpUtil.getLoginId());
        tenantAccessService.requireMembership(userId, tenantId);
        Map<String, Object> account = controlIdentityService.find(tenantId, userId);
        if (account == null || !"0".equals(String.valueOf(account.get("status")))) {
            throw new TenantAccessException("IDENTITY-ACCOUNT-UNAVAILABLE", "当前账号已停用或不存在");
        }
        return Map.of("tenantId", tenantId, "userId", userId);
    }

    @Comment("校验租户具有显式业务数据源绑定，不使用默认回退且不改变当前上下文")
    public String requireTenantBinding(String tenantId) {
        String key = tenantId == null ? null : databaseProperties.getTenantBindings().get(tenantId);
        if (!databaseProperties.isEnabled()
                || tenantId == null || tenantId.isBlank()
                || key == null || key.isBlank()
                || TenantDatabaseProperties.CONTROL_KEY.equals(key)
                || !(dataSourceRegistry.targets().get(key) instanceof DataSource)) {
            throw new TenantAccessException(
                    "IDENTITY-TENANT-DATASOURCE-UNBOUND", "当前租户尚未配置可用的业务数据源"
            );
        }
        return key;
    }

    @Comment("核验已登记租户的有效数据源并返回非秘密运行标识；不改变路由配置")
    public Map<String, String> inspectTenantBinding(String tenantId) throws SQLException {
        String key = requireTenantBinding(tenantId);
        try (Connection connection = dataSourceRegistry.dataSourceForTenant(tenantId).getConnection()) {
            String catalog = connection.getCatalog();
            String sqlSchema = connection.getSchema();
            String namespace = catalog != null && !catalog.isBlank() ? catalog : sqlSchema;
            return Map.of("dataSourceKey", key, "schema", namespace == null ? "" : namespace,
                    "databaseProduct", connection.getMetaData().getDatabaseProductName(),
                    "sqlSchema", sqlSchema == null ? "" : sqlSchema);
        }
    }

    @Comment("检查当前租户业务库是否已安装指定表；表名只允许字母、数字和下划线")
    public boolean hasTable(String table) throws SQLException {
        if (table == null || !table.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("表名格式不正确");
        }
        String tenantId = TenantContext.requireTenantId();
        if (TenantContext.usesControlDatabase()) {
            throw new TenantAccessException(
                    "IDENTITY-TENANT-CONTEXT-MISMATCH", "检查租户表必须使用租户业务上下文"
            );
        }
        requireTenantBinding(tenantId);
        DataSource dataSource = dataSourceRegistry.dataSourceForTenant(tenantId);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String normalized = metadata.storesUpperCaseIdentifiers()
                    ? table.toUpperCase(java.util.Locale.ROOT)
                    : metadata.storesLowerCaseIdentifiers()
                            ? table.toLowerCase(java.util.Locale.ROOT) : table;
            String escape = metadata.getSearchStringEscape();
            String pattern = escape == null || escape.isEmpty()
                    ? normalized : normalized.replace("_", escape + "_");
            try (ResultSet tables = metadata.getTables(
                    connection.getCatalog(), connection.getSchema(), pattern,
                    new String[]{"TABLE", "VIEW"}
            )) {
                while (tables.next()) {
                    if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.hasResource(routingDataSource);
    }

    @Comment("供已完成登录或服务签名验证的Magic闭包选择显式绑定；不能在事务内切库")
    public Object inTenant(String tenantId, Supplier<Object> action) {
        if (hasTransaction()) throw new IllegalStateException("必须在开启事务前选择业务库");
        requireTenantBinding(tenantId);
        try (TenantContext.Scope ignored = TenantContext.use(tenantId)) {
            return action.get();
        }
    }
}
