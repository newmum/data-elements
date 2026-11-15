package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class LakeGovernanceMagicTest {
    private static final String PAGE = "d2e6b6a85fd549718eace4d68b108af6";
    private static final String REGISTER = "55e367e198734f66bb37efe17a7fc9b0";
    private static final String RULES = "70cfff4ea75848ba85f7c5f5a645ce52";
    private static final String TENANT = "validation-tenant";
    private static final String FOREIGN = "validation-other";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void registersAndReadsOnePageWithConstantScopedStatements(int size) throws Exception {
        var fixture = new Fixture();
        fixture.jdbc.update("""
                insert into lake_zone_object_t(tid,tenant_id,zone_id,object_type,object_ref,is_del)
                values('foreign-object',?,'zone','TABLE','validation_0',0),
                      ('deleted-object',?,'zone','TABLE','validation_deleted',1)
                """, FOREIGN, TENANT);
        var objects = IntStream.range(0, size).mapToObj(i ->
                Map.of("objectType", "TABLE", "objectRef", "validation_" + i)).toList();
        fixture.source.statements.set(0);
        var registration = (Map<?, ?>) fixture.run(REGISTER, "zone", Map.of(), Map.of("objects", objects));
        assertThat(((Number) registration.get("inserted")).intValue()).isEqualTo(size);
        assertThat(((Number) registration.get("skipped")).intValue()).isZero();
        assertThat((List<?>) registration.get("objects")).hasSize(size);
        assertThat(fixture.source.statements).hasValue(3);

        fixture.source.statements.set(0);
        var page = (Map<?, ?>) fixture.run(PAGE, "zone",
                Map.of("pageNo",1,"pageSize",size,"keyword","validation_%","objectType","TABLE"), Map.of());
        assertThat((List<?>) page.get("list")).hasSize(size);
        assertThat(((Number) page.get("total")).intValue()).isEqualTo(size);
        assertThat(fixture.source.statements).hasValue(3);

        fixture.source.statements.set(0);
        var replay = (Map<?, ?>) fixture.run(REGISTER, "zone", Map.of(), Map.of("objects", objects));
        assertThat(((Number) replay.get("inserted")).intValue()).isZero();
        assertThat(fixture.source.statements).hasValue(2);
        System.out.printf("Lake objects %,d: page 3 SQL, register 3 SQL, replay 2 SQL%n", size);
    }

    @Test
    void rejectsForeignAndDeletedZonesBeforeReadingOrChangingTheirObjects() throws Exception {
        var fixture = new Fixture();
        for (String zone : List.of("foreign-zone", "deleted-zone")) {
            for (String resource : List.of(PAGE, REGISTER, RULES)) {
                fixture.source.statements.set(0);
                assertThat(fixture.run(resource, zone, Map.of(), Map.of("tenantId", FOREIGN)))
                        .isInstanceOfSatisfying(ExitValue.class, exit ->
                                assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(404));
                assertThat(fixture.source.statements).hasValue(1);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void replacesRulesAtomicallyAndKeepsTheOldSetOnInsertFailure(int size) throws Exception {
        var fixture = new Fixture();
        fixture.jdbc.update("""
                insert into lake_zone_rule_t(tid,tenant_id,zone_id,rule_code,rule_name,rule_type,is_del)
                values('old',?,'zone','old','old','IN',0),('foreign',?,'zone','foreign','foreign','IN',0)
                """, TENANT, FOREIGN);
        var invalid = List.of(rule("valid","valid"),rule("fails","保存失败"));
        assertThatThrownBy(() -> fixture.run(RULES,"zone",Map.of(),Map.of("rules",invalid)))
                .hasStackTraceContaining("rule_name_valid");
        assertThat(fixture.ruleCodes()).containsExactly("old");
        var replacement=IntStream.range(0,size).mapToObj(i -> rule("validation_"+i,"valid")).toList();
        fixture.source.statements.set(0);
        var result=(Map<?, ?>) fixture.run(RULES,"zone",Map.of(),Map.of("rules",replacement));
        assertThat(((Number) result.get("saved")).intValue()).isEqualTo(size);
        assertThat(fixture.source.statements).hasValue(3);
        assertThat(fixture.ruleCodes()).hasSize(size).doesNotContain("old");
        assertThat(fixture.jdbc.queryForObject("select count(*) from lake_zone_rule_t where tenant_id=?",
                Integer.class, FOREIGN)).isEqualTo(1);
        System.out.printf("Lake rules %,d: replace 3 SQL; failed insert rolls back the delete%n",size);
    }

    @Test
    void concurrentReplacementsLeaveOneCompleteRuleSet() throws Exception {
        var fixture = new Fixture();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                return fixture.run(RULES,"zone",Map.of(),Map.of("rules",List.of(rule("a1","a"),rule("a2","a"))));
            });
            var second = pool.submit(() -> {
                start.await();
                return fixture.run(RULES,"zone",Map.of(),Map.of("rules",List.of(rule("b1","b"),rule("b2","b"))));
            });
            start.countDown();
            assertThat(first.get(8, TimeUnit.SECONDS)).isInstanceOf(Map.class);
            assertThat(second.get(8, TimeUnit.SECONDS)).isInstanceOf(Map.class);
        }
        assertThat(fixture.ruleCodes()).isIn(List.of("a1","a2"),List.of("b1","b2"));
    }

    private static Map<String, String> rule(String code, String name) {
        return Map.of("ruleCode",code,"ruleName",name,"ruleType","IN","objectScope","ALL");
    }

    private static class Fixture {
        final CountedSource source = new CountedSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final Object db = DatasourceCompletionMagicTest.magicDb(source);

        Fixture() {
            jdbc.execute("create table lake_zone_t(tid varchar(32) primary key,tenant_id varchar(32),is_del int)");
            jdbc.update("insert into lake_zone_t values ('zone',?,0),('foreign-zone',?,0),('deleted-zone',?,1)",
                    TENANT, FOREIGN, TENANT);
            jdbc.execute("""
                    create table lake_zone_object_t(
                        tid varchar(32) primary key, tenant_id varchar(32), zone_id varchar(32),
                        object_type varchar(16), object_ref varchar(128), object_name varchar(128), source_id varchar(32),
                        lifecycle_status varchar(16), last_sync_at timestamp, registered_by varchar(32),
                        created_time timestamp, updated_time timestamp, is_del int,
                        unique(tenant_id,zone_id,object_type,object_ref,is_del))
                    """);
            jdbc.execute("""
                    create table lake_zone_rule_t(
                        tid varchar(32) primary key, tenant_id varchar(32), zone_id varchar(32),
                        rule_code varchar(32), rule_name varchar(100) constraint rule_name_valid check(rule_name <> '保存失败'),
                        rule_type varchar(8), object_scope varchar(32), file_format varchar(64), compression varchar(32),
                        partition_rule varchar(256), retention_days int, approval_required int, interaction_mode varchar(32),
                        priority int, status varchar(16), description varchar(500), created_by varchar(32), updated_by varchar(32),
                        created_time timestamp, updated_time timestamp, is_del int)
                    """);
        }

        Object run(String id, String zone, Map<String, Object> query, Map<String, ?> body) throws Exception {
            var sourceCode = CanonicalMagicSources.byId(id)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\r?\\n", "");
            var params = new LinkedHashMap<>(query);
            params.put("id",zone);
            var context = new MagicScriptContext(Map.of(
                    "param",params,"body",body,"db",db,"tenantRuntime",new TenantMagicModule(null),
                    "tableRead",new StructuredTableReadMagicModule(jdbc),"tableWrite",new StructuredTableWriteMagicModule(jdbc),
                    "me",(MagicScriptLambdaFunction) (runtime,args) -> Map.of("id","validation-user")));
            return TenantContext.call(TENANT,() -> MagicScript.create(sourceCode,null).execute(context));
        }

        List<String> ruleCodes() {
            return jdbc.queryForList("select rule_code from lake_zone_rule_t where tenant_id=? order by rule_code",
                    String.class,TENANT);
        }
    }

    static class CountedSource extends DriverManagerDataSource {
        final AtomicInteger statements = new AtomicInteger();

        CountedSource() {
            super("jdbc:h2:mem:lake_" + System.nanoTime()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getMetaData")) {
                            var metadata = connection.getMetaData();
                            return Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                                    new Class<?>[]{DatabaseMetaData.class}, (ignored, name, arguments) ->
                                            name.getName().equals("getURL") ? "jdbc:mysql://lake-validation"
                                                    : invoke(metadata,name,arguments));
                        }
                        if (method.getName().equals("prepareStatement")) statements.incrementAndGet();
                        return invoke(connection,method,args);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target,args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }
}
