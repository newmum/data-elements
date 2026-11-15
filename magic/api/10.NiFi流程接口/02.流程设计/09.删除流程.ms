{
  "properties": {},
  "id": "c6f76e36f0106bc81f93a2f372c80571",
  "script": null,
  "groupId": "cc4166baa42e4584900fcef4d877ed83",
  "name": "09.删除流程",
  "path": "/delete",
  "description": "按当前租户画布标识清理 NiFi 原生组、解绑接入任务并软删除画布。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "method": "POST",
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiPipelineListResponse' as respond
import '@/common/nifiDeletionGroupScope' as groupScope
import '@/common/nifiDecodeCanvas' as decodeCanvas
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect as mutation
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer as organizer
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import java.lang.IllegalStateException
import java.lang.System
import com.linewell.dataelement.elasticsearch.EsCommonService as index
import java.time.LocalDateTime
import org.springframework.http.ResponseEntity
import log
import tableRead
import tableWrite
import tenantRuntime

// POST /nifi/flow/delete 删除流程。 查询参数 id。
// POST /nifi/flow/delete 删除当前租户画布。不存在时返回空体 404。
// 共享锁内预取任务，验证并清理全部原生组，再在同一事务内解绑任务和软删除画布。成功返回 204。
// 只解绑仍指向本次删除任务的目录属性，返回提交后的索引刷新操作。
var unlinkCatalogs = (tenantId, tasks) => {
    // 调用方已锁定流程。目录、任务和扩展属性在一条关联查询中检查，目录搜索补丁在提交后执行。
    var ids = tasks.map(task => task.catalogId).filter(id => id != null && !id.isBlank()).distinct()
    if (ids.isEmpty()) {
        return null
    }
    var text = (alias, column) => tableRead.column(alias, column, 'TEXT')
    var active = alias => tableRead.equal(tableRead.column(alias, 'is_del', 'NUMBER'), 0)
    var props = tableRead.from('da_prop_t', 'p', tenantId)
    props.leftJoin('da_catalog_t', 'c', 'tid', 'parent_id', [active('c')])
    props.where(active('p'))
    props.where(tableRead.not(tableRead.isNull(text('c', 'tid'))))
    props.where(tableRead.in(text('p', 'parent_id'), ids))
    props.where(tableRead.equal(text('p', 'prop_name'), 'accessTaskId'))
    props.where(tableRead.equal(text('p', 'data_type'), 'catalog'))
    var recent = props.ranked('r', {id: text('p', 'tid'), catalogId: text('p', 'parent_id'),
        value: tableRead.column('p', 'prop_value', 'LOB'), count: tableRead.column('c', 'market_view_count', 'NUMBER')},
        [text('p', 'parent_id')], [tableRead.column('p', 'updated_time', 'TIME'), text('p', 'tid')], ['desc', 'desc'])
    var rows = recent.grouped({id: text('r', 'id'), catalogId: text('r', 'catalogId'),
        value: tableRead.column('r', 'value', 'LOB'), count: tableRead.column('r', 'count', 'NUMBER')}, [])
    var owned = {}
    for (task in tasks) {
        if (task.catalogId != null) {
            if (owned[task.catalogId] == null) {
                owned[task.catalogId] = []
            }
            owned[task.catalogId].add(task.id)
        }
    }
    var pending = rows.filter(row => row.value != null && owned[row.catalogId].contains(row.value))
    if (pending.isEmpty()) {
        return null
    }
    var locked = tableWrite.lockRows('da_prop_t', tenantId, 'tid', pending.map(row => row.id),
        ['tid','parent_id','prop_value'], {is_del: 0, data_type: 'catalog', prop_name: 'accessTaskId'})
    var locks = {}
    for (row in locked) {
        locks[row.tid] = row
    }
    var updates = []
    var catalogs = []
    var docs = []
    var counters = []
    var now = LocalDateTime.now()
    for (row in pending) {
        var current = locks[row.id]
        if (current != null && current.parent_id == row.catalogId && current.prop_value == row.value) {
            updates.add({tid: row.id, prop_value: '', prop_type: '0', updated_time: now})
            catalogs.add(row.catalogId)
            docs.add({tid: row.catalogId, tenantId: tenantId, assetType: 'catalog', accessTaskId: ''})
            counters.add(row.count == null ? 0 : row.count)
        }
    }
    if (updates.isEmpty()) {
        return null
    }
    tableWrite.batchUpdate('da_prop_t', tenantId, ['prop_value','prop_type','updated_time'], ['tid'],
        {is_del: 0, data_type: 'catalog', prop_name: 'accessTaskId'}, updates)
    return () => index.bulkMergeCatalogProjections('dataassets', catalogs, docs, counters)
}

return respond(() => mutation.withMutationLock(() => {
    var tenantId = tenantRuntime.id()
    var flowId = param.id
    var result = db.transaction(() => {
        var row = tableWrite.lockRow('nifi_pipeline_t', tenantId, 'id', flowId, ['id','name','description',
            'created_at','updated_at','dsl_json','nifi_process_group_id','status','last_deployed_hash',
            'last_deployed_at','last_stopped_at','node_mapping_json','last_bulletin_id'], {is_del: 0})
        if (row == null) {
            return {found: false}
        }
        var flow = decodeCanvas({id: row.id, name: row.name, description: row.description, createdAt: row.created_at,
            updatedAt: row.updated_at, dsl: row.dsl_json, processGroupId: row.nifi_process_group_id, status: row.status,
            deployedHash: row.last_deployed_hash, deployedAt: row.last_deployed_at, stoppedAt: row.last_stopped_at,
            mapping: row.node_mapping_json, lastBulletinId: row.last_bulletin_id})
        if (flow == null) {
            return {found: false}
        }
        var query = tableRead.from('data_access_agg_task_t', 't', tenantId)
        query.where(tableRead.equal(tableRead.column('t', 'pipeline_id', 'TEXT'), flowId))
        query.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
        var taskId = tableRead.column('t', 'tid', 'TEXT')
        var tasks = query.rows({id: taskId, catalogId: tableRead.column('t', 'data_catalog_id', 'TEXT'),
            processGroupId: tableRead.column('t', 'process_group_id', 'TEXT')}, [taskId], ['asc'], 1001, true)
        if (tasks.size() > 1000) {
            throw new IllegalStateException('画布关联任务超过删除上限')
        }
        var nativeIds = tasks.map(task => task.processGroupId)
        nativeIds.add(flow.nifiProcessGroupId())
        if (flow.nodeMapping() != null && flow.nodeMapping().candidate() != null) {
            nativeIds.add(flow.nodeMapping().candidate().processGroupId())
        }
        nativeIds = nativeIds.filter(id => id != null && !id.isBlank()).distinct()
        // 从共享原生协议模块找回历史受管组。原生删除失败时 SQL 事务回滚，可重试缺失组。
        if (!nativeIds.isEmpty() || flow.lastDeployedAt() != null || flow.lastDeployedHash() != null || flow.nodeMapping() != null
            || !['SAVED','DRAFT'].contains(flow.status().name())) {
            var runtime = nifi.bindRuntimeNode()
            try {
                var ids = organizer.existingIds(flow, nativeIds)
                var targets = ids.map(id => ({id: id, linked: nativeIds.contains(id)}))
                var rootId = nifi.getRootProcessGroupId()
                var groups = groupScope(targets, rootId, flowId)
                var parents = groups.map(group => group.parentId).filter(id => !id.isBlank() && id != rootId).distinct()
                for (group in groups) {
                    nifi.cleanupProcessGroup(group.id)
                }
                organizer.pruneEmptyAncestors(parents, null)
            } finally {
                if (runtime != null) {
                    runtime.close()
                }
            }
        }

        var refresh = unlinkCatalogs(tenantId, tasks)
        db.normal().table('data_access_agg_task_t').exclude('updated_by').withBlank().where()
            .eq('tenant_id', tenantId).eq('is_del', 0).eq('pipeline_id', flowId)
            .update({is_del: 1, task_status: 0, process_group_id: null, updated_time: LocalDateTime.now()})
        var changed = tableWrite.batchUpdate('nifi_pipeline_t', tenantId, ['is_del','updated_at'], ['id'],
            {}, [{id: flowId, is_del: 1, updated_at: System.currentTimeMillis()}])
        if (changed != 1) {
            throw new IllegalStateException('流程删除写入未确认')
        }
        return {found: true, refresh: refresh}
    })
    if (!result.found) {
        return ResponseEntity.notFound().build()
    }
    if (result.refresh != null) {
        try {
            result.refresh()
        } catch (failure) {
            // 目录库是权威来源。搜索投影失败保留诊断，避免重建已经清理的 NiFi 组。
            log.warn('流程 {} 删除后的目录搜索投影刷新失败', flowId)
        }
    }
    return ResponseEntity.noContent().build()
}))
