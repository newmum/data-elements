{
  "properties": {},
  "id": "3fce842fc70d1d259e9938d81f963e11",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "44.海通持久化画布设计",
  "path": "/nifiWriteCanvas",
  "description": "在调用方的事务里校验并更新已有画布设计，保留既有运行状态并同步字段映射。",
  "parameters": [
    {
      "name": "submitted",
      "description": "请求中的画布设计",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "pipelineId",
      "description": "已有画布主键",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "tenantId",
      "description": "调用入口已核验的行业租户 ID",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "verifyRevision",
      "description": "锁定画布后执行调用入口提供的版本检查",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import '@/common/nifiDecodeCanvas' as decodeCanvas
import com.fasterxml.jackson.databind.ObjectMapper as mapper
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingProjectionService as projections
import com.linewell.dataelement.integration.nifi.canvas.pipeline.BatchSchedulePolicy
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline
import com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService as runtimeStatus
import java.lang.IllegalStateException
import java.lang.Math
import java.lang.System
import java.util.ArrayList
import java.util.Objects
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.slf4j.LoggerFactory
import tableWrite

// @/common/nifiWriteCanvas 共享已有画布的设计更新，调用方持有写事务和共享 NiFi 锁。
// 编辑只更新设计属性，运行状态等字段原样保留。数据库行锁同时保护非 HTTP 写入。
var table = 'nifi_pipeline_t'
var designValues = canvas => {
    var dsl = canvas.dsl()
    return {name: canvas.name(), description: canvas.description(), updated_at: canvas.updatedAt(),
        dsl_json: dsl == null ? null : mapper.writeValueAsString(dsl),
        dsl_hash: dsl == null ? null : DslHasher.hash(dsl),
        dsl_version: dsl == null ? null : dsl.version()}
}
var row = tableWrite.lockRow(table, tenantId, 'id', pipelineId,
    ['id', 'name', 'description', 'created_at', 'updated_at', 'dsl_json', 'nifi_process_group_id', 'status',
        'last_deployed_hash', 'last_deployed_at', 'last_stopped_at', 'node_mapping_json', 'last_bulletin_id', 'is_del'], {})
if (row == null) {
    return null
}
if (row.is_del == 1) {
    return null
}
var current = decodeCanvas({id: row.id, name: row.name, description: row.description, createdAt: row.created_at,
    updatedAt: row.updated_at, dsl: row.dsl_json, processGroupId: row.nifi_process_group_id, status: row.status,
    deployedHash: row.last_deployed_hash, deployedAt: row.last_deployed_at, stoppedAt: row.last_stopped_at,
    mapping: row.node_mapping_json, lastBulletinId: row.last_bulletin_id})
if (current == null) {
    return null
}
verifyRevision(current)

// 历史画布只缺节点 ID 时，按原组及处理器定位后补齐；不能连通或存在歧义仍允许保存设计。
// 定位失败由后续属性同步/部署展示具体诊断，不以当前默认节点覆盖历史关联。
var mapping = current.nodeMapping()
if (current.nifiProcessGroupId() != null && !current.nifiProcessGroupId().isBlank()
    && (mapping?.runtimeNodeId() == null || mapping.runtimeNodeId().isBlank())) {
    try {
        var owner = runtimeStatus.resolveLegacyOwner(current)
        if (owner != null && !owner.isBlank()) {
            if (mapping == null) { mapping = new NifiNodeMapping(null, null, null) }
            mapping = mapping.withRuntimeNode(owner)
        }
    } catch (failure) {
        LoggerFactory.getLogger('com.linewell.dataelement.integration.nifi.canvas.LegacyBinding')
            .warn('历史运行节点未自动确认，保留画布设计及原部署关联，pipeline={}', current.id())
    }
}

var design = HiveSinkPublicationPolicy.forCanvas(submitted)
var description = design.description() == null ? current.description() : design.description()
var unnormalized = new Pipeline(current.id(), design.name(), description, current.createdAt(), current.updatedAt(),
    design.dsl(), current.nifiProcessGroupId(), current.status(), current.lastDeployedHash(),
    current.lastDeployedAt(), current.lastStoppedAt(), current.nodeMapping(), current.lastBulletinId())
// 与映射资产恢复共用规则投影模块。投影失败时整个保存事务回滚。
projections.persist(unnormalized)
var timestamp = current.updatedAt() == null ? System.currentTimeMillis()
    : Math.max(System.currentTimeMillis(), current.updatedAt() + 1L)
var dsl = Objects.equals(design.dsl(), current.dsl()) ? current.dsl()
    : BatchSchedulePolicy.normalize(design.dsl(), current.dsl(), tenantId, current.id())
var saved = new Pipeline(current.id(), design.name(), description, current.createdAt(), timestamp, dsl,
    current.nifiProcessGroupId(), current.status(), current.lastDeployedHash(), current.lastDeployedAt(),
    current.lastStoppedAt(), mapping, current.lastBulletinId())
var values = designValues(saved)
if (!Objects.equals(mapping, current.nodeMapping())) {
    values.node_mapping_json = mapper.writeValueAsString(mapping)
}
// 历史 ORM 更新保留未提供的名称和空 DSL。DRAFT 等旧状态值沿用其规范化语义。
for (key in ['name', 'dsl_json', 'dsl_hash', 'dsl_version']) {
    if (values.get(key) == null) {
        values.remove(key)
    }
}
values.status = current.status().name()
var columns = new ArrayList(values.keySet())
values.id = current.id()
var changed = tableWrite.batchUpdate(table, tenantId, columns, ['id'], {is_del: 0}, [values])
if (changed != 1) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, '流程已被其他入口修改，请重新加载后重试')
}
return saved
