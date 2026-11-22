{
  "id": "collect_rename_v1",
  "groupId": "collection_api_group_v1",
  "name": "09.关联缺失登记表新名称",
  "path": "/rename",
  "method": "POST",
  "parameters": [],
  "options": [
    {
      "name": "jsonBody",
      "value": "OBJECT_MAP"
    },
    {
      "name": "access",
      "value": "TENANT_SESSION"
    }
  ],
  "headers": [],
  "paths": [],
  "description": "当前角色可见来源内，将历史缺失表关联到本次发现的未标注快照。支持 dryRun 校验。"
}
================================
import '@/collect/response' as respond
import cn.hutool.core.collection.CollUtil
import cn.hutool.extra.spring.SpringUtil
import com.fasterxml.jackson.databind.ObjectMapper
import com.linewell.dataelement.platform.configuration.MagicBootConfiguration
import java.util.List
import java.util.Locale
import java.util.Map
import dataScope
import request
import tableRead
import tableWrite
import tenantRuntime
import com.linewell.dataelement.metautil.ddl.DdlDialect

// POST /dst/collect/rename。登记表保留原 ID 和治理属性，合并快照与历史差异同事务保存。
return respond(() => {
    var jsonError = request.get().getAttribute(MagicBootConfiguration.MAGIC_COLLECTION_WRITE_JSON_ERROR)
    if (jsonError != null) {
        throw jsonError
    }
    var text = value => value == null ? '' : (value + '').trim()
    var sourceId = text(body?.dbId)
    var tableId = text(body?.tableId)
    var nextName = text(body?.tableName)
    if (!sourceId.matches('[A-Za-z0-9_-]{1,64}')) {
        throw new IllegalArgumentException('dbId 格式不正确')
    }
    if (tableId.isBlank()) {
        throw new IllegalArgumentException('缺少登记表标识')
    }
    if (nextName.isBlank()) {
        throw new IllegalArgumentException('数据表名不能为空')
    }
    if (nextName.length() > 255) {
        throw new IllegalArgumentException('数据表名不能超过255个字符')
    }
    var tenantId = tenantRuntime.id()
    var active = {is_del: 0}
    var scoped = {datasource_id: sourceId, is_del: 0}
    var validationOnly = body?.dryRun == true
    return db.transaction(() => {
        var source = tableWrite.lockRow('db_datasource_t', tenantId, 'tid', sourceId,
            ['tid', 'org_id', 'created_by', 'db_type'], active)
        if (source == null || !dataScope.canReadDataSource(source)) {
            throw new IllegalArgumentException('登记表不存在或无访问权限')
        }
        var kind = text(source.db_type == null ? source.dbType : source.db_type).toUpperCase(Locale.ROOT)
        var oracleObjects = kind == 'ORACLE' || kind == 'OCEANBASE_ORACLE'
        var normalize = value => oracleObjects ? DdlDialect.oracleObjectKey(text(value)) : text(value).toLowerCase(Locale.ROOT)
        var target = tableWrite.lockRow('db_table_t', tenantId, 'tid', tableId, ['tid', 'table_name'], scoped)
        if (target == null) {
            throw new IllegalArgumentException('登记表不存在或无访问权限')
        }
        var oldName = text(target.table_name)
        var oldKey = normalize(oldName)

        // 来源已经锁定，取同一条成功任务校验与更新，避免用上一任务校验后写入新任务。
        var jobs = tableRead.from('metadata_table_collection_job_t', 'j', tenantId)
        var jobId = tableRead.column('j', 'tid', 'TEXT')
        jobs.where(tableRead.equal(tableRead.column('j', 'datasource_id', 'TEXT'), sourceId))
        jobs.where(tableRead.equal(tableRead.column('j', 'is_del', 'NUMBER'), 0))
        jobs.where(tableRead.equal(tableRead.column('j', 'status', 'TEXT'), 'SUCCEEDED'))
        var newest = jobs.rows({tid: jobId}, [tableRead.column('j', 'created_time', 'TIME'), jobId], ['desc', 'desc'], 1, true)
        var history = newest.isEmpty() ? null : tableWrite.lockRow('metadata_table_collection_job_t', tenantId, 'tid',
            newest[0].tid, ['tid', 'deleted_detail'], {datasource_id: sourceId, is_del: 0, status: 'SUCCEEDED'})
        var json = SpringUtil.getBean(ObjectMapper.class)
        var rows = []
        if (history != null && !text(history.deleted_detail).isBlank()) {
            try {
                var rowType = json.getTypeFactory().constructCollectionType(List.class, Map.class)
                var decoded = json.readValue(text(history.deleted_detail), rowType)
                if (decoded != null) {
                    rows = decoded
                }
            } catch (error) {
                // 历史坏文档不能证明表处于待改名状态。
            }
        }
        if (oldKey == '' || rows.filter(row => normalize(row?.tableName) == oldKey).isEmpty()) {
            throw new IllegalStateException('该表不在当前变更列表中，请重新探查后再修改')
        }
        var merged = {tableId: tableId, oldTableName: oldName, newTableName: nextName, mergedSnapshot: false}
        if (validationOnly) {
            merged.dryRun = true
        }
        if (oldKey == normalize(nextName)) {
            return merged
        }

        var matches = tableRead.from('db_table_t', 't', tenantId)
        var candidateId = tableRead.column('t', 'tid', 'TEXT')
        var annotated = tableRead.column('t', 'annotated', 'NUMBER')
        matches.where(tableRead.equal(tableRead.column('t', 'datasource_id', 'TEXT'), sourceId))
        matches.where(tableRead.equal(tableRead.column('t', 'is_del', 'NUMBER'), 0))
        matches.where(tableRead.not(tableRead.equal(candidateId, tableId)))
        var tableName = tableRead.column('t', 'table_name', 'TEXT')
        var nameKey = oracleObjects ? tableRead.exactTextKey(tableRead.when(tableRead.like(tableName, '%"%'), tableRead.trim(tableName), tableRead.upper(tableRead.trim(tableName)))) : tableRead.lower(tableName)
        matches.where(tableRead.equal(nameKey, normalize(nextName)))
        var unmarked = []
        var unknown = []
        matches.forEachOrderedRow({tid: candidateId, annotated: annotated}, [candidateId], ['asc'], false, row => {
            if (row.annotated == null) {
                unknown.add(row.tid)
            } else {
                if (row.annotated != 0) {
                    throw new IllegalStateException('新表名已对应另一张已标注登记表，不能覆盖')
                }
                unmarked.add(row.tid)
            }
        })
        var count = unmarked.size() + unknown.size()
        if (count == 0) {
            throw new IllegalArgumentException('新表名尚未在本次探查结果中发现，请先重新探查后再关联')
        }
        merged.mergedSnapshot = true
        merged.removedSnapshotCount = count
        if (validationOnly) {
            return merged
        }

        // 旧快照可能以 NULL 表示未标注。各组更新仍带标注状态条件，任一并发修改回滚本次合并。
        var timestamp = new Date()
        var remove = (ids, withoutAnnotation) => {
            for (batch in CollUtil.split(ids, 500)) {
                var write = db.table('db_table_t').where().eq('tenant_id', tenantId)
                    .eq('datasource_id', sourceId).eq('is_del', 0).in('tid', batch)
                if (withoutAnnotation) {
                    write.isNull('annotated')
                } else {
                    write.eq('annotated', 0)
                }
                if (write.update({is_del: 1, updated_time: timestamp}) != batch.size()) {
                    throw new IllegalStateException('表快照在合并期间已变化，请重新探查后再关联')
                }
            }
        }
        remove(unmarked, false)
        remove(unknown, true)
        if (tableWrite.updateWithDatabaseTimestamp('db_table_t', tenantId, 'tid', tableId,
            {table_name: nextName, table_name_en: nextName}, scoped, 'updated_time') != 1) {
            throw new IllegalStateException('登记表在合并期间已变化，请重新探查后再关联')
        }
        var remaining = rows.filter(row => {
            var name = text(row?.tableName)
            if (name == '') {
                name = text(row?.tableNameEn)
            }
            if (name == '') {
                name = text(row?.name)
            }
            return normalize(name) != oldKey
        })
        if (tableWrite.updateWithDatabaseTimestamp('metadata_table_collection_job_t', tenantId, 'tid', history.tid,
            {deleted_count: remaining.size(), deleted_detail: json.writeValueAsString(remaining)},
            {datasource_id: sourceId, is_del: 0, status: 'SUCCEEDED'}, 'updated_time') != 1) {
            throw new IllegalStateException('更新变更表记录失败')
        }
        return merged
    })
})
