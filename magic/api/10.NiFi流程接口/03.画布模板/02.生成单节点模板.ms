{
  "properties": {},
  "id": "2e2c144806278f8c48faf98938ca63f5",
  "script": null,
  "groupId": "061bd630dc3758bdaf894b4508985387",
  "name": "02.生成单节点模板",
  "path": "/node",
  "description": "根据 key 和登记表标识生成来源、目标或字段治理配置。只接受当前行业库及角色范围内的有效表及数据源，返回原始 manifestKey 和 config，无写入。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiTemplateAssets' as readAssets
import '@/common/nifiTemplateSource' as sourceNode
import '@/common/nifiTemplateSink' as targetNode
import '@/common/nifiTemplateMapping' as fieldMappings
import '@/common/nifiPipelineListResponse' as respond
import org.springframework.http.ResponseEntity

// POST /nifi/canvas/node 生成单节点模板。
// POST /nifi/canvas/node 生成单个来源、目标或字段治理节点。
// 用 key 选择节点类型，按 tableId 或来源与目标表 ID 一次预取并校验登记数据。无业务写入。
var text = value => value == null ? '' : (value + '').trim()
return respond(() => {
    var key = text(body?.key)
    var transform = ['transform.field-mapping', 'transform.field-enrichment'].contains(key)
    var sink = ['sink.jdbc', 'sink.hive'].contains(key)
    var source = key.startsWith('source.')
    if (!(source || sink || transform)) {
        return ResponseEntity.badRequest().build()
    }
    var tableId = text(body.tableId)
    var sourceId = transform ? text(body.sourceTableId) : (source ? tableId : null)
    var targetId = transform ? text(body.targetTableId) : (sink ? tableId : null)
    if (transform ? (sourceId.isEmpty() || targetId.isEmpty()) : tableId.isEmpty()) {
        return ResponseEntity.badRequest().build()
    }

    var assets = readAssets({sourceTableId: sourceId, targetTableId: targetId})
    if (assets == null) {
        return ResponseEntity.notFound().build()
    }
    var sourceColumns = assets.columns[sourceId] == null ? [] : assets.columns[sourceId]
    var targetColumns = assets.columns[targetId] == null ? [] : assets.columns[targetId]
    if (transform) {
        var config = {mappings: fieldMappings(sourceColumns, targetColumns)}
        if (key == 'transform.field-enrichment') {
            config.executionMode = 'AUTO'
        }
        return {manifestKey: key, config: config}
    }
    var table = assets.tables[tableId]
    var datasource = assets.sources[table.datasourceId]
    if (datasource == null) {
        return ResponseEntity.notFound().build()
    }
    return source ? sourceNode(datasource, table, sourceColumns, null) : targetNode(datasource, table, targetColumns, null)
})
