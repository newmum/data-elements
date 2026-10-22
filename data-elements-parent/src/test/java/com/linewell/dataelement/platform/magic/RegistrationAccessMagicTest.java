package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** 用真实Magic解析和SQL验证登记申请、任务动作及20/100项固定批量读取。 */
class RegistrationAccessMagicTest {
    static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    static String source(String relative) throws Exception {
        String all = Files.readString(ROOT.resolve("magic").resolve(relative));
        return all.substring(all.indexOf("================================")+32).replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "");
    }
    static class Fixture {
        final DatasourceCompletionMagicTest.Fixture f = new DatasourceCompletionMagicTest.Fixture(0);
        final DatasourceCompletionMagicTest.ScopeRuntime scope = f.scope;
        final Map<String,Object> bindings = new LinkedHashMap<>();
        final TaskCategoryWarehouseScopeMagicTest.Counter counter = new TaskCategoryWarehouseScopeMagicTest.Counter();
        Fixture(int count) {
            for(String column:List.of("db_name varchar(100)","org_id varchar(64)","created_by varchar(64)")) f.jdbc.execute("alter table db_datasource_t add "+column);
            for(String column:List.of("table_name varchar(128)","table_name_cn varchar(128)","business_type varchar(20)","field_count int","timestamp_field varchar(128)","access_request_status int default 0 not null","access_requested_by varchar(32)","access_requested_at timestamp")) f.jdbc.execute("alter table db_table_t add "+column);
            f.jdbc.execute("create table data_access_agg_task_t(tid varchar(64),source_table_id varchar(64),target_table_id varchar(64),pipeline_id varchar(64),task_name varchar(128),schedule_cycle int,last_running timestamp,end_running timestamp,is_timeout int,tenant_id varchar(32),is_del int,process_group_id varchar(64),created_time timestamp)");
            f.jdbc.execute("create table nifi_pipeline_t(id varchar(64),tenant_id varchar(32),is_del int,status varchar(32),last_deployed_hash varchar(64),last_deployed_at timestamp,nifi_process_group_id varchar(64))");
            f.jdbc.update("update db_datasource_t set db_name='测试数据源',asset_status=0");
            for (String db : List.of("target", "other-source")) f.jdbc.update("insert into db_datasource_t(tid,tenant_id,db_name,is_del) values(?,'ga',?,0)", db,db);
            for (int i=0;i<count;i++) f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,table_name,table_name_cn,business_type,field_count,access_request_status) values(?,'ga','db',1,0,?,'测试表','业务表',4,0)","s"+i,"s"+i);
            f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,table_name,business_type) values('target','ga','target',2,0,'target','业务表'),('foreign','other','db',2,0,'foreign','业务表'),('wrong-source','ga','other-source',2,0,'wrong','业务表')");
            f.db.setSqlInterceptors(List.of(counter));
            bindings.put("db",f.db); bindings.put("tenantRuntime",scope); bindings.put("dataScope",scope);
            bindings.put("metadataAsset",new Completion()); bindings.put("StpUtil",new Actor());
        }
        Object execute(String resource, Map<String,Object> body, TaskRuntimeMagicFixture runtime) throws Exception {
            bindings.put("taskRuntime",runtime.module);bindings.put("body",body);
            String summary = source("function/01.通用函数/19.登记表接入状态汇总.ms");
            String api = resource.equals("summary") ? "return registrationAccessSummary(body)" : source("api/03.数据接入/"+resource+".ms");
            return MagicScript.create("var registrationAccessSummary = payload => {\n"+summary+"\n};\n"+api,null).execute(new MagicScriptContext(bindings));
        }
        void task(String status) {
            f.jdbc.update("insert into nifi_pipeline_t values('p','ga',0,?,'hash',current_timestamp,'pg')",status);
            f.jdbc.update("insert into data_access_agg_task_t(tid,source_table_id,target_table_id,pipeline_id,task_name,schedule_cycle,tenant_id,is_del,created_time) values('task','s0','target','p','已配置任务',1,'ga',0,current_timestamp)");
        }
    }
    public static class Actor {public String getLoginIdAsString(){return "tester";}}
    public static class Completion {public Map<String,Object> refreshRegistrationStatus(String id,boolean partial){return Map.of("assetStatus",1);}}
    public static class Lifecycle {
        int starts,stops;final Deque<Map<String,Object>> results=new ArrayDeque<>();
        public Map<String,Object> start(String id){starts++;return results.isEmpty()?Map.of("accepted",true):results.removeFirst();}
        public Map<String,Object> stop(String id){stops++;return Map.of("accepted",true);}
    }
    public static class Deployment {
        int calls;org.springframework.http.ResponseEntity<?> result=org.springframework.http.ResponseEntity.ok(Map.of("mode","full-deploy"));
        public org.springframework.http.ResponseEntity<?> deploy(String id,Object request){assertThat(id).isEqualTo("p");assertThat(request).isNull();calls++;return result;}
    }
    static Map<String,Object> deploymentRequired(){return Map.of("accepted",false,"status",409,"body",Map.of("error","deployment-required"));}
    static Map<String,Object> body(String... ids){return Map.of("datasourceId","db","tableIds",List.of(ids));}
    @Test void fixedBatchReadsAndPerIdScope() throws Exception {
        for(int count:List.of(20,100)) {
            var f = new Fixture(count);
            try(var runtime=new TaskRuntimeMagicFixture("ga")) {
                var ids=new ArrayList<String>();for(int i=0;i<count;i++)ids.add("s"+i);
                var result=(Map<?,?>)f.execute("summary",Map.of("datasourceId","db","tableIds",ids),runtime);
                assertThat((List<?>)result.get("list")).hasSize(count);assertThat(f.counter.reads).isEqualTo(4);runtime.verifySingleRootRead();
                System.out.println("registration summary items="+count+", SQL=4, NiFi root=1");
                assertThat(f.execute("summary",body("s0","foreign"),runtime)).isInstanceOf(ExitValue.class);
                assertThat(f.execute("summary",body("s0","wrong-source"),runtime)).isInstanceOf(ExitValue.class);
                f.scope.denied=true;assertThat(f.execute("summary",body("s0"),runtime)).isInstanceOf(ExitValue.class);
            }
        }
    }
    @Test void applyIsIdempotentDryRunDoesNotWriteAndExistingTaskDoesNotReapply() throws Exception {
        var f=new Fixture(1);
        try(var runtime=new TaskRuntimeMagicFixture("ga")) {
            f.execute("21.提交登记表接入申请",Map.of("datasourceId","db","tableId","s0","dryRun",true),runtime);
            assertThat(f.f.jdbc.queryForObject("select access_request_status from db_table_t where tid='s0'",Integer.class)).isZero();
            f.execute("21.提交登记表接入申请",Map.of("datasourceId","db","tableId","s0"),runtime);
            var time=f.f.jdbc.queryForObject("select access_requested_at from db_table_t where tid='s0'",java.sql.Timestamp.class);
            var result=(Map<?,?>)f.execute("21.提交登记表接入申请",Map.of("datasourceId","db","tableId","s0"),runtime);
            assertThat(f.f.jdbc.queryForObject("select access_requested_at from db_table_t where tid='s0'",java.sql.Timestamp.class)).isEqualTo(time);
            assertThat(((Map<?,?>)((List<?>)result.get("list")).getFirst()).get("accessStatus")).isEqualTo("2");
            f.task("SAVED");
            result=(Map<?,?>)f.execute("summary",body("s0"),runtime);
            assertThat(((Map<?,?>)((List<?>)result.get("list")).getFirst()).get("accessStatus")).isEqualTo("3");
            f.f.jdbc.update("update db_table_t set access_request_status=2 where tid='s0'");
            f.f.jdbc.update("delete from data_access_agg_task_t where tid='task'");
            result=(Map<?,?>)f.execute("summary",body("s0"),runtime);
            assertThat(((Map<?,?>)((List<?>)result.get("list")).getFirst()).get("accessStatus")).isEqualTo("0");
        }
    }
    public static class CompletionFailure {
        private final com.linewell.dataelement.dataassets.runtime.MetadataAssetPersistenceModule delegate;
        CompletionFailure(com.linewell.dataelement.dataassets.runtime.MetadataAssetPersistenceModule delegate){this.delegate=delegate;}
        public Map<String,Object> refreshRegistrationStatus(String id,boolean finish){delegate.refreshRegistrationStatus(id,finish);throw new IllegalStateException("rollback source summary");}
    }
    @Test void applicationPromotesDraftSourceUsingTheActualRegistrationRule() throws Exception {
        var f=new Fixture(1);f.bindings.put("metadataAsset",f.f.metadata);
        try(var runtime=new TaskRuntimeMagicFixture("ga")) {
            var result=(Map<?,?>)f.execute("21.提交登记表接入申请",Map.of("datasourceId","db","tableId","s0"),runtime);
            assertThat(result.get("datasourceAssetStatus")).isEqualTo(1);
            assertThat(f.f.status()).isEqualTo(1);
            assertThat(f.f.jdbc.queryForObject("select asset_status from db_table_t where tid='s0'",Integer.class)).isEqualTo(1);
        }
    }
    @Test void applicationAndActualSourceSummaryRollBackTogetherOnFailure() throws Exception {
        var f=new Fixture(1);f.bindings.put("metadataAsset",new CompletionFailure(f.f.metadata));
        try(var runtime=new TaskRuntimeMagicFixture("ga")) {
            assertThatThrownBy(() -> f.execute("21.提交登记表接入申请",Map.of("datasourceId","db","tableId","s0"),runtime)).isInstanceOf(RuntimeException.class);
            assertThat(f.f.status()).isZero();
            assertThat(f.f.jdbc.queryForObject("select access_request_status from db_table_t where tid='s0'",Integer.class)).isZero();
        }
    }
    @Test void runningAndInvalidRootGroupsExpandBoundChunksBeforeSqlParsing() throws Exception {
        var f=new Fixture(1);f.task("DEPLOYED");
        var response=(com.fasterxml.jackson.databind.node.ObjectNode)TaskRuntimeMagicFixture.emptyRootStatus();
        var groups=response.withObject("/processGroupStatus/aggregateSnapshot").putArray("processGroupStatusSnapshots");
        for(int i=0;i<601;i++) {
            var processors=groups.addObject().putObject("processGroupStatusSnapshot").put("id",i==0?"pg":"extra-"+i).putArray("processorStatusSnapshots");
            processors.addObject().putObject("processorStatusSnapshot").put("runStatus","Running");
            processors.addObject().putObject("processorStatusSnapshot").put("runStatus","Invalid");
        }
        try(var runtime=new TaskRuntimeMagicFixture("ga",response)) {
            var result=(Map<?,?>)f.execute("summary",body("s0"),runtime);
            var row=(Map<?,?>)((List<?>)result.get("list")).getFirst();
            assertThat(row.get("accessStatus")).isEqualTo("1");
            assertThat(((Number)((Map<?,?>)result.get("totals")).get("running")).intValue()).isEqualTo(1);
            assertThat(((Number)((Map<?,?>)result.get("totals")).get("problem")).intValue()).isEqualTo(1);
            assertThat(f.counter.reads).isEqualTo(4);runtime.verifySingleRootRead();
        }
    }
    @Test void multipleTasksUseTheirLatestSyncFactsAndKeepDistinctFrequencies() throws Exception {
        var f=new Fixture(1);f.task("SAVED");
        f.f.jdbc.update("update data_access_agg_task_t set last_running=timestamp '2026-10-19 09:00:00',end_running=timestamp '2026-10-19 09:01:00' where tid='task'");
        f.f.jdbc.update("insert into data_access_agg_task_t(tid,source_table_id,target_table_id,pipeline_id,task_name,schedule_cycle,last_running,end_running,tenant_id,is_del,created_time) values('task2','s0','target','p','另一直接任务',4,timestamp '2026-10-20 10:00:00',timestamp '2026-10-20 10:01:00','ga',0,current_timestamp)");
        try(var runtime=new TaskRuntimeMagicFixture("ga")) {
            var result=(Map<?,?>)f.execute("summary",body("s0"),runtime);
            var row=(Map<?,?>)((List<?>)result.get("list")).getFirst();
            assertThat(row.get("lastExecutedAt").toString()).startsWith("2026-10-20 10:00");
            assertThat(row.get("lastDataUpdatedAt").toString()).startsWith("2026-10-20 10:01");
            assertThat(row.get("frequency")).isEqualTo("多个周期（2个任务）");
        }
    }
    @Test void realStateControlsSingleAuthorizedTaskIncludingSavedConfiguration() throws Exception {
        var f=new Fixture(1);f.task("DEPLOYED");
        var response=(com.fasterxml.jackson.databind.node.ObjectNode)TaskRuntimeMagicFixture.emptyRootStatus();
        response.withObject("/processGroupStatus/aggregateSnapshot").putArray("processGroupStatusSnapshots").addObject().putObject("processGroupStatusSnapshot").put("id","pg").putArray("processorStatusSnapshots").addObject().putObject("processorStatusSnapshot").put("runStatus","Stopped");
        try(var runtime=new TaskRuntimeMagicFixture("ga",response)) {
            var lifecycle=new Lifecycle();f.bindings.put("taskLifecycle",lifecycle);
            var valid=Map.<String,Object>of("datasourceId","db","tableId","s0","taskId","task","action","start");
            assertThat(f.execute("22.登记表接入任务启停",valid,runtime)).isInstanceOf(Map.class);assertThat(lifecycle.starts).isEqualTo(1);
            assertThat(f.execute("22.登记表接入任务启停",Map.of("datasourceId","db","tableId","s0","taskId","other-task","action","start"),runtime)).isInstanceOf(ExitValue.class);
            f.f.jdbc.update("update nifi_pipeline_t set status='SAVED'");
            assertThat(f.execute("22.登记表接入任务启停",valid,runtime)).isInstanceOf(Map.class);assertThat(lifecycle.starts).isEqualTo(2);
        }
    }
    @Test void undeployedTaskDryRunDoesNotExecuteThenDeploysBeforeStarting() throws Exception {
        var f=new Fixture(1);f.task("SAVED");
        f.f.jdbc.update("update nifi_pipeline_t set nifi_process_group_id=null,last_deployed_hash=null");
        try(var runtime=new TaskRuntimeMagicFixture("ga")) {
            var lifecycle=new Lifecycle();var deployment=new Deployment();f.bindings.put("taskLifecycle",lifecycle);f.bindings.put("pipelineDeployment",deployment);
            var valid=new HashMap<String,Object>(Map.of("datasourceId","db","tableId","s0","taskId","task","action","start"));valid.put("dryRun",true);
            assertThat(((Map<?,?>)f.execute("22.登记表接入任务启停",valid,runtime)).get("deploymentRequired")).isEqualTo(true);
            assertThat(lifecycle.starts).isZero();assertThat(deployment.calls).isZero();valid.remove("dryRun");
            lifecycle.results.add(deploymentRequired());
            assertThat(((Map<?,?>)f.execute("22.登记表接入任务启停",valid,runtime)).get("deploymentPerformed")).isEqualTo(true);
            assertThat(deployment.calls).isEqualTo(1);assertThat(lifecycle.starts).isEqualTo(2);
        }
    }
    @Test void deploymentFailureAndCleanupConfirmationNeverFallThroughToStart() throws Exception {
        for(boolean cleanup:List.of(false,true)) {
            var f=new Fixture(1);f.task("SAVED");f.f.jdbc.update("update nifi_pipeline_t set nifi_process_group_id=null,last_deployed_hash=null");
            try(var runtime=new TaskRuntimeMagicFixture("ga")) {
                var lifecycle=new Lifecycle();var deployment=new Deployment();f.bindings.put("taskLifecycle",lifecycle);f.bindings.put("pipelineDeployment",deployment);
                lifecycle.results.add(cleanup?Map.of("accepted",false,"status",409,"body",Map.of("error","cleanup-confirmation-required")):deploymentRequired());
                deployment.result=org.springframework.http.ResponseEntity.unprocessableEntity().body(Map.of("message","来源配置不完整"));
                assertThat(f.execute("22.登记表接入任务启停",Map.of("datasourceId","db","tableId","s0","taskId","task","action","start"),runtime)).isInstanceOf(ExitValue.class);
                assertThat(lifecycle.starts).isEqualTo(1);assertThat(deployment.calls).isEqualTo(cleanup?0:1);
            }
        }
    }
    @Test void missingPipelineAndBusyLifecycleCannotStartOrDeploy() throws Exception {
        for(String status:List.of("DEPLOYING","STARTING","STOPPING","MISSING_RECORD")) {
            var f=new Fixture(1);f.task(status);f.f.jdbc.update("update nifi_pipeline_t set nifi_process_group_id=null,last_deployed_hash=null");
            if(status.equals("MISSING_RECORD"))f.f.jdbc.update("delete from nifi_pipeline_t");
            try(var runtime=new TaskRuntimeMagicFixture("ga")) {
                var lifecycle=new Lifecycle();var deployment=new Deployment();f.bindings.put("taskLifecycle",lifecycle);f.bindings.put("pipelineDeployment",deployment);
                assertThat(f.execute("22.登记表接入任务启停",Map.of("datasourceId","db","tableId","s0","taskId","task","action","start"),runtime)).isInstanceOf(ExitValue.class);
                assertThat(lifecycle.starts).isZero();assertThat(deployment.calls).isZero();
            }
        }
    }
}
