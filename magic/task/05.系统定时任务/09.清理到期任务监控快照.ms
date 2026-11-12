{
  "properties": {},
  "id": "3ed89c080ce8418790474305211073dc",
  "script": null,
  "groupId": "30fc31e9c7e057f4983f7342850f1811",
  "name": "09.清理到期任务监控快照",
  "path": "purge-task-monitor-snapshots",
  "cron": "0 * * * * ?",
  "enabled": false,
  "description": "按租户物理删除到期监控快照，每分钟每租户最多 1000 条。在目标行业库应用保留索引并核对保留天数后启用。"
}
================================
import '@/common/purge-task-monitor-snapshots' as purgeSnapshots
import com.linewell.dataelement.platform.tenant.application.TenantExecutionCatalog as tenantCatalog
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import org.springframework.core.env.Environment as environment
import log

// 任务：/system-jobs/purge-task-monitor-snapshots。每分钟分租户清理一个有界批次。
// 租户来自控制库启用清单，每个批次独立执行并在结束时释放租户上下文。
var retentionDays = environment.getProperty('nifi.monitor-retention-days', '7')::int(0)
if (retentionDays < 1 || retentionDays > 1000) {
    throw new IllegalArgumentException('nifi.monitor-retention-days 必须在 1–1000 之间')
}

var results = []
for (tenantId in tenantCatalog.activeTenantIds()) {
    var scope = TenantContext.use(tenantId)
    try {
        var result = purgeSnapshots(retentionDays, false)
        results.add({tenantId: tenantId, deleted: result.deleted, hasMore: result.hasMore})
    } catch (error) {
        log.warn('任务监控快照清理失败，tenantId={}', tenantId)
        results.add({tenantId: tenantId, failed: true})
    } finally {
        scope.close()
    }
}
return results
