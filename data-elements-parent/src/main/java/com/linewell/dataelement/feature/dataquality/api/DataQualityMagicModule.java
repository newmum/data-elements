package com.linewell.dataelement.feature.dataquality.api;

import com.linewell.dataelement.feature.dataquality.application.DataQualityApplicationService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Magic API adapter; current-tenant operations are enforced by the application service. */
@Component
@MagicModule("dataQuality")
public class DataQualityMagicModule {

    private final DataQualityApplicationService service;

    public DataQualityMagicModule(DataQualityApplicationService service) {
        this.service = service;
    }

    @Comment("读取当前租户的质量概览")
    public Map<String, Object> summary() {
        return service.summary();
    }

    @Comment("按租户读取质检任务分页")
    public Map<String, Object> taskPage(String status, String keyword, int page, int size, String kind) {
        return service.taskPage(status, keyword, page, size, kind);
    }

    @Comment("读取任务及其规则")
    public Map<String, Object> taskDetail(String taskId) {
        return service.taskDetail(taskId);
    }

    @Comment("在仓储事务中保存任务及规则，返回任务标识")
    public String persistTask(Map<String, Object> body) {
        return service.persistTask(body);
    }

    @Comment("跨数据源执行质检预检，不改变任务状态")
    public Map<String, Object> inspectTask(String taskId) {
        return service.inspectTask(taskId);
    }

    @Comment("保存预检结果供启用校验")
    public boolean persistPrecheck(String taskId, Map<String, Object> result) {
        return service.persistPrecheck(taskId, result);
    }

    @Comment("启停任务；仓储层强制检查预检状态")
    public boolean setEnabled(String taskId, boolean enabled) {
        return service.setEnabled(taskId, enabled);
    }

    @Comment("软删除任务并保留执行历史")
    public boolean deleteTask(String taskId) {
        return service.deleteTask(taskId);
    }

    @Comment("创建幂等手动质检实例")
    public String createRun(String taskId, String requestId) {
        return service.createRun(taskId, requestId);
    }

    @Comment("按租户读取质检实例分页")
    public Map<String, Object> runPage(String status, String keyword, int page, int size, String kind) {
        return service.runPage(status, keyword, page, size, kind);
    }

    @Comment("读取实例指标和分片")
    public Map<String, Object> runDetail(String runId) {
        return service.runDetail(runId);
    }

    @Comment("读取当前实例的问题样例分页")
    public Map<String, Object> issuePage(String runId, String status, String severity, int page, int size) {
        return service.issuePage(runId, status, severity, page, size);
    }

    @Comment("标记质检实例取消")
    public boolean cancel(String runId) {
        return service.cancel(runId);
    }

    @Comment("读取可质检数据源")
    public List<Map<String, Object>> datasources() {
        return service.datasources();
    }

    @Comment("读取数据源下已登记的数据表")
    public List<Map<String, Object>> tables(String datasourceId) {
        return service.tables(datasourceId);
    }

    @Comment("读取登记表字段")
    public List<Map<String, Object>> columns(String tableId) {
        return service.columns(tableId);
    }

    @Comment("批量读取当前租户至多 50 张有效数据表的字段，按 tableId 分组")
    public Map<String,List<Map<String,Object>>> columnsBatch(List<String> tableIds) {
        return service.columnsBatch(tableIds);
    }
}
