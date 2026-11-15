package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
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

class TimerConfigurationMagicTest {
    private static final String PAGE = "7d2f4a9c1e8b4c6d0a3f5e7b9c1d2a84";
    private static final String MANAGE = "8e3a5b0d2f1c4d7e9a6b0c2f4d8e1b73";
    private static final String LOGS = "9f4b6c1e3a2d4f8b0c7d1e5a9b3f6c82";
    private static final String TENANT = "validation-tenant";
    private static final String OTHER = "validation-other";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pagingUsesTwoQueriesAndScopesTasksAndHistoricalLogs(int size) throws Exception {
        var f = new Fixture();
        f.jdbc.batchUpdate("insert into sys_timer_t(tid,tenant_id,timer_code,timer_name,status,is_del) values(?,?,?,?,'ENABLED',0)",
                IntStream.range(0,size).mapToObj(n -> new Object[]{"task-"+n,TENANT,"validation_"+n,"timer"}).toList());
        f.jdbc.batchUpdate("insert into sys_timer_log_t(tid,tenant_id,timer_id,timer_code,result) values(?,?,'inactive-task','validation','SUCCESS')",
                IntStream.range(0,size).mapToObj(n -> new Object[]{"log-"+n,TENANT}).toList());
        f.jdbc.update("insert into sys_timer_log_t(tid,tenant_id,timer_id,timer_code,result) values('foreign',?,'inactive-task','foreign','SUCCESS')",OTHER);
        var page = (Map<?, ?>) f.run(PAGE,Map.of(),Map.of("pageSize",size,"keyword","validation_","status","ENABLED"));
        assertThat((List<?>) page.get("list")).hasSize(size);
        assertThat(((Number) page.get("total")).intValue()).isEqualTo(size);
        assertThat(f.source.statements).hasValue(2);
        var logs = (Map<?, ?>) f.run(LOGS,Map.of(),Map.of("pageSize",size,"timerId","inactive-task"));
        assertThat((List<?>) logs.get("list")).hasSize(size);
        assertThat(((Number) logs.get("total")).intValue()).isEqualTo(size);
        assertThat(f.source.statements).hasValue(2);
        System.out.printf("Tenant timers %d: one list invocation with 2 SQL and one log invocation with 2 SQL%n",size);
    }

    @Test
    void previewAndSaveSupportFiveFieldsAndToggleUpdatesNextFireTime() throws Exception {
        var f = new Fixture();
        var preview = (Map<?, ?>) f.manage(form(Map.of("dryRun",true)));
        assertThat(preview.get("dryRun")).isEqualTo(true);
        assertThat(String.valueOf(preview.get("nextFireAt"))).isNotBlank();
        assertThat(f.count()).isZero();
        assertExit(f.manage(form(Map.of("paramsJson","{} {}"))),400);
        assertExit(f.manage(form(Map.of("cron","invalid"))),400);
        var saved = (Map<?, ?>) f.manage(form(Map.of()));
        var id = String.valueOf(saved.get("id"));
        assertThat(f.count()).isEqualTo(1);
        assertExit(f.manage(form(Map.of())),409);
        var toggled = (Map<?, ?>) f.manage(Map.of("action","toggle","id",id,"status","DISABLED"));
        assertThat(toggled.get("status")).isEqualTo("DISABLED");
        assertThat(f.jdbc.queryForObject("select next_fire_at from sys_timer_t where tid=?",Object.class,id)).isNull();
        f.manage(Map.of("action","toggle","id",id,"status","ENABLED"));
        assertThat(f.jdbc.queryForObject("select next_fire_at from sys_timer_t where tid=?",Object.class,id)).isNotNull();
        f.manage(form(Map.of("id",id,"status","DISABLED")));
        assertThat(f.jdbc.queryForObject("select next_fire_at from sys_timer_t where tid=?",Object.class,id)).isNull();
        assertExit(f.manage(Map.of("action","fire","timerCode","validation")),501);
        assertThat(f.jdbc.queryForObject("select count(*) from sys_timer_log_t",Integer.class)).isZero();
        assertThat(((Map<?, ?>) f.manage(Map.of("action","remove","id",id))).get("removed")).isEqualTo(true);
        assertThat(f.count()).isZero();
    }

