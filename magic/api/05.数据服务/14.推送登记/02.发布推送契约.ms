{
  "id": "dws_push_registry_publish_v1",
  "groupId": "dws_push_registry_group_v1",
  "name": "02.发布推送契约",
  "path": "/publish",
  "method": "POST",
  "description": "批量发布当前角色可访问的数据源下已登记的数据表契约，返回发布版本和指纹。",
  "requestBody": "{\"datasourceId\":\"当前租户数据源标识\"}",
  "parameters": [],
  "options": [{"name": "access", "value": "TENANT_SESSION"}],
  "headers": [],
  "paths": []
}
================================
import '@/pushregistry/prepare' as prepare
import '@/pushregistry/persist' as persist
import cn.hutool.extra.spring.SpringUtil
import java.util.Map
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import dataScope
import tenantRuntime

// POST /dws/push/publish 发布当前登记角色可访问的数据源契约，不调用外部节点。
// /pushregistry/prepare 构造规范字段和版本，/pushregistry/persist 对照旧版本在租户事务中批量保存。
var errors = SpringUtil.getBean('globalExceptionHandler')
var datasourceId = body instanceof Map && body.datasourceId != null ? (body.datasourceId + '').trim() : ''
if (!datasourceId.matches('[A-Za-z0-9_.-]{1,255}')) {
    return ResponseEntity.ok(errors.handleException(
        new ResponseStatusException(HttpStatus.BAD_REQUEST, 'datasourceId 格式不正确')))
}
var tenantId = tenantRuntime.id()
try {
    var sources = db.table('db_datasource_t').columns(['tid', 'org_id', 'created_by']).where()
        .eq('tenant_id', tenantId).eq('tid', datasourceId)
        .and(w => w.eq('is_del', 0).or().isNull('is_del')).select()
    if (sources.size() == 0) { throw new IllegalArgumentException('登记数据源不存在或无权访问') }
    if (dataScope.visibleDataSourcesFor('MENU:2081000000000000002', sources).isEmpty()) {
        exit 403, '登记数据源不存在或无权访问'
    }
    var plan = prepare(tenantId, datasourceId)
    if (plan.publications.size() == 0) {
        throw new IllegalStateException('数据源尚无登记成功的数据表，无法发布推送规范')
    }
    persist(tenantId, plan.publications)
    var schemas = plan.publications.map(entry => {
        schemaId: entry.schemaId, tableId: entry.tableId, tableName: entry.tableName,
        schemaVersion: entry.version, schemaHash: entry.hash
    })
    return ResponseEntity.ok({code: 0, message: 'success', data: {datasourceId: datasourceId, schemas: schemas}})
} catch (error) {
    return ResponseEntity.ok(errors.handleException(error))
}
