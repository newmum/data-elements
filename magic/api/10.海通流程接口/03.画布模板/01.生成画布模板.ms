{
  "properties": {},
  "id": "nifi_canvas_template_post_v1",
  "script": null,
  "groupId": "061bd630dc3758bdaf894b4508985387",
  "name": "01.生成画布模板",
  "path": "/template",
  "description": "按当前租户目录生成三节点画布或返回已关联的有效画布。只读取登记配置和必要的历史字段，无业务写入。",
  "parameters": [],
  "options": [],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/resource-center/catalog-read' as readCatalog
import '@/common/nifiCatalogPipeline' as readSavedCanvas
import '@/common/nifiTemplateAssets' as readAssets
import '@/common/nifiTemplateSource' as sourceNode
import '@/common/nifiTemplateSink' as targetNode
import '@/common/nifiTemplateMapping' as fieldMappings
import '@/common/nifiPipelineListResponse' as respond
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Dsl as CanvasDsl
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Node as CanvasNode
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Edge as CanvasEdge
import dataScope
import org.springframework.http.ResponseEntity
import java.util.Map
import tenantRuntime

// POST /nifi/canvas/template 生成画布模板。
// POST /nifi/canvas/template 按目录标识 tid 生成新画布或读取已关联画布。
// 先核对目录权限，再读取关联或批量预取来源与目标资产。此接口无业务写入。
var empty = value => value == null || (value + '').isBlank()
var first = (row, keys) => {
    if (row != null) {
        for (key in keys) {
            var value = row[key]
            if (!empty(value)) {
                return value + ''
            }
        }
    }
    return null
}
var coalesce = (value, fallback) => value == null ? fallback : value
var nested = (row, keys) => {
    for (key in keys) {
        var item = row[key]
        if (item instanceof Map && !item.isEmpty()) {
            return item
        }
    }
    return {}
}
var draft = (name, description, nodes, edges) => new Pipeline(null, name, description,
    null, null, new CanvasDsl(1, nodes, edges), null, null, null, null, null, null, null)

return respond(() => {
    var tenantId = tenantRuntime.id()
    if (body == null || empty(body.tid)) {
        return ResponseEntity.badRequest().build()
    }
    var catalogId = (body.tid + '').trim()
    // 先查可见目录，使无权或缺失资源都能按旧 MVC 契约返回空体 404。
    var catalogQuery = db.camel().table('da_catalog_t').columns('tid').where()
        .eq('tenant_id', tenantId).eq('tid', catalogId).eq('is_del', 0)
    var scope = dataScope.resource('MENU:2081000000000000002')
    if (scope.scope == 'OWNER' && !empty(scope.userId)) {
        catalogQuery.eq('created_by', scope.userId)
    } else if (scope.scope == 'ORG' && scope.organizationIds != null && !scope.organizationIds.isEmpty()) {
        catalogQuery.in('org_id', scope.organizationIds)
    } else if (scope.scope != 'ALL') {
        return ResponseEntity.notFound().build()
    }
    if (catalogQuery.selectOne() == null) {
        return ResponseEntity.notFound().build()
    }
    var catalog = readCatalog({mode: 'directory-index-projection', id: catalogId})
    var existing = readSavedCanvas(catalogId)
    if (existing != null) {
        return existing
    }

    var sourceTableId = first(catalog, ['sourceTableId'])
    var targetTableId = first(catalog, ['targetTableId'])
    var sourceDetails = nested(catalog, ['sourceDatasource', 'sourceDataSource', 'sourceDs', 'belongDataSource'])
    var targetDetails = nested(catalog, ['targetDatasource', 'targetDataSource', 'targetDs', 'targetBelongDataSource'])
    var sourceId = coalesce(first(catalog, ['dbId', 'sourceDbId']), first(sourceDetails, ['tid', 'id', 'dbId', 'datasourceId', 'dataSourceId']))
    var targetId = coalesce(first(catalog, ['targetDbId']), first(targetDetails, ['tid', 'id', 'dbId', 'datasourceId', 'dataSourceId']))
    var assets = readAssets({sourceTableId: sourceTableId, targetTableId: targetTableId, sourceDbId: sourceId, targetDbId: targetId})
    if (assets == null) {
        return ResponseEntity.notFound().build()
    }
    var sourceTable = coalesce(assets.tables[sourceTableId], nested(catalog, ['source', 'sourceInfo', 'sourceTableInfo', 'input', 'inputTable']))
    var targetTable = coalesce(assets.tables[targetTableId], nested(catalog, ['target', 'targetInfo', 'targetTableInfo', 'output', 'outputTable']))
    var sourceName = coalesce(first(catalog, ['sourceTable', 'source_table', 'fromTable', 'from_table']),
        first(sourceTable, ['tableName', 'table', 'name', 'tableCode', 'tableNameCn']))
    var targetName = coalesce(first(targetTable, ['tableName', 'targetTableName', 'table', 'name']),
        first(catalog, ['targetTable', 'target_table', 'toTable', 'to_table']))
    if (empty(sourceName) || (targetTable.isEmpty() && empty(targetName))) {
        return ResponseEntity.badRequest().build()
    }

    // 若目录同时保存了数据源和登记表，同一端的两个引用必须指向同一资产。
    if ((sourceId != null && sourceTable.datasourceId != null && sourceId != sourceTable.datasourceId)
        || (targetId != null && targetTable.datasourceId != null && targetId != targetTable.datasourceId)) {
        return ResponseEntity.notFound().build()
    }
    var source = coalesce(assets.sources[coalesce(sourceId, sourceTable.datasourceId)], sourceDetails)
    var target = coalesce(assets.sources[coalesce(targetId, targetTable.datasourceId)], targetDetails)
    var sourceColumns = assets.columns[sourceTableId] == null ? [] : assets.columns[sourceTableId]
    var targetColumns = assets.columns[targetTableId] == null ? [] : assets.columns[targetTableId]
    var input = sourceNode(source, sourceTable, sourceColumns, sourceName)
    input.config.table = sourceName.trim()
    var output = targetNode(target, targetTable, targetColumns, targetName)
    var mapping = fieldMappings(sourceColumns, targetColumns)
    var nodes = [new CanvasNode('source-1', input.manifestKey,
        coalesce(first(sourceTable, ['tableNameCn', 'table_name_cn']), sourceName.trim()), 'source', -120, 120, input.config),
        new CanvasNode('transform-1', 'transform.field-enrichment', '字段治理', 'transform', 420, 120, {mappings: mapping}),
        new CanvasNode('sink-1', output.manifestKey,
            coalesce(first(targetTable, ['tableNameCn', 'table_name_cn']), targetName), 'sink', 960, 120, output.config)]
    var edges = [new CanvasEdge('edge-1', 'source-1', 'transform-1', null), new CanvasEdge('edge-2', 'transform-1', 'sink-1', null)]
    return draft(coalesce(first(catalog, ['name', 'catalogName', 'title']), '资源目录表转换模板').trim(),
        first(catalog, ['description', 'desc', 'remark']), nodes, edges)
})