    @Test
    void writesCheckCurrentTenantAndKeepConfigurationOnFailure() throws Exception {
        var f = new Fixture();
        var own = (Map<?, ?>) f.manage(form(Map.of("cron","0 * * * * *")));
        String id=String.valueOf(own.get("id"));
        assertExit(f.manage(form(Map.of("id","foreign","tenantId",OTHER))),404);
        assertExit(f.manage(Map.of("action","fire","timerCode","foreign","tenantId",OTHER)),404);
        assertExit(f.manage(Map.of("action","remove","id","foreign","tenantId",OTHER)),404);
        assertThatThrownBy(() -> f.manage(form(Map.of("id",id,"timerName","db-fail"))))
                .hasStackTraceContaining("timer_name_valid");
        assertThat(f.jdbc.queryForObject("select timer_name from sys_timer_t where tid=?",String.class,id))
                .isEqualTo("validation");
    }

    @Test
    void concurrentEditsCommitCompleteConfigurations() throws Exception {
        var f = new Fixture();
        String id = String.valueOf(((Map<?, ?>) f.manage(form(Map.of()))).get("id"));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                return f.manage(form(Map.of("id",id,"timerName","first","handler","bean:first")));
            });
            var second = pool.submit(() -> {
                start.await();
                return f.manage(form(Map.of("id",id,"timerName","second","handler","bean:second")));
            });
            start.countDown();
            assertThat(first.get(8,TimeUnit.SECONDS)).isInstanceOf(Map.class);
            assertThat(second.get(8,TimeUnit.SECONDS)).isInstanceOf(Map.class);
        }
        var current=f.jdbc.queryForMap("select timer_name,handler from sys_timer_t where tid=?",id);
        assertThat(current.get("handler")).isEqualTo("bean:"+current.get("timer_name"));
    }

    private static Map<String,Object> form(Map<String,?> changes) {
        var values = new LinkedHashMap<String,Object>(Map.of("timerCode","validation","timerName","validation",
                "cron","* * * * *","handler","bean:validation","paramsJson","{}"));
        values.putAll(changes);
        return values;
    }

    private static void assertExit(Object value, int code) {
        assertThat(value).isInstanceOfSatisfying(ExitValue.class,e ->
                assertThat(((Number)e.getValues()[0]).intValue()).isEqualTo(code));
    }

    private static class Fixture {
        final LakeGovernanceMagicTest.CountedSource source = new LakeGovernanceMagicTest.CountedSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final Object db = DatasourceCompletionMagicTest.magicDb(source);

        Fixture() {
            jdbc.execute("""
                    create table sys_timer_t(tid varchar(32) primary key,tenant_id varchar(32),timer_code varchar(64),
                    timer_name varchar(100) constraint timer_name_valid check(timer_name <> 'db-fail'),
                    cron varchar(100),handler varchar(200),params_json varchar(2000),status varchar(16),
                    last_fire_at timestamp,next_fire_at timestamp,lock_ttl_seconds int,description varchar(500),
                    created_by varchar(32),updated_by varchar(32),created_time timestamp,updated_time timestamp,
                    is_del int,unique(tenant_id,timer_code,is_del))
                    """);
            jdbc.execute("""
                    create table sys_timer_log_t(tid varchar(32) primary key,tenant_id varchar(32),timer_id varchar(32),
                    timer_code varchar(64),fire_at timestamp,result varchar(16),cost_ms int,error_msg varchar(1000),created_time timestamp)
                    """);
            jdbc.update("insert into sys_timer_t(tid,tenant_id,timer_code,timer_name,status,is_del) values('foreign',?,'foreign','foreign','ENABLED',0)",OTHER);
        }

        int count() {
            return jdbc.queryForObject("select count(*) from sys_timer_t where tenant_id=? and is_del=0",Integer.class,TENANT);
        }

        Object manage(Map<String,?> body) throws Exception {
            return run(MANAGE,body,Map.of());
        }

        Object run(String id, Map<String,?> body, Map<String,?> query) throws Exception {
            var script=CanonicalMagicSources.byId(id).replaceAll(
                    "(?m)^import (?:tableRead|tableWrite|tenantRuntime|[\"']@post:[^\\r\\n]+)[ \\t]*\\r?\\n","");
            var context=new LinkedHashMap<String,Object>(query);
            context.putAll(Map.of("body",body,"db",db,"tenantRuntime",new TenantMagicModule(null),
                    "tableRead",new StructuredTableReadMagicModule(jdbc),"tableWrite",new StructuredTableWriteMagicModule(jdbc),
                    "me",(MagicScriptLambdaFunction)(runtime,args) -> Map.of("id","validation-user")));
            source.statements.set(0);
            return TenantContext.call(TENANT,() -> MagicScript.create(script,null).execute(new MagicScriptContext(context)));
        }
    }
}
