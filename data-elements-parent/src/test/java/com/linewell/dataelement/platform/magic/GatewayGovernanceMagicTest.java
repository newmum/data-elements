package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class GatewayGovernanceMagicTest {
    private static final String INSTANCES = "ae5a7b6f3b1c4e9a2f4d5c6b7a8e9f04";
    private static final String POLICIES = "7b2d4e3c0e8f4b6d9c1a2f3e4d5b6c71";
    private static final String METRICS = "d18d0e9c6e4f7b2d5c7a8f9e0d1b2c37";
    private static final String PACKAGES = "a40b3b2f9b7c0e5a8f0d1c2b3a4e5f6a";
    private static final String TENANT = "validation-tenant";
    private static final String OTHER = "validation-other";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void instanceReadsDoNotScaleAndRejectAnUnauthorizedIdBeforeWriting(int size) throws Exception {
        var f = new Fixture();
        var rows = IntStream.range(0,size).mapToObj(i -> Map.of(
                "serviceCode","service","instanceUrl","https://validation.example.test/"+i)).toList();
        var first = f.instances("save",rows);
        assertThat(f.source.statements).hasValue(2);
        var replay = f.instances("save",rows);
        assertThat(f.source.statements).hasValue(3);
        assertThat(replay.get("ids")).isEqualTo(first.get("ids"));
        assertThat((List<?>) replay.get("ids")).hasSize(size);
        var ids = (List<?>) replay.get("ids");
        var updates = IntStream.range(0,size).mapToObj(i -> Map.of(
                "id",ids.get(i),"healthStatus",i%2==0?"UP":"DOWN")).toList();
        assertThat(f.instances("heartbeat",updates).get("updated")).isEqualTo(size);
        assertThat(f.source.statements).hasValue(3);
        f.jdbc.update("""
                insert into svc_gateway_instance_t(tid,tenant_id,service_code,version,instance_url,health_status,is_del)
                values('foreign',?,'service','v1','foreign','UNKNOWN',0)
                """,OTHER);
        Object denied=f.run(INSTANCES,Map.of("action","heartbeat","tenantId",OTHER,"instances",
                List.of(Map.of("id",ids.getFirst(),"healthStatus","DOWN"),Map.of("id","foreign","healthStatus","UP"))));
        assertExit(denied,404);
        assertThat(f.source.statements).hasValue(1);
        assertThat(f.jdbc.queryForObject("select health_status from svc_gateway_instance_t where tid=?",String.class,ids.getFirst()))
                .isEqualTo("UP");
        assertThat(f.jdbc.queryForObject("select health_status from svc_gateway_instance_t where tid='foreign'",String.class))
                .isEqualTo("UNKNOWN");
        System.out.printf("Gateway instances %d: register 2 SQL, replay 3 SQL, mixed heartbeat 3 SQL%n",size);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void replacesPoliciesInOneTransactionAndRetainsTheOldSetOnFailure(int size) throws Exception {
        var f = new Fixture();
        assertExit(f.run(POLICIES,Map.of("action","save","routeId","foreign")),404);
        var valid = IntStream.range(0,size).mapToObj(i -> Map.of("policyCode","validation_"+i)).toList();
        var result = (Map<?, ?>) f.run(POLICIES,Map.of("action","save","routeId","route","policies",valid));
        assertThat((List<?>) result.get("list")).hasSize(size);
        assertThat(f.source.statements).hasValue(4);
        assertExit(f.run(POLICIES,Map.of("action","save","routeId","route","policies",List.of(Map.of()))),400);
        assertThatThrownBy(() -> f.run(POLICIES,Map.of("action","save","routeId","route",
                "policies",List.of(Map.of("policyCode","ok"),Map.of("policyCode","db-fail")))))
                .hasStackTraceContaining("policy_valid");
        assertThat(f.policyCodes()).hasSize(size).allMatch(code -> code.startsWith("validation_"));
        System.out.printf("Gateway policies %d: replace 4 SQL; invalid row retains previous set%n",size);
    }

    @Test
    void concurrentPolicyWritesCommitOneCompleteSet() throws Exception {
        var f = new Fixture();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> {
                start.await();
                return f.run(POLICIES,policyRequest("left"));
            });
            var right = pool.submit(() -> {
                start.await();
                return f.run(POLICIES,policyRequest("right"));
            });
            start.countDown();
            assertThat(left.get(8,TimeUnit.SECONDS)).isInstanceOf(Map.class);
            assertThat(right.get(8,TimeUnit.SECONDS)).isInstanceOf(Map.class);
        }
        assertThat(f.policyCodes()).isIn(List.of("left1","left2"),List.of("right1","right2"));
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void metricInsertFailureRollsBackReplacement(int size) throws Exception {
        var f = new Fixture();
        f.jdbc.update("insert into svc_metric_hour_t(tid,tenant_id,stat_hour,service_code,is_del) values('old',?,'2026-01-01 01:00:00','old',0)",TENANT);
        f.jdbc.update("insert into svc_gateway_log_t values(?,0,'2026-01-01 01:00:00','db-fail','app',200,30)",TENANT);
        var body = Map.of("action","aggregate","statHour","2026-01-01 01:00:00",
                "startTime","2026-01-01 01:00:00","endTime","2026-01-01 02:00:00");
        assertThatThrownBy(() -> f.run(METRICS,body)).hasStackTraceContaining("metric_valid");
        assertThat(f.jdbc.queryForList("select tid from svc_metric_hour_t",String.class)).containsExactly("old");
        f.jdbc.update("update svc_gateway_log_t set service_code='valid_0'");
        f.jdbc.batchUpdate("insert into svc_gateway_log_t values(?,0,'2026-01-01 01:00:00',?,'app',200,30)",
                IntStream.range(1,size).mapToObj(i -> new Object[]{TENANT,"valid_"+i}).toList());
        assertThat(((Map<?, ?>) f.run(METRICS,body)).get("written")).isEqualTo(size);
        assertThat(f.source.statements).hasValue(3);
        System.out.printf("Gateway metrics %d groups: replace 3 SQL%n",size);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void packageItemFailureRollsBackParentAndPreviousItems(int size) throws Exception {
        var f = new Fixture();
        f.jdbc.update("insert into svc_market_package_t(tid,tenant_id,package_code,package_name,is_del) values('bundle',?,'BUNDLE','old',0)",TENANT);
        f.jdbc.update("insert into svc_market_package_item_t(tid,tenant_id,package_id,service_code,version,sort_no,is_del) values('old',?,'bundle','service','v1',0,0)",TENANT);
        var body=Map.of("action","save","id","bundle","packageCode","BUNDLE","packageName","new",
                "items",List.of(Map.of("serviceCode","service","sortNo",-1)));
        assertThatThrownBy(() -> f.run(PACKAGES,body)).hasStackTraceContaining("package_item_valid");
        assertThat(f.jdbc.queryForObject("select package_name from svc_market_package_t where tid='bundle'",String.class)).isEqualTo("old");
        assertThat(f.jdbc.queryForList("select tid from svc_market_package_item_t",String.class)).containsExactly("old");
        assertExit(f.run(PACKAGES,Map.of("action","save","id","bundle","packageCode","BUNDLE","packageName","new",
                "items",List.of(Map.of("serviceCode","foreign")))),400);
        f.jdbc.batchUpdate("insert into svc_gateway_route_t values(?,?,?,'v1',0)",
                IntStream.range(0,size).mapToObj(i -> new Object[]{"route-"+i,TENANT,"service_"+i}).toList());
        var items=IntStream.range(0,size).mapToObj(i -> Map.of("serviceCode","service_"+i)).toList();
        assertThat(f.run(PACKAGES,Map.of("action","save","id","bundle","packageCode","BUNDLE","packageName","new",
                "items",items))).isInstanceOf(Map.class);
        assertThat(f.source.statements).hasValue(6);
        assertThat(f.jdbc.queryForObject("select count(*) from svc_market_package_item_t where package_id='bundle'",
                Integer.class)).isEqualTo(size);
        var list=(Map<?, ?>) f.run(PACKAGES,Map.of("action","list"));
        assertThat((List<?>) list.get("list")).singleElement().satisfies(row ->
                assertThat((List<?>) ((Map<?, ?>)row).get("items")).hasSize(size));
        assertThat(f.source.statements).hasValue(2);
        System.out.printf("Gateway packages %d items: replace 6 SQL, list 2 SQL%n",size);
    }

    private static Map<String, Object> policyRequest(String name) {
        return Map.of("action","save","routeId","route","policies",
                List.of(Map.of("policyCode",name+"1"),Map.of("policyCode",name+"2")));
    }

    private static void assertExit(Object result, int code) {
        assertThat(result).isInstanceOfSatisfying(ExitValue.class,exit ->
                assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(code));
    }

    private static class Fixture {
        final LakeGovernanceMagicTest.CountedSource source = new LakeGovernanceMagicTest.CountedSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final Object db = DatasourceCompletionMagicTest.magicDb(source);

        Fixture() {
            jdbc.execute("create table svc_gateway_route_t(tid varchar(32) primary key,tenant_id varchar(32),service_code varchar(64),version varchar(32),is_del int)");
            jdbc.update("insert into svc_gateway_route_t values('route',?,'service','v1',0),('foreign',?,'foreign','v1',0)",TENANT,OTHER);
            jdbc.execute("""
                    create table svc_gateway_instance_t(tid varchar(32) primary key,tenant_id varchar(32),service_code varchar(64),
                    version varchar(32),instance_url varchar(512),weight int,health_status varchar(16),last_heartbeat timestamp,
                    status varchar(16),description varchar(500),created_by varchar(32),updated_by varchar(32),created_time timestamp,
                    updated_time timestamp,is_del int,unique(tenant_id,service_code,version,instance_url))
                    """);
            jdbc.execute("""
                    create table svc_gateway_policy_t(tid varchar(32) primary key,tenant_id varchar(32),route_id varchar(32),
                    policy_code varchar(64) constraint policy_valid check(policy_code <> 'db-fail'),policy_name varchar(100),priority int,
                    auth_mode varchar(16),ip_allow varchar(1000),ip_deny varchar(1000),qps int,burst int,circuit_threshold int,
                    circuit_window_ms int,retry_backoff_ms int,param_rules_json clob,status varchar(16),description varchar(500),
                    created_by varchar(32),updated_by varchar(32),created_time timestamp,updated_time timestamp,is_del int)
                    """);
            jdbc.execute("create table svc_gateway_log_t(tenant_id varchar(32),is_del int,created_time timestamp,service_code varchar(64),app_id varchar(32),status_code int,cost_ms int)");
            jdbc.execute("""
                    create table svc_metric_hour_t(tid varchar(32) primary key,tenant_id varchar(32),stat_hour timestamp,
                    service_code varchar(64) constraint metric_valid check(service_code <> 'db-fail'),app_id varchar(32),call_count bigint,
                    success_count bigint,fail_count bigint,p95_ms int,active_users int,created_time timestamp,updated_time timestamp,is_del int)
                    """);
            jdbc.execute("""
                    create table svc_market_package_t(tid varchar(32) primary key,tenant_id varchar(32),package_code varchar(64),
                    package_name varchar(100),subscription_mode varchar(16),approval_required int,status varchar(16),description varchar(500),
                    sort_no int,created_by varchar(32),updated_by varchar(32),created_time timestamp,updated_time timestamp,is_del int)
                    """);
            jdbc.execute("""
                    create table svc_market_package_item_t(tid varchar(32) primary key,tenant_id varchar(32),package_id varchar(32),
                    service_code varchar(64),version varchar(32),sort_no int constraint package_item_valid check(sort_no >= 0),
                    created_by varchar(32),updated_by varchar(32),created_time timestamp,updated_time timestamp,is_del int)
                    """);
        }

        Object run(String id, Map<String,?> body) throws Exception {
            var script = CanonicalMagicSources.byId(id)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\r?\\n","");
            var context = new MagicScriptContext(Map.of("body",body,"db",db,"tenantRuntime",new TenantMagicModule(null),
                    "tableRead",new StructuredTableReadMagicModule(jdbc),"tableWrite",new StructuredTableWriteMagicModule(jdbc),
                    "me",(MagicScriptLambdaFunction) (runtime,args) -> Map.of("id","validation-user")));
            source.statements.set(0);
            return TenantContext.call(TENANT,() -> MagicScript.create(script,null).execute(context));
        }

        Map<?, ?> instances(String action, List<?> items) throws Exception {
            return (Map<?, ?>) run(INSTANCES,Map.of("action",action,"instances",items));
        }

        List<String> policyCodes() {
            return jdbc.queryForList("select policy_code from svc_gateway_policy_t where tenant_id=? order by policy_code",String.class,TENANT);
        }
    }
}
