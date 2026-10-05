package com.linewell.dataelement.feature.dataquality.infrastructure.persistence;

import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc.RegisteredJdbcDataSourceResolver;
import com.linewell.dataelement.feature.dataquality.config.DataQualityProperties;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Evaluation;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.IssueSample;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.MetricResult;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Plan;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Rule;
import com.linewell.dataelement.feature.dataquality.domain.DataQualityModels.Shard;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.mapper.DataQualityMapper;
import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DataQualityRepository {

    private final DataQualityMapper mapper;
    private final RegisteredJdbcDataSourceResolver dataSources;
    private final DataQualityProperties properties;
    private final ObjectMapper objectMapper;

    public DataQualityRepository(DataQualityMapper mapper, RegisteredJdbcDataSourceResolver dataSources,
            DataQualityProperties properties, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.dataSources = dataSources;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> taskPage(String tenantId, String status, String keyword, int page, int size) {
        return taskPage(tenantId,status,keyword,page,size,null);
    }
    public Map<String, Object> taskPage(String tenantId, String status, String keyword, int page, int size,String kind) {
        IPage<Map<String, Object>> result = mapper.selectTaskPage(Page.of(page, size), tenantId,
                text(status), text(keyword),kind);
        return page(result, page, size);
    }

    public Map<String, Object> runPage(String tenantId, String status, String keyword, int page, int size) {
        return runPage(tenantId,status,keyword,page,size,null);
    }
    public Map<String, Object> runPage(String tenantId, String status, String keyword, int page, int size,String kind) {
        IPage<Map<String, Object>> result = mapper.selectRunPage(Page.of(page, size), tenantId,
                text(status), text(keyword),kind);
        return page(result, page, size);
    }

    public Map<String, Object> issuePage(String tenantId, String runId, String status,
            String severity, int page, int size) {
        IPage<Map<String, Object>> result = mapper.selectIssuePage(Page.of(page, size), tenantId,
                runId, text(status), text(severity));
        return page(result, page, size);
    }

    public Map<String, Object> taskDetail(String tenantId, String taskId) {
        Map<String, Object> task = required(mapper.selectTask(tenantId, taskId));
        task.put("rules", JdbcValueNormalizer.maps(mapper.selectTaskRules(tenantId, taskId)));
        task.put("task_kind",mapList(task.get("rules")).stream().anyMatch(r->"PROFILE".equals(r.get("rule_type")))?"PROFILE":"QUALITY");
        return task;
    }

    public Map<String, Object> runDetail(String tenantId, String runId) {
        Map<String, Object> run = required(mapper.selectRun(tenantId, runId));
        List<Map<String,Object>> metrics = JdbcValueNormalizer.maps(mapper.selectRunMetrics(tenantId, runId));
        for (Map<String,Object> metric : metrics) {
            metric.put("profile",jsonMap(metric.get("profile_json")));
            if (longValue(metric.get("checked_count")) == 0) {
                metric.put("pass_rate",null);
                metric.put("score",null);
            }
        }
        run.put("metrics", metrics);
        run.put("task_kind",defaultText(text(jsonMap(run.get("task_snapshot")).get("task_kind")),"QUALITY"));
        run.put("shards", JdbcValueNormalizer.maps(mapper.selectRunShards(tenantId, runId)));
        return run;
    }

    @Transactional
    public String saveTask(String tenantId, String operator, Map<String, Object> body) {
        List<Map<String, Object>> rules = mapList(body.get("rules"));
        if (rules.isEmpty()) throw new IllegalArgumentException("至少配置一条质检规则");
        String taskId = text(body.get("tid"));
        if (taskId != null) {
            required(mapper.lockTask(tenantId, taskId));
            if (mapper.activeRuns(tenantId, taskId)>0) throw new IllegalStateException("任务正在执行，请等待完成或取消后再修改");
        }
        // Validate the authoritative source/table ownership before any persistent write.
        dataSources.resolve(tenantId, requiredText(body,"datasourceId","请选择数据源"), requiredText(body,"tableId","请选择数据表"));
        String sourceId=text(body.get("datasourceId")),tableId=text(body.get("tableId"));
        if(rules.size()>64)throw new IllegalArgumentException("最多配置 64 条规则或探查字段");
        Set<String> targetIds=new LinkedHashSet<>();
        for(var rule:rules)if("REFERENCE".equals(rule.get("ruleType"))){Map<String,Object> parameters=rule.get("parameters") instanceof Map<?,?> raw?(Map<String,Object>)raw:Map.of();targetIds.add(requiredText(parameters,"targetTableId","请选择引用目标表"));}
        if(!targetIds.isEmpty()&&!Set.copyOf(mapper.selectValidTableIds(tenantId,sourceId,List.copyOf(targetIds))).containsAll(targetIds))
            throw new TenantAccessException("REGISTERED-JDBC-ENDPOINT-NOT-FOUND","数据源或数据表不存在，或者不属于当前租户");
        Set<String> requiredTables=new LinkedHashSet<>();requiredTables.add(tableId);requiredTables.addAll(targetIds);
        Map<String,List<Map<String,Object>>> columnOptions=columnsByTable(tenantId,requiredTables);
        var ownedColumns=columnOptions.get(tableId).stream().map(r->text(r.get("column_name"))).collect(java.util.stream.Collectors.toSet());
        boolean profile=rules.stream().anyMatch(r->"PROFILE".equals(r.get("ruleType")));
        if(profile&&(integer(body.get("shardCount"),1)!=1||rules.stream().anyMatch(r->!"PROFILE".equals(r.get("ruleType")))))throw new IllegalArgumentException("字段探查须使用单分片且不能混入评分规则");
        for(var rule:rules) {
            if(!ownedColumns.contains(text(rule.get("columnName"))))throw new IllegalArgumentException("校验字段不存在于当前数据表");
            Map<String,Object> p=rule.get("parameters") instanceof Map<?,?> raw?new LinkedHashMap<>((Map<String,Object>)raw):new LinkedHashMap<>();
            p.remove("resolvedTargetTable");p.remove("evaluationTime");
            for(String key:List.of("columns","sourceColumns")) if(p.containsKey(key)) {
                List<String> names=columnList(p.get(key));
                if(!ownedColumns.containsAll(names))throw new IllegalArgumentException("联合规则含非本表字段");
                if(!names.contains(text(rule.get("columnName"))))throw new IllegalArgumentException("主要字段必须包含在联合字段中");
            }
            if("REFERENCE".equals(rule.get("ruleType"))) {
                String target=requiredText(p,"targetTableId","请选择引用目标表");
                var targetColumns=columnOptions.get(target).stream().map(r->text(r.get("column_name"))).collect(java.util.stream.Collectors.toSet());
                var from=columnList(p.get("sourceColumns"));var to=columnList(p.get("targetColumns"));
                if(from.size()!=to.size()||!targetColumns.containsAll(to))throw new IllegalArgumentException("引用目标字段无效或映射数量不一致");
            }
            rule.put("parameters",p);
        }
        if ("CRON".equals(text(body.get("triggerMode")))) {
            org.springframework.scheduling.support.CronExpression.parse(requiredText(body,"cronExpression","请配置有效的 Cron 表达式"));
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> values = params(
                "tid", taskId == null ? NumericId.nextId() : taskId,
                "tenantId", tenantId,
                "taskName", requiredText(body, "taskName", "任务名称不能为空"),
                "taskCode", defaultText(text(body.get("taskCode")), "DQ_" + System.currentTimeMillis()),
                "datasourceId", requiredText(body, "datasourceId", "请选择数据源"),
                "tableId", requiredText(body, "tableId", "请选择数据表"),
                "shardKey", text(body.get("shardKey")),
                "triggerMode", enumValue(body.get("triggerMode"), List.of("MANUAL", "CRON"), "MANUAL"),
                "cronExpression", text(body.get("cronExpression")),
                "shardCount", clamp(integer(body.get("shardCount"), 1), 1, properties.getMaxShardCount()),
                "sampleLimit", clamp(integer(body.get("sampleLimit"), 200), 0, properties.getMaxSampleLimit()),
                "timeoutMinutes", clamp(integer(body.get("timeoutMinutes"), 120), 1, 1440),
                "resourceGroup", defaultText(text(body.get("resourceGroup")), "DEFAULT"),
                "description", text(body.get("description")), "operator", operator, "now", now
        );
        taskId = String.valueOf(values.get("tid"));
        if ("CRON".equals(values.get("triggerMode")) && blank(text(values.get("cronExpression")))) {
            throw new IllegalArgumentException("定时任务必须配置 Cron 表达式");
        }
        if (text(body.get("tid")) == null) {
            try {mapper.insertTask(values);} catch(DuplicateKeyException duplicate) {
                throw new IllegalArgumentException("任务编码已存在，请使用其他任务编码",duplicate);
            }
        } else if (mapper.updateTask(values) == 0) {
            throw new TenantAccessException("DQ-TASK-NOT-FOUND", "质检任务不存在或无权访问");
        } else {
            mapper.softDeleteRules(params("tenantId", tenantId, "taskId", taskId,
                    "operator", operator, "now", now));
        }
        int sort = 0;
        for (Map<String, Object> rule : rules) {
            mapper.insertTaskRule(params(
                    "tid", NumericId.nextId(), "tenantId", tenantId, "taskId", taskId,
                    "ruleName", requiredText(rule, "ruleName", "规则名称不能为空"),
                    "ruleType", enumValue(rule.get("ruleType"),
                            List.of("NOT_NULL", "NOT_EMPTY", "UNIQUE", "RANGE", "ENUM", "LENGTH", "REGEX", "TIMELINESS", "REFERENCE", "PROFILE"), null),
                    "dimension", defaultText(text(rule.get("dimension")), dimensionOf(text(rule.get("ruleType")))),
                    "columnName", requiredText(rule, "columnName", "校验字段不能为空"),
                    "relatedColumn", text(rule.get("relatedColumn")),
                    "parametersJson", json(rule.getOrDefault("parameters", Map.of())),
                    "severity", enumValue(rule.get("severity"), List.of("LOW", "MEDIUM", "HIGH", "CRITICAL"), "MEDIUM"),
                    "weight", clamp(integer(rule.get("weight"), 10), 1, 100),
                    "sortNo", sort++, "operator", operator, "now", now
            ));
        }
        return taskId;
    }

    public Plan loadPlan(String tenantId, String taskId, String runId) {
        Map<String, Object> task = required(mapper.selectTask(tenantId, taskId));
        return plan(tenantId, runId, taskId, task, rules(tenantId, taskId));
    }

    public Plan loadRunPlan(String tenantId, String runId) {
        Map<String, Object> run = required(mapper.selectRun(tenantId, runId));
        Map<String,Object> snapshot=jsonMap(run.get("task_snapshot"));
        if(snapshot.isEmpty()) throw new IllegalStateException("执行实例缺少任务快照，不能使用修改后的配置代替");
        List<Rule> snapshotRules=mapList(snapshot.get("rules")).stream().map(this::rule).toList();
        snapshot.put("evaluation_time",run.getOrDefault("created_time",LocalDateTime.now()));
        return plan(tenantId,runId,text(run.get("task_id")),snapshot,snapshotRules);
    }

    private Plan plan(String tenantId,String runId,String taskId,Map<String,Object> task,List<Rule> rules) {
        String sourceId=text(task.get("datasource_id"));
        List<Rule> resolvedRules=rules.stream().map(rule->{
            Map<String,Object> p=new LinkedHashMap<>(rule.parameters());p.remove("resolvedTargetTable");p.remove("evaluationTime");
            if("REFERENCE".equals(rule.type())) p.put("resolvedTargetTable",dataSources.resolve(tenantId,sourceId,requiredText(p,"targetTableId","引用目标表缺失")).tableName());
            if("TIMELINESS".equals(rule.type())) p.put("evaluationTime",text(task.getOrDefault("evaluation_time",LocalDateTime.now())).replace(' ','T'));
            return new Rule(rule.id(),rule.name(),rule.type(),rule.dimension(),rule.columnName(),rule.relatedColumn(),p,rule.severity(),rule.weight());
        }).toList();
        return new Plan(tenantId,runId,taskId,text(task.get("shard_key")),integer(task.get("shard_count"),1),integer(task.get("sample_limit"),200),
            dataSources.resolve(tenantId,sourceId,text(task.get("table_id"))),resolvedRules,Math.max(1,integer(task.get("timeout_minutes"),120))*60);
    }

    @Transactional
    public boolean deleteTask(String tenantId,String taskId,String operator) {
        required(mapper.lockTask(tenantId,taskId));
        if(mapper.activeRuns(tenantId,taskId)>0) throw new IllegalStateException("运行中的任务不能删除");
        Map<String,Object> values=params("tenantId",tenantId,"taskId",taskId,"operator",operator,"now",LocalDateTime.now());
        mapper.deleteTask(values);mapper.softDeleteRules(values);return true;
    }

    public void savePrecheck(String tenantId, String taskId, Map<String, Object> result) {
        mapper.updatePrecheck(params("tenantId", tenantId, "taskId", taskId,
                "status", Boolean.TRUE.equals(result.get("success")) ? "PASSED" : "FAILED",
                "message", text(result.get("message")), "now", LocalDateTime.now()));
    }

    @Transactional
    public void enable(String tenantId, String taskId, boolean enabled) {
        Map<String, Object> task = required(mapper.lockTask(tenantId, taskId));
        if (enabled && !"PASSED".equals(text(task.get("precheck_status")))) {
            throw new IllegalStateException("质检任务启用前必须通过数据源、字段和规则预检");
        }
        if(enabled && "CRON".equals(text(task.get("trigger_mode")))) {
            LocalDateTime next=org.springframework.scheduling.support.CronExpression.parse(text(task.get("cron_expression"))).next(LocalDateTime.now());
            if(next==null) throw new IllegalArgumentException("Cron 表达式没有可执行的未来时间");
            updateNextRun(tenantId,taskId,next);
        }
        mapper.updateTaskEnabled(params("tenantId", tenantId, "taskId", taskId,
                "status", enabled ? 1 : 2, "now", LocalDateTime.now()));
    }

    @Transactional
    public String createRun(String tenantId, String taskId, String triggerType,
            String requestId, String operator) {
        required(mapper.lockTask(tenantId,taskId));
        Map<String, Object> task = taskDetail(tenantId, taskId);
        if(!"PASSED".equals(text(task.get("precheck_status")))) throw new IllegalStateException("执行前必须通过预检");
        String request = defaultText(requestId, java.util.UUID.randomUUID().toString());
        String dedupeKey = sha256(tenantId + "|" + taskId + "|" + triggerType + "|" + request);
        String existing=mapper.selectRunIdByDedupe(tenantId,dedupeKey);
        if(existing!=null) return existing;
        if(mapper.activeRuns(tenantId,taskId)>0) throw new IllegalStateException("任务已有运行中的实例，请等待完成或取消");
        String runId = NumericId.nextId();
        LocalDateTime now = LocalDateTime.now();
        try {
            mapper.insertRun(params("tid", runId, "tenantId", tenantId, "taskId", taskId,
                    "taskVersion", integer(task.get("version_no"), 1),
                    "taskSnapshot", json(JdbcValueNormalizer.normalize(task)),
                    "taskKind",task.get("task_kind"),
                    "triggerType", triggerType, "requestId", request, "dedupeKey", dedupeKey,
                    "operator", operator, "now", now));
        } catch (DuplicateKeyException duplicate) {
            return mapper.selectRunIdByDedupe(tenantId, dedupeKey);
        }
        mapper.touchTaskRun(params("tenantId", tenantId, "taskId", taskId,
                "runId", runId, "now", now));
        return runId;
    }

    public Map<String, Object> summary(String tenantId) {
        Map<String, Object> result = new LinkedHashMap<>(JdbcValueNormalizer.map(
                mapper.selectSummary(tenantId, LocalDateTime.now().minusDays(30))));
        result.put("dimensions", JdbcValueNormalizer.maps(
                mapper.selectDimensionSummary(tenantId, LocalDateTime.now().minusDays(30))));
        return result;
    }

    public List<Map<String, Object>> datasourceOptions(String tenantId) { return JdbcValueNormalizer.maps(mapper.selectDatasourceOptions(tenantId)); }
    public List<Map<String, Object>> tableOptions(String tenantId, String datasourceId) { return JdbcValueNormalizer.maps(mapper.selectTableOptions(tenantId, datasourceId)); }
    public List<Map<String, Object>> columnOptions(String tenantId, String tableId) { return JdbcValueNormalizer.maps(mapper.selectColumnOptions(tenantId, tableId)); }
    public Map<String,List<Map<String,Object>>> columnOptionsBatch(String tenantId,List<String> tableIds){
        if(tableIds==null||tableIds.isEmpty()||tableIds.size()>50)throw new IllegalArgumentException("一次最多查询 50 张表的字段选项");
        Set<String> ids=new LinkedHashSet<>();for(String id:tableIds){if(id==null||id.isBlank())throw new IllegalArgumentException("数据表标识不能为空");ids.add(id.trim());}
        return columnsByTable(tenantId,ids);
    }
    private Map<String,List<Map<String,Object>>> columnsByTable(String tenantId,Set<String> tableIds){
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();tableIds.forEach(id->result.put(id,new ArrayList<>()));
        for(var row:JdbcValueNormalizer.maps(mapper.selectColumnOptionsBatch(tenantId,List.copyOf(tableIds)))){
            List<Map<String,Object>> fields=result.get(text(row.get("table_id")));if(fields!=null)fields.add(row);
        }
        return result;
    }
    @Transactional
    public void cancel(String tenantId, String runId) { if (mapper.requestCancel(params("tenantId", tenantId, "runId", runId, "now", LocalDateTime.now())) == 0) throw new IllegalStateException("当前实例不存在或已经结束"); cancelRun(tenantId,runId); }

    public List<Map<String, Object>> pendingRuns(int limit) { return mapper.selectPendingRuns(Page.of(1, limit, false)).getRecords(); }
    public int claimRun(String tenantId, String runId, String workerId) { return mapper.claimRun(params("tenantId", tenantId, "runId", runId, "workerId", workerId, "now", LocalDateTime.now())); }
    public void resetShards(String tenantId, String runId) { mapper.deleteRunShards(tenantId, runId); }
    public void insertShard(String tenantId, String runId, String id, int number, String lower, String upper, boolean inclusive) { mapper.insertShard(params("tid", id, "tenantId", tenantId, "runId", runId, "shardNo", number, "keyLower", lower, "keyUpper", upper, "upperInclusive", inclusive ? 1 : 0, "now", LocalDateTime.now())); }
    public void markRunRunning(String tenantId, String runId, int total) { mapper.markRunRunning(params("tenantId", tenantId, "runId", runId, "shardTotal", total, "now", LocalDateTime.now())); }
    public List<Map<String, Object>> candidateShards(int limit) { return mapper.selectCandidateShards(Page.of(1, limit, false)).getRecords(); }
    public int claimShard(String id, String workerId) { return mapper.claimShard(params("shardId", id, "workerId", workerId, "now", LocalDateTime.now())); }
    public Shard shard(String tenantId, String runId, String shardId) { Map<String,Object> row=required(mapper.selectShard(tenantId,runId,shardId)); return new Shard(shardId,integer(row.get("shard_no"),0),text(row.get("key_lower")),text(row.get("key_upper")),integer(row.get("upper_inclusive"),0)==1); }

    @Transactional
    public void saveEvaluation(String tenantId, String runId, String shardId, Evaluation evaluation) {
        Map<String,Object> run=required(mapper.lockRun(tenantId,runId));
        if(!"RUNNING".equals(text(run.get("status"))) || integer(run.get("cancel_requested"),0)==1) return;
        Map<String,Object> shard=required(mapper.selectShard(tenantId,runId,shardId));
        if(!"RUNNING".equals(text(shard.get("status")))) return;
        LocalDateTime now = LocalDateTime.now();
        for (MetricResult metric : evaluation.metrics()) {
            Map<String,Object> values=params("tid", NumericId.nextId(), "tenantId", tenantId,
                    "runId", runId, "ruleId", metric.rule().id(), "ruleName", metric.rule().name(),
                    "ruleType", metric.rule().type(), "dimension", metric.rule().dimension(),
                    "columnName", metric.rule().columnName(), "severity", metric.rule().severity(),
                    "weight", metric.rule().weight(), "checked", metric.checkedCount(),
                    // Existing schemas require numeric metric columns. Zero-check rows
                    // store a neutral 0, are excluded from scoring, and return null rates.
                    "violations", metric.violationCount(), "passRate", metric.passRate()==null?BigDecimal.ZERO:metric.passRate(),
                    "score", metric.score()==null?BigDecimal.ZERO:metric.score(), "profileJson",metric.profile().isEmpty()?null:json(metric.profile()), "now", now);
            if(mapper.addMetric(values)==0) mapper.insertMetric(values);
        }
        List<Map<String, Object>> issues = new ArrayList<>();
        for (IssueSample issue : evaluation.samples()) {
            String hash = sha256(runId + "|" + issue.rule().id() + "|" + issue.rowKey() + "|" + issue.issueValue());
            issues.add(params("tid", NumericId.nextId(), "tenantId", tenantId, "runId", runId,
                    "shardId", shardId, "ruleId", issue.rule().id(), "issueHash", hash,
                    "rowKey", issue.rowKey(), "columnName", issue.rule().columnName(),
                    "issueValue", mask(issue.issueValue()), "rowSnapshot", mask(issue.rowSnapshot()),
                    "severity", issue.rule().severity(), "now", now));
        }
        for (Map<String, Object> issue : issues) {
            try {
                mapper.insertIssue(issue);
            } catch (DuplicateKeyException ignored) {
                // A retried shard may emit the same bounded sample again.
            }
        }
        mapper.completeShard(params("tenantId", tenantId, "runId", runId, "shardId", shardId,
                "rowCount", evaluation.rowCount(), "checked", evaluation.checkedCount(),
                "violations", evaluation.violationCount(), "now", now));
    }

    public void finalizeRun(String tenantId, String runId) {
        Map<String,Object> state=mapper.selectShardState(tenantId,runId);
        long total=longValue(state.get("total")), completed=longValue(state.get("completed")), failed=longValue(state.get("failed"));
        if (total==0 || completed+failed<total) return;
        if (failed>0) { failRun(tenantId,runId,"DQ-SHARD-FAILED","一个或多个质检分片执行失败",null); return; }
        mapper.refreshMetricRates(tenantId,runId);
        Map<String,Object> sums=mapper.selectRunSums(tenantId,runId);
        BigDecimal score=decimal(sums.get("quality_score"),null);
        mapper.finishRun(params("tenantId",tenantId,"runId",runId,"status",longValue(sums.get("violation_count"))>0?"COMPLETED_WITH_ISSUES":"COMPLETED",
                "shardCompleted",completed,"rowCount",longValue(sums.get("row_count")),"checked",longValue(sums.get("checked_count")),
                "violations",longValue(sums.get("violation_count")),"score",score,"now",LocalDateTime.now()));
    }

    public int shardAttempt(String tenantId,String runId,String shardId){Integer value=mapper.selectShardAttempt(tenantId,runId,shardId);return value==null?0:value;}
    public void failShard(String tenantId,String runId,String shardId,boolean retry,String message){mapper.updateShardFailure(params("tenantId",tenantId,"runId",runId,"shardId",shardId,"status",retry?"PENDING":"FAILED","message",message,"now",LocalDateTime.now()));}
    public void failRun(String tenantId,String runId,String code,String message,String detail){mapper.failRun(params("tenantId",tenantId,"runId",runId,"code",code,"message",message,"detail",detail,"traceId",java.util.UUID.randomUUID().toString(),"now",LocalDateTime.now()));}
    public boolean cancelled(String tenantId,String runId){Integer value=mapper.selectCancelRequested(tenantId,runId);return value!=null&&value==1;}
    public void cancelRun(String tenantId,String runId){Map<String,Object> value=params("tenantId",tenantId,"runId",runId,"now",LocalDateTime.now());mapper.cancelRun(value);mapper.cancelPendingShards(value);}
    public void recover(LocalDateTime staleBefore,int maxAttempts){mapper.recoverInitializingRuns(staleBefore);mapper.recoverRunningShards(maxAttempts,staleBefore);}
    public List<Map<String,Object>> dueTasks(LocalDateTime now){return mapper.selectDueTasks(Page.of(1,100,false),now).getRecords();}
    public void updateNextRun(String tenantId,String taskId,LocalDateTime next){mapper.updateNextRun(params("tenantId",tenantId,"taskId",taskId,"nextRunAt",next));}

    private Rule rule(Map<String,Object> row){return new Rule(text(row.get("tid")),text(row.get("rule_name")),text(row.get("rule_type")),text(row.get("quality_dimension")),text(row.get("column_name")),text(row.get("related_column")),jsonMap(row.get("parameters_json")),text(row.get("severity")),integer(row.get("weight_value"),10));}
    private List<Rule> rules(String tenantId,String taskId){return mapper.selectTaskRules(tenantId,taskId).stream().map(this::rule).toList();}
    private Map<String,Object> page(IPage<Map<String,Object>> result,int page,int size){return params("list",JdbcValueNormalizer.maps(result.getRecords()),"total",result.getTotal(),"page",page,"size",size);}
    private Map<String,Object> required(Map<String,Object> row){if(row==null||row.isEmpty())throw new TenantAccessException("DQ-NOT-FOUND","质检数据不存在或无权访问");return JdbcValueNormalizer.map(row);}
    private List<Map<String,Object>> mapList(Object value){if(!(value instanceof List<?> list))return List.of();List<Map<String,Object>> out=new ArrayList<>();for(Object item:list)if(item instanceof Map<?,?> map){Map<String,Object> row=new LinkedHashMap<>();map.forEach((k,v)->row.put(String.valueOf(k),v));out.add(row);}return out;}
    private Map<String,Object> jsonMap(Object value){String content=JdbcValueNormalizer.text(value);if(content==null||content.isBlank())return Map.of();try{return objectMapper.readValue(content,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){return Map.of();}}
    private String json(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("序列化质检配置失败",e);}}
    private String mask(String value){if(value==null)return null;return value.length()>2000?value.substring(0,2000):value;}
    private List<String> columnList(Object value){if(!(value instanceof List<?> list)||list.isEmpty()||list.size()>16)throw new IllegalArgumentException("请选择 1–16 个联合字段");List<String> out=list.stream().map(this::text).toList();if(out.stream().anyMatch(this::blank)||out.stream().distinct().count()!=out.size())throw new IllegalArgumentException("联合字段不能为空或重复");return out;}
    private String dimensionOf(String type){return switch(defaultText(type,"")){case "NOT_NULL","NOT_EMPTY"->"COMPLETENESS";case "UNIQUE"->"UNIQUENESS";case "TIMELINESS"->"TIMELINESS";case "REFERENCE"->"CONSISTENCY";case "PROFILE"->"PROFILE";case "REGEX","LENGTH"->"VALIDITY";default->"ACCURACY";};}
    private String enumValue(Object value,List<String> allowed,String fallback){String normalized=defaultText(text(value),fallback);if(normalized==null||!allowed.contains(normalized.toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("不支持的枚举值: "+normalized);return normalized.toUpperCase(Locale.ROOT);}
    private String requiredText(Map<String,Object> body,String key,String message){String value=text(body.get(key));if(blank(value))throw new IllegalArgumentException(message);return value;}
    private Map<String,Object> params(Object... pairs){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put(String.valueOf(pairs[i]),pairs[i+1]);return out;}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String text(Object value){String text=JdbcValueNormalizer.text(value);return text==null?null:text.trim();}
    private boolean blank(String value){return value==null||value.isBlank()||"null".equalsIgnoreCase(value);}
    private String defaultText(String value,String fallback){return blank(value)?fallback:value;}
    private int integer(Object value,int fallback){try{return value==null?fallback:Integer.parseInt(String.valueOf(value));}catch(Exception e){return fallback;}}
    private long longValue(Object value){try{return value==null?0:Long.parseLong(String.valueOf(value));}catch(Exception e){return 0;}}
    private BigDecimal decimal(Object value,BigDecimal fallback){try{return value==null?fallback:new BigDecimal(String.valueOf(value));}catch(Exception e){return fallback;}}
    private int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
