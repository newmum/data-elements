package com.linewell.dataelement.integration.workflow;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.linewell.dataelement.feature.identity.application.ControlIdentityService;
import com.linewell.dataelement.feature.identity.application.IdentityDirectoryServiceImpl;
import com.linewell.dataelement.feature.identity.application.TenantIdentityQueryService;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityDirectoryMapper;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserOrganizationRelationMapper;
import com.linewell.dataelement.platform.tenant.application.TenantAccountPolicy;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.IntStream;
import org.dromara.warm.flow.ui.dto.HandlerQuery;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WarmFlowDirectoryTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void projectsNativeRowsAndResolvesEachRequestedIdWithinTheCurrentTenant(int size) throws Exception {
        var dataSource = new CountedDataSource();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                create table rm_user_t(id varchar(32), tenant_id varchar(32), real_name varchar(64), user_name varchar(64),
                deleted int, status int, phone varchar(32), gender int, app_file_id varchar(32), create_id varchar(32),
                create_time timestamp, update_time timestamp, id_card varchar(32), date_birth date, nation varchar(64),
                office_address varchar(128), job_number varchar(32), authorized_strength varchar(64),
                origin_position varchar(64), origin_rank varchar(64))
                """);
        jdbc.execute("""
                create table rm_org_t(id varchar(32), tenant_id varchar(32), serial_number varchar(32), name varchar(64),
                parent_id varchar(32), status int, sort_num int, deleted int, create_time timestamp, update_time timestamp)
                """);
        jdbc.execute("""
                create table rm_role_t(id varchar(32), tenant_id varchar(32), code_num varchar(32), name varchar(64),
                app_id varchar(32), description varchar(64), status int, sort_num int, deleted int,
                create_time timestamp, update_time timestamp)
                """);
        for (int index = 0; index < 100; index++) {
            String id = "validation-user-%03d".formatted(index);
            jdbc.update("insert into rm_user_t(id,tenant_id,real_name,user_name,deleted,status) values (?,'ga',?,?,0,0)",
                    id, "姓名" + index, "账号" + index);
        }
        jdbc.update("insert into rm_user_t(id,tenant_id,real_name,user_name,deleted,status) values ('validation-other','ct','其他租户名称','他人账号',0,0)");
        jdbc.update("insert into rm_org_t(id,tenant_id,name,sort_num,deleted) values ('validation-org','ga','当前组织',1,0)");
        jdbc.update("insert into rm_org_t(id,tenant_id,name,sort_num,deleted) values ('validation-other-org','ct','其他组织',1,0)");
        jdbc.update("insert into rm_role_t(id,tenant_id,name,code_num,sort_num,deleted,create_time) values ('validation-role','ga','当前角色','VALIDATION',1,0,TIMESTAMP '2026-01-02 03:04:05')");
        jdbc.update("insert into rm_role_t(id,tenant_id,name,code_num,sort_num,deleted) values ('validation-other-role','ct','其他角色','OTHER',1,0)");
        var policy = mock(TenantAccountPolicy.class);
        when(policy.isLocal("ga")).thenReturn(true);
        var legacy = mock(ControlIdentityService.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factory.setPlugins(pagination);
        factory.setMapperLocations(new ClassPathResource("mapper/feature/identity/IdentityDirectoryMapper.xml"));
        try (var tenant = TenantContext.use("ga"); var session = factory.getObject().openSession()) {
            var service = new HandlerSelectServiceImpl(new IdentityDirectoryServiceImpl(session.getMapper(IdentityDirectoryMapper.class),
                    mock(IdentityUserOrganizationRelationMapper.class), new TenantIdentityQueryService(legacy, policy, jdbc)));
            var query = new HandlerQuery();
            query.setHandlerType("用户");
            query.setPageNum(1);
            query.setPageSize(size);
            var select = service.getHandlerSelect(query);
            assertThat(select.getHandlerAuths().getRows()).hasSize(size).allSatisfy(row -> {
                assertThat(row.getStorageId()).startsWith("validation-user-");
                assertThat(row.getHandlerCode()).startsWith("账号");
                assertThat(row.getHandlerName()).startsWith("姓名");
            });
            assertThat(select.getTreeSelections()).hasSize(1);
            assertThat(dataSource.reads).hasSize(3).allMatch(sql -> sql.contains("tenant_id"));

            var ids = new ArrayList<>(IntStream.range(0, size - 1).mapToObj(index -> "validation-user-%03d".formatted(index)).toList());
            ids.add("validation-other");
            dataSource.reads.clear();
            var resolved = service.handlerFeedback(ids);
            assertThat(resolved).hasSize(size);
            for (int index = 0; index < size - 1; index++) {
                assertThat(resolved.get(index).getHandlerName()).isEqualTo("姓名" + index);
            }
            assertThat(resolved.getLast().getHandlerName()).isEqualTo("validation-other");
            assertThat(dataSource.reads).hasSize(2).allMatch(sql -> sql.contains("tenant_id") && sql.contains("id in"));

            query.setHandlerType("角色");
            dataSource.reads.clear();
            var roles = service.getHandlerSelect(query).getHandlerAuths().getRows();
            assertThat(roles).singleElement().satisfies(role -> {
                assertThat(role.getStorageId()).isEqualTo("role:validation-role");
                assertThat(role.getHandlerName()).isEqualTo("当前角色");
                assertThat(role.getHandlerCode()).isEqualTo("VALIDATION");
                assertThat(role.getCreateTime()).isEqualTo("2026-01-02 03:04:05");
            });
            assertThat(dataSource.reads).hasSize(2).allMatch(sql -> sql.contains("tenant_id"));
            dataSource.reads.clear();
            var roleNames = service.handlerFeedback(java.util.List.of("role:validation-role", "role:validation-other-role"));
            assertThat(roleNames.getFirst().getHandlerName()).isEqualTo("当前角色");
            assertThat(roleNames.getLast().getHandlerName()).isEqualTo("role:validation-other-role");
            assertThat(dataSource.reads).singleElement().satisfies(sql -> assertThat(sql).contains("tenant_id", "id in"));
            verifyNoInteractions(legacy);
            System.out.printf("Warm-Flow size=%d: user selector=3 SQL, user feedback=2 SQL, role selector=2 SQL, role feedback=1 SQL; foreign ID excluded%n", size);
        }
    }

    private static final class CountedDataSource extends DriverManagerDataSource {
        final ArrayList<String> reads = new ArrayList<>();

        CountedDataSource() {
            super("jdbc:h2:mem:warmflow_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection delegate = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("prepareStatement") && arguments[0] instanceof String sql
                                && sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) {
                            reads.add(sql);
                        }
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }
    }
}
